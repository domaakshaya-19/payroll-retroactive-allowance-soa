package com.example.payroll.contracts;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
public record ApprovedRetroResponse(Long paymentPeriodId, String employeeId, BigDecimal total, List<RetroResponse> adjustments) {}
