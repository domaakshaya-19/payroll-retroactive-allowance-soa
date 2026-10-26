package com.example.payroll;

import org.springframework.data.jpa.repository.*;
import jakarta.persistence.LockModeType;
public interface RetroRepository extends JpaRepository<RetroRecord,Long>{
    java.util.List<RetroRecord> findByEmployeeId(String employeeId);
    java.util.List<RetroRecord> findByEventId(java.util.UUID eventId);
    java.util.List<RetroRecord> findByEmployeeIdAndPaymentPeriodIdAndStatus(String employeeId,Long paymentPeriodId,String status);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    java.util.Optional<RetroRecord> findByIdAndEmployeeId(Long id,String employeeId);
}
