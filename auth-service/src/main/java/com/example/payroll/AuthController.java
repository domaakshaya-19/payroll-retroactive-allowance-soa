package com.example.payroll;
import com.example.payroll.contracts.*;
import java.security.interfaces.*;
import java.time.*;
import java.util.*;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
@RestController @RequestMapping("/api/auth")
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name="payroll.auth.demo-enabled",havingValue="true")
public class AuthController {
    private final JwtEncoder encoder;
    private final String issuer, clerkHash, approverHash, serviceHash;
    private final List<String> clerkEmployees, approverEmployees, serviceEmployees;
    private final BCryptPasswordEncoder passwords=new BCryptPasswordEncoder();
    public AuthController(@Value("${payroll.jwt.private-key}") Resource privateKey, @Value("${payroll.jwt.public-key}") Resource publicKey,
        @Value("${payroll.jwt.issuer}") String issuer, @Value("${payroll.auth.clerk-password}") String clerk,
        @Value("${payroll.auth.approver-password}") String approver,
        @Value("${payroll.auth.clerk-employees}") String clerkEmployees, @Value("${payroll.auth.approver-employees}") String approverEmployees,
        @Value("${payroll.auth.service-password}") String servicePassword, @Value("${payroll.auth.service-employees}") String serviceEmployees) throws Exception {
        RSAPrivateKey priv; RSAPublicKey pub;
        try(var s=privateKey.getInputStream()){priv=RsaKeyConverters.pkcs8().convert(s);}
        try(var s=publicKey.getInputStream()){pub=RsaKeyConverters.x509().convert(s);}
        var key=new com.nimbusds.jose.jwk.RSAKey.Builder(pub).privateKey(priv).keyID("local-payroll").build();
        encoder=new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(key)));
        serviceHash=passwords.encode(servicePassword);this.serviceEmployees=Arrays.asList(serviceEmployees.split(","));
        this.issuer=issuer; clerkHash=passwords.encode(clerk); approverHash=passwords.encode(approver);
        this.clerkEmployees=Arrays.asList(clerkEmployees.split(",")); this.approverEmployees=Arrays.asList(approverEmployees.split(","));
    }
    @PostMapping("/token") public TokenResponse token(@jakarta.validation.Valid @RequestBody TokenRequest request) {
        boolean clerk="clerk".equals(request.username());
        boolean service="retro-service".equals(request.username());
        boolean known=clerk || service || "approver".equals(request.username());
        boolean matches=passwords.matches(request.password()==null ? "" : request.password(),clerk?clerkHash:service?serviceHash:approverHash);
        if (!known || !matches) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        var now=Instant.now();
        var claims=JwtClaimsSet.builder().issuer(issuer).subject(request.username()).audience(List.of("payroll-api"))
            .issuedAt(now).expiresAt(now.plusSeconds(900)).id(UUID.randomUUID().toString())
            .claim("roles",List.of(clerk?"payrollClerk":service?"payrollSnapshotReader":"payrollApprover"))
            .claim("employees",clerk?clerkEmployees:service?serviceEmployees:approverEmployees).build();
        var token=encoder.encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).keyId("local-payroll").build(),claims));
        return new TokenResponse(token.getTokenValue(),"Bearer",900);
    }
}
