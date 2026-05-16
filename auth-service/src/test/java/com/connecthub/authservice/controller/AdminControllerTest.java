package com.connecthub.authservice.controller;

import com.connecthub.authservice.dto.UserSummaryResponse;
import com.connecthub.authservice.entity.User;
import com.connecthub.authservice.repository.UserRepository;
import com.connecthub.authservice.security.JwtUtil;
import com.connecthub.authservice.service.AuthService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminControllerTest {

    @Mock
    private AuthService authService;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private AdminController controller;

    @Test
    void rejectsMissingAuthorizationHeader() {
        assertThatThrownBy(() -> controller.getAllUsers(null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("401");
    }

    @Test
    void listsUsersForAdmin() {
        User admin = adminUser("admin-1");
        UserSummaryResponse summary = new UserSummaryResponse("user-1", "alice", "Alice", "a@b.com", "1", null, null, "en", 10, "OFFLINE", null, "USER", false);

        when(jwtUtil.extractSubject("valid")).thenReturn("admin-1");
        when(userRepository.findById("admin-1")).thenReturn(Optional.of(admin));
        when(authService.getAllUsersForAdmin()).thenReturn(List.of(summary));

        assertThat(controller.getAllUsers("Bearer valid").getBody()).containsExactly(summary);
    }

    @Test
    void preventsAdminFromBlockingSelf() {
        User admin = adminUser("admin-1");
        when(jwtUtil.extractSubject("valid")).thenReturn("admin-1");
        when(userRepository.findById("admin-1")).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> controller.toggleBlock("admin-1", "Bearer valid"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400");
    }

    @Test
    void togglesBlockForAnotherUser() {
        User admin = adminUser("admin-1");
        UserSummaryResponse summary = new UserSummaryResponse("user-2", "bob", "Bob", "b@c.com", "2", null, null, "en", 4, "OFFLINE", null, "USER", true);

        when(jwtUtil.extractSubject("valid")).thenReturn("admin-1");
        when(userRepository.findById("admin-1")).thenReturn(Optional.of(admin));
        when(authService.toggleUserBlockStatus("user-2")).thenReturn(summary);

        assertThat(controller.toggleBlock("user-2", "Bearer valid").getBody()).isEqualTo(summary);
    }

    @Test
    void changesRoleForExistingUser() {
        User admin = adminUser("admin-1");
        User target = new User();
        target.setUserId("user-2");
        target.setRole("USER");
        UserSummaryResponse summary = new UserSummaryResponse("user-2", "bob", "Bob", "b@c.com", "2", null, null, "en", 4, "OFFLINE", null, "ADMIN", false);

        when(jwtUtil.extractSubject("valid")).thenReturn("admin-1");
        when(userRepository.findById("admin-1")).thenReturn(Optional.of(admin));
        when(userRepository.findById("user-2")).thenReturn(Optional.of(target));
        when(authService.getUserById("user-2")).thenReturn(summary);

        assertThat(controller.changeRole("user-2", "ADMIN", "Bearer valid").getBody()).isEqualTo(summary);
        verify(userRepository).save(target);
    }

    @Test
    void rejectsUnsupportedRoleValue() {
        User admin = adminUser("admin-1");
        when(jwtUtil.extractSubject("valid")).thenReturn("admin-1");
        when(userRepository.findById("admin-1")).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> controller.changeRole("user-2", "SUPERADMIN", "Bearer valid"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400");
    }

    @Test
    void setsCreditsAndDeletesUser() {
        User admin = adminUser("admin-1");
        UserSummaryResponse summary = new UserSummaryResponse("user-2", "bob", "Bob", "b@c.com", "2", null, null, "en", 99, "OFFLINE", null, "USER", false);

        when(jwtUtil.extractSubject("valid")).thenReturn("admin-1");
        when(userRepository.findById("admin-1")).thenReturn(Optional.of(admin));
        when(authService.adminSetCredits("user-2", 99)).thenReturn(summary);

        assertThat(controller.setCredits("user-2", 99, "Bearer valid").getBody()).isEqualTo(summary);
        assertThat(controller.deleteUser("user-2", "Bearer valid").getBody())
                .containsEntry("message", "User deleted successfully.");
        verify(authService).deleteUserAsAdmin("user-2");
    }

    private User adminUser(String id) {
        User admin = new User();
        admin.setUserId(id);
        admin.setRole("ADMIN");
        return admin;
    }
}
