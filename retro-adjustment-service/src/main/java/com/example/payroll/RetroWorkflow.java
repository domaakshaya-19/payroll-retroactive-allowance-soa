package com.example.payroll;

import jakarta.persistence.*;
@Entity @Table(uniqueConstraints=@UniqueConstraint(columnNames={"employeeId","allowanceType","newVersion"}))
public class RetroWorkflow {
    @Id java.util.UUID eventId;
    @Column(nullable=false,updatable=false) String aggregateId;
    @Column(nullable=false,updatable=false) java.util.UUID correlationId;
    @Column(nullable=false,updatable=false) String employeeId;
    @Column(nullable=false,updatable=false) String allowanceType;
    @Column(nullable=false,updatable=false) int newVersion;
    @Lob @Column(nullable=false,updatable=false) String payload;
    @Column(nullable=false) String status;
    int attempts;
    String errorCode;
    java.time.Instant receivedAt;
    java.time.Instant completedAt;
    @Version Long lockVersion;
    protected RetroWorkflow(){}
}
