package com.example.payroll;

import com.example.payroll.contracts.*;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
@RestController @RequestMapping("/api/pay-periods/employees/{employeeId}")
public class PayPeriodController {
    private final PayPeriodRepository repository;private final PayPeriodService service;private final SnapshotRepository snapshots;
    public PayPeriodController(PayPeriodRepository repository,PayPeriodService service,SnapshotRepository snapshots){this.repository=repository;this.service=service;this.snapshots=snapshots;}
    @GetMapping @PreAuthorize("hasAnyRole('payrollClerk','payrollApprover') and @employeeAccess.allowed(authentication,#employeeId)")
    public java.util.List<PayPeriodResponse> list(@PathVariable String employeeId){return repository.findByEmployeeId(employeeId).stream().map(PayPeriodService::response).toList();}
    @PostMapping @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    @PreAuthorize("hasRole('payrollClerk') and @employeeAccess.allowed(authentication,#employeeId)")
    public PayPeriodResponse create(@PathVariable String employeeId,@Valid @RequestBody PayPeriodRequest request){return service.create(employeeId,request);}
    @PostMapping("/{periodId}/issue") @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    @PreAuthorize("hasRole('payrollClerk') and @employeeAccess.allowed(authentication,#employeeId)")
    public java.util.List<HistoricalSnapshot> issue(@PathVariable String employeeId,@PathVariable Long periodId,@Valid @RequestBody IssueStatementRequest request){return service.issue(employeeId,periodId,request);}
    @GetMapping("/snapshots") @PreAuthorize("hasAnyRole('payrollClerk','payrollApprover','payrollSnapshotReader') and @employeeAccess.allowed(authentication,#employeeId)")
    public java.util.List<HistoricalSnapshot> snapshots(@PathVariable String employeeId){return snapshots.findByEmployeeIdOrderByStartsOnAsc(employeeId).stream().map(PayPeriodService::snapshot).toList();}
    @GetMapping("/{periodId}/snapshots") @PreAuthorize("hasAnyRole('payrollClerk','payrollApprover','payrollSnapshotReader') and @employeeAccess.allowed(authentication,#employeeId)")
    public java.util.List<HistoricalSnapshot> historical(@PathVariable String employeeId,@PathVariable Long periodId){return snapshots.findByEmployeeIdAndPeriodId(employeeId,periodId).stream().map(PayPeriodService::snapshot).toList();}
    @GetMapping("/open") @PreAuthorize("hasAnyRole('payrollClerk','payrollApprover','payrollSnapshotReader') and @employeeAccess.allowed(authentication,#employeeId)")
    public PayPeriodResponse open(@PathVariable String employeeId){return service.open(employeeId);}
}
