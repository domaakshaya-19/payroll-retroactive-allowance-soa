package com.example.payroll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.Mockito.*;
@WebMvcTest(controllers=RetroController.class,properties={"payroll.cors-origins=https://localhost:3000","payroll.jwt.public-key=unused","payroll.jwt.issuer=https://issuer.test"})
@Import({SecurityConfig.class,EmployeeAccess.class})
class EmployeeSecurityTest {
    @Autowired MockMvc mvc;
    @MockitoBean JwtDecoder decoder;
    @MockitoBean RetroRepository repository;
    @MockitoBean WorkflowRepository workflows;
    @MockitoBean RetroWorkflowService service;

    private org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor clerk() {
        return jwt().jwt(j -> j.claim("employees",java.util.List.of("E001"))).authorities(new SimpleGrantedAuthority("ROLE_payrollClerk"));
    }
    @Test void unauthenticatedDenied() throws Exception {
        mvc.perform(get("/api/retro-adjustments/employees/E001")).andExpect(status().isUnauthorized());
    }
    @Test void scopedEmployeeAllowed() throws Exception {
        when(repository.findByEmployeeId("E001")).thenReturn(java.util.List.of());
        mvc.perform(get("/api/retro-adjustments/employees/E001").with(clerk())).andExpect(status().isOk());
    }
    @Test void otherEmployeeDeniedBeforeDatabaseAccess() throws Exception {
        mvc.perform(get("/api/retro-adjustments/employees/E999").with(clerk())).andExpect(status().isForbidden());
        verifyNoInteractions(repository);
    }
    @Test void missingEmployeeClaimDenied() throws Exception {
        mvc.perform(get("/api/retro-adjustments/employees/E001").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_payrollClerk")))).andExpect(status().isForbidden());
        verifyNoInteractions(repository);
    }
    @Test void unrelatedRoleDenied() throws Exception {
        mvc.perform(get("/api/retro-adjustments/employees/E001").with(jwt().jwt(j -> j.claim("employees",java.util.List.of("E001"))).authorities(new SimpleGrantedAuthority("ROLE_admin")))).andExpect(status().isForbidden());
    }
    @Test void corsRejectsUnexpectedOrigin() throws Exception {
        mvc.perform(options("/api/retro-adjustments/employees/E001").header("Origin","https://evil.example").header("Access-Control-Request-Method","GET")).andExpect(status().isForbidden());
    }
    @Test void clerkCannotApprove() throws Exception {
        mvc.perform(post("/api/retro-adjustments/employees/E001/1/approve").with(clerk())).andExpect(status().isForbidden());
        verifyNoInteractions(repository,service);
    }
    @Test void approverCannotApproveAnotherEmployee() throws Exception {
        mvc.perform(post("/api/retro-adjustments/employees/E999/1/approve").with(jwt().jwt(j -> j.claim("employees",java.util.List.of("E001"))).authorities(new SimpleGrantedAuthority("ROLE_payrollApprover")))).andExpect(status().isForbidden());
        verifyNoInteractions(repository,service);
    }
    @Test void scopedApproverCanApprove() throws Exception {
        when(service.currentOpen("E001")).thenReturn(new com.example.payroll.contracts.PayPeriodResponse(3L,"E001",java.time.LocalDate.of(2026,10,1),java.time.LocalDate.of(2026,10,31),"OPEN"));
        mvc.perform(post("/api/retro-adjustments/employees/E001/1/approve").with(jwt().jwt(j -> j.claim("employees",java.util.List.of("E001"))).authorities(new SimpleGrantedAuthority("ROLE_payrollApprover")))).andExpect(status().isOk());
        verify(service).approve("E001",1L,3L);
    }
}
