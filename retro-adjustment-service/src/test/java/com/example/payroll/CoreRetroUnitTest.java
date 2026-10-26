package com.example.payroll;
import com.example.payroll.contracts.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.mockito.ArgumentCaptor;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class CoreRetroUnitTest {
    @Test void normalTwoPeriodBackdatedIncreaseCreatesExactlyTwoThousand(){
        var fixture=new Fixture(LocalDate.of(2026,8,1));fixture.service.process(fixture.id);
        var saved=ArgumentCaptor.forClass(RetroRecord.class);verify(fixture.adjustments,times(2)).save(saved.capture());
        assertThat(saved.getAllValues()).extracting(row -> row.difference).allSatisfy(amount -> assertThat(amount).isEqualByComparingTo("1000.00"));
        assertThat(saved.getAllValues().stream().map(row -> row.difference).reduce(BigDecimal.ZERO,BigDecimal::add)).isEqualByComparingTo("2000.00");
        assertThat(saved.getAllValues()).allSatisfy(row -> {assertThat(row.eventId).isEqualTo(fixture.id);assertThat(row.paymentPeriodId).isEqualTo(3L);assertThat(row.status).isEqualTo("DRAFT");});
        assertThat(fixture.workflow.status).isEqualTo("COMPLETED");
    }
    @Test void noClosedAffectedPeriodsHasZeroBusinessEffect(){
        var fixture=new Fixture(LocalDate.of(2026,10,1));fixture.service.process(fixture.id);
        verify(fixture.adjustments,never()).save(any());assertThat(fixture.workflow.status).isEqualTo("COMPLETED");
    }
    static class Fixture {
        final UUID id=UUID.fromString("ebbe594b-59d3-4bf0-9c04-e97c466e69ca");
        final RetroRepository adjustments=mock(RetroRepository.class);final RetroWorkflow workflow=new RetroWorkflow();final RetroWorkflowService service;
        Fixture(LocalDate effective){
            var progress=mock(AggregateProgressRepository.class);var workflows=mock(WorkflowRepository.class);var receipt=mock(EventReceipt.class);var snapshots=mock(SnapshotClient.class);
            var cursor=new AggregateProgress();cursor.aggregateId="EMP001:HOUSING";cursor.lastCompletedVersion=1;
            workflow.eventId=id;workflow.aggregateId=cursor.aggregateId;workflow.status="RECEIVED";workflow.payload="fixture";
            when(workflows.aggregateId(id)).thenReturn(Optional.of(cursor.aggregateId));when(progress.lock(cursor.aggregateId)).thenReturn(Optional.of(cursor));when(workflows.lock(id)).thenReturn(Optional.of(workflow));
            when(receipt.decode("fixture")).thenReturn(new AllowanceRuleBackdated(id,cursor.aggregateId,1,Instant.parse("2026-10-25T00:00:00Z"),id,"AllowanceRuleBackdated","EMP001","HOUSING",1,2,new BigDecimal("2000.00"),new BigDecimal("3000.00"),effective));
            when(snapshots.openPeriod("EMP001")).thenReturn(new PayPeriodResponse(3L,"EMP001",LocalDate.of(2026,10,1),LocalDate.of(2026,10,31),"OPEN"));
            when(snapshots.history("EMP001")).thenReturn(List.of(
                new HistoricalSnapshot(1L,1L,"EMP001",LocalDate.of(2026,8,1),LocalDate.of(2026,8,31),"HOUSING",1,new BigDecimal("2000.00"),Instant.parse("2026-08-31T00:00:00Z")),
                new HistoricalSnapshot(2L,2L,"EMP001",LocalDate.of(2026,9,1),LocalDate.of(2026,9,30),"HOUSING",1,new BigDecimal("2000.00"),Instant.parse("2026-09-30T00:00:00Z"))));
            service=new RetroWorkflowService(progress,workflows,adjustments,receipt,snapshots,mock(OutboxRepository.class),JsonMapper.builder().findAndAddModules().build());
        }
    }
}
