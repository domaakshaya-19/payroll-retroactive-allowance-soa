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
@WebMvcTest(controllers=PayPeriodController.class,properties={"payroll.cors-origins=https://localhost:3000","payroll.jwt.public-key=unused","payroll.jwt.issuer=https://issuer.test"})
@Import({SecurityConfig.class,EmployeeAccess.class})
class EmployeeSecurityTest {
    @Autowired MockMvc mvc;
    @MockitoBean JwtDecoder decoder;
    @MockitoBean PayPeriodService service;
    @MockitoBean SnapshotRepository snapshots;
    @MockitoBean PayPeriodRepository repository;
    
    private org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor clerk() {
        return jwt().jwt(j -> j.claim("employees",java.util.List.of("E001"))).authorities(new SimpleGrantedAuthority("ROLE_payrollClerk"));
    }
    @Test void unauthenticatedDenied() throws Exception {
        mvc.perform(get("/api/pay-periods/employees/E001")).andExpect(status().isUnauthorized());
    }
    @Test void scopedEmployeeAllowed() throws Exception {
        when(repository.findByEmployeeId("E001")).thenReturn(java.util.List.of());
        mvc.perform(get("/api/pay-periods/employees/E001").with(clerk())).andExpect(status().isOk());
    }
    @Test void otherEmployeeDeniedBeforeDatabaseAccess() throws Exception {
        mvc.perform(get("/api/pay-periods/employees/E999").with(clerk())).andExpect(status().isForbidden());
        verifyNoInteractions(repository);
    }
    @Test void missingEmployeeClaimDenied() throws Exception {
        mvc.perform(get("/api/pay-periods/employees/E001").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_payrollClerk")))).andExpect(status().isForbidden());
        verifyNoInteractions(repository);
    }
    @Test void unrelatedRoleDenied() throws Exception {
        mvc.perform(get("/api/pay-periods/employees/E001").with(jwt().jwt(j -> j.claim("employees",java.util.List.of("E001"))).authorities(new SimpleGrantedAuthority("ROLE_admin")))).andExpect(status().isForbidden());
    }
    @Test void corsRejectsUnexpectedOrigin() throws Exception {
        mvc.perform(options("/api/pay-periods/employees/E001").header("Origin","https://evil.example").header("Access-Control-Request-Method","GET")).andExpect(status().isForbidden());
    }
    @Test void serviceCanReadOnlyEntitledHistoricalSnapshots() throws Exception {
        var reader=jwt().jwt(j -> j.claim("employees",java.util.List.of("E001"))).authorities(new SimpleGrantedAuthority("ROLE_payrollSnapshotReader"));
        when(snapshots.findByEmployeeIdOrderByStartsOnAsc("E001")).thenReturn(java.util.List.of());
        mvc.perform(get("/api/pay-periods/employees/E001/snapshots").with(reader)).andExpect(status().isOk());
        mvc.perform(get("/api/pay-periods/employees/E999/snapshots").with(reader)).andExpect(status().isForbidden());
        mvc.perform(post("/api/pay-periods/employees/E001").with(reader).contentType("application/json").content("{\"startsOn\":\"2026-10-01\",\"endsOn\":\"2026-10-31\"}")).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
}
