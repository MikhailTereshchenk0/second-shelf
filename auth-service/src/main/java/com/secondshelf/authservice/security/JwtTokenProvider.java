package com.secondshelf.authservice.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;
import java.util.List;

@Component
public class JwtTokenProvider {
    private final SecretKey key;
    private final long validityInMs;

    public JwtTokenProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration-ms}") long validityInMs
    ) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.validityInMs = validityInMs;
    }

    public String generateToken(String username, List<String> roles) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + validityInMs);

        return Jwts.builder()
                .subject(username)       // "sub" в payload
                .issuedAt(now)           // "iat"
                .expiration(expiry)      // "exp"
                .claim("roles", roles)
                .signWith(key)           // HS256 по умолчанию для HMAC ключа
                .compact();              // собирает токен в строку "xxx.yyy.zzz"
    }

    public String getUsername(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
    }

    @SuppressWarnings("unchecked")
    public List<String> getRoles(String token) {
        return (List<String>) Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .get("roles");
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (Exception exception) {
            return false;
        }
    }
}
