package com.example.payroll;

import jakarta.persistence.*;
@Entity @Table(name="payroll_period",uniqueConstraints=@UniqueConstraint(columnNames={"employeeId","startsOn"}))
public class PayPeriodRecord {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) Long id;
    @Version Long lockVersion;
    @Column(nullable=false,updatable=false) String employeeId;
    @Column(nullable=false,updatable=false) java.time.LocalDate startsOn;
    @Column(nullable=false,updatable=false) java.time.LocalDate endsOn;
    @Column(nullable=false) String status;
    @Transient boolean wasClosed;
    protected PayPeriodRecord(){}
    @PostLoad void remember(){wasClosed="CLOSED".equals(status);}
    @PreUpdate @PreRemove void protect(){if(wasClosed)throw new IllegalStateException("Closed period is immutable");}
}
