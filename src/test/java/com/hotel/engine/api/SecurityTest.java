package com.hotel.engine.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Sign-in and role rules over HTTP, against the seeded demo hotel. */
@SpringBootTest
class SecurityTest {

    @Autowired WebApplicationContext context;
    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    @DisplayName("No session: API calls get 401 JSON, not a login redirect")
    void requiresLogin() throws Exception {
        mvc.perform(get("/api/overview")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Sign in to continue."));
    }

    @Test
    @DisplayName("Login works with the right password and CSRF token; wrong password and missing token are refused")
    void login() throws Exception {
        String ok = "{\"username\":\"manager\",\"password\":\"test-manager-pass\"}";
        mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(ok))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("MANAGER"));
        mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"manager\",\"password\":\"nope\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(ok))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Front desk can't add maintenance or see the watchlist; a manager can")
    void roleRules() throws Exception {
        String block = "{\"roomNumber\":\"999\",\"startDate\":\"2099-01-01\",\"endDate\":\"2099-01-02\",\"reason\":\"x\"}";
        mvc.perform(post("/api/maintenance").with(csrf()).with(user("frontdesk").roles("FRONT_DESK"))
                .contentType(MediaType.APPLICATION_JSON).content(block)).andExpect(status().isForbidden());
        mvc.perform(post("/api/maintenance").with(csrf()).with(user("manager").roles("MANAGER"))
                .contentType(MediaType.APPLICATION_JSON).content(block)).andExpect(status().isNotFound());
        mvc.perform(get("/api/watchlist").with(user("frontdesk").roles("FRONT_DESK"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/watchlist").with(user("manager").roles("MANAGER"))).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Only admins manage staff accounts")
    void adminOnly() throws Exception {
        mvc.perform(get("/api/admin/users").with(user("manager").roles("MANAGER"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/users").with(user("admin").roles("ADMIN"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));
    }

    @Test
    @DisplayName("Signed-in staff can read the overview, and every number in it comes from the seeded hotel")
    void overview() throws Exception {
        mvc.perform(get("/api/overview").with(user("frontdesk").roles("FRONT_DESK"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.kpis.totalRooms").value(100));
    }
}
