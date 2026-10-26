package com.example.payroll;
import org.springframework.stereotype.Component;
import org.springframework.web.server.*;
import reactor.core.publisher.Mono;
@Component
public class AuditFilter implements WebFilter {
    private static final org.slf4j.Logger log=org.slf4j.LoggerFactory.getLogger("PAYROLL_AUDIT");
    public Mono<Void> filter(ServerWebExchange exchange,WebFilterChain chain) {
        String id=java.util.UUID.randomUUID().toString();
        return chain.filter(exchange).doFinally(signal -> log.info("request={} method={} status={} identity=[REDACTED] employee=[REDACTED]",id,exchange.getRequest().getMethod(),exchange.getResponse().getStatusCode()));
    }
}
