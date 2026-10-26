package com.example.payroll;

import org.springframework.stereotype.Component;
import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.transaction.annotation.Transactional;
import com.example.payroll.contracts.*;
import java.time.LocalDate;
@Component @ConditionalOnProperty(name="payroll.seed.enabled",havingValue="true")
public class SyntheticSeed implements ApplicationRunner {
    private final PayPeriodService service;private final PayPeriodRepository periods;
    public SyntheticSeed(PayPeriodService service,PayPeriodRepository periods){this.service=service;this.periods=periods;}
    @Transactional public void run(ApplicationArguments args){
        for(int month=8;month<=10;month++){
            var start=LocalDate.of(2026,month,1);
            if(periods.existsByEmployeeIdAndStartsOn("EMP001",start))continue;
            var row=service.create("EMP001",new PayPeriodRequest(start,start.withDayOfMonth(start.lengthOfMonth())));
            if(month<10)service.issue("EMP001",row.id(),new IssueStatementRequest(java.util.List.of(new SnapshotLineRequest("HOUSING",1,new java.math.BigDecimal("2000.00")))));
        }
    }
}
