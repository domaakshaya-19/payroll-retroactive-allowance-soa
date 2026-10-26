package com.example.payroll;
import com.example.payroll.contracts.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
@DataJpaTest(showSql=false) @Import(PayPeriodService.class) @Transactional(propagation=Propagation.NOT_SUPPORTED)
class StatementHistoryTest {
    @Autowired PayPeriodService service;@Autowired PayPeriodRepository periods;@Autowired SnapshotRepository snapshots;
    @Autowired IssuedStatementRepository statements;@Autowired JdbcTemplate jdbc;
    @BeforeEach void clean(){jdbc.update("delete from allowance_snapshot");jdbc.update("delete from issued_statement");jdbc.update("delete from payroll_period");jdbc.update("delete from employee_ledger");}
    private void seed(){new SyntheticSeed(service,periods).run(new org.springframework.boot.DefaultApplicationArguments());}
    @Test void seedHasTwoClosedStatementsAndOpenOctober(){
        seed();seed();assertEquals(3,periods.count());assertEquals(2,statements.count());assertEquals(2,snapshots.count());
        var history=snapshots.findByEmployeeIdOrderByStartsOnAsc("EMP001");
        assertEquals(List.of(LocalDate.of(2026,8,1),LocalDate.of(2026,9,1)),history.stream().map(s -> s.startsOn).toList());
        assertTrue(history.stream().allMatch(s -> s.ruleVersion==1 && s.originalAmount.equals(new BigDecimal("2000.00"))));
        assertEquals(LocalDate.of(2026,10,1),service.open("EMP001").startsOn());assertEquals(2,periods.findByEmployeeIdAndStatus("EMP001","CLOSED").size());
    }
    @Test void closedPeriodCannotBeReissued(){
        seed();var august=snapshots.findByEmployeeIdOrderByStartsOnAsc("EMP001").get(0);
        assertEquals(409,assertThrows(ResponseStatusException.class,() -> service.issue("EMP001",august.periodId,new IssueStatementRequest(List.of(new SnapshotLineRequest("HOUSING",2,new BigDecimal("3000.00")))))).getStatusCode().value());
        assertEquals(new BigDecimal("2000.00"),snapshots.findByEmployeeIdAndPeriodId("EMP001",august.periodId).get(0).originalAmount);assertEquals(2,statements.count());
    }
    @Test void issuedSnapshotsCannotBeRewritten(){
        seed();var snapshot=snapshots.findByEmployeeIdOrderByStartsOnAsc("EMP001").get(0);snapshot.originalAmount=new BigDecimal("9999.00");snapshots.saveAndFlush(snapshot);
        assertEquals(new BigDecimal("2000.00"),snapshots.findById(snapshot.id).orElseThrow().originalAmount);
    }
    @Test void duplicateLinesRollBackIssuance(){
        seed();var october=service.open("EMP001");var line=new SnapshotLineRequest("HOUSING",1,new BigDecimal("2000.00"));
        assertThrows(ResponseStatusException.class,() -> service.issue("EMP001",october.id(),new IssueStatementRequest(List.of(line,line))));
        assertEquals(october.id(),service.open("EMP001").id());assertEquals(2,statements.count());
    }
    @Test void multipleOpenPeriodsAndPartialMonthsRejected(){
        seed();assertThrows(ResponseStatusException.class,() -> service.create("EMP001",new PayPeriodRequest(LocalDate.of(2026,11,1),LocalDate.of(2026,11,30))));
        assertEquals(400,assertThrows(ResponseStatusException.class,() -> service.create("EMP002",new PayPeriodRequest(LocalDate.of(2026,8,2),LocalDate.of(2026,8,31)))).getStatusCode().value());
    }
    @Test void issuedStatementPeriodUniquenessIsEnforcedInDatabase(){
        seed();var old=statements.findAll().get(0);var duplicate=new IssuedStatement();duplicate.periodId=old.periodId;duplicate.employeeId=old.employeeId;duplicate.issuedAt=old.issuedAt;
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> statements.saveAndFlush(duplicate)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        org.assertj.core.api.Assertions.assertThat(statements.count()).isEqualTo(2);
    }
    @Test void snapshotAllowanceUniquenessAndRequiredAmountAreEnforced(){
        seed();var original=snapshots.findAll().get(0);var duplicate=new AllowanceSnapshot();duplicate.statementId=original.statementId;duplicate.periodId=original.periodId;duplicate.employeeId=original.employeeId;duplicate.startsOn=original.startsOn;duplicate.endsOn=original.endsOn;duplicate.allowanceType=original.allowanceType;duplicate.ruleVersion=1;duplicate.originalAmount=original.originalAmount;duplicate.issuedAt=original.issuedAt;
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> snapshots.saveAndFlush(duplicate)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        duplicate.id=null;duplicate.allowanceType="TRAVEL";duplicate.originalAmount=null;
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> snapshots.saveAndFlush(duplicate)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        org.assertj.core.api.Assertions.assertThat(snapshots.count()).isEqualTo(2);
    }
}
