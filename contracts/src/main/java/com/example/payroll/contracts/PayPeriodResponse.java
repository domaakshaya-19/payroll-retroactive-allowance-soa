package com.example.payroll.contracts;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
public record PayPeriodResponse(Long id, String employeeId, LocalDate startsOn, LocalDate endsOn, String status) {}
