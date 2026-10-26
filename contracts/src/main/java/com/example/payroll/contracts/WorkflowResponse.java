package com.example.payroll.contracts;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
public record WorkflowResponse(UUID eventId, String aggregateId, UUID correlationId, String employeeId, String allowanceType, int newVersion, String status, int attempts, String errorCode) {}
