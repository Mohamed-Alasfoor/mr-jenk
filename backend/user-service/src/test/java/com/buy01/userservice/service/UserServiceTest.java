package com.buy01.userservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.buy01.userservice.client.MediaServiceClient;
import com.buy01.userservice.dto.ProfileResponse;
import com.buy01.userservice.model.Role;
import com.buy01.userservice.model.User;
import com.buy01.userservice.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private MediaServiceClient mediaServiceClient;

    @InjectMocks
    private UserService userService;

    @Test
    void updateProfileWithAvatarRejectsEmptyAvatarFile() {
        User user = seller("seller@example.com");
        MockMultipartFile avatarFile = new MockMultipartFile("avatar", "empty.png", "image/png", new byte[0]);

        when(userRepository.findByEmail("seller@example.com")).thenReturn(Optional.of(user));

        assertThatThrownBy(() ->
                userService.updateProfileWithAvatar("seller@example.com", "Bearer token", "Seller Updated", avatarFile)
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Avatar file is required");

        verify(userRepository, never()).save(any(User.class));
        verifyNoInteractions(mediaServiceClient);
    }

    @Test
    void updateProfileWithAvatarAllowsMissingAvatarPart() {
        User user = seller("seller@example.com");

        when(userRepository.findByEmail("seller@example.com")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProfileResponse response = userService.updateProfileWithAvatar(
                "seller@example.com",
                "Bearer token",
                "Seller Updated",
                null
        );

        assertThat(response.fullName()).isEqualTo("Seller Updated");
        assertThat(user.getFullName()).isEqualTo("Seller Updated");
        verify(userRepository).save(user);
        verifyNoInteractions(mediaServiceClient);
    }

    private User seller(String email) {
        User user = new User();
        user.setId("seller-1");
        user.setEmail(email);
        user.setFullName("Seller");
        user.setRole(Role.SELLER);
        return user;
    }
}
