package com.example.payroll;

import org.springframework.data.jpa.repository.*;
import jakarta.persistence.LockModeType;
public interface EmployeeLedgerRepository extends JpaRepository<EmployeeLedger,String>{
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select e from EmployeeLedger e where e.employeeId=:employeeId")
    java.util.Optional<EmployeeLedger> lock(String employeeId);
}
