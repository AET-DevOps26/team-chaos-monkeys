package com.foundflow.auth.controller;

import com.foundflow.auth.domain.Role;
import com.foundflow.auth.dto.CreateUserRequest;
import com.foundflow.auth.dto.UpdateUserRequest;
import com.foundflow.auth.dto.UserResponse;
import com.foundflow.auth.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

@WebMvcTest(UserController.class)
@WithMockUser
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper jsonMapper;

    @MockitoBean
    private UserService userService;

    @Test
    void createUser_shouldReturnCreatedUser() throws Exception {
        UUID id = UUID.randomUUID();

        CreateUserRequest request = new CreateUserRequest(
                "staff@example.com",
                Role.STAFF,
                "password123"
        );

        UserResponse response = new UserResponse(
                id,
                "staff@example.com",
                Role.STAFF
        );

        when(userService.createUser(request)).thenReturn(response);

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.email").value("staff@example.com"))
                .andExpect(jsonPath("$.role").value("STAFF"));
    }

    @Test
    void getAllUsers_shouldReturnUsers() throws Exception {
        UserResponse user1 = new UserResponse(
                UUID.randomUUID(),
                "staff@example.com",
                Role.STAFF
        );

        UserResponse user2 = new UserResponse(
                UUID.randomUUID(),
                "admin@example.com",
                Role.ADMIN
        );

        when(userService.getAllUsers()).thenReturn(List.of(user1, user2));

        mockMvc.perform(get("/api/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].email").value("staff@example.com"))
                .andExpect(jsonPath("$[0].role").value("STAFF"))
                .andExpect(jsonPath("$[1].email").value("admin@example.com"))
                .andExpect(jsonPath("$[1].role").value("ADMIN"));
    }

    @Test
    void getUserById_shouldReturnUserWhenExists() throws Exception {
        UUID id = UUID.randomUUID();

        UserResponse response = new UserResponse(
                id,
                "manager@example.com",
                Role.OPS_MANAGER
        );

        when(userService.getUserById(id)).thenReturn(Optional.of(response));

        mockMvc.perform(get("/api/users/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.email").value("manager@example.com"))
                .andExpect(jsonPath("$.role").value("OPS_MANAGER"));
    }

    @Test
    void getUserById_shouldReturnNotFoundWhenMissing() throws Exception {
        UUID id = UUID.randomUUID();

        when(userService.getUserById(id)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/users/{id}", id))
                .andExpect(status().isNotFound());
    }

    @Test
    void getUserByEmail_shouldReturnUserWhenExists() throws Exception {
        UserResponse response = new UserResponse(
                UUID.randomUUID(),
                "admin@example.com",
                Role.ADMIN
        );

        when(userService.getUserByEmail("admin@example.com"))
                .thenReturn(Optional.of(response));

        mockMvc.perform(get("/api/users/by-email")
                        .param("email", "admin@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("admin@example.com"))
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    void getUserByEmail_shouldReturnNotFoundWhenMissing() throws Exception {
        when(userService.getUserByEmail("missing@example.com"))
                .thenReturn(Optional.empty());

        mockMvc.perform(get("/api/users/by-email")
                        .param("email", "missing@example.com"))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateUser_shouldReturnUpdatedUser() throws Exception {
        UUID id = UUID.randomUUID();

        UpdateUserRequest request = new UpdateUserRequest(
                "updated@example.com",
                Role.ADMIN
        );

        UserResponse response = new UserResponse(
                id,
                "updated@example.com",
                Role.ADMIN
        );

        when(userService.updateUser(id, request))
                .thenReturn(Optional.of(response));

        mockMvc.perform(put("/api/users/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.email").value("updated@example.com"))
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }
}