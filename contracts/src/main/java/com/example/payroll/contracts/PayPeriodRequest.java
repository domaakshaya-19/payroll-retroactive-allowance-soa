package com.example.payroll.contracts;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
public record PayPeriodRequest(@NotNull LocalDate startsOn, @NotNull LocalDate endsOn) {}
