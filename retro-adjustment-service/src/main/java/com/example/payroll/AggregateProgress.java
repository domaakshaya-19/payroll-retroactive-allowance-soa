package com.example.payroll;

import jakarta.persistence.*;
@Entity
public class AggregateProgress {
    @Id String aggregateId;
    @Column(nullable=false) int lastCompletedVersion=1;
    @Version Long lockVersion;
    protected AggregateProgress(){}
}
