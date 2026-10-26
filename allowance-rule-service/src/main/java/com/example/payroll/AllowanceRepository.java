package com.example.payroll;

import org.springframework.data.jpa.repository.JpaRepository;
public interface AllowanceRepository extends JpaRepository<AllowanceRecord,Long> {
    java.util.List<AllowanceRecord> findByEmployeeId(String employeeId);
    java.util.Optional<AllowanceRecord> findByEmployeeIdAndAllowanceTypeAndRuleVersion(String employeeId,String allowanceType,int ruleVersion);
}
