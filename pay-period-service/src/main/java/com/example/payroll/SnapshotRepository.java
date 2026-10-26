package com.example.payroll;

public interface SnapshotRepository extends org.springframework.data.jpa.repository.JpaRepository<AllowanceSnapshot,Long>{
    java.util.List<AllowanceSnapshot> findByEmployeeIdOrderByStartsOnAsc(String employeeId);
    java.util.List<AllowanceSnapshot> findByEmployeeIdAndPeriodId(String employeeId,Long periodId);
}
