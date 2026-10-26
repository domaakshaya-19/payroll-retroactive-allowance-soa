package com.example.payroll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.bind.annotation.*;
@WebFluxTest(controllers=GatewayHttpSecurityTest.BackendStub.class) @Import({SecurityConfig.class,GatewayHttpSecurityTest.BackendStub.class})
class GatewayHttpSecurityTest extends SignedTokensSupport {
    @RestController static class BackendStub {@RequestMapping("/api/**") String response(){return "reached backend";}}
    @Autowired WebTestClient client;
    private static final String READ="/api/allowances/employees/EMP001";
    @Test void anonymousProtectedRequestsAre401(){client.get().uri(READ).exchange().expectStatus().isUnauthorized();}
    @Test void postLoginIsPublic(){client.post().uri("/api/auth/token").exchange().expectStatus().isOk();client.get().uri("/api/auth/token").exchange().expectStatus().isUnauthorized();}
    @Test void bothRolesCanReadExplicitEmployee(){for(String role:java.util.List.of("payrollClerk","payrollApprover"))client.get().uri(READ).header("Authorization",bearer(role,"EMP001")).exchange().expectStatus().isOk();}
    @Test void unauthorizedEmployeeIs403(){for(String role:java.util.List.of("payrollClerk","payrollApprover"))client.get().uri("/api/allowances/employees/EMP999").header("Authorization",bearer(role,"EMP001")).exchange().expectStatus().isForbidden();}
    @Test void clerkCanWriteRulesAndApproverCannot(){
        for(String path:java.util.List.of(READ,READ+"/HOUSING/backdate")){
            client.post().uri(path).header("Authorization",bearer("payrollClerk","EMP001")).exchange().expectStatus().isOk();
            client.post().uri(path).header("Authorization",bearer("payrollApprover","EMP001")).exchange().expectStatus().isForbidden();
        }
    }
    @Test void clerkApprovingIs403AndApproverCanApprove(){
        String path="/api/retro-adjustments/employees/EMP001/1/approve";
        client.post().uri(path).header("Authorization",bearer("payrollClerk","EMP001")).exchange().expectStatus().isForbidden();
        client.post().uri(path).header("Authorization",bearer("payrollApprover","EMP001")).exchange().expectStatus().isOk();
    }
    @Test void wrongSignatureAndExpiryAre401(){
        client.get().uri(READ).header("Authorization",bearer("payrollClerk","EMP001",keys(),java.time.Instant.now().plusSeconds(60))).exchange().expectStatus().isUnauthorized();
        client.get().uri(READ).header("Authorization",bearer("payrollClerk","EMP001",KEYS,java.time.Instant.now().minusSeconds(2))).exchange().expectStatus().isUnauthorized();
    }
    @Test void issuedStatementEditsAreForbidden(){client.patch().uri("/api/pay-periods/employees/EMP001/1/snapshots").header("Authorization",bearer("payrollClerk","EMP001")).exchange().expectStatus().isForbidden();}
    @Test void unrelatedRoleDoesNotGrantReadAccess(){client.get().uri(READ).header("Authorization",bearer("admin","EMP001")).exchange().expectStatus().isForbidden();}
}
