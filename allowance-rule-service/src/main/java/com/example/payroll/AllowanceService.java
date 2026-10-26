package com.example.payroll;

import com.example.payroll.contracts.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.time.*;
import java.util.*;
@Service @Transactional
public class AllowanceService {
    private final AllowanceRepository rules;
    private final RuleSeriesRepository series;
    private final RuleOutboxRepository outbox;
    private final ObjectMapper mapper;
    @org.springframework.beans.factory.annotation.Value("${payroll.business-date:2026-10-25}") private LocalDate businessDate=LocalDate.of(2026,10,25);
    public AllowanceService(AllowanceRepository rules,RuleSeriesRepository series,RuleOutboxRepository outbox,ObjectMapper mapper){
        this.rules=rules;this.series=series;this.outbox=outbox;this.mapper=mapper;
    }
    public AllowanceResponse create(String employeeId,AllowanceRequest request){
        validate(request.monthlyAmount(),request.effectiveFrom());
        if(series.findByEmployeeIdAndAllowanceType(employeeId,request.allowanceType()).isPresent())
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Rule exists; append a backdated version");
        var aggregate=new RuleSeries();aggregate.employeeId=employeeId;aggregate.allowanceType=request.allowanceType();aggregate.currentVersion=1;
        series.saveAndFlush(aggregate);
        return response(append(employeeId,request.allowanceType(),1,request.monthlyAmount(),request.effectiveFrom()));
    }
    public AllowanceResponse backdate(String employeeId,String allowanceType,BackdateRequest request){
        return backdate(employeeId,allowanceType,request,UUID.randomUUID());
    }
    public AllowanceResponse backdate(String employeeId,String allowanceType,BackdateRequest request,UUID correlationId){
        validate(request.monthlyAmount(),request.effectiveFrom());
        if(request.effectiveFrom().isAfter(businessDate)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Backdate must not be in the future");
        var aggregate=series.findByEmployeeIdAndAllowanceType(employeeId,allowanceType)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,"Allowance rule not found"));
        if(aggregate.currentVersion!=request.expectedVersion()) throw new ResponseStatusException(HttpStatus.CONFLICT,"Rule version has changed");
        var old=rules.findByEmployeeIdAndAllowanceTypeAndRuleVersion(employeeId,allowanceType,aggregate.currentVersion).orElseThrow();
        if(old.monthlyAmount.compareTo(request.monthlyAmount())==0)throw new ResponseStatusException(HttpStatus.CONFLICT,"Backdate must change the allowance amount");
        int next=aggregate.currentVersion+1;
        var rule=append(employeeId,allowanceType,next,request.monthlyAmount(),request.effectiveFrom());
        aggregate.currentVersion=next;
        var event=new AllowanceRuleBackdated(UUID.randomUUID(),employeeId+":"+allowanceType,1,Instant.now(),
            correlationId==null?UUID.randomUUID():correlationId,"AllowanceRuleBackdated",employeeId,allowanceType,old.ruleVersion,next,
            old.monthlyAmount,rule.monthlyAmount,rule.effectiveFrom);
        var pending=new RuleOutbox();pending.id=event.eventId();pending.eventKey=event.aggregateId();pending.aggregateVersion=event.newVersion();pending.createdAt=event.occurredAt();
        try{pending.payload=mapper.writeValueAsString(event);}catch(Exception error){throw new IllegalStateException("Event serialization failed");}
        outbox.save(pending);
        return response(rule);
    }
    private void validate(java.math.BigDecimal amount,LocalDate from){
        if(amount==null || amount.signum()<0 || amount.scale()>2 || amount.precision()-amount.scale()>12 || from==null || from.getDayOfMonth()!=1)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Use a nonnegative two-decimal amount and the first day of a month");
    }
    private AllowanceRecord append(String employeeId,String type,int version,java.math.BigDecimal amount,LocalDate from){
        var row=new AllowanceRecord();row.employeeId=employeeId;row.allowanceType=type;row.ruleVersion=version;row.monthlyAmount=amount.setScale(2);row.effectiveFrom=from;
        return rules.save(row);
    }
    static AllowanceResponse response(AllowanceRecord row){return new AllowanceResponse(row.id,row.employeeId,row.allowanceType,row.ruleVersion,row.monthlyAmount,row.effectiveFrom);}
}
