package com.example.payroll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.Mockito.*;
@WebMvcTest(controllers=RetroController.class) @Import({SecurityConfig.class,EmployeeAccess.class})
class SignedHttpSecurityTest extends SignedTokensSupport {
    static final String BASE="/api/retro-adjustments/employees/EMP001";
    @Autowired MockMvc mvc;@MockitoBean RetroRepository repository;@MockitoBean RetroWorkflowService service;@MockitoBean WorkflowRepository workflows;
    @Test void anonymousProtectedRequestIs401() throws Exception {mvc.perform(get(BASE)).andExpect(status().isUnauthorized());verifyNoInteractions(repository);}
    @Test void bothRolesCanReadTheirExplicitEmployee() throws Exception {
        when(repository.findByEmployeeId("EMP001")).thenReturn(java.util.List.of());
        for(String role:java.util.List.of("payrollClerk","payrollApprover"))mvc.perform(get(BASE).header("Authorization",bearer(role,"EMP001"))).andExpect(status().isOk());
    }
    @Test void bothRolesAreForbiddenForAnotherEmployee() throws Exception {
        for(String role:java.util.List.of("payrollClerk","payrollApprover"))mvc.perform(get("/api/retro-adjustments/employees/EMP999").header("Authorization",bearer(role,"EMP001"))).andExpect(status().isForbidden());verifyNoInteractions(repository);
    }
    @Test void wrongSignatureOrExpiredTokenReturns401() throws Exception {
        mvc.perform(get(BASE).header("Authorization",bearer("payrollClerk","EMP001",keys(),java.time.Instant.now().plusSeconds(60)))).andExpect(status().isUnauthorized());
        mvc.perform(get(BASE).header("Authorization",bearer("payrollClerk","EMP001",KEYS,java.time.Instant.now().minusSeconds(2)))).andExpect(status().isUnauthorized());verifyNoInteractions(repository);
    }

@Test void clerkApprovalIs403AndApproverCanApprove() throws Exception {
    mvc.perform(post(BASE+"/1/approve").header("Authorization",bearer("payrollClerk","EMP001"))).andExpect(status().isForbidden());verifyNoInteractions(service);
    when(service.currentOpen("EMP001")).thenReturn(new com.example.payroll.contracts.PayPeriodResponse(3L,"EMP001",java.time.LocalDate.of(2026,10,1),java.time.LocalDate.of(2026,10,31),"OPEN"));
    mvc.perform(post(BASE+"/1/approve").header("Authorization",bearer("payrollApprover","EMP001"))).andExpect(status().isOk());verify(service).approve("EMP001",1L,3L);
}

}
