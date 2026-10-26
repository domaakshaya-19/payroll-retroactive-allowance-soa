package com.example.payroll;

import jakarta.persistence.*;
import org.hibernate.annotations.Immutable;
@Entity @Immutable @Table(uniqueConstraints=@UniqueConstraint(columnNames={"periodId","allowanceType"}))
public class AllowanceSnapshot {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) Long id;
    @Column(nullable=false,updatable=false) Long statementId;
    @Column(nullable=false,updatable=false) Long periodId;
    @Column(nullable=false,updatable=false) String employeeId;
    @Column(nullable=false,updatable=false) java.time.LocalDate startsOn;
    @Column(nullable=false,updatable=false) java.time.LocalDate endsOn;
    @Column(nullable=false,updatable=false) String allowanceType;
    @Column(nullable=false,updatable=false) int ruleVersion;
    @Column(nullable=false,updatable=false,precision=14,scale=2) java.math.BigDecimal originalAmount;
    @Column(nullable=false,updatable=false) java.time.Instant issuedAt;
    protected AllowanceSnapshot(){}
    @PreUpdate @PreRemove void immutable(){throw new IllegalStateException("Issued snapshots are immutable");}
}
