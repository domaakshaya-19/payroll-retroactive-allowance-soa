package com.example.payroll;
    import jakarta.servlet.*;
    import jakarta.servlet.http.*;
    import java.io.IOException;
    import org.springframework.stereotype.Component;
    import org.springframework.web.filter.OncePerRequestFilter;
    @Component
    @org.springframework.core.annotation.Order(org.springframework.core.Ordered.HIGHEST_PRECEDENCE)
    public class AuditFilter extends OncePerRequestFilter {
        @org.springframework.beans.factory.annotation.Value("${spring.application.name}:${server.port}") private String instanceId;
        private static final org.slf4j.Logger log=org.slf4j.LoggerFactory.getLogger("PAYROLL_AUDIT");
        @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws ServletException,IOException {
            String id=java.util.UUID.randomUUID().toString(); response.setHeader("X-Audit-Id",id);
            response.setHeader("X-Payroll-Instance",instanceId);
            try {chain.doFilter(request,response);} finally { log.info("request={} method={} status={} identity=[REDACTED] employee=[REDACTED]",id,request.getMethod(),response.getStatus()); }
        }
    }
