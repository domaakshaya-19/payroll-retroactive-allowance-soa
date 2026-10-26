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
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;
@DataJpaTest(showSql=false) @Import({AllowanceService.class,AllowanceWorkflowTest.Config.class})
@Transactional(propagation=Propagation.NOT_SUPPORTED)
class AllowanceWorkflowTest {
    @TestConfiguration static class Config {@Bean ObjectMapper mapper(){return JsonMapper.builder().findAndAddModules().disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build();}}
    @Autowired AllowanceService service;@Autowired AllowanceRepository rules;@Autowired RuleSeriesRepository series;
    @Autowired RuleOutboxRepository outbox;@Autowired ObjectMapper mapper;@Autowired JdbcTemplate jdbc;@Autowired PlatformTransactionManager transactions;
    @BeforeEach void clean(){jdbc.update("delete from rule_outbox");jdbc.update("delete from allowance_rule_version");jdbc.update("delete from rule_series");}
    private void original(){service.create("EMP001",new AllowanceRequest("HOUSING",new BigDecimal("2000.00"),LocalDate.of(2026,8,1)));}
    @Test void backdateAppendsVersionAndMatchingOutboxWithoutChangingOriginal() throws Exception {
        original();var response=service.backdate("EMP001","HOUSING",new BackdateRequest(1,new BigDecimal("3000.00"),LocalDate.of(2026,8,1)));
        assertEquals(2,response.ruleVersion());assertEquals(2,rules.count());assertEquals(1,outbox.count());
        assertEquals(new BigDecimal("2000.00"),rules.findByEmployeeIdAndAllowanceTypeAndRuleVersion("EMP001","HOUSING",1).orElseThrow().monthlyAmount);
        var event=mapper.readValue(outbox.findAll().get(0).payload,AllowanceRuleBackdated.class);
        assertEquals("AllowanceRuleBackdated",event.eventType());assertNotNull(event.eventId());assertNotNull(event.occurredAt());
        assertEquals("EMP001:HOUSING",event.aggregateId());assertEquals(1,event.schemaVersion());assertNotNull(event.correlationId());
        assertEquals(event.aggregateId(),outbox.findAll().get(0).eventKey);assertEquals(event.newVersion(),outbox.findAll().get(0).aggregateVersion);
        assertEquals("EMP001",event.employeeId());assertEquals("HOUSING",event.allowanceType());assertEquals(1,event.oldVersion());assertEquals(2,event.newVersion());
        assertEquals(new BigDecimal("2000.00"),event.oldAmount());assertEquals(new BigDecimal("3000.00"),event.newAmount());assertEquals(LocalDate.of(2026,8,1),event.effectiveFrom());
    }
    @Test void staleVersionAndDuplicateCreateAreConflicts(){
        original();assertEquals(409,assertThrows(ResponseStatusException.class,() -> service.create("EMP001",new AllowanceRequest("HOUSING",BigDecimal.ONE,LocalDate.of(2026,8,1)))).getStatusCode().value());
        service.backdate("EMP001","HOUSING",new BackdateRequest(1,new BigDecimal("3000.00"),LocalDate.of(2026,8,1)));
        assertEquals(409,assertThrows(ResponseStatusException.class,() -> service.backdate("EMP001","HOUSING",new BackdateRequest(1,new BigDecimal("4000.00"),LocalDate.of(2026,8,1)))).getStatusCode().value());
        assertEquals(2,rules.count());assertEquals(1,outbox.count());
    }
    @Test void rolledBackBackdateCannotPublish(){
        original();new TransactionTemplate(transactions).executeWithoutResult(tx -> {service.backdate("EMP001","HOUSING",new BackdateRequest(1,new BigDecimal("3000.00"),LocalDate.of(2026,8,1)));tx.setRollbackOnly();});
        assertEquals(1,rules.count());assertEquals(0,outbox.count());
        assertEquals(2,service.backdate("EMP001","HOUSING",new BackdateRequest(1,new BigDecimal("3000.00"),LocalDate.of(2026,8,1))).ruleVersion());
    }
    @Test void distinctEmployeesAndTypesHaveIndependentVersions(){
        original();service.create("EMP001",new AllowanceRequest("TRAVEL",BigDecimal.TEN,LocalDate.of(2026,8,1)));
        service.create("EMP002",new AllowanceRequest("HOUSING",BigDecimal.ONE,LocalDate.of(2026,8,1)));assertEquals(3,rules.count());
    }
    @Test void invalidMoneyAndPartialMonthRejected(){
        for(var amount:java.util.List.of(new BigDecimal("-1.00"),new BigDecimal("1.001")))
            assertThrows(ResponseStatusException.class,() -> service.create("EMP001",new AllowanceRequest("HOUSING",amount,LocalDate.of(2026,8,1))));
        assertThrows(ResponseStatusException.class,() -> service.create("EMP001",new AllowanceRequest("HOUSING",BigDecimal.ONE,LocalDate.of(2026,8,2))));assertEquals(0,rules.count());
    }
    @Test void syntheticSeedIsIdempotent(){var seed=new SyntheticSeed(series,service);new TransactionTemplate(transactions).executeWithoutResult(tx -> seed.run(new org.springframework.boot.DefaultApplicationArguments()));new TransactionTemplate(transactions).executeWithoutResult(tx -> seed.run(new org.springframework.boot.DefaultApplicationArguments()));assertEquals(1,rules.count());}
    @Test void persistedRuleCannotBeUpdated(){original();var row=rules.findAll().get(0);row.monthlyAmount=new BigDecimal("9999.00");rules.saveAndFlush(row);assertEquals(new BigDecimal("2000.00"),rules.findById(row.id).orElseThrow().monthlyAmount);}
    @Test void databaseRejectsDuplicateRuleVersion(){
        original();var duplicate=new AllowanceRecord();duplicate.employeeId="EMP001";duplicate.allowanceType="HOUSING";duplicate.ruleVersion=1;duplicate.monthlyAmount=new BigDecimal("2000.00");duplicate.effectiveFrom=LocalDate.of(2026,8,1);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> rules.saveAndFlush(duplicate)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        org.assertj.core.api.Assertions.assertThat(rules.count()).isEqualTo(1);
    }
    @Test void producerMatchesPublishedConsumerSchema() throws Exception {
        original();service.backdate("EMP001","HOUSING",new BackdateRequest(1,new BigDecimal("3000.00"),LocalDate.of(2026,8,1)));
        var payload=(com.fasterxml.jackson.databind.node.ObjectNode)mapper.readTree(outbox.findAll().get(0).payload);
        if(Boolean.getBoolean("contract.break"))payload.set("correctedAmount",payload.remove("newAmount")); // Deliberate required-field incompatibility, test-only.
        try(var resource=getClass().getResourceAsStream("/schemas/allowance-rule-backdated-v1.schema.json")){
            var schema=com.networknt.schema.JsonSchemaFactory.getInstance(com.networknt.schema.SpecVersion.VersionFlag.V202012).getSchema(resource);
            org.assertj.core.api.Assertions.assertThat(schema.validate(payload)).as("Producer must satisfy the consumer's published required-field contract").isEmpty();
        }
    }
}
