package com.example.payroll.contracts;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
public record HistoricalSnapshot(Long statementId, Long periodId, String employeeId, LocalDate startsOn, LocalDate endsOn, String allowanceType, int ruleVersion, BigDecimal originalAmount, Instant issuedAt) {}
