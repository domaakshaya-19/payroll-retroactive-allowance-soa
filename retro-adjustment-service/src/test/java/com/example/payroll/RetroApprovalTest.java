package com.example.payroll;
import com.example.payroll.contracts.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
@DataJpaTest(showSql=false) @Import({EventReceipt.class,RetroWorkflowService.class,RuleEventConsumer.class,RetroApprovalTest.Config.class})
@Transactional(propagation=Propagation.NOT_SUPPORTED)
class RetroApprovalTest {
    @TestConfiguration static class Config {
        @Bean ObjectMapper mapper(){return JsonMapper.builder().findAndAddModules().disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build();}
        @Bean LocalValidatorFactoryBean validator(){return new LocalValidatorFactoryBean();}
    }
    @Autowired RetroWorkflowService service;@Autowired RuleEventConsumer consumer;@Autowired EventReceipt receipt;
    @Autowired RetroRepository adjustments;@Autowired WorkflowRepository workflows;@Autowired OutboxRepository outbox;
    @Autowired JdbcTemplate jdbc;@Autowired ObjectMapper mapper;@MockitoBean SnapshotClient snapshots;
    @BeforeEach void clean(){jdbc.update("delete from outbox");jdbc.update("delete from retro_period_adjustment");jdbc.update("delete from retro_workflow");reset(snapshots);when(snapshots.openPeriod("EMP001")).thenReturn(new PayPeriodResponse(3L,"EMP001",LocalDate.of(2026,10,1),LocalDate.of(2026,10,31),"OPEN"));jdbc.update("delete from aggregate_progress");}
    private AllowanceRuleBackdated event(LocalDate from,BigDecimal amount){return new AllowanceRuleBackdated(UUID.randomUUID(),"EMP001:HOUSING",1,Instant.parse("2026-10-01T00:00:00Z"),UUID.randomUUID(),"AllowanceRuleBackdated","EMP001","HOUSING",1,2,new BigDecimal("2000.00"),amount,from);}
    private List<HistoricalSnapshot> history(){return List.of(
        new HistoricalSnapshot(11L,1L,"EMP001",LocalDate.of(2026,8,1),LocalDate.of(2026,8,31),"HOUSING",1,new BigDecimal("2000.00"),Instant.parse("2026-08-31T00:00:00Z")),
        new HistoricalSnapshot(12L,2L,"EMP001",LocalDate.of(2026,9,1),LocalDate.of(2026,9,30),"HOUSING",1,new BigDecimal("2000.00"),Instant.parse("2026-09-30T00:00:00Z")));}
    @Test void augustBackdateProducesExactlyTwoThousandAndApprovedOctoberAmounts() throws Exception {
        var original=history();when(snapshots.history("EMP001")).thenReturn(original);
        var event=event(LocalDate.of(2026,8,1),new BigDecimal("3000.00"));var payload=mapper.writeValueAsString(event);
        consumer.consume("EMP001:HOUSING",payload);consumer.consume("EMP001:HOUSING",payload);assertEquals(1,workflows.count());
        service.process(event.eventId());service.process(event.eventId());consumer.consume("EMP001:HOUSING",payload);
        var rows=adjustments.findByEventId(event.eventId());assertEquals(2,rows.size());assertEquals("COMPLETED",workflows.findById(event.eventId()).orElseThrow().status);
        assertTrue(rows.stream().allMatch(row -> new BigDecimal("1000.00").equals(row.difference) && Long.valueOf(3L).equals(row.paymentPeriodId) && "DRAFT".equals(row.status)));
        assertEquals(Set.of(1L,2L),rows.stream().map(row -> row.sourcePeriodId).collect(java.util.stream.Collectors.toSet()));
        assertEquals(Set.of(11L,12L),rows.stream().map(row -> row.statementId).collect(java.util.stream.Collectors.toSet()));
        assertEquals(new BigDecimal("2000.00"),rows.stream().map(row -> row.difference).reduce(BigDecimal.ZERO,BigDecimal::add));
        assertEquals(new BigDecimal("0.00"),service.approved("EMP001",3L).total());
        for(var row:rows)service.approve("EMP001",row.id,3L);
        assertEquals(new BigDecimal("2000.00"),service.approved("EMP001",3L).total());assertEquals(2,outbox.count());
        assertEquals(new BigDecimal("0.00"),service.approved("EMP001",4L).total());
        assertThrows(ResponseStatusException.class,() -> service.approve("EMP001",rows.get(0).id,3L));assertEquals(2,outbox.count());
        assertTrue(original.stream().allMatch(snapshot -> snapshot.originalAmount().equals(new BigDecimal("2000.00"))));
        verify(snapshots,times(1)).history("EMP001");
    }
    @Test void calculationsUsePaidSnapshotsRatherThanEventOldAmount() throws Exception {
        var snapshot=new HistoricalSnapshot(11L,1L,"EMP001",LocalDate.of(2026,8,1),LocalDate.of(2026,8,31),"HOUSING",1,new BigDecimal("1800.25"),Instant.now());
        when(snapshots.history("EMP001")).thenReturn(List.of(snapshot));var event=event(LocalDate.of(2026,8,1),new BigDecimal("3000.00"));receipt.record(event);service.process(event.eventId());
        assertEquals(new BigDecimal("1199.75"),adjustments.findAll().get(0).difference);
    }
    @Test void septemberEffectiveDateExcludesAugustAndSupportsNegativeDifferences(){
        when(snapshots.history("EMP001")).thenReturn(history());var event=event(LocalDate.of(2026,9,1),new BigDecimal("1500.00"));receipt.record(event);service.process(event.eventId());
        var rows=adjustments.findAll();assertEquals(1,rows.size());assertEquals(2L,rows.get(0).sourcePeriodId);assertEquals(new BigDecimal("-500.00"),rows.get(0).difference);
    }
    @Test void snapshotFailureIsDurableRetryableAndCreatesNoPartialAdjustments(){
        var event=event(LocalDate.of(2026,8,1),new BigDecimal("3000.00"));receipt.record(event);
        when(snapshots.history("EMP001")).thenThrow(new IllegalStateException());
        assertThrows(IllegalStateException.class,() -> service.process(event.eventId()));service.failed(event.eventId());
        assertEquals("RETRY_PENDING",workflows.findById(event.eventId()).orElseThrow().status);assertEquals(0,adjustments.count());
        doReturn(history()).when(snapshots).history("EMP001");service.process(event.eventId());assertEquals(2,adjustments.count());
    }
    @Test void lateFailureRollsBackAllPeriodAdjustments(){
        var event=event(LocalDate.of(2026,8,1),new BigDecimal("3000.00"));receipt.record(event);
        when(snapshots.history("EMP001")).thenReturn(List.of(history().get(0),history().get(0)));
        assertThrows(IllegalStateException.class,() -> service.process(event.eventId()));assertEquals(0,adjustments.count());assertEquals("RECEIVED",workflows.findById(event.eventId()).orElseThrow().status);
    }
    @Test void fiveFailuresRequireExplicitScopedRetry(){
        var event=event(LocalDate.of(2026,8,1),new BigDecimal("3000.00"));receipt.record(event);for(int n=0;n<5;n++)service.failed(event.eventId());
        assertEquals("FAILED",workflows.findById(event.eventId()).orElseThrow().status);
        assertThrows(ResponseStatusException.class,() -> service.retry("EMP999",event.eventId()));assertEquals("RECEIVED",service.retry("EMP001",event.eventId()).status());
    }
    @Test void conflictingEventIdentityAndVersionCannotDuplicateWorkflow() throws Exception {
        var event=event(LocalDate.of(2026,8,1),new BigDecimal("3000.00"));consumer.consume("EMP001:HOUSING",mapper.writeValueAsString(event));
        var conflict=new AllowanceRuleBackdated(event.eventId(),event.aggregateId(),1,event.occurredAt(),event.correlationId(),event.eventType(),event.employeeId(),event.allowanceType(),1,2,event.oldAmount(),new BigDecimal("9000.00"),event.effectiveFrom());
        assertThrows(IllegalArgumentException.class,() -> consumer.consume("EMP001:HOUSING",mapper.writeValueAsString(conflict)));
        var differentId=event(LocalDate.of(2026,8,1),new BigDecimal("3000.00"));assertThrows(DataIntegrityViolationException.class,() -> receipt.record(differentId));assertEquals(1,workflows.count());
    }
    @Test void databaseRejectsDuplicateEventPeriodAdjustments(){
        var event=event(LocalDate.of(2026,8,1),new BigDecimal("3000.00"));receipt.record(event);when(snapshots.history("EMP001")).thenReturn(history());service.process(event.eventId());
        var old=adjustments.findAll().get(0);var duplicate=new RetroRecord();duplicate.eventId=old.eventId;duplicate.employeeId=old.employeeId;duplicate.allowanceType=old.allowanceType;
        duplicate.sourcePeriodId=old.sourcePeriodId;duplicate.statementId=old.statementId;duplicate.previousVersion=1;duplicate.newVersion=2;duplicate.originallyPaid=old.originallyPaid;duplicate.newAmount=old.newAmount;duplicate.difference=old.difference;duplicate.status="DRAFT";
        assertThrows(DataIntegrityViolationException.class,() -> adjustments.saveAndFlush(duplicate));assertEquals(2,adjustments.count());
    }
    @Test void malformedEventsAreRejectedWithoutPayloadInError(){
        var error=assertThrows(IllegalArgumentException.class,() -> consumer.consume("EMP001:HOUSING","private employee content"));assertTrue(error.getMessage().contains("REDACTED"));assertNull(error.getCause());assertEquals(0,workflows.count());
    }
    @Test void metadataSchemaAndMessageKeyAreStrictlyValidated() throws Exception {
        var event=event(LocalDate.of(2026,8,1),new BigDecimal("3000.00"));
        String valid=mapper.writeValueAsString(event);assertEquals(event,receipt.decode(valid));
        var tree=(com.fasterxml.jackson.databind.node.ObjectNode)mapper.readTree(valid);
        tree.put("schemaVersion",2);String unsupported=tree.toString();assertEquals("UNSUPPORTED_SCHEMA_VERSION",assertThrows(InvalidRuleEventException.class,() -> receipt.decode(unsupported)).code());
        tree.put("schemaVersion",1);tree.remove("correlationId");String missing=tree.toString();assertThrows(InvalidRuleEventException.class,() -> receipt.decode(missing));
        tree=(com.fasterxml.jackson.databind.node.ObjectNode)mapper.readTree(valid);tree.put("newAmount","3000.00");
        String wrongType=tree.toString();assertThrows(InvalidRuleEventException.class,() -> receipt.decode(wrongType));
        assertEquals("AGGREGATE_KEY_MISMATCH",assertThrows(InvalidRuleEventException.class,() -> consumer.consume("other-aggregate",valid)).code());
        assertThrows(InvalidRuleEventException.class,() -> receipt.decode(valid+" {}"));assertEquals(0,workflows.count());
        var precise=(com.fasterxml.jackson.databind.node.ObjectNode)mapper.readTree(valid);
        precise.put("newAmount",new BigDecimal("999999999999.99"));
        assertEquals(new BigDecimal("999999999999.99"),receipt.decode(precise.toString()).newAmount());
        var invalidUuid=(com.fasterxml.jackson.databind.node.ObjectNode)mapper.readTree(valid);invalidUuid.put("eventId","1-1-1-1-1");
        assertEquals("EVENT_FORMAT_MISMATCH",assertThrows(InvalidRuleEventException.class,() -> receipt.decode(invalidUuid.toString())).code());
        assertThrows(InvalidRuleEventException.class,() -> consumer.consume(event.aggregateId(),null));
    }
    @Test void concurrentReplicaDeliveriesAndWorkersHaveExactlyOneBusinessEffect() throws Exception {
        when(snapshots.history("EMP001")).thenReturn(history());var event=event(LocalDate.of(2026,8,1),new BigDecimal("3000.00"));
        String payload=mapper.writeValueAsString(event);var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var barrier=new java.util.concurrent.CyclicBarrier(2);
            var tasks=new java.util.ArrayList<java.util.concurrent.Future<?>>();
            for(int n=0;n<2;n++)tasks.add(pool.submit(() -> {try{barrier.await();consumer.consume(event.aggregateId(),payload);}catch(Exception error){throw new RuntimeException(error);}}));
            for(var task:tasks)task.get(20,java.util.concurrent.TimeUnit.SECONDS);assertEquals(1,workflows.count());
            tasks.clear();
            for(int n=0;n<2;n++)tasks.add(pool.submit(() -> service.process(event.eventId())));
            for(var task:tasks)task.get(20,java.util.concurrent.TimeUnit.SECONDS);
            assertEquals(2,adjustments.count());assertEquals("COMPLETED",workflows.findById(event.eventId()).orElseThrow().status);
            Long id=adjustments.findAll().get(0).id;
            var approvals=new java.util.ArrayList<java.util.concurrent.Future<Integer>>();
            for(int n=0;n<2;n++)approvals.add(pool.submit(() -> {try{service.approve("EMP001",id,3L);return 200;}catch(ResponseStatusException conflict){return conflict.getStatusCode().value();}}));
            var statuses=new java.util.HashSet<Integer>();for(var approval:approvals)statuses.add(approval.get(20,java.util.concurrent.TimeUnit.SECONDS));
            assertEquals(Set.of(200,409),statuses);assertEquals(1,outbox.count());
            consumer.consume(event.aggregateId(),payload);service.process(event.eventId());assertEquals(2,adjustments.count());
        } finally {pool.shutdownNow();}
    }
    @Test void laterAggregateVersionsWaitForTheirPredecessor() {
        when(snapshots.history("EMP001")).thenReturn(history());
        var first=event(LocalDate.of(2026,8,1),new BigDecimal("3000.00"));
        var later=new AllowanceRuleBackdated(UUID.randomUUID(),first.aggregateId(),1,first.occurredAt(),first.correlationId(),first.eventType(),"EMP001","HOUSING",2,3,new BigDecimal("3000.00"),new BigDecimal("3500.00"),first.effectiveFrom());
        receipt.record(later);service.process(later.eventId());assertEquals("WAITING_PREDECESSOR",workflows.findById(later.eventId()).orElseThrow().status);assertEquals(0,adjustments.count());
        receipt.record(first);service.process(first.eventId());service.process(later.eventId());
        assertEquals("COMPLETED",workflows.findById(later.eventId()).orElseThrow().status);assertEquals(2,adjustments.findByEventId(later.eventId()).size());
    }
    @Test void draftPayrollLinkCannotBeMovedDuringApproval(){
        when(snapshots.history("EMP001")).thenReturn(history());var event=event(LocalDate.of(2026,8,1),new BigDecimal("3000.00"));receipt.record(event);service.process(event.eventId());
        Long id=adjustments.findAll().get(0).id;
        assertEquals(409,assertThrows(ResponseStatusException.class,() -> service.approve("EMP001",id,4L)).getStatusCode().value());
        assertEquals("DRAFT",adjustments.findById(id).orElseThrow().status);assertEquals(0,outbox.count());
        assertEquals(event,service.event("EMP001",event.eventId()));assertThrows(ResponseStatusException.class,() -> service.event("EMP999",event.eventId()));
    }
    @Test void consumedEventIdPrimaryKeyRejectsDuplicateIndependentlyOfBusinessKey(){
        var event=event(LocalDate.of(2026,8,1),new BigDecimal("3000.00"));receipt.record(event);
        var duplicate=new RetroWorkflow();duplicate.eventId=event.eventId();duplicate.aggregateId="EMP999:TRAVEL";duplicate.correlationId=UUID.randomUUID();duplicate.employeeId="EMP999";duplicate.allowanceType="TRAVEL";duplicate.newVersion=2;duplicate.payload="{}";duplicate.status="RECEIVED";
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> workflows.saveAndFlush(duplicate)).isInstanceOf(DataIntegrityViolationException.class);
        org.assertj.core.api.Assertions.assertThat(workflows.count()).isEqualTo(1);
    }
    @Test void noClosedAffectedPeriodsCompletesWithoutCreatingAdjustments(){
        when(snapshots.history("EMP001")).thenReturn(history());var event=event(LocalDate.of(2026,10,1),new BigDecimal("3000.00"));receipt.record(event);service.process(event.eventId());
        org.assertj.core.api.Assertions.assertThat(adjustments.findAll()).isEmpty();
        org.assertj.core.api.Assertions.assertThat(workflows.findById(event.eventId()).orElseThrow().status).isEqualTo("COMPLETED");
        org.assertj.core.api.Assertions.assertThat(outbox.count()).isZero();
    }
}
