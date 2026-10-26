package com.example.payroll;

import org.springframework.data.jpa.repository.*;
import jakarta.persistence.LockModeType;
public interface RuleSeriesRepository extends JpaRepository<RuleSeries,Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    java.util.Optional<RuleSeries> findByEmployeeIdAndAllowanceType(String employeeId,String allowanceType);
}
