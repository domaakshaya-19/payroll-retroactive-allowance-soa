package com.example.payroll;
import org.springframework.data.jpa.repository.JpaRepository;
public interface OutboxRepository extends JpaRepository<Outbox,java.util.UUID> {
    java.util.List<Outbox> findTop50ByOrderByIdAsc();
}
