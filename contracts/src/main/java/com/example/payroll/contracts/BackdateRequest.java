package com.example.payroll.contracts;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
public record BackdateRequest(@Min(1) int expectedVersion, @NotNull @DecimalMin("0.00") @Digits(integer=12,fraction=2) BigDecimal monthlyAmount, @NotNull LocalDate effectiveFrom) {}
