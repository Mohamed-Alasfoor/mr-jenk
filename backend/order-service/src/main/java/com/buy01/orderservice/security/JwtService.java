package com.buy01.orderservice.security;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
@Service
public class JwtService {
    private final SecretKey key;
    public JwtService(@Value("${security.jwt.secret}") String secret){key=Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));}
    public AuthenticatedUser parse(String token){
        Claims c=Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        String userId=c.get("userId",String.class);
        String email=c.getSubject();
        String role=c.get("role",String.class);
        if(userId==null||email==null||role==null)throw new io.jsonwebtoken.JwtException("Token is missing required claims");
        return new AuthenticatedUser(userId,email,role);
    }
}
