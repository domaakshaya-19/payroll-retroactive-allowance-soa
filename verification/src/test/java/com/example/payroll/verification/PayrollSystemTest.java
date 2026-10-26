package com.example.payroll.verification;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.networknt.schema.*;
import org.apache.kafka.clients.admin.*;
import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.clients.producer.*;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(classes=PayrollSystemTest.Harness.class,webEnvironment=SpringBootTest.WebEnvironment.NONE,properties={
    "logging.level.org.apache.kafka=WARN","logging.level.kafka=WARN","logging.level.org.springframework.kafka=WARN","logging.level.state.change.logger=WARN"})
@EmbeddedKafka(kraft=true,partitions=3,topics={"soa.s08.t04.events","soa.s08.t04.events.DLT","payroll.retro-approved.v1","payroll.retro-approved.v1.DLT"},brokerProperties={"auto.create.topics.enable=false","group.initial.rebalance.delay.ms=0","offsets.topic.num.partitions=3"})
@DirtiesContext
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PayrollSystemTest {
    @SpringBootConfiguration @EnableAutoConfiguration static class Harness {}
    @Autowired EmbeddedKafkaBroker broker;
    PayrollRig rig;
    KafkaProducer<String,String> producer;
    KafkaConsumer<String,String> eventObserver,dltObserver;
    AdminClient admin;
    JsonNode originalSnapshots,published,adjustments;
    String publishedJson;
    RecordMetadata publishedOffset;
    final Map<String,Object> facts=new LinkedHashMap<>();
    static final String EVENTS="soa.s08.t04.events",BASE="/api/retro-adjustments/employees/EMP001";
    @BeforeAll void startRealSystem() throws Exception {
        rig=new PayrollRig(broker.getBrokersAsString());
        Runtime.getRuntime().addShutdownHook(new Thread(rig::close));
        try{
            var props=new Properties();props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,broker.getBrokersAsString());props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,StringSerializer.class);props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,StringSerializer.class);props.put(ProducerConfig.ACKS_CONFIG,"all");props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG,true);
            producer=new KafkaProducer<>(props);admin=AdminClient.create(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG,broker.getBrokersAsString()));
            eventObserver=observer(EVENTS);dltObserver=observer(EVENTS+".DLT");rig.start();
            facts.put("startedAt",Instant.now().toString());facts.put("broker","real embedded Kafka KRaft");facts.put("transport","HTTPS with generated trusted localhost certificate");facts.put("applicationProcesses",7);
        }catch(Exception|AssertionError error){rig.close();throw error;}
    }
    KafkaConsumer<String,String> observer(String topic){
        var props=new Properties();props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,broker.getBrokersAsString());props.put(ConsumerConfig.GROUP_ID_CONFIG,"verification-"+UUID.randomUUID());props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,"earliest");props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG,false);props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,StringDeserializer.class);props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,StringDeserializer.class);
        var consumer=new KafkaConsumer<String,String>(props);consumer.subscribe(List.of(topic));return consumer;
    }
    @AfterAll void closeSystem() throws Exception {
        try{if(rig!=null){facts.put("finishedAt",Instant.now().toString());rig.evidence("system-facts.json",facts);}}
        finally{if(rig!=null)rig.close();try{if(eventObserver!=null)eventObserver.close(Duration.ofSeconds(5));}finally{try{if(dltObserver!=null)dltObserver.close(Duration.ofSeconds(5));}finally{try{if(producer!=null)producer.close(Duration.ofSeconds(5));}finally{if(admin!=null)admin.close(Duration.ofSeconds(5));}}}}
    }
    @Test @Order(1) void eurekaRegistersAllSixApplicationsAndBothRetroInstances() throws Exception {
        var instances=rig.registered();assertThat(instances).hasSize(7);
        assertThat(instances.stream().map(n -> n.path("app").asText()).collect(java.util.stream.Collectors.toSet()))
            .containsExactlyInAnyOrder("AUTH-SERVICE","ALLOWANCE-RULE-SERVICE","PAY-PERIOD-SERVICE","RETRO-ADJUSTMENT-SERVICE","DISCOVERY-SERVER","API-GATEWAY");
        assertThat(instances.stream().filter(n -> n.path("app").asText().equals("RETRO-ADJUSTMENT-SERVICE")).count()).isEqualTo(2);
        facts.put("eureka",Map.of("applicationNames",6,"registeredInstances",7,"retroInstances",2));
    }
    @Test @Order(2) void authenticatedGatewayBackdatePublishesAndActuallyConsumesIntoPersistedAdjustments() throws Exception {
        assertThat(rig.get(BASE,null).statusCode()).isEqualTo(401);
        assertThat(rig.get("/api/retro-adjustments/employees/EMP999",rig.clerk).statusCode()).isEqualTo(403);
        assertThat(rig.request("POST",BASE+"/1/approve",rig.clerk,null).statusCode()).isEqualTo(403);
        originalSnapshots=body(rig.get("/api/pay-periods/employees/EMP001/snapshots",rig.clerk));assertThat(originalSnapshots.size()).isEqualTo(2);
        for(var snapshot:originalSnapshots)assertSchema("historical-snapshot-v1.schema.json",snapshot);
        var response=rig.request("POST","/api/allowances/employees/EMP001/HOUSING/backdate",rig.clerk,"{\"expectedVersion\":1,\"monthlyAmount\":3000.00,\"effectiveFrom\":\"2026-08-01\"}");
        assertThat(response.statusCode()).isEqualTo(201);
        var message=poll(eventObserver,record -> record.value().contains("\"newVersion\":2"),60);
        publishedJson=message.value();published=PayrollRig.JSON.readTree(publishedJson);
        assertSchema("allowance-rule-backdated-v1.schema.json",published);assertThat(message.key()).isEqualTo(published.path("aggregateId").asText());
        String id=published.path("eventId").asText();
        rig.await("real Kafka consumption and downstream COMMITTED workflow",90,() -> {
            var workflows=body(rig.get(BASE+"/workflows",rig.clerk));for(var workflow:workflows)if(workflow.path("eventId").asText().equals(id))return workflow.path("status").asText().equals("COMPLETED");return false;
        });
        adjustments=body(rig.get(BASE,rig.clerk));assertTwoAdjustments();
        facts.put("workflow",Map.of("eventId",id,"publishedKey",message.key(),"eventSchemaVersion",1,"sourcePeriods",2,"draftTotal","2000.00","status","COMPLETED","clerkApprovalStatus",403));
    }
    void assertTwoAdjustments() throws Exception {
        var rows=body(rig.get(BASE,rig.clerk));assertThat(rows.size()).isEqualTo(2);
        var open=body(rig.get("/api/pay-periods/employees/EMP001/open",rig.clerk));assertThat(open.path("startsOn").asText()).isEqualTo("2026-10-01");
        BigDecimal total=BigDecimal.ZERO;var periods=new HashSet<Long>();
        for(var row:rows){assertThat(row.path("eventId").asText()).isEqualTo(published.path("eventId").asText());assertThat(row.path("paymentPeriodId").asLong()).isEqualTo(open.path("id").asLong());assertThat(row.path("statementId").asLong()).isPositive();assertThat(row.path("difference").decimalValue()).isEqualByComparingTo("1000.00");total=total.add(row.path("difference").decimalValue());periods.add(row.path("sourcePeriodId").asLong());}
        assertThat(periods).hasSize(2);assertThat(total).isEqualByComparingTo("2000.00");
    }
    @Test @Order(3) void duplicateKafkaDeliveryHasOneBusinessEffect() throws Exception {
        producer.send(new ProducerRecord<>(EVENTS,published.path("aggregateId").asText(),publishedJson)).get(15,TimeUnit.SECONDS);
        var last=producer.send(new ProducerRecord<>(EVENTS,published.path("aggregateId").asText(),publishedJson)).get(15,TimeUnit.SECONDS);
        awaitConsumed(last);assertTwoAdjustments();
        assertThat(body(rig.get(BASE+"/workflows",rig.clerk)).size()).isEqualTo(1);facts.put("duplicateDelivery",Map.of("additionalDeliveries",2,"adjustmentsAfterReplay",2));
    }
    @Test @Order(4) void stopRestartConsumersAndReplayPreservesPersistedState() throws Exception {
        rig.stop("retro-one");rig.stop("retro-two");
        rig.launch("retro-one");rig.awaitRetro("retro-one");rig.awaitGatewayStable();
        var last=producer.send(new ProducerRecord<>(EVENTS,published.path("aggregateId").asText(),publishedJson)).get(15,TimeUnit.SECONDS);awaitConsumed(last);assertTwoAdjustments();
        rig.launch("retro-two");rig.await("both restarted consumers registered",120,() -> rig.registered().size()==7);
        facts.put("consumerRestart",Map.of("bothStopped",true,"sameDatabaseRetained",true,"sameConsumerGroupRetained",true,"adjustmentsAfterRestartAndReplay",2));
    }
    @Test @Order(5) void permanentlyInvalidEventReachesActualDltWithErrorContext() throws Exception {
        var invalid=(ObjectNode)published.deepCopy();invalid.put("schemaVersion",99);invalid.put("eventId",UUID.randomUUID().toString());
        String json=invalid.toString();var metadata=producer.send(new ProducerRecord<>(EVENTS,invalid.path("aggregateId").asText(),json)).get(15,TimeUnit.SECONDS);
        var dead=poll(dltObserver,record -> record.value()!=null && record.value().contains(invalid.path("eventId").asText()),60);
        assertThat(dead.key()).isEqualTo(invalid.path("aggregateId").asText());assertThat(PayrollRig.JSON.readTree(dead.value())).isEqualTo(invalid);
        var code=dead.headers().lastHeader("payroll-error-code");assertThat(code).isNotNull();assertThat(new String(code.value(),java.nio.charset.StandardCharsets.UTF_8)).isEqualTo("UNSUPPORTED_SCHEMA_VERSION");
        assertThat(dead.headers().lastHeader(org.springframework.kafka.support.KafkaHeaders.DLT_ORIGINAL_OFFSET)).isNotNull();assertTwoAdjustments();
        facts.put("invalidEventDlt",Map.of("sourcePartition",metadata.partition(),"sourceOffset",metadata.offset(),"destination",dead.topic(),"errorCode","UNSUPPORTED_SCHEMA_VERSION"));
    }
    @Test @Order(6) void approverApprovalIsPersistedAndOriginalStatementsNeverChange() throws Exception {
        for(var row:adjustments)assertThat(rig.request("POST",BASE+"/"+row.path("id").asLong()+"/approve",rig.approver,null).statusCode()).isEqualTo(200);
        var approved=body(rig.get(BASE+"/approved/current-open",rig.approver));assertThat(approved.path("total").decimalValue()).isEqualByComparingTo("2000.00");
        assertThat(rig.request("POST",BASE+"/"+adjustments.get(0).path("id").asLong()+"/approve",rig.approver,null).statusCode()).isEqualTo(409);
        assertThat(body(rig.get("/api/pay-periods/employees/EMP001/snapshots",rig.approver))).isEqualTo(originalSnapshots);
        facts.put("approval",Map.of("approvedAdjustments",2,"approvedOctoberTotal","2000.00","repeatApprovalStatus",409,"originalSnapshotsUnchanged",true));
    }
    @Test @Order(7) void gatewayBalancesAcrossTwoRetroProcessesAndSurvivesOneStopping() throws Exception {
        rig.await("two Kafka group members",60,() -> admin.describeConsumerGroups(List.of("retro-backdate-v1")).all().get(10,TimeUnit.SECONDS).get("retro-backdate-v1").members().size()==2);
        var observed=new HashSet<String>();
        rig.await("Gateway registry/cache converges to both restarted replicas",30,() -> {var response=rig.get(BASE,rig.clerk);assertThat(response.statusCode()).isEqualTo(200);observed.add(response.headers().firstValue("X-Payroll-Instance").orElseThrow());return observed.size()==2;});
        assertThat(observed).hasSize(2);rig.stop("retro-one");
        for(int n=0;n<8;n++)assertThat(rig.get(BASE,rig.clerk).statusCode()).isEqualTo(200);
        rig.launch("retro-one");rig.await("recovered replica registered",120,() -> rig.registered().size()==7);
        rig.awaitRetroReady();
        facts.put("replicaRecovery",Map.of("observedInstances",observed,"successfulRequestsAfterOneStop",8,"restarted",true));
    }
    @Test @Order(8) void fiftyAuthenticatedGetsAtConcurrencyFiveReportLatencyAndErrors() throws Exception {
        rig.awaitRetroReady();
        for(int n=0;n<5;n++)assertThat(rig.get(BASE,rig.clerk).statusCode()).isEqualTo(200); // Separate warm-up, excluded from the sample.
        var pool=Executors.newFixedThreadPool(5);var futures=new ArrayList<Future<Sample>>();long begin=System.nanoTime();
        try {
            for(int n=0;n<50;n++)futures.add(pool.submit(() -> {long start=System.nanoTime();int status=0;String exception="",instance="";try{var response=rig.get(BASE,rig.clerk);status=response.statusCode();instance=response.headers().firstValue("X-Payroll-Instance").orElse("");}catch(Exception error){exception=error.getClass().getName();}return new Sample((System.nanoTime()-start)/1_000_000.0,status,exception,instance);}));
            var samples=new ArrayList<Sample>();for(var future:futures)samples.add(future.get(30,TimeUnit.SECONDS));
            var latencies=samples.stream().map(Sample::milliseconds).sorted().toList();long errors=samples.stream().filter(s -> !s.ok()).count();
            var report=new LinkedHashMap<String,Object>();report.put("requestCount",samples.size());report.put("concurrency",5);report.put("warmupRequestsExcluded",5);report.put("p50Ms",latencies.get((int)Math.ceil(0.50*samples.size())-1));report.put("p95Ms",latencies.get((int)Math.ceil(0.95*samples.size())-1));report.put("errors",errors);report.put("errorRatePercent",100.0*errors/samples.size());report.put("elapsedMs",(System.nanoTime()-begin)/1_000_000.0);report.put("measuredAt",Instant.now().toString());report.put("transport","HTTPS Gateway, real signed clerk JWT, real Retro DB reads");
            report.put("samples",samples);report.put("processes",rig.diagnostics());
            rig.evidence("load-test.json",report);facts.put("loadTest",report);assertThat(samples).hasSize(50);assertThat(errors).as("Load failures: %s; %s",samples.stream().filter(s -> !s.ok()).toList(),rig.diagnostics()).isZero();
        } finally {pool.shutdownNow();}
    }
    record Sample(double milliseconds,int status,String exception,String instance){boolean ok(){return status==200 && exception.isEmpty();}}
    @Test @Order(9) void noClosedAffectedPeriodProducesNoNewAdjustments() throws Exception {
        assertThat(rig.request("POST","/api/allowances/employees/EMP001/HOUSING/backdate",rig.clerk,"{\"expectedVersion\":2,\"monthlyAmount\":3500.00,\"effectiveFrom\":\"2026-10-01\"}").statusCode()).isEqualTo(201);
        rig.await("October-only boundary workflow completes with no closed periods",60,() -> {for(var row:body(rig.get(BASE+"/workflows",rig.clerk)))if(row.path("newVersion").asInt()==3)return row.path("status").asText().equals("COMPLETED");return false;});
        assertTwoAdjustments();assertThat(body(rig.get(BASE+"/approved/current-open",rig.approver)).path("total").decimalValue()).isEqualByComparingTo("2000.00");facts.put("boundaryNoClosedPeriods",Map.of("effectiveFrom","2026-10-01","additionalAdjustments",0));
    }
    @Test @Order(10) void invalidChangesAndImmutablePayslipEditsAreRejectedThroughGateway() throws Exception {
        assertThat(rig.request("POST","/api/allowances/employees/EMP001/HOUSING/backdate",rig.clerk,"{\"expectedVersion\":3,\"monthlyAmount\":-1,\"effectiveFrom\":\"2026-08-01\"}").statusCode()).isEqualTo(400);
        assertThat(rig.request("PATCH","/api/pay-periods/employees/EMP001/1/snapshots",rig.clerk,"{\"originalAmount\":9000.00}").statusCode()).isEqualTo(403);
        long august=originalSnapshots.get(0).path("periodId").asLong();
        assertThat(rig.request("POST","/api/pay-periods/employees/EMP001/"+august+"/issue",rig.clerk,"{\"allowances\":[{\"allowanceType\":\"HOUSING\",\"ruleVersion\":3,\"originalAmount\":9000.00}]}").statusCode()).isEqualTo(409);
        assertThat(body(rig.get("/api/pay-periods/employees/EMP001/snapshots",rig.clerk))).isEqualTo(originalSnapshots);
        facts.put("immutableAndInvalid",Map.of("negativeChange",400,"editIssuedSnapshot",403,"reissueClosedPeriod",409,"snapshotsUnchanged",true));
    }
    void awaitConsumed(RecordMetadata metadata) throws Exception {rig.await("record offset committed by the actual Retro consumer",60,() -> {var offsets=admin.listConsumerGroupOffsets("retro-backdate-v1").partitionsToOffsetAndMetadata().get(10,TimeUnit.SECONDS);var committed=offsets.get(new TopicPartition(metadata.topic(),metadata.partition()));return committed!=null && committed.offset()>metadata.offset();});}
    interface Match {boolean matches(ConsumerRecord<String,String> record) throws Exception;}
    ConsumerRecord<String,String> poll(KafkaConsumer<String,String> consumer,Match match,int seconds) throws Exception {long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(seconds);while(System.nanoTime()<end)for(var record:consumer.poll(Duration.ofMillis(500)))if(match.matches(record))return record;throw new AssertionError("Expected actual Kafka record not received");}
    static JsonNode body(java.net.http.HttpResponse<String> response) throws Exception {assertThat(response.statusCode()).isEqualTo(200);return PayrollRig.JSON.readTree(response.body());}
    static void assertSchema(String resource,JsonNode value) throws Exception {try(var input=PayrollSystemTest.class.getResourceAsStream("/schemas/"+resource)){assertThat(input).isNotNull();var schema=JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(input);assertThat(schema.validate(value)).isEmpty();}}
}
