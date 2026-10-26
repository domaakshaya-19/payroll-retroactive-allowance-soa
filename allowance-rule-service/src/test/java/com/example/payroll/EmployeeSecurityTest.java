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
@WebMvcTest(controllers=AllowanceController.class,properties={"payroll.cors-origins=https://localhost:3000","payroll.jwt.public-key=unused","payroll.jwt.issuer=https://issuer.test"})
@Import({SecurityConfig.class,EmployeeAccess.class})
class EmployeeSecurityTest {
    @Autowired MockMvc mvc;
    @MockitoBean JwtDecoder decoder;
    @MockitoBean AllowanceService service;
    @MockitoBean AllowanceRepository repository;
    
    private org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor clerk() {
        return jwt().jwt(j -> j.claim("employees",java.util.List.of("E001"))).authorities(new SimpleGrantedAuthority("ROLE_payrollClerk"));
    }
    @Test void unauthenticatedDenied() throws Exception {
        mvc.perform(get("/api/allowances/employees/E001")).andExpect(status().isUnauthorized());
    }
    @Test void scopedEmployeeAllowed() throws Exception {
        when(repository.findByEmployeeId("E001")).thenReturn(java.util.List.of());
        mvc.perform(get("/api/allowances/employees/E001").with(clerk())).andExpect(status().isOk());
    }
    @Test void otherEmployeeDeniedBeforeDatabaseAccess() throws Exception {
        mvc.perform(get("/api/allowances/employees/E999").with(clerk())).andExpect(status().isForbidden());
        verifyNoInteractions(repository);
    }
    @Test void missingEmployeeClaimDenied() throws Exception {
        mvc.perform(get("/api/allowances/employees/E001").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_payrollClerk")))).andExpect(status().isForbidden());
        verifyNoInteractions(repository);
    }
    @Test void unrelatedRoleDenied() throws Exception {
        mvc.perform(get("/api/allowances/employees/E001").with(jwt().jwt(j -> j.claim("employees",java.util.List.of("E001"))).authorities(new SimpleGrantedAuthority("ROLE_admin")))).andExpect(status().isForbidden());
    }
    @Test void corsRejectsUnexpectedOrigin() throws Exception {
        mvc.perform(options("/api/allowances/employees/E001").header("Origin","https://evil.example").header("Access-Control-Request-Method","GET")).andExpect(status().isForbidden());
    }
    @Test void backdatingAnotherEmployeeDeniedBeforeServiceAccess() throws Exception {
        mvc.perform(post("/api/allowances/employees/E999/HOUSING/backdate").with(clerk()).contentType("application/json")
            .content("{\"expectedVersion\":1,\"monthlyAmount\":3000.00,\"effectiveFrom\":\"2026-08-01\"}")).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
    @Test void invalidAmountReturnsSanitizedProblem() throws Exception {
        mvc.perform(post("/api/allowances/employees/E001/HOUSING/backdate").with(clerk()).contentType("application/json")
            .content("{\"expectedVersion\":1,\"monthlyAmount\":-1234.567,\"effectiveFrom\":\"2026-08-01\"}"))
            .andExpect(status().isBadRequest()).andExpect(content().contentTypeCompatibleWith("application/problem+json"))
            .andExpect(jsonPath("$.detail").value("Invalid request"));
        verifyNoInteractions(service);
    }
}
