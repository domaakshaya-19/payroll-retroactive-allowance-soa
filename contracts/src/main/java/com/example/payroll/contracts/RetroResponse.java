package com.example.payroll.contracts;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
public record RetroResponse(Long id, UUID eventId, String employeeId, String allowanceType, Long sourcePeriodId, Long statementId, int previousVersion, int newVersion, BigDecimal originallyPaid, BigDecimal newAmount, BigDecimal difference, String status, Long paymentPeriodId) {}
