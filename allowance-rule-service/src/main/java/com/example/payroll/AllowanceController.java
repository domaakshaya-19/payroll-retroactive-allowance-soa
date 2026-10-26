package com.example.payroll;

import com.example.payroll.contracts.*;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
@RestController @RequestMapping("/api/allowances/employees/{employeeId}")
public class AllowanceController {
    private final AllowanceRepository repository;private final AllowanceService service;
    public AllowanceController(AllowanceRepository repository,AllowanceService service){this.repository=repository;this.service=service;}
    @GetMapping @PreAuthorize("hasAnyRole('payrollClerk','payrollApprover') and @employeeAccess.allowed(authentication,#employeeId)")
    public java.util.List<AllowanceResponse> list(@PathVariable String employeeId){return repository.findByEmployeeId(employeeId).stream().map(AllowanceService::response).toList();}
    @PostMapping @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    @PreAuthorize("hasRole('payrollClerk') and @employeeAccess.allowed(authentication,#employeeId)")
    public AllowanceResponse create(@PathVariable String employeeId,@Valid @RequestBody AllowanceRequest request){return service.create(employeeId,request);}
    @PostMapping("/{allowanceType}/backdate") @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    @PreAuthorize("hasRole('payrollClerk') and @employeeAccess.allowed(authentication,#employeeId)")
    public AllowanceResponse backdate(@PathVariable String employeeId,@PathVariable String allowanceType,@Valid @RequestBody BackdateRequest request,@RequestHeader(value="X-Correlation-ID",required=false) java.util.UUID correlationId){return service.backdate(employeeId,allowanceType,request,correlationId);}
}
