package com.example.payroll;

import org.springframework.data.jpa.repository.*;
import jakarta.persistence.LockModeType;
public interface WorkflowRepository extends JpaRepository<RetroWorkflow,java.util.UUID>{
    @Query("select w.aggregateId from RetroWorkflow w where w.eventId=:eventId")
    java.util.Optional<String> aggregateId(java.util.UUID eventId);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select w from RetroWorkflow w where w.eventId=:eventId")
    java.util.Optional<RetroWorkflow> lock(java.util.UUID eventId);
    java.util.List<RetroWorkflow> findTop50ByStatusInOrderByReceivedAtAsc(java.util.Collection<String> statuses);
    java.util.List<RetroWorkflow> findByEmployeeId(String employeeId);
    java.util.Optional<RetroWorkflow> findByEventIdAndEmployeeId(java.util.UUID eventId,String employeeId);
}
