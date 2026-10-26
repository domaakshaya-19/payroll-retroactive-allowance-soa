package com.example.payroll;

import com.example.payroll.contracts.*;
public interface SnapshotClient {
    java.util.List<HistoricalSnapshot> history(String employeeId);
    PayPeriodResponse openPeriod(String employeeId);
}
