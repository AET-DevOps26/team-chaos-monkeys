package com.foundflow.auth.controller;

import com.foundflow.auth.dto.LoginRequest;
import com.foundflow.auth.dto.PasswordResetConfirmRequest;
import com.foundflow.auth.dto.PasswordResetRequest;
import com.foundflow.auth.dto.RefreshTokenRequest;
import com.foundflow.auth.dto.TokenResponse;
import com.foundflow.auth.service.AuthService;
import com.foundflow.auth.service.PasswordResetService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper jsonMapper;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private PasswordResetService passwordResetService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void login_shouldReturnTokenPair() throws Exception {
        LoginRequest request = new LoginRequest("admin@foundflow.local", "admin12345");
        TokenResponse response = new TokenResponse(
                "access-token",
                "refresh-token",
                "Bearer",
                1800
        );

        when(authService.login(request)).thenReturn(response);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-token"))
                .andExpect(jsonPath("$.refreshToken").value("refresh-token"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(1800));
    }

    @Test
    void login_shouldReturnUnauthorizedForBadCredentials() throws Exception {
        LoginRequest request = new LoginRequest("admin@foundflow.local", "wrong");

        when(authService.login(request))
                .thenThrow(new BadCredentialsException("Invalid credentials"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refresh_shouldReturnRotatedTokenPair() throws Exception {
        RefreshTokenRequest request = new RefreshTokenRequest("refresh-token");
        TokenResponse response = new TokenResponse(
                "new-access-token",
                "new-refresh-token",
                "Bearer",
                1800
        );

        when(authService.refresh(request)).thenReturn(response);

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("new-access-token"))
                .andExpect(jsonPath("$.refreshToken").value("new-refresh-token"))
                .andExpect(jsonPath("$.expiresIn").value(1800));
    }

    @Test
    void refresh_shouldReturnUnauthorizedForInvalidRefreshToken() throws Exception {
        RefreshTokenRequest request = new RefreshTokenRequest("invalid-refresh-token");

        when(authService.refresh(request))
                .thenThrow(new BadCredentialsException("Invalid refresh token"));

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logout_shouldRevokeRefreshToken() throws Exception {
        RefreshTokenRequest request = new RefreshTokenRequest("refresh-token");

        mockMvc.perform(post("/api/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isNoContent());

        verify(authService).logout(request);
    }

    @Test
    void logout_shouldReturnUnauthorizedWhenServiceRejectsToken() throws Exception {
        RefreshTokenRequest request = new RefreshTokenRequest("invalid-refresh-token");

        doThrow(new BadCredentialsException("Invalid refresh token"))
                .when(authService)
                .logout(request);

        mockMvc.perform(post("/api/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void requestPasswordReset_shouldReturnNoContent() throws Exception {
        PasswordResetRequest request = new PasswordResetRequest("staff@example.com");

        mockMvc.perform(post("/api/auth/password-reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isNoContent());

        verify(passwordResetService).requestPasswordReset(request);
    }

    @Test
    void confirmPasswordReset_shouldReturnNoContent() throws Exception {
        PasswordResetConfirmRequest request =
                new PasswordResetConfirmRequest("reset-token", "new-password");

        mockMvc.perform(post("/api/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isNoContent());

        verify(passwordResetService).confirmPasswordReset(request);
    }

    @Test
    void confirmPasswordReset_shouldReturnUnauthorizedWhenServiceRejectsToken() throws Exception {
        PasswordResetConfirmRequest request =
                new PasswordResetConfirmRequest("invalid-token", "new-password");

        doThrow(new BadCredentialsException("Invalid password reset token"))
                .when(passwordResetService)
                .confirmPasswordReset(request);

        mockMvc.perform(post("/api/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }
}
