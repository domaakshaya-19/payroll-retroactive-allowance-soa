package com.example.payroll;

import org.springframework.stereotype.Component;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.transaction.annotation.Transactional;
import com.example.payroll.contracts.AllowanceRequest;
@Component @ConditionalOnProperty(name="payroll.seed.enabled",havingValue="true")
public class SyntheticSeed implements ApplicationRunner {
    private final RuleSeriesRepository series;private final AllowanceService service;
    public SyntheticSeed(RuleSeriesRepository series,AllowanceService service){this.series=series;this.service=service;}
    @Transactional public void run(ApplicationArguments args){
        if(series.findByEmployeeIdAndAllowanceType("EMP001","HOUSING").isEmpty())
            service.create("EMP001",new AllowanceRequest("HOUSING",new java.math.BigDecimal("2000.00"),java.time.LocalDate.of(2026,8,1)));
    }
}
