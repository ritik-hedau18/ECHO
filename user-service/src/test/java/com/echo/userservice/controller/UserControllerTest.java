package com.echo.userservice.controller;

import com.echo.userservice.dto.UserResponse;
import com.echo.userservice.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Collections;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@AutoConfigureMockMvc(addFilters = false) // Bypass Security filters for simpler controller unit testing
public class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserService userService;

    @Test
    void getUserProfile_Success() throws Exception {
        UserResponse response = UserResponse.builder()
                .id("test-id")
                .username("testuser")
                .email("test@echo.com")
                .createdAt(LocalDateTime.now())
                .build();

        when(userService.getUserProfile("test-id")).thenReturn(response);

        mockMvc.perform(get("/api/users/test-id")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("testuser"))
                .andExpect(jsonPath("$.email").value("test@echo.com"));
    }

    @Test
    void searchUsers_Success() throws Exception {
        UserResponse response = UserResponse.builder()
                .id("test-id")
                .username("testuser")
                .email("test@echo.com")
                .build();

        when(userService.searchUsers("test")).thenReturn(Collections.singletonList(response));

        mockMvc.perform(get("/api/users/search")
                        .param("query", "test")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].username").value("testuser"));
    }
}
