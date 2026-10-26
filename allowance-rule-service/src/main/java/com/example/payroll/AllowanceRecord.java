package com.example.payroll;

import jakarta.persistence.*;
import org.hibernate.annotations.Immutable;
@Entity @Immutable
@Table(name="allowance_rule_version",uniqueConstraints=@UniqueConstraint(columnNames={"employeeId","allowanceType","ruleVersion"}))
public class AllowanceRecord {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) Long id;
    @Column(nullable=false,updatable=false) String employeeId;
    @Column(nullable=false,updatable=false) String allowanceType;
    @Column(nullable=false,updatable=false) int ruleVersion;
    @Column(nullable=false,updatable=false,precision=14,scale=2) java.math.BigDecimal monthlyAmount;
    @Column(nullable=false,updatable=false) java.time.LocalDate effectiveFrom;
    protected AllowanceRecord() {}
    @PreUpdate @PreRemove void immutable(){throw new IllegalStateException("Rule versions are immutable");}
}
