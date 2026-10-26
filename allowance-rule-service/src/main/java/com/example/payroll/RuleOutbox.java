package com.example.payroll;

import jakarta.persistence.*;
@Entity
public class RuleOutbox {
    @Id java.util.UUID id;
    @Column(nullable=false) String eventKey;
    @Column(nullable=false) int aggregateVersion;
    @Lob @Column(nullable=false) String payload;
    @Column(nullable=false) java.time.Instant createdAt;
    protected RuleOutbox() {}
}
