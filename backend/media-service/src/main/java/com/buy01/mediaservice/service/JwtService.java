package com.buy01.mediaservice.service;

import com.buy01.mediaservice.security.AuthenticatedUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    @Value("${security.jwt.secret}")
    private String jwtSecret;

    public AuthenticatedUser parseToken(String token) {
        Claims claims = extractAllClaims(token);
        Date expiration = claims.getExpiration();
        if (expiration != null && expiration.before(new Date())) {
            throw new io.jsonwebtoken.JwtException("Token expired");
        }

        String userId = claims.get("userId", String.class);
        String role = claims.get("role", String.class);
        String email = claims.getSubject();

        if (userId == null || role == null || email == null) {
            throw new io.jsonwebtoken.JwtException("Token is missing required claims");
        }

        return new AuthenticatedUser(userId, email, role);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(resolveSecretBytes()))
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private byte[] resolveSecretBytes() {
        if (jwtSecret != null && jwtSecret.startsWith("base64:")) {
            return io.jsonwebtoken.io.Decoders.BASE64.decode(jwtSecret.substring("base64:".length()));
        }
        return jwtSecret.getBytes(StandardCharsets.UTF_8);
    }
}
