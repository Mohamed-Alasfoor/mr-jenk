package com.buy01.orderservice.security;
import java.security.Principal;
public record AuthenticatedUser(String userId, String email, String role) implements Principal {
    public boolean isSeller(){return "SELLER".equalsIgnoreCase(role);}
    @Override public String getName(){return userId;}
}
