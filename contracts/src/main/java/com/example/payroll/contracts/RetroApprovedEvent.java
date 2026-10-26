package com.example.payroll.contracts;
public record RetroApprovedEvent(java.util.UUID eventId, Long adjustmentId, String employeeId, java.math.BigDecimal difference, java.time.Instant occurredAt) {}
