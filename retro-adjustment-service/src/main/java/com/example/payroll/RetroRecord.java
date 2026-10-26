package com.example.payroll;

import jakarta.persistence.*;
@Entity @Table(name="retro_period_adjustment",uniqueConstraints=@UniqueConstraint(columnNames={"eventId","sourcePeriodId","allowanceType"}))
public class RetroRecord {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) Long id;
    @Version Long lockVersion;
    @Column(nullable=false,updatable=false) java.util.UUID eventId;
    @Column(nullable=false,updatable=false) String employeeId;
    @Column(nullable=false,updatable=false) String allowanceType;
    @Column(nullable=false,updatable=false) Long sourcePeriodId;
    @Column(nullable=false,updatable=false) Long statementId;
    @Column(nullable=false,updatable=false) int previousVersion;
    @Column(nullable=false,updatable=false) int newVersion;
    @Column(nullable=false,updatable=false,precision=14,scale=2) java.math.BigDecimal originallyPaid;
    @Column(nullable=false,updatable=false,precision=14,scale=2) java.math.BigDecimal newAmount;
    @Column(nullable=false,updatable=false,precision=15,scale=2) java.math.BigDecimal difference;
    @Column(nullable=false) String status;
    @Column(updatable=false) Long paymentPeriodId;
    java.time.Instant approvedAt;
    protected RetroRecord(){}
}
