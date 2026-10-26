package com.example.payroll.contracts;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
public record AllowanceRequest(@NotBlank @Pattern(regexp="[A-Z][A-Z0-9_]{0,39}") String allowanceType, @NotNull @DecimalMin("0.00") @Digits(integer=12,fraction=2) BigDecimal monthlyAmount, @NotNull LocalDate effectiveFrom) {}
