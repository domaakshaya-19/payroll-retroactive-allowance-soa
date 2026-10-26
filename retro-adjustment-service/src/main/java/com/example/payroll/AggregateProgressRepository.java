package com.example.payroll;

import org.springframework.data.jpa.repository.*;
import jakarta.persistence.LockModeType;
public interface AggregateProgressRepository extends JpaRepository<AggregateProgress,String>{
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select p from AggregateProgress p where p.aggregateId=:aggregateId")
    java.util.Optional<AggregateProgress> lock(String aggregateId);
}
