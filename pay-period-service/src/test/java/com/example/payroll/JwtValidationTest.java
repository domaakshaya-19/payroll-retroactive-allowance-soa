package com.example.payroll;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import java.security.*;
import java.security.interfaces.*;
import java.time.*;
import java.util.*;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
class JwtValidationTest {
    private KeyPair pair;
    private ByteArrayResource keyResource;
    @BeforeEach void keys() throws Exception {
        pair=KeyPairGenerator.getInstance("RSA").generateKeyPair();
        String pem="-----BEGIN PUBLIC KEY-----\n"+Base64.getEncoder().encodeToString(pair.getPublic().getEncoded())+"\n-----END PUBLIC KEY-----";
        keyResource=new ByteArrayResource(pem.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
    }
    private String token(KeyPair signingKey,Instant expiry,String issuer,String audience,boolean includeExpiry) {
        var key=new com.nimbusds.jose.jwk.RSAKey.Builder((RSAPublicKey)signingKey.getPublic()).privateKey((RSAPrivateKey)signingKey.getPrivate()).build();
        var encoder=new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(key)));
        var claims=JwtClaimsSet.builder().subject("clerk").issuer(issuer).audience(List.of(audience)).issuedAt(Instant.now().minusSeconds(600));
        if(includeExpiry) claims.expiresAt(expiry);
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).build(),claims.build())).getTokenValue();
    }
    @Test void acceptsValidSignedToken() throws Exception {
        var decoder=new SecurityConfig().jwtDecoder(keyResource, "https://issuer.test");
        String token=token(pair,Instant.now().plusSeconds(60),"https://issuer.test","payroll-api",true);
        assertEquals("clerk",decoder.decode(token).getSubject());
    }
    @Test void rejectsExpiredTokenWithoutGracePeriod() throws Exception {
        var decoder=new SecurityConfig().jwtDecoder(keyResource, "https://issuer.test");
        String token=token(pair,Instant.now().minusSeconds(2),"https://issuer.test","payroll-api",true);
        assertThrows(JwtException.class,() -> decoder.decode(token));
    }
    @Test void rejectsWrongSignature() throws Exception {
        var decoder=new SecurityConfig().jwtDecoder(keyResource, "https://issuer.test");
        String token=token(KeyPairGenerator.getInstance("RSA").generateKeyPair(),Instant.now().plusSeconds(60),"https://issuer.test","payroll-api",true);
        assertThrows(JwtException.class,() -> decoder.decode(token));
    }
    @Test void rejectsWrongIssuerAndAudience() throws Exception {
        var decoder=new SecurityConfig().jwtDecoder(keyResource, "https://issuer.test");
        String token=token(pair,Instant.now().plusSeconds(60),"https://attacker.test","other-api",true);
        assertThrows(JwtException.class,() -> decoder.decode(token));
    }
    @Test void requiresExpiryClaim() throws Exception {
        var decoder=new SecurityConfig().jwtDecoder(keyResource, "https://issuer.test");
        String token=token(pair,Instant.now(),"https://issuer.test","payroll-api",false);
        assertThrows(JwtException.class,() -> decoder.decode(token));
    }
}
