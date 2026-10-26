package com.example.payroll;

import jakarta.persistence.*;
@Entity @Table(uniqueConstraints=@UniqueConstraint(columnNames={"employeeId","allowanceType"}))
public class RuleSeries {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) Long id;
    @Column(nullable=false,updatable=false) String employeeId;
    @Column(nullable=false,updatable=false) String allowanceType;
    int currentVersion;
    @Version Long lockVersion;
    protected RuleSeries() {}
}
