package com.example.payroll;

import com.example.payroll.contracts.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
@RestController @RequestMapping("/api/retro-adjustments/employees/{employeeId}")
public class RetroController {
    private final RetroRepository repository;private final WorkflowRepository workflows;private final RetroWorkflowService service;
    public RetroController(RetroRepository repository,WorkflowRepository workflows,RetroWorkflowService service){this.repository=repository;this.workflows=workflows;this.service=service;}
    @GetMapping @PreAuthorize("hasAnyRole('payrollClerk','payrollApprover') and @employeeAccess.allowed(authentication,#employeeId)")
    public java.util.List<RetroResponse> list(@PathVariable String employeeId){return repository.findByEmployeeId(employeeId).stream().map(RetroWorkflowService::response).toList();}
    @GetMapping("/workflows") @PreAuthorize("hasAnyRole('payrollClerk','payrollApprover') and @employeeAccess.allowed(authentication,#employeeId)")
    public java.util.List<WorkflowResponse> workflows(@PathVariable String employeeId){return workflows.findByEmployeeId(employeeId).stream().map(RetroWorkflowService::workflowResponse).toList();}
    @GetMapping("/workflows/{eventId}/event") @PreAuthorize("hasAnyRole('payrollClerk','payrollApprover') and @employeeAccess.allowed(authentication,#employeeId)")
    public AllowanceRuleBackdated event(@PathVariable String employeeId,@PathVariable java.util.UUID eventId){return service.event(employeeId,eventId);}
    @PostMapping("/workflows/{eventId}/retry") @PreAuthorize("hasRole('payrollApprover') and @employeeAccess.allowed(authentication,#employeeId)")
    public WorkflowResponse retry(@PathVariable String employeeId,@PathVariable java.util.UUID eventId){return service.retry(employeeId,eventId);}
    @PostMapping("/{id}/approve") @PreAuthorize("hasRole('payrollApprover') and @employeeAccess.allowed(authentication,#employeeId)")
    public RetroResponse approve(@PathVariable String employeeId,@PathVariable Long id){return service.approve(employeeId,id,service.currentOpen(employeeId).id());}
    @GetMapping("/approved/current-open") @PreAuthorize("hasAnyRole('payrollClerk','payrollApprover') and @employeeAccess.allowed(authentication,#employeeId)")
    public ApprovedRetroResponse approved(@PathVariable String employeeId){return service.approved(employeeId,service.currentOpen(employeeId).id());}
}
