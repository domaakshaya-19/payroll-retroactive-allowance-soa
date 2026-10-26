package com.example.payroll.contracts;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
public record IssueStatementRequest(@NotEmpty List<@NotNull @jakarta.validation.Valid SnapshotLineRequest> allowances) {}
