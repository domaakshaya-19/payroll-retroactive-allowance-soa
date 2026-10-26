package com.example.payroll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import com.fasterxml.jackson.databind.ObjectMapper;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;
@WebMvcTest(controllers=AuthController.class,properties={"payroll.auth.demo-enabled=true","payroll.auth.clerk-password=Clerk@123","payroll.auth.approver-password=Approver@123","payroll.auth.service-password=test-service-password","payroll.auth.clerk-employees=EMP001","payroll.auth.approver-employees=EMP001","payroll.auth.service-employees=EMP001"})
@Import(SecurityConfig.class)
class DemoLoginHttpTest extends SignedTokensSupport {
    @Autowired MockMvc mvc;@Autowired JwtDecoder decoder;@Autowired ObjectMapper mapper;
    @Test void demoCredentialsIssueCorrectlySignedScopedTokens() throws Exception {
        for(var entry:java.util.Map.of("clerk",new String[]{"Clerk@123","payrollClerk"},"approver",new String[]{"Approver@123","payrollApprover"}).entrySet()){
            var result=mvc.perform(post("/api/auth/token").contentType("application/json").content(mapper.writeValueAsString(java.util.Map.of("username",entry.getKey(),"password",entry.getValue()[0]))))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andReturn();
            var response=mapper.readTree(result.getResponse().getContentAsString());var token=decoder.decode(response.get("accessToken").asText());
            assertEquals(entry.getKey(),token.getSubject());assertEquals(java.util.List.of(entry.getValue()[1]),token.getClaimAsStringList("roles"));
            assertEquals(java.util.List.of("EMP001"),token.getClaimAsStringList("employees"));
            assertEquals(900,java.time.Duration.between(token.getIssuedAt(),token.getExpiresAt()).toSeconds());
        }
    }
    @Test void wrongPasswordAndUnknownUserReturn401() throws Exception {
        for(var body:java.util.List.of("{\"username\":\"clerk\",\"password\":\"wrong\"}","{\"username\":\"nobody\",\"password\":\"Approver@123\"}"))
            mvc.perform(post("/api/auth/token").contentType("application/json").content(body)).andExpect(status().isUnauthorized());
    }
    @Test void invalidLoginBodyReturns400() throws Exception {mvc.perform(post("/api/auth/token").contentType("application/json").content("{}")).andExpect(status().isBadRequest());}
    @Test void onlyPostLoginIsAnonymous() throws Exception {mvc.perform(get("/api/auth/token")).andExpect(status().isUnauthorized());}
}
