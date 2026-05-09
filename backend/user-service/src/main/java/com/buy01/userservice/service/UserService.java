package com.buy01.userservice.service;

import com.buy01.userservice.client.MediaServiceClient;
import com.buy01.userservice.dto.ProfileResponse;
import com.buy01.userservice.dto.PublicSellerProfileResponse;
import com.buy01.userservice.dto.UpdateProfileRequest;
import com.buy01.userservice.exception.UserNotFoundException;
import com.buy01.userservice.model.Role;
import com.buy01.userservice.model.User;
import com.buy01.userservice.repository.UserRepository;
import java.util.Collection;
import java.util.List;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class UserService implements UserDetailsService {

    private final UserRepository userRepository;
    private final MediaServiceClient mediaServiceClient;

    public UserService(UserRepository userRepository, MediaServiceClient mediaServiceClient) {
        this.userRepository = userRepository;
        this.mediaServiceClient = mediaServiceClient;
    }

    public boolean emailExists(String email) {
        return userRepository.existsByEmail(email.trim().toLowerCase());
    }

    public User save(User user) {
        return userRepository.save(user);
    }

    public User getByEmail(String email) {
        return userRepository.findByEmail(email.trim().toLowerCase())
                .orElseThrow(() -> new UserNotFoundException(email));
    }

    public ProfileResponse getProfile(String email) {
        return mapToProfile(getByEmail(email));
    }

    public PublicSellerProfileResponse getPublicSellerProfile(String userId) {
        User user = userRepository.findById(userId)
                .filter(candidate -> candidate.getRole() == Role.SELLER || candidate.getRole() == Role.ADMIN)
                .orElseThrow(() -> new UserNotFoundException(userId));

        return new PublicSellerProfileResponse(
                user.getId(),
                user.getFullName(),
                user.getAvatarUrl()
        );
    }

    public ProfileResponse updateProfile(String email, UpdateProfileRequest request) {
        User user = getByEmail(email);
        user.setFullName(request.fullName().trim());
        User updatedUser = userRepository.save(user);
        return mapToProfile(updatedUser);
    }

    public ProfileResponse updateProfileWithAvatar(
            String email,
            String authorizationHeader,
            String fullName,
            MultipartFile avatarFile
    ) {
        User user = getByEmail(email);
        user.setFullName(fullName.trim());

        if (avatarFile == null) {
            return mapToProfile(userRepository.save(user));
        }
        if (avatarFile.isEmpty()) {
            throw new IllegalArgumentException("Avatar file is required");
        }

        if (user.getRole() != Role.SELLER && user.getRole() != Role.ADMIN) {
            throw new AccessDeniedException("Only sellers can upload avatars");
        }

        String previousAvatarUrl = user.getAvatarUrl();
        String newAvatarUrl = mediaServiceClient.uploadAvatar(authorizationHeader, avatarFile);
        user.setAvatarUrl(newAvatarUrl);

        try {
            User updatedUser = userRepository.save(user);
            mediaServiceClient.deleteMediaIfManaged(authorizationHeader, previousAvatarUrl);
            return mapToProfile(updatedUser);
        } catch (RuntimeException exception) {
            mediaServiceClient.deleteMediaIfManaged(authorizationHeader, newAvatarUrl);
            throw exception;
        }
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(username.trim().toLowerCase())
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        return new org.springframework.security.core.userdetails.User(
                user.getEmail(),
                user.getPasswordHash(),
                getAuthorities(user)
        );
    }

    private Collection<? extends GrantedAuthority> getAuthorities(User user) {
        return List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
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
