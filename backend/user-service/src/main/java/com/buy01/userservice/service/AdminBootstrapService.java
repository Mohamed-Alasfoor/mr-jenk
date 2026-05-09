package com.buy01.userservice.service;

import com.buy01.userservice.model.Role;
import com.buy01.userservice.model.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class AdminBootstrapService implements CommandLineRunner {

    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final String adminEmail;
    private final String adminPassword;
    private final String adminFullName;

    public AdminBootstrapService(
            UserService userService,
            PasswordEncoder passwordEncoder,
            @Value("${app.admin.bootstrap.email:}") String adminEmail,
            @Value("${app.admin.bootstrap.password:}") String adminPassword,
            @Value("${app.admin.bootstrap.full-name:Marketplace Admin}") String adminFullName
    ) {
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
        this.adminFullName = adminFullName;
    }

    @Override
    public void run(String... args) {
        if (!StringUtils.hasText(adminEmail) || !StringUtils.hasText(adminPassword)) {
            return;
        }

        if (userService.emailExists(adminEmail)) {
            return;
        }

        User adminUser = new User();
        adminUser.setEmail(adminEmail.trim().toLowerCase());
        adminUser.setPasswordHash(passwordEncoder.encode(adminPassword));
        adminUser.setFullName(adminFullName.trim());
        adminUser.setRole(Role.ADMIN);
        userService.save(adminUser);
    }
}
