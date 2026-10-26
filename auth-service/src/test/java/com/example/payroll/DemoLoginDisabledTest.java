package com.example.payroll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@WebMvcTest(controllers=AuthController.class,properties="payroll.auth.demo-enabled=false") @Import(SecurityConfig.class)
class DemoLoginDisabledTest extends SignedTokensSupport {
    @Autowired MockMvc mvc;
    @Test void localIssuerIsAbsentUnlessDemoIsExplicitlyEnabled() throws Exception {
        mvc.perform(post("/api/auth/token").contentType("application/json").content("{\"username\":\"clerk\",\"password\":\"Clerk@123\"}")).andExpect(status().isNotFound());
    }
}
