package com.example.payroll;

import com.example.payroll.contracts.*;
import java.util.*;
import java.math.BigDecimal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
@Service
public class RetroWorkflowService {
    private final AggregateProgressRepository progress;
    private final WorkflowRepository workflows;private final RetroRepository adjustments;private final EventReceipt receipt;
    private final SnapshotClient snapshots;private final OutboxRepository outbox;private final com.fasterxml.jackson.databind.ObjectMapper mapper;
    public RetroWorkflowService(AggregateProgressRepository progress,WorkflowRepository workflows,RetroRepository adjustments,EventReceipt receipt,SnapshotClient snapshots,OutboxRepository outbox,com.fasterxml.jackson.databind.ObjectMapper mapper){
        this.progress=progress;this.workflows=workflows;this.adjustments=adjustments;this.receipt=receipt;this.snapshots=snapshots;this.outbox=outbox;this.mapper=mapper;
    }
    @Transactional public void process(UUID eventId){
        var aggregateId=workflows.aggregateId(eventId).orElseThrow();
        var cursor=progress.lock(aggregateId).orElseThrow();
        var workflow=workflows.lock(eventId).orElseThrow();
        if("COMPLETED".equals(workflow.status) || "FAILED".equals(workflow.status))return;
        var event=receipt.decode(workflow.payload);
        if(event.oldVersion()!=cursor.lastCompletedVersion){workflow.status="WAITING_PREDECESSOR";return;}
        var open=snapshots.openPeriod(event.employeeId());
        if(open==null || !"OPEN".equals(open.status()) || !event.employeeId().equals(open.employeeId()))throw new IllegalStateException("Current payroll unavailable");
        var history=snapshots.history(event.employeeId());
        Set<Long> seen=new HashSet<>();
        for(var snapshot:history){
            if(!event.employeeId().equals(snapshot.employeeId()) || !event.allowanceType().equals(snapshot.allowanceType()) || snapshot.startsOn().isBefore(event.effectiveFrom()) || snapshot.ruleVersion()>=event.newVersion())continue;
            if(snapshot.periodId()==null || snapshot.statementId()==null || snapshot.originalAmount()==null || !seen.add(snapshot.periodId()))
                throw new IllegalStateException("Invalid historical snapshots");
            var row=new RetroRecord();row.eventId=eventId;row.employeeId=event.employeeId();row.allowanceType=event.allowanceType();
            row.sourcePeriodId=snapshot.periodId();row.statementId=snapshot.statementId();row.previousVersion=snapshot.ruleVersion();row.newVersion=event.newVersion();
            row.originallyPaid=snapshot.originalAmount().setScale(2);row.newAmount=event.newAmount().setScale(2);
            if(!snapshot.endsOn().isBefore(open.startsOn()))throw new IllegalStateException("Historical snapshot must precede current payroll");
            row.difference=row.newAmount.subtract(row.originallyPaid);row.status="DRAFT";row.paymentPeriodId=open.id();adjustments.save(row);
        }
        cursor.lastCompletedVersion=event.newVersion();
        workflow.status="COMPLETED";workflow.attempts++;workflow.errorCode=null;workflow.completedAt=java.time.Instant.now();
    }
    @Transactional public void failed(UUID eventId){
        var row=workflows.lock(eventId).orElseThrow();if("COMPLETED".equals(row.status))return;
        row.attempts++;row.status=row.attempts>=5?"FAILED":"RETRY_PENDING";row.errorCode="SNAPSHOT_PROCESSING_FAILED";
    }
    @Transactional public WorkflowResponse retry(String employeeId,UUID eventId){
        var row=workflows.lock(eventId).filter(w -> employeeId.equals(w.employeeId)).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,"Workflow not found"));
        if(!"FAILED".equals(row.status))throw new ResponseStatusException(HttpStatus.CONFLICT,"Only failed workflows can be retried");
        row.status="RECEIVED";row.attempts=0;row.errorCode=null;return workflowResponse(row);
    }
    public PayPeriodResponse currentOpen(String employeeId){
        try{return snapshots.openPeriod(employeeId);}catch(Exception ignored){throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Current open pay period unavailable");}
    }
    @Transactional(readOnly=true) public AllowanceRuleBackdated event(String employeeId,UUID eventId){
        return receipt.decode(workflows.findByEventIdAndEmployeeId(eventId,employeeId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,"Workflow not found")).payload);
    }
    @Transactional public RetroResponse approve(String employeeId,Long id,Long paymentPeriodId){
        var row=adjustments.findByIdAndEmployeeId(id,employeeId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,"Adjustment not found"));
        if(!"DRAFT".equals(row.status))throw new ResponseStatusException(HttpStatus.CONFLICT,"Only draft adjustments can be approved");
        if(!Objects.equals(row.paymentPeriodId,paymentPeriodId))throw new ResponseStatusException(HttpStatus.CONFLICT,"Adjustment belongs to a different payroll period");
        row.status="APPROVED";row.approvedAt=java.time.Instant.now();
        var event=new RetroApprovedEvent(UUID.randomUUID(),row.id,row.employeeId,row.difference,row.approvedAt);
        var pending=new Outbox();pending.id=event.eventId();
        try{pending.payload=mapper.writeValueAsString(event);}catch(Exception ignored){throw new IllegalStateException("Event serialization failed");}
        outbox.save(pending);return response(row);
    }
    @Transactional(readOnly=true) public ApprovedRetroResponse approved(String employeeId,Long openPeriodId){
        var rows=adjustments.findByEmployeeIdAndPaymentPeriodIdAndStatus(employeeId,openPeriodId,"APPROVED").stream().map(RetroWorkflowService::response).toList();
        return new ApprovedRetroResponse(openPeriodId,employeeId,rows.stream().map(RetroResponse::difference).reduce(new BigDecimal("0.00"),BigDecimal::add),rows);
    }
    static RetroResponse response(RetroRecord row){return new RetroResponse(row.id,row.eventId,row.employeeId,row.allowanceType,row.sourcePeriodId,row.statementId,row.previousVersion,row.newVersion,row.originallyPaid,row.newAmount,row.difference,row.status,row.paymentPeriodId);}
    static WorkflowResponse workflowResponse(RetroWorkflow row){return new WorkflowResponse(row.eventId,row.aggregateId,row.correlationId,row.employeeId,row.allowanceType,row.newVersion,row.status,row.attempts,row.errorCode);}
}
