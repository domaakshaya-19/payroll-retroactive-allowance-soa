package com.example.payroll.contracts;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
public record AllowanceResponse(Long id, String employeeId, String allowanceType, int ruleVersion, BigDecimal monthlyAmount, LocalDate effectiveFrom) {}
