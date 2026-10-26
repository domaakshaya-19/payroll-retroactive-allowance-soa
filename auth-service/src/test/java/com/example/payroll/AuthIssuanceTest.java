package com.example.payroll;

import com.example.payroll.contracts.TokenRequest;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;

class AuthIssuanceTest {
    @Test void localCredentialsIssueVerifiableScopedTokensAndRejectBadCredentials() throws Exception {
        var generator=KeyPairGenerator.getInstance("RSA"); generator.initialize(2048);
        var pair=generator.generateKeyPair();
        var publicKey=pem("PUBLIC KEY",pair.getPublic().getEncoded());
        var controller=new AuthController(pem("PRIVATE KEY",pair.getPrivate().getEncoded()),publicKey,
            "https://issuer.test","test-clerk-password","test-approver-password","E001,E002","E002","test-service-password","EMP001");
        var decoder=new SecurityConfig().jwtDecoder(publicKey,"https://issuer.test");
        var response=controller.token(new TokenRequest("clerk","test-clerk-password"));
        var clerk=decoder.decode(response.accessToken());
        assertEquals(List.of("payrollClerk"),clerk.getClaimAsStringList("roles"));
        assertEquals(List.of("E001","E002"),clerk.getClaimAsStringList("employees"));
        assertEquals(900,java.time.Duration.between(clerk.getIssuedAt(),clerk.getExpiresAt()).toSeconds());
        var approver=decoder.decode(controller.token(new TokenRequest("approver","test-approver-password")).accessToken());
        assertEquals(List.of("payrollApprover"),approver.getClaimAsStringList("roles"));
        assertEquals(List.of("E002"),approver.getClaimAsStringList("employees"));
        var service=decoder.decode(controller.token(new TokenRequest("retro-service","test-service-password")).accessToken());
        assertEquals(List.of("payrollSnapshotReader"),service.getClaimAsStringList("roles"));
        assertEquals(List.of("EMP001"),service.getClaimAsStringList("employees"));
        assertThrows(ResponseStatusException.class,() -> controller.token(new TokenRequest("clerk","wrong")));
        assertThrows(ResponseStatusException.class,() -> controller.token(new TokenRequest("unknown","test-approver-password")));
    }
    private ByteArrayResource pem(String label,byte[] bytes) {
        return new ByteArrayResource(("-----BEGIN "+label+"-----\n"+Base64.getEncoder().encodeToString(bytes)+"\n-----END "+label+"-----").getBytes(StandardCharsets.US_ASCII));
    }
}
