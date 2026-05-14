package com.foundflow.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "app.cors.allowed-origins=http://localhost:3000,http://localhost:13000")
class CorsConfigTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void firstAllowedOriginGetsCorsHeader() throws Exception {
        mockMvc.perform(get("/greet").header("Origin", "http://localhost:3000"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"));
    }

    @Test
    void secondAllowedOriginGetsCorsHeader() throws Exception {
        mockMvc.perform(get("/greet").header("Origin", "http://localhost:13000"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:13000"));
    }

    @Test
    void disallowedOriginIsRejected() throws Exception {
        mockMvc.perform(get("/greet").header("Origin", "http://malicious.example"))
                .andExpect(status().isForbidden());
    }

    @Test
    void preflightFromAllowedOriginSucceeds() throws Exception {
        mockMvc.perform(options("/greet")
                        .header("Origin", "http://localhost:13000")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:13000"));
    }
}
