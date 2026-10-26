package com.example.payroll.contracts;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;
public record AllowanceRuleBackdated(
    @NotNull UUID eventId,
    @NotBlank @Size(max=105) String aggregateId,
    @Min(1) @Max(1) int schemaVersion,
    @NotNull Instant occurredAt,
    @NotNull UUID correlationId,
    @NotBlank @Pattern(regexp="AllowanceRuleBackdated") String eventType,
    @NotBlank @Pattern(regexp="[A-Za-z0-9_-]{1,64}") String employeeId,
    @NotBlank @Pattern(regexp="[A-Z][A-Z0-9_]{0,39}") String allowanceType,
    @Min(1) int oldVersion,
    @Min(2) int newVersion,
    @NotNull @DecimalMin("0.00") @Digits(integer=12,fraction=2) BigDecimal oldAmount,
    @NotNull @DecimalMin("0.00") @Digits(integer=12,fraction=2) BigDecimal newAmount,
    @NotNull LocalDate effectiveFrom) {}
