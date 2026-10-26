package com.example.payroll;

import jakarta.persistence.*;
import org.hibernate.annotations.Immutable;
@Entity @Immutable
public class IssuedStatement {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) Long id;
    @Column(nullable=false,unique=true,updatable=false) Long periodId;
    @Column(nullable=false,updatable=false) String employeeId;
    @Column(nullable=false,updatable=false) java.time.Instant issuedAt;
    protected IssuedStatement(){}
    @PreUpdate @PreRemove void immutable(){throw new IllegalStateException("Issued statements are immutable");}
}
