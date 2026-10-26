package com.example.payroll;
import java.security.interfaces.RSAPublicKey;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.core.io.Resource;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.*;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.*;
@Configuration @EnableMethodSecurity
public class SecurityConfig {
    @Bean JwtDecoder jwtDecoder(@Value("${payroll.jwt.public-key}") Resource key, @Value("${payroll.jwt.issuer}") String issuer) throws Exception {
        RSAPublicKey publicKey;
        try(var stream=key.getInputStream()){ publicKey=RsaKeyConverters.x509().convert(stream); }
        var decoder=NimbusJwtDecoder.withPublicKey(publicKey).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(new JwtTimestampValidator(java.time.Duration.ZERO), new JwtIssuerValidator(issuer),
            new JwtClaimValidator<List<String>>("aud", aud -> aud != null && aud.contains("payroll-api")),
            new JwtClaimValidator<java.time.Instant>("exp", Objects::nonNull)));
        return decoder;
    }
    @Bean JwtAuthenticationConverter authenticationConverter() {
        var roles=new JwtGrantedAuthoritiesConverter(); roles.setAuthoritiesClaimName("roles"); roles.setAuthorityPrefix("ROLE_");
        var converter=new JwtAuthenticationConverter(); converter.setJwtGrantedAuthoritiesConverter(roles); return converter;
    }
    @Bean SecurityFilterChain filterChain(HttpSecurity http, JwtAuthenticationConverter converter) throws Exception {
        return http.csrf(c -> c.disable()).cors(c -> {}).sessionManagement(c -> c.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(c -> c.requestMatchers(org.springframework.http.HttpMethod.OPTIONS,"/**").permitAll().anyRequest().authenticated())
            .oauth2ResourceServer(c -> c.jwt(j -> j.jwtAuthenticationConverter(converter))).build();
    }
    @Bean CorsConfigurationSource cors(@Value("${payroll.cors-origins}") String origins) {
        var cors=new CorsConfiguration(); cors.setAllowedOrigins(Arrays.asList(origins.split(",")));
        cors.setAllowedMethods(List.of("GET","POST","OPTIONS")); cors.setAllowedHeaders(List.of("Authorization","Content-Type","X-Correlation-ID")); cors.setAllowCredentials(false);
        var source=new UrlBasedCorsConfigurationSource(); source.registerCorsConfiguration("/**",cors); return source;
    }
}
