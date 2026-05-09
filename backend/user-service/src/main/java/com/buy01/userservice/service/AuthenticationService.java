package com.buy01.userservice.service;

import com.buy01.userservice.dto.AuthResponse;
import com.buy01.userservice.dto.LoginRequest;
import com.buy01.userservice.dto.RegisterRequest;
import com.buy01.userservice.dto.ProfileResponse;
import com.buy01.userservice.exception.EmailAlreadyExistsException;
import com.buy01.userservice.model.User;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthenticationService {

    private final UserService userService;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;

    public AuthenticationService(
            UserService userService,
            JwtService jwtService,
            PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager
    ) {
        this.userService = userService;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
    }

    public AuthResponse register(RegisterRequest request) {
        if (request.role() == com.buy01.userservice.model.Role.ADMIN) {
            throw new IllegalArgumentException("Public registration cannot assign the ADMIN role");
        }

        if (userService.emailExists(request.email())) {
            throw new EmailAlreadyExistsException(request.email());
        }

        User user = new User();
        user.setEmail(request.email().trim().toLowerCase());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setFullName(request.fullName().trim());
        user.setRole(request.role());

        User savedUser = userService.save(user);
        String token = jwtService.generateToken(savedUser);
        return new AuthResponse(token, mapToProfile(savedUser));
    }

    public AuthResponse login(LoginRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase();

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(normalizedEmail, request.password())
            );
        } catch (org.springframework.security.core.AuthenticationException exception) {
            throw new BadCredentialsException("Invalid email or password");
        }

        User user = userService.getByEmail(normalizedEmail);
        String token = jwtService.generateToken(user);
        return new AuthResponse(token, mapToProfile(user));
    }

    private ProfileResponse mapToProfile(User user) {
        return new ProfileResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getRole(),
                user.getAvatarUrl()
        );
    }
}
