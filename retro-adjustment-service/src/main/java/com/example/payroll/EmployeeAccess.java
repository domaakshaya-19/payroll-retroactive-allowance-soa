package com.example.payroll;
    import org.springframework.stereotype.Component;
    import org.springframework.security.core.Authentication;
    import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
    @Component("employeeAccess")
    public class EmployeeAccess {
        public boolean allowed(Authentication authentication, String employeeId) {
            if (!(authentication instanceof JwtAuthenticationToken token) || employeeId == null) return false;
            var employees=token.getToken().getClaimAsStringList("employees");
            return employees != null && employees.contains(employeeId);
        }
    }
