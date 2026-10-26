package com.example.payroll;

import org.springframework.data.jpa.repository.*;
import jakarta.persistence.LockModeType;
public interface PayPeriodRepository extends JpaRepository<PayPeriodRecord,Long>{
    java.util.List<PayPeriodRecord> findByEmployeeId(String employeeId);
    java.util.List<PayPeriodRecord> findByEmployeeIdAndStatus(String employeeId,String status);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    java.util.Optional<PayPeriodRecord> findByIdAndEmployeeId(Long id,String employeeId);
    boolean existsByEmployeeIdAndStartsOn(String employeeId,java.time.LocalDate startsOn);
}
