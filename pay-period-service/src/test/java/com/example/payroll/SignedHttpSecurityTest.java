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
@WebMvcTest(controllers=PayPeriodController.class) @Import({SecurityConfig.class,EmployeeAccess.class})
class SignedHttpSecurityTest extends SignedTokensSupport {
    static final String BASE="/api/pay-periods/employees/EMP001";
    @Autowired MockMvc mvc;@MockitoBean PayPeriodRepository repository;@MockitoBean PayPeriodService service;@MockitoBean SnapshotRepository snapshots;
    @Test void anonymousProtectedRequestIs401() throws Exception {mvc.perform(get(BASE)).andExpect(status().isUnauthorized());verifyNoInteractions(repository);}
    @Test void bothRolesCanReadTheirExplicitEmployee() throws Exception {
        when(repository.findByEmployeeId("EMP001")).thenReturn(java.util.List.of());
        for(String role:java.util.List.of("payrollClerk","payrollApprover"))mvc.perform(get(BASE).header("Authorization",bearer(role,"EMP001"))).andExpect(status().isOk());
    }
    @Test void bothRolesAreForbiddenForAnotherEmployee() throws Exception {
        for(String role:java.util.List.of("payrollClerk","payrollApprover"))mvc.perform(get("/api/pay-periods/employees/EMP999").header("Authorization",bearer(role,"EMP001"))).andExpect(status().isForbidden());verifyNoInteractions(repository);
    }
    @Test void wrongSignatureOrExpiredTokenReturns401() throws Exception {
        mvc.perform(get(BASE).header("Authorization",bearer("payrollClerk","EMP001",keys(),java.time.Instant.now().plusSeconds(60)))).andExpect(status().isUnauthorized());
        mvc.perform(get(BASE).header("Authorization",bearer("payrollClerk","EMP001",KEYS,java.time.Instant.now().minusSeconds(2)))).andExpect(status().isUnauthorized());verifyNoInteractions(repository);
    }

@Test void noRoleCanEditOrDeleteIssuedStatements() throws Exception {
    for(String role:java.util.List.of("payrollClerk","payrollApprover")){
        String token=bearer(role,"EMP001");
        mvc.perform(put(BASE+"/1/snapshots").header("Authorization",token).contentType("application/json").content("{}")).andExpect(status().isForbidden());
        mvc.perform(patch(BASE+"/1/snapshots").header("Authorization",token).contentType("application/json").content("{}")).andExpect(status().isForbidden());
        mvc.perform(delete(BASE+"/1/snapshots").header("Authorization",token)).andExpect(status().isForbidden());
    }
    verifyNoInteractions(service,snapshots);
}
@Test void attemptingToReissueClosedPeriodReturns409() throws Exception {
    when(service.issue(eq("EMP001"),eq(1L),any())).thenThrow(new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT,"Closed period or issued statement cannot be modified"));
    mvc.perform(post(BASE+"/1/issue").header("Authorization",bearer("payrollClerk","EMP001")).contentType("application/json")
        .content("{\"allowances\":[{\"allowanceType\":\"HOUSING\",\"ruleVersion\":2,\"originalAmount\":3000.00}]}")).andExpect(status().isConflict());
}

}
