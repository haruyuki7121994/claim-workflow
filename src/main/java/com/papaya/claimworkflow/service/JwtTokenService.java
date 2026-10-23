package com.papaya.claimworkflow.service;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.papaya.claimworkflow.entity.AppUser;
import com.papaya.claimworkflow.enums.Role;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtTokenService {
    private final String secret;
    private final String issuer;
    private final String audience;
    private final long expirySeconds;

    public JwtTokenService(@Value("${app.jwt.secret}") String secret,
                           @Value("${app.jwt.issuer}") String issuer,
                           @Value("${app.jwt.audience}") String audience,
                           @Value("${app.jwt.expiry-seconds:3600}") String expirySecondsRaw) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("JWT_SECRET must contain at least 32 bytes; use the local profile for development");
        }
        this.secret = secret;
        this.issuer = issuer;
        this.audience = audience;
        this.expirySeconds = Long.parseLong(expirySecondsRaw);
    }

    public String issue(AppUser user) {
        var now = Instant.now();
        var roles = user.roles().stream()
                .map(Role::value)
                .sorted()
                .toList();
        var claims = new JWTClaimsSet.Builder()
                .issuer(issuer)
                .audience(audience)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(expirySeconds)))
                .subject(user.subject())
                .claim("tenant_id", user.tenantId())
                .claim("display_name", user.displayName())
                .claim("roles", roles)
                .build();

        try {
            var signer = new MACSigner(secret.getBytes(StandardCharsets.UTF_8));
            var signed = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
            signed.sign(signer);
            return signed.serialize();
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to sign JWT for user " + user.subject(), exception);
        }
    }
}
