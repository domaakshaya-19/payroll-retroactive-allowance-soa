package com.example.payroll;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.core.io.Resource;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.*;
import org.springframework.security.authorization.*;
import org.springframework.security.web.server.authorization.AuthorizationContext;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.PathContainer;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.cors.*;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
@Configuration
public class SecurityConfig {
    @Bean ReactiveJwtDecoder jwtDecoder(@Value("${payroll.jwt.public-key}") Resource key,@Value("${payroll.jwt.issuer}") String issuer) throws Exception {
        java.security.interfaces.RSAPublicKey pub;
        try(var s=key.getInputStream()){pub=RsaKeyConverters.x509().convert(s);}
        var decoder=NimbusReactiveJwtDecoder.withPublicKey(pub).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(new JwtTimestampValidator(java.time.Duration.ZERO), new JwtIssuerValidator(issuer),
            new JwtClaimValidator<List<String>>("aud", aud -> aud != null && aud.contains("payroll-api")),
            new JwtClaimValidator<java.time.Instant>("exp", Objects::nonNull)));
        return decoder;
    }
    @Bean SecurityWebFilterChain security(ServerHttpSecurity http,@Value("${payroll.cors-origins}") String origins) {
        var cors=new CorsConfiguration(); cors.setAllowedOrigins(Arrays.asList(origins.split(",")));
        cors.setAllowedMethods(List.of("GET","POST","OPTIONS")); cors.setAllowedHeaders(List.of("Authorization","Content-Type","X-Correlation-ID")); cors.setAllowCredentials(false);
        var source=new UrlBasedCorsConfigurationSource(); source.registerCorsConfiguration("/**",cors);
        return http.csrf(c -> c.disable()).cors(c -> c.configurationSource(source))
            .authorizeExchange(c -> c.pathMatchers(HttpMethod.OPTIONS,"/**").permitAll()
                .pathMatchers(HttpMethod.POST,"/api/auth/token").permitAll()
                .pathMatchers(HttpMethod.GET,"/api/allowances/**","/api/pay-periods/**","/api/retro-adjustments/**")
                    .access(employeeRoles("payrollClerk","payrollApprover"))
                .pathMatchers(HttpMethod.POST,"/api/allowances/**","/api/pay-periods/**").access(employeeRoles("payrollClerk"))
                .pathMatchers(HttpMethod.POST,"/api/retro-adjustments/employees/*/*/approve","/api/retro-adjustments/employees/*/workflows/*/retry")
                    .access(employeeRoles("payrollApprover"))
                .anyExchange().denyAll())
            .oauth2ResourceServer(c -> c.jwt(j -> {
                var roles=new JwtGrantedAuthoritiesConverter();roles.setAuthoritiesClaimName("roles");roles.setAuthorityPrefix("ROLE_");
                var converter=new JwtAuthenticationConverter();converter.setJwtGrantedAuthoritiesConverter(roles);
                j.jwtAuthenticationConverter(new ReactiveJwtAuthenticationConverterAdapter(converter));
            })).build();
    }
    private ReactiveAuthorizationManager<AuthorizationContext> employeeRoles(String... roles) {
        var required=Arrays.stream(roles).map(role -> "ROLE_"+role).toList();
        return (authentication,context) -> authentication.map(auth -> {
            if(!(auth instanceof JwtAuthenticationToken token) || !auth.isAuthenticated())return new AuthorizationDecision(false);
            var segments=context.getExchange().getRequest().getPath().pathWithinApplication().elements().stream()
                .filter(PathContainer.PathSegment.class::isInstance).map(PathContainer.PathSegment.class::cast).map(PathContainer.PathSegment::valueToMatch).toList();
            int position=segments.indexOf("employees");
            var employees=token.getToken().getClaimAsStringList("employees");
            boolean employeeAllowed=position>=0 && position+1<segments.size() && employees!=null && employees.contains(segments.get(position+1));
            boolean roleAllowed=auth.getAuthorities().stream().anyMatch(authority -> required.contains(authority.getAuthority()));
            return new AuthorizationDecision(employeeAllowed && roleAllowed);
        }).defaultIfEmpty(new AuthorizationDecision(false));
    }
}
