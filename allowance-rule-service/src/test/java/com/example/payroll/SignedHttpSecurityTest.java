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
@WebMvcTest(controllers=AllowanceController.class) @Import({SecurityConfig.class,EmployeeAccess.class})
class SignedHttpSecurityTest extends SignedTokensSupport {
    static final String BASE="/api/allowances/employees/EMP001";
    @Autowired MockMvc mvc;@MockitoBean AllowanceRepository repository;@MockitoBean AllowanceService service;
    @Test void anonymousProtectedRequestIs401() throws Exception {mvc.perform(get(BASE)).andExpect(status().isUnauthorized());verifyNoInteractions(repository);}
    @Test void bothRolesCanReadTheirExplicitEmployee() throws Exception {
        when(repository.findByEmployeeId("EMP001")).thenReturn(java.util.List.of());
        for(String role:java.util.List.of("payrollClerk","payrollApprover"))mvc.perform(get(BASE).header("Authorization",bearer(role,"EMP001"))).andExpect(status().isOk());
    }
    @Test void bothRolesAreForbiddenForAnotherEmployee() throws Exception {
        for(String role:java.util.List.of("payrollClerk","payrollApprover"))mvc.perform(get("/api/allowances/employees/EMP999").header("Authorization",bearer(role,"EMP001"))).andExpect(status().isForbidden());verifyNoInteractions(repository);
    }
    @Test void wrongSignatureOrExpiredTokenReturns401() throws Exception {
        mvc.perform(get(BASE).header("Authorization",bearer("payrollClerk","EMP001",keys(),java.time.Instant.now().plusSeconds(60)))).andExpect(status().isUnauthorized());
        mvc.perform(get(BASE).header("Authorization",bearer("payrollClerk","EMP001",KEYS,java.time.Instant.now().minusSeconds(2)))).andExpect(status().isUnauthorized());verifyNoInteractions(repository);
    }

@Test void clerkCanCreateAndBackdateWhileApproverCannot() throws Exception {
    String create="{\"allowanceType\":\"TRAVEL\",\"monthlyAmount\":2000.00,\"effectiveFrom\":\"2026-08-01\"}";
    String backdate="{\"expectedVersion\":1,\"monthlyAmount\":3000.00,\"effectiveFrom\":\"2026-08-01\"}";
    mvc.perform(post(BASE).header("Authorization",bearer("payrollClerk","EMP001")).contentType("application/json").content(create)).andExpect(status().isCreated());
    mvc.perform(post(BASE+"/HOUSING/backdate").header("Authorization",bearer("payrollClerk","EMP001")).contentType("application/json").content(backdate)).andExpect(status().isCreated());
    verify(service).create(eq("EMP001"),any());verify(service).backdate(eq("EMP001"),eq("HOUSING"),any(),isNull());clearInvocations(service);
    mvc.perform(post(BASE).header("Authorization",bearer("payrollApprover","EMP001")).contentType("application/json").content(create)).andExpect(status().isForbidden());
    mvc.perform(post(BASE+"/HOUSING/backdate").header("Authorization",bearer("payrollApprover","EMP001")).contentType("application/json").content(backdate)).andExpect(status().isForbidden());verifyNoInteractions(service);
}

}
