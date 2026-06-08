package com.foundflow.auth.controller;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.foundflow.auth.domain.Role;
import com.foundflow.auth.dto.CreateUserRequest;
import com.foundflow.auth.dto.UpdateUserRequest;
import com.foundflow.auth.dto.UserResponse;
import com.foundflow.auth.service.UserService;

import tools.jackson.databind.json.JsonMapper;

@WebMvcTest(UserController.class)
class UserControllerTest {

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private JsonMapper jsonMapper;

        @MockitoBean
        private UserService userService;

        @MockitoBean
        private JwtDecoder jwtDecoder;

        @Test
        void createUser_shouldReturnCreatedUser() throws Exception {
                UUID id = UUID.randomUUID();
                UUID venueId = UUID.randomUUID();

                CreateUserRequest request = new CreateUserRequest(
                        "staff@example.com",
                        Role.STAFF,
                        "password123",
                        venueId
                );

                UserResponse response = new UserResponse(
                        id,
                        "staff@example.com",
                        Role.STAFF,
                        venueId
                );

                when(userService.createUser(eq(request), any(Jwt.class))).thenReturn(response);

                mockMvc.perform(post("/api/users")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(jsonMapper.writeValueAsString(request))
                                .with(adminPrincipal()))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.id").value(id.toString()))
                        .andExpect(jsonPath("$.email").value("staff@example.com"))
                        .andExpect(jsonPath("$.role").value("STAFF"))
                        .andExpect(jsonPath("$.venueId").value(venueId.toString()));
        }

        @Test
        void getAllUsers_shouldReturnUsers() throws Exception {
                UUID venueId = UUID.randomUUID();
                UserResponse user1 = new UserResponse(
                        UUID.randomUUID(),
                        "staff@example.com",
                        Role.STAFF,
                        venueId
                );

                UserResponse user2 = new UserResponse(
                        UUID.randomUUID(),
                        "admin@example.com",
                        Role.ADMIN,
                        null
                );

                when(userService.getAllUsers(isNull(), isNull(), any(Jwt.class)))
                        .thenReturn(List.of(user1, user2));

                mockMvc.perform(get("/api/users")
                                .with(adminPrincipal()))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$[0].email").value("staff@example.com"))
                        .andExpect(jsonPath("$[0].role").value("STAFF"))
                        .andExpect(jsonPath("$[1].email").value("admin@example.com"))
                        .andExpect(jsonPath("$[1].role").value("ADMIN"));
        }

        @Test
        void getAllUsers_shouldPassVenueAndRoleFilters() throws Exception {
                UUID venueId = UUID.randomUUID();
                UserResponse user = new UserResponse(
                        UUID.randomUUID(),
                        "staff@example.com",
                        Role.STAFF,
                        venueId
                );

                when(userService.getAllUsers(eq(venueId), eq(Role.STAFF), any(Jwt.class)))
                        .thenReturn(List.of(user));

                mockMvc.perform(get("/api/users")
                                .param("venueId", venueId.toString())
                                .param("role", "STAFF")
                                .with(adminPrincipal()))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$[0].email").value("staff@example.com"))
                        .andExpect(jsonPath("$[0].role").value("STAFF"))
                        .andExpect(jsonPath("$[0].venueId").value(venueId.toString()));
        }

        @Test
        void getUserById_shouldReturnUserWhenExists() throws Exception {
                UUID id = UUID.randomUUID();

                UserResponse response = new UserResponse(
                        id,
                        "manager@example.com",
                        Role.OPS_MANAGER,
                        UUID.randomUUID()
                );

                when(userService.getUserById(eq(id), any(Jwt.class))).thenReturn(Optional.of(response));

                mockMvc.perform(get("/api/users/{id}", id)
                                .with(adminPrincipal()))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.id").value(id.toString()))
                        .andExpect(jsonPath("$.email").value("manager@example.com"))
                        .andExpect(jsonPath("$.role").value("OPS_MANAGER"));
        }

        @Test
        void getUserById_shouldReturnNotFoundWhenMissing() throws Exception {
                UUID id = UUID.randomUUID();

                when(userService.getUserById(eq(id), any(Jwt.class))).thenReturn(Optional.empty());

                mockMvc.perform(get("/api/users/{id}", id)
                                .with(adminPrincipal()))
                        .andExpect(status().isNotFound());
        }

        @Test
        void getUserByEmail_shouldReturnUserWhenExists() throws Exception {
                UserResponse response = new UserResponse(
                        UUID.randomUUID(),
                        "admin@example.com",
                        Role.ADMIN,
                        null
                );

                when(userService.getUserByEmail(eq("admin@example.com"), any(Jwt.class)))
                        .thenReturn(Optional.of(response));

                mockMvc.perform(get("/api/users/by-email")
                                .param("email", "admin@example.com")
                                .with(adminPrincipal()))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.email").value("admin@example.com"))
                        .andExpect(jsonPath("$.role").value("ADMIN"));
        }

        @Test
        void getUserByEmail_shouldReturnNotFoundWhenMissing() throws Exception {
                when(userService.getUserByEmail(eq("missing@example.com"), any(Jwt.class)))
                        .thenReturn(Optional.empty());

                mockMvc.perform(get("/api/users/by-email")
                                .param("email", "missing@example.com")
                                .with(adminPrincipal()))
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
                        Role.ADMIN,
                        null
                );

                when(userService.updateUser(eq(id), eq(request), any(Jwt.class)))
                        .thenReturn(Optional.of(response));

                mockMvc.perform(put("/api/users/{id}", id)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(jsonMapper.writeValueAsString(request))
                                .with(adminPrincipal()))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.id").value(id.toString()))
                        .andExpect(jsonPath("$.email").value("updated@example.com"))
                        .andExpect(jsonPath("$.role").value("ADMIN"));
        }

        @Test
        void deleteUser_shouldReturnNoContentWhenDeleted() throws Exception {
                UUID id = UUID.randomUUID();

                when(userService.deleteUser(eq(id), any(Jwt.class))).thenReturn(true);

                mockMvc.perform(delete("/api/users/{id}", id)
                                .with(adminPrincipal()))
                        .andExpect(status().isNoContent());
        }

        private RequestPostProcessor adminPrincipal() {
                return jwt()
                        .jwt(token -> token
                                .subject("admin@example.com")
                                .claim("roles", List.of("ADMIN")))
                        .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
        }
}
