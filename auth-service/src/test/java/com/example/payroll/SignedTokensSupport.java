package com.example.payroll;
import java.security.*;
import java.security.interfaces.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
/** Runtime-generated test keys, saved only below Maven's ignored target directory. */
abstract class SignedTokensSupport {
    static final KeyPair KEYS=keys();
    static KeyPair keys(){try{var generator=KeyPairGenerator.getInstance("RSA");generator.initialize(2048);return generator.generateKeyPair();}catch(Exception error){throw new IllegalStateException(error);}}
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) throws Exception {
        var directory=Path.of("target","security-test-keys").toAbsolutePath();Files.createDirectories(directory);
        var publicFile=directory.resolve("public.pem");var privateFile=directory.resolve("private.pem");
        Files.writeString(publicFile,pem("PUBLIC KEY",KEYS.getPublic().getEncoded()));
        Files.writeString(privateFile,pem("PRIVATE KEY",KEYS.getPrivate().getEncoded()));
        registry.add("payroll.jwt.public-key",() -> publicFile.toUri().toString());
        registry.add("payroll.jwt.private-key",() -> privateFile.toUri().toString());
        registry.add("payroll.jwt.issuer",() -> "https://issuer.test");
        registry.add("payroll.cors-origins",() -> "https://localhost:3000");
    }
    private static String pem(String label,byte[] bytes){return "-----BEGIN "+label+"-----\n"+Base64.getEncoder().encodeToString(bytes)+"\n-----END "+label+"-----\n";}
    static String bearer(String role,String employee){return bearer(role,employee,KEYS,Instant.now().plusSeconds(900));}
    static String bearer(String role,String employee,KeyPair key,Instant expiry){
        var jwk=new com.nimbusds.jose.jwk.RSAKey.Builder((RSAPublicKey)key.getPublic()).privateKey((RSAPrivateKey)key.getPrivate()).build();
        var encoder=new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(jwk)));
        var claims=JwtClaimsSet.builder().issuer("https://issuer.test").audience(List.of("payroll-api")).subject(role)
            .issuedAt(Instant.now().minusSeconds(30)).expiresAt(expiry).claim("roles",List.of(role)).claim("employees",List.of(employee)).build();
        return "Bearer "+encoder.encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).build(),claims)).getTokenValue();
    }
}
