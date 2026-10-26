package com.example.payroll;
import org.springframework.context.annotation.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
@Configuration
public class SecurityConfig {
    @Bean org.springframework.security.crypto.password.PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }
    @Bean UserDetailsService users(@Value("${EUREKA_USER}") String user,@Value("${EUREKA_PASSWORD}") String password) {
        return new InMemoryUserDetailsManager(User.withUsername(user).password(new BCryptPasswordEncoder().encode(password)).roles("DISCOVERY").build());
    }
    @Bean SecurityFilterChain security(HttpSecurity http) throws Exception {
        return http.csrf(c -> c.disable()).authorizeHttpRequests(c -> c.anyRequest().hasRole("DISCOVERY")).httpBasic(c -> {}).build();
    }
}
