package com.example.payroll.verification;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import javax.net.ssl.*;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.security.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.ZipFile;
import static org.assertj.core.api.Assertions.*;

/** Runs the real packaged applications; no application controllers/repositories are mocked. */
final class PayrollRig implements AutoCloseable {
    static final ObjectMapper JSON=JsonMapper.builder().findAndAddModules()
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).build();
    final Path root=Path.of("..").toAbsolutePath().normalize();
    final Path run=Path.of("target","system-run-"+UUID.randomUUID()).toAbsolutePath();
    final Path evidence=Path.of("target","evidence").toAbsolutePath();
    final Map<String,Integer> ports=new LinkedHashMap<>();
    final Map<String,Process> active=new LinkedHashMap<>();
    final List<Process> started=new ArrayList<>();
    final Map<Integer,String> lastResponses=new ConcurrentHashMap<>();
    final String brokers;
    final String discoveryUser="discovery",discoveryPassword=secret(),tlsPassword=secret(),servicePassword=secret();
    final String allowancePassword=secret(),periodPassword=secret(),retroPassword=secret();
    final String clerkPassword="Clerk@123",approverPassword="Approver@123";
    HttpClient http;
    String clerk,approver;
    boolean closed;
    long startupDeadline=Long.MAX_VALUE;
    PayrollRig(String brokers){this.brokers=brokers;}
    static String secret(){return UUID.randomUUID().toString().replace("-","")+UUID.randomUUID().toString().replace("-","");}
    static String executable(String command){return Path.of(System.getProperty("java.home"),"bin",command+(System.getProperty("os.name").startsWith("Windows")?".exe":"")).toString();}
    static int port() throws Exception {try(var socket=new java.net.ServerSocket(0)){return socket.getLocalPort();}}
    void start() throws Exception {
        startupDeadline=System.nanoTime()+TimeUnit.MINUTES.toNanos(6);
        Files.createDirectories(run);Files.createDirectories(evidence);
        for(String app:List.of("discovery-server","auth-service","allowance-rule-service","pay-period-service","retro-one","retro-two","api-gateway","retro-store")){int candidate;do{candidate=port();}while(ports.containsValue(candidate));ports.put(app,candidate);}
        for(String app:List.of("discovery-server","auth-service","allowance-rule-service","pay-period-service","retro-adjustment-service","api-gateway"))
            assertThat(Files.isRegularFile(jar(app))).as("Build packaged apps with root mvn verify before running verification alone: %s",app).isTrue();
        keysAndTls();startStore();
        launch("discovery-server");await("Eureka HTTPS ready",120,() -> response(ports.get("discovery-server"),"GET","/eureka/apps",null,null,true).statusCode()==200);
        launch("auth-service");await("local Auth login",120,() -> {
            var response=response(ports.get("auth-service"),"POST","/api/auth/token",null,JSON.writeValueAsString(Map.of("username","clerk","password",clerkPassword)),false);
            if(response.statusCode()!=200)return false;clerk=JSON.readTree(response.body()).path("accessToken").asText();return !clerk.isBlank();
        });
        launch("allowance-rule-service");launch("pay-period-service");
        await("synthetic rule and immutable statements",120,() -> {
            var rules=response(ports.get("allowance-rule-service"),"GET","/api/allowances/employees/EMP001",clerk,null,false);
            var history=response(ports.get("pay-period-service"),"GET","/api/pay-periods/employees/EMP001/snapshots",clerk,null,false);
            return rules.statusCode()==200 && history.statusCode()==200 && JSON.readTree(rules.body()).size()==1 && JSON.readTree(history.body()).size()==2;
        });
        launch("retro-one");await("first Retro DB schema ready",120,() -> response(ports.get("retro-one"),"GET","/api/retro-adjustments/employees/EMP001",clerk,null,false).statusCode()==200);
        launch("retro-two");launch("api-gateway");
        await("all six app names and both Retro instances registered",120,() -> registered().size()==7);
        await("Gateway discovery routes ready",120,() -> get("/api/pay-periods/employees/EMP001/snapshots",clerk).statusCode()==200 && get("/api/retro-adjustments/employees/EMP001",clerk).statusCode()==200);
        awaitRetroReady();
        approver=JSON.readTree(request("POST","/api/auth/token",null,JSON.writeValueAsString(Map.of("username","approver","password",approverPassword))).body()).path("accessToken").asText();
        assertThat(approver).isNotBlank();
        startupDeadline=Long.MAX_VALUE;
    }
    Path jar(String app){return root.resolve(app+"/target/"+app+"-1.0.0-SNAPSHOT.jar");}
    void keysAndTls() throws Exception {
        var generator=KeyPairGenerator.getInstance("RSA");generator.initialize(2048);var pair=generator.generateKeyPair();
        Files.writeString(run.resolve("jwt-private.pem"),pem("PRIVATE KEY",pair.getPrivate().getEncoded()));
        Files.writeString(run.resolve("jwt-public.pem"),pem("PUBLIC KEY",pair.getPublic().getEncoded()));
        var command=new ProcessBuilder(executable("keytool"),"-genkeypair","-alias","localhost","-keyalg","RSA","-keysize","2048","-validity","2","-dname","CN=localhost","-ext","SAN=dns:localhost,ip:127.0.0.1","-storetype","PKCS12","-keystore",run.resolve("localhost.p12").toString(),"-storepass:env","TLS_KEYSTORE_PASSWORD");
        command.environment().put("TLS_KEYSTORE_PASSWORD",tlsPassword);command.redirectErrorStream(true).redirectOutput(run.resolve("keytool.log").toFile());
        var tool=command.start();started.add(tool);assertThat(tool.waitFor(45,TimeUnit.SECONDS)).isTrue();assertThat(tool.exitValue()).as("keytool generation; see ignored run directory").isZero();
        var keyStore=KeyStore.getInstance("PKCS12");try(var input=Files.newInputStream(run.resolve("localhost.p12"))){keyStore.load(input,tlsPassword.toCharArray());}
        var trust=KeyStore.getInstance("PKCS12");trust.load(null,"changeit".toCharArray());trust.setCertificateEntry("localhost",keyStore.getCertificate("localhost"));
        try(var output=Files.newOutputStream(run.resolve("trust.p12"))){trust.store(output,"changeit".toCharArray());}
        var managers=TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());managers.init(trust);
        var context=SSLContext.getInstance("TLS");context.init(null,managers.getTrustManagers(),null);
        http=HttpClient.newBuilder().sslContext(context).connectTimeout(Duration.ofSeconds(5)).version(HttpClient.Version.HTTP_1_1).build();
    }
    static String pem(String label,byte[] bytes){return "-----BEGIN "+label+"-----\n"+Base64.getMimeEncoder(64,new byte[]{'\n'}).encodeToString(bytes)+"\n-----END "+label+"-----\n";}
    void startStore() throws Exception {
        Path h2=run.resolve("h2.jar");try(var zip=new ZipFile(jar("retro-adjustment-service").toFile())){
            var entry=zip.stream().filter(e -> e.getName().startsWith("BOOT-INF/lib/h2-") && e.getName().endsWith(".jar")).findFirst().orElseThrow();
            try(var input=zip.getInputStream(entry)){Files.copy(input,h2);}
        }
        Files.createDirectories(run.resolve("retro"));
        launchProcess("retro-store",List.of(executable("java"),"-Xmx128m","-XX:ActiveProcessorCount=2","-cp",h2.toString(),"org.h2.tools.Server","-tcp","-tcpPort",ports.get("retro-store").toString(),"-baseDir",run.resolve("retro").toString(),"-ifNotExists"),Map.of());
        await("independent Retro H2 server",30,() -> {try(var socket=new Socket()){socket.connect(new InetSocketAddress("localhost",ports.get("retro-store")),300);return true;}});
    }
    void launch(String name) throws Exception {
        String app=name.startsWith("retro-")?"retro-adjustment-service":name;
        var args=new ArrayList<>(List.of(executable("java"),"-Xmx256m","-XX:ActiveProcessorCount=2","-Djavax.net.ssl.trustStore="+run.resolve("trust.p12"),"-Djavax.net.ssl.trustStorePassword=changeit","-jar",jar(app).toString(),
            "--server.port="+ports.get(name),"--spring.application.admin.enabled=true",
            "--eureka.client.service-url.defaultZone=https://${EUREKA_USER}:${EUREKA_PASSWORD}@localhost:"+ports.get("discovery-server")+"/eureka/",
            "--eureka.client.registry-fetch-interval-seconds=2","--eureka.instance.lease-renewal-interval-in-seconds=2","--eureka.instance.lease-expiration-duration-in-seconds=6",
            "--payroll.business-date=2026-10-25","--payroll.outbox.delay=500","--payroll.workflow.delay=500","--spring.kafka.admin.fail-fast=true",
            "--spring.kafka.producer.properties.max.block.ms=5000"));
        var env=new HashMap<String,String>();
        env.put("EUREKA_USER",discoveryUser);env.put("EUREKA_PASSWORD",discoveryPassword);
        env.put("TLS_KEYSTORE",run.resolve("localhost.p12").toUri().toString());env.put("TLS_KEYSTORE_PASSWORD",tlsPassword);
        env.put("JWT_PUBLIC_KEY",run.resolve("jwt-public.pem").toUri().toString());env.put("JWT_ISSUER","https://localhost:"+ports.get("auth-service"));
        env.put("KAFKA_BOOTSTRAP_SERVERS",brokers);env.put("CORS_ORIGINS","https://localhost:3000");env.put("PAYROLL_SEED_ENABLED","true");
        if(app.equals("auth-service")){
            env.put("AUTH_DEMO_ENABLED","true");env.put("JWT_PRIVATE_KEY",run.resolve("jwt-private.pem").toUri().toString());
            env.put("CLERK_PASSWORD",clerkPassword);env.put("APPROVER_PASSWORD",approverPassword);env.put("RETRO_SERVICE_PASSWORD",servicePassword);
            env.put("CLERK_EMPLOYEES","EMP001");env.put("APPROVER_EMPLOYEES","EMP001");env.put("RETRO_SERVICE_EMPLOYEES","EMP001");
        }
        if(app.equals("allowance-rule-service")){env.put("ALLOWANCE_DB_USER","allowance_owner");env.put("ALLOWANCE_DB_PASSWORD",allowancePassword);args.add("--spring.datasource.url=jdbc:h2:file:"+file("allowance/payroll")+";DB_CLOSE_ON_EXIT=FALSE");}
        if(app.equals("pay-period-service")){env.put("PAY_PERIOD_DB_USER","period_owner");env.put("PAY_PERIOD_DB_PASSWORD",periodPassword);args.add("--spring.datasource.url=jdbc:h2:file:"+file("period/payroll")+";DB_CLOSE_ON_EXIT=FALSE");}
        if(app.equals("retro-adjustment-service")){env.put("RETRO_DB_USER","retro_owner");env.put("RETRO_DB_PASSWORD",retroPassword);env.put("RETRO_SERVICE_PASSWORD",servicePassword);args.add("--spring.datasource.url=jdbc:h2:tcp://localhost:"+ports.get("retro-store")+"/./payroll;LOCK_TIMEOUT=60000");}
        if(app.equals("discovery-server")){args.add("--eureka.server.enable-self-preservation=false");args.add("--eureka.server.eviction-interval-timer-in-ms=1000");}
        launchProcess(name,args,env);
    }
    String file(String suffix){return run.resolve(suffix).toString().replace('\\','/');}
    void launchProcess(String name,List<String> args,Map<String,String> env) throws Exception {
        if(active.containsKey(name) && active.get(name).isAlive())throw new IllegalStateException("Already running: "+name);
        var builder=new ProcessBuilder(args);builder.directory(root.toFile());
        builder.environment().keySet().removeIf(k -> k.endsWith("PASSWORD") || k.endsWith("DB_USER") || k.startsWith("JWT_") || k.equals("AUTH_DEMO_ENABLED") || k.equals("SPRING_PROFILES_ACTIVE") || k.equals("JAVA_TOOL_OPTIONS") || k.equals("JDK_JAVA_OPTIONS"));
        builder.environment().putAll(env);builder.redirectErrorStream(true).redirectOutput(run.resolve(name+".log").toFile());
        var process=builder.start();active.put(name,process);started.add(process);
    }
    void stop(String name) throws Exception {
        var process=active.get(name);assertThat(process).isNotNull();assertThat(process.isAlive()).as("%s is running before stop",name).isTrue();
        var helper=new ProcessBuilder(executable("java"),"--add-modules","jdk.attach","--source","17",root.resolve("scripts/StopLocal.java").toString(),Long.toString(process.pid()),"retro-adjustment-service",ports.get(name).toString());
        helper.redirectErrorStream(true).redirectOutput(run.resolve(name+"-stop.log").toFile());var stopper=helper.start();started.add(stopper);
        if(!stopper.waitFor(90,TimeUnit.SECONDS)){stopper.destroyForcibly();throw new AssertionError("Graceful stop timed out: "+name);}
        assertThat(stopper.exitValue()).as("Graceful stop via local admin; ignored log %s",name).isZero();
        assertThat(process.waitFor(10,TimeUnit.SECONDS)).isTrue();assertThat(process.isAlive()).isFalse();
        active.remove(name);
    }
    HttpResponse<String> get(String path,String token) throws Exception {return request("GET",path,token,null);}
    HttpResponse<String> request(String method,String path,String token,String body) throws Exception {return response(ports.get("api-gateway"),method,path,token,body,false);}
    HttpResponse<String> response(int port,String method,String path,String token,String body,boolean basic) throws Exception {
        var request=HttpRequest.newBuilder(URI.create("https://localhost:"+port+path)).timeout(Duration.ofSeconds(15)).header("Accept","application/json");
        if(token!=null)request.header("Authorization","Bearer "+token);
        if(basic)request.header("Authorization","Basic "+Base64.getEncoder().encodeToString((discoveryUser+":"+discoveryPassword).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        if(body!=null)request.header("Content-Type","application/json");
        request.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body));
        try{var result=http.send(request.build(),HttpResponse.BodyHandlers.ofString());lastResponses.put(port,"HTTP "+result.statusCode());return result;}
        catch(Exception error){lastResponses.put(port,error.getClass().getSimpleName()+" cause="+(error.getCause()==null?"none":error.getCause().getClass().getSimpleName()));throw error;}
    }
    List<JsonNode> registered() throws Exception {
        var response=response(ports.get("discovery-server"),"GET","/eureka/apps",null,null,true);
        if(response.statusCode()!=200)return List.of();var result=new ArrayList<JsonNode>();
        for(var app:items(JSON.readTree(response.body()).path("applications").path("application")))result.addAll(items(app.path("instance")));
        return result;
    }
    static List<JsonNode> items(JsonNode node){var result=new ArrayList<JsonNode>();if(node.isArray())node.forEach(result::add);else if(node.isObject())result.add(node);return result;}
    interface Check {boolean done() throws Exception;}
    void awaitRetro(String name) throws Exception {
        await(name+" authenticated HTTPS ready",120,() -> {
            var result=response(ports.get(name),"GET","/api/retro-adjustments/employees/EMP001",clerk,null,false);
            if(result.statusCode()!=200)throw new IllegalStateException(name+" HTTP "+result.statusCode());return true;
        });
    }
    void awaitGatewayStable() throws Exception {
        int[] consecutive={0};
        await("Gateway stable after discovery refresh",60,() -> {
            try{var result=get("/api/retro-adjustments/employees/EMP001",clerk);if(result.statusCode()!=200){consecutive[0]=0;throw new IllegalStateException("Gateway readiness HTTP "+result.statusCode());}return ++consecutive[0]>=12;}
            catch(Exception error){consecutive[0]=0;throw error;}
        });
    }
    void awaitRetroReady() throws Exception {
        for(String name:List.of("retro-one","retro-two"))awaitRetro(name);
        await("both Retro registrations UP",60,() -> registered().stream().filter(n -> n.path("app").asText().equals("RETRO-ADJUSTMENT-SERVICE") && n.path("status").asText().equals("UP")).count()==2);
        var observed=new HashSet<String>();
        await("Gateway serves both ready replicas",60,() -> {
            var result=get("/api/retro-adjustments/employees/EMP001",clerk);
            if(result.statusCode()!=200){observed.clear();throw new IllegalStateException("Gateway readiness HTTP "+result.statusCode());}
            observed.add(result.headers().firstValue("X-Payroll-Instance").orElseThrow());return observed.size()==2;
        });
        awaitGatewayStable();
    }
    String diagnostics(){
        var result=new StringBuilder("Processes: ");
        active.forEach((name,process) -> result.append(name).append(" pid=").append(process.pid()).append(process.isAlive()?" alive":" exit="+process.exitValue()).append("; "));
        return result.append(" last HTTP outcomes=").append(lastResponses).append(" logs=").append(run).toString();
    }
    void await(String description,int seconds,Check check) throws Exception {
        long begin=System.nanoTime(),until=Math.min(startupDeadline,begin+TimeUnit.SECONDS.toNanos(seconds));Exception last=null;
        System.out.println("Readiness: "+description+" (budget <= "+seconds+"s)");
        while(System.nanoTime()<until){
            for(var entry:active.entrySet())if(!entry.getValue().isAlive())throw new AssertionError("Child exited during "+description+": "+diagnostics());
            try{if(check.done()){System.out.println("Ready: "+description+" after "+TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-begin)+"ms");return;}}catch(InterruptedException error){Thread.currentThread().interrupt();throw error;}catch(Exception error){last=error;}
            Thread.sleep(250);
        }
        throw new AssertionError("Timed out after "+seconds+"s: "+description+"; last="+(last==null?"condition false":last.getClass().getSimpleName())+"; "+diagnostics(),last);
    }
    void evidence(String file,Object value) throws Exception {JSON.writerWithDefaultPrettyPrinter().writeValue(evidence.resolve(file).toFile(),value);}
    public synchronized void close(){
        if(closed)return;closed=true;boolean interrupted=false;
        var reverse=new ArrayList<>(started);Collections.reverse(reverse);
        for(var process:reverse)if(process.isAlive())process.destroy();
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(20);
        for(var process:reverse)try{if(!process.waitFor(Math.max(1,deadline-System.nanoTime()),TimeUnit.NANOSECONDS))process.destroyForcibly();}catch(InterruptedException error){interrupted=true;process.destroyForcibly();}
        for(var process:reverse)try{if(!process.waitFor(5,TimeUnit.SECONDS))System.err.println("Cleanup could not reap owned PID "+process.pid());}catch(InterruptedException error){interrupted=true;process.destroyForcibly();}
        if(interrupted)Thread.currentThread().interrupt();
    }
}
