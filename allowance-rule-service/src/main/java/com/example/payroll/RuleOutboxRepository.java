package com.example.payroll;

import org.springframework.data.jpa.repository.JpaRepository;
public interface RuleOutboxRepository extends JpaRepository<RuleOutbox,java.util.UUID> {
    java.util.List<RuleOutbox> findTop50ByOrderByEventKeyAscAggregateVersionAsc();
}
