package com.example.payroll;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.*;
import org.springframework.cloud.client.*;
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest(classes=GatewayReplicaRoutingTest.Config.class,webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
    "server.ssl.enabled=false","server.ssl.key-store=classpath:unused.p12","server.ssl.key-store-password=test-only",
    "eureka.client.enabled=false","eureka.client.service-url.defaultZone=http://localhost:8761/eureka/"})
class GatewayReplicaRoutingTest extends SignedTokensSupport {
    static final HttpServer FIRST=backend("first");static final HttpServer SECOND=backend("second");
    @TestConfiguration(proxyBeanMethods=false) @EnableAutoConfiguration @Import(SecurityConfig.class)
    @org.springframework.cloud.loadbalancer.annotation.LoadBalancerClient(name="retro-adjustment-service",configuration=ReplicaSupplier.class)
    static class Config {}
    static class ReplicaSupplier {
        @Bean ServiceInstanceListSupplier replicas(){return new ServiceInstanceListSupplier(){
            public String getServiceId(){return "retro-adjustment-service";}
            public Flux<List<ServiceInstance>> get(){return Flux.just(List.of(
                new DefaultServiceInstance("retro-one",getServiceId(),"localhost",FIRST.getAddress().getPort(),false),
                new DefaultServiceInstance("retro-two",getServiceId(),"localhost",SECOND.getAddress().getPort(),false)));}
        };}
    }
    @LocalServerPort int port;
    private static HttpServer backend(String label){
        try{var server=HttpServer.create(new java.net.InetSocketAddress("localhost",0),0);
            server.createContext("/",exchange -> {byte[] body=label.getBytes(java.nio.charset.StandardCharsets.UTF_8);exchange.sendResponseHeaders(200,body.length);try(var stream=exchange.getResponseBody()){stream.write(body);}});
            server.start();return server;
        }catch(Exception error){throw new IllegalStateException(error);}
    }
    @AfterAll static void stop(){FIRST.stop(0);SECOND.stop(0);}
    @Test void gatewayBalancesThenRetriesReadsOnSurvivingReplica(){
        var client=WebTestClient.bindToServer().baseUrl("http://localhost:"+port).responseTimeout(java.time.Duration.ofSeconds(15)).build();
        String token=bearer("payrollClerk","EMP001");var observed=new HashSet<String>();
        for(int n=0;n<4;n++)observed.add(client.get().uri("/api/retro-adjustments/employees/EMP001").header("Authorization",token).exchange().expectStatus().isOk().expectBody(String.class).returnResult().getResponseBody());
        assertEquals(Set.of("first","second"),observed);
        FIRST.stop(0); // Supplier intentionally retains the stale instance: exercise the actual GET retry filter.
        for(int n=0;n<4;n++)client.get().uri("/api/retro-adjustments/employees/EMP001").header("Authorization",token).exchange().expectStatus().isOk().expectBody(String.class).isEqualTo("second");
    }
}
