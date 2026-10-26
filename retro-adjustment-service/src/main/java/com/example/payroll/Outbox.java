package com.example.payroll;
import jakarta.persistence.*;
@Entity
public class Outbox {
    @Id java.util.UUID id;
    @Lob @Column(nullable=false) String payload;
    public Outbox() {}
}
