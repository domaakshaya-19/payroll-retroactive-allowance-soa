package com.example.payroll;

import com.example.payroll.contracts.*;
import java.util.*;
import java.time.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
@Service @Transactional
public class PayPeriodService {
    private final PayPeriodRepository periods;private final EmployeeLedgerRepository ledgers;
    private final IssuedStatementRepository statements;private final SnapshotRepository snapshots;
    public PayPeriodService(PayPeriodRepository periods,EmployeeLedgerRepository ledgers,IssuedStatementRepository statements,SnapshotRepository snapshots){
        this.periods=periods;this.ledgers=ledgers;this.statements=statements;this.snapshots=snapshots;
    }
    public PayPeriodResponse create(String employeeId,PayPeriodRequest request){
        if(request.startsOn()==null || request.endsOn()==null || request.startsOn().getDayOfMonth()!=1 || !request.endsOn().equals(request.startsOn().withDayOfMonth(request.startsOn().lengthOfMonth())))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Period must cover one complete calendar month");
        if(ledgers.lock(employeeId).isEmpty()){var ledger=new EmployeeLedger();ledger.employeeId=employeeId;ledgers.saveAndFlush(ledger);}
        if(!periods.findByEmployeeIdAndStatus(employeeId,"OPEN").isEmpty())throw new ResponseStatusException(HttpStatus.CONFLICT,"Employee already has an open period");
        if(periods.existsByEmployeeIdAndStartsOn(employeeId,request.startsOn()))throw new ResponseStatusException(HttpStatus.CONFLICT,"Period already exists");
        var row=new PayPeriodRecord();row.employeeId=employeeId;row.startsOn=request.startsOn();row.endsOn=request.endsOn();row.status="OPEN";
        return response(periods.save(row));
    }
    public List<HistoricalSnapshot> issue(String employeeId,Long periodId,IssueStatementRequest request){
        var period=periods.findByIdAndEmployeeId(periodId,employeeId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,"Period not found"));
        if(!"OPEN".equals(period.status))throw new ResponseStatusException(HttpStatus.CONFLICT,"Closed period or issued statement cannot be modified");
        if(request.allowances()==null || request.allowances().isEmpty())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"At least one allowance snapshot is required");
        var types=new HashSet<String>();
        for(var line:request.allowances())if(!types.add(line.allowanceType()))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Duplicate allowance type");
        var statement=new IssuedStatement();statement.employeeId=employeeId;statement.periodId=periodId;statement.issuedAt=Instant.now();statements.saveAndFlush(statement);
        var result=new ArrayList<HistoricalSnapshot>();
        for(var line:request.allowances()){
            var row=new AllowanceSnapshot();row.statementId=statement.id;row.periodId=periodId;row.employeeId=employeeId;row.startsOn=period.startsOn;row.endsOn=period.endsOn;
            row.allowanceType=line.allowanceType();row.ruleVersion=line.ruleVersion();row.originalAmount=line.originalAmount().setScale(2);row.issuedAt=statement.issuedAt;
            result.add(snapshot(snapshots.save(row)));
        }
        period.status="CLOSED";
        return List.copyOf(result);
    }
    @Transactional(readOnly=true) public PayPeriodResponse open(String employeeId){
        var open=periods.findByEmployeeIdAndStatus(employeeId,"OPEN");
        if(open.size()!=1)throw new ResponseStatusException(HttpStatus.CONFLICT,"Exactly one open period is required");
        return response(open.get(0));
    }
    static PayPeriodResponse response(PayPeriodRecord row){return new PayPeriodResponse(row.id,row.employeeId,row.startsOn,row.endsOn,row.status);}
    static HistoricalSnapshot snapshot(AllowanceSnapshot row){return new HistoricalSnapshot(row.statementId,row.periodId,row.employeeId,row.startsOn,row.endsOn,row.allowanceType,row.ruleVersion,row.originalAmount,row.issuedAt);}
}
