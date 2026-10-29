package com.restaurantapp.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurantapp.user.UserAccount;
import com.restaurantapp.user.UserAccountRepository;
import com.restaurantapp.user.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.http.HttpHeaders;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.Map;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void registrationCreatesCustomerWithHashedPasswordAndIgnoresSuppliedRole() throws Exception {
        String email = "customer-" + UUID.randomUUID() + "@example.test";
        String password = "correct horse battery";
        String request = objectMapper.writeValueAsString(Map.of(
                "name", " Test Customer ",
                "email", email.toUpperCase(),
                "password", password,
                "role", "ADMIN"));
        MvcResult csrfResult = mockMvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isOk())
                .andReturn();
        AuthController.CsrfTokenInfo csrfToken = objectMapper.readValue(
                csrfResult.getResponse().getContentAsString(), AuthController.CsrfTokenInfo.class);
        MockHttpSession session = (MockHttpSession) csrfResult.getRequest().getSession(false);

        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .session(session)
                        .header(csrfToken.headerName(), csrfToken.token())
                        .contentType("application/json")
                        .content(request))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("CUSTOMER"))
                .andExpect(jsonPath("$.email").value(email.toLowerCase()))
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andReturn();

        UserAccount account = userAccountRepository.findByEmail(email.toLowerCase()).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals("Test Customer", account.getName());
        org.junit.jupiter.api.Assertions.assertEquals(UserRole.CUSTOMER, account.getRole());
        org.junit.jupiter.api.Assertions.assertNotEquals(password, account.getPasswordHash());
        org.junit.jupiter.api.Assertions.assertTrue(passwordEncoder.matches(password, account.getPasswordHash()));
        org.junit.jupiter.api.Assertions.assertFalse(result.getResponse().getContentAsString().contains(account.getPasswordHash()));
    }

    @Test
    void loginPersistsSessionAndCurrentUserCanBeRead() throws Exception {
        String email = "login-" + UUID.randomUUID() + "@example.test";
        String password = "correct horse battery";
        userAccountRepository.saveAndFlush(new UserAccount(
                "Login Customer", email, passwordEncoder.encode(password), UserRole.CUSTOMER));

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .with(csrf().asHeader())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, password))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andReturn();

        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);
        org.junit.jupiter.api.Assertions.assertNotNull(session);

        mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.role").value("CUSTOMER"));
    }

    @Test
    void invalidLoginUsesGenericCredentialError() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .with(csrf().asHeader())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest("missing@example.test", "wrong password"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.message").value("Email or password is incorrect."));
    }

    @Test
    void duplicateRegistrationDoesNotRevealWhetherEmailIsRegistered() throws Exception {
        String email = "duplicate-" + UUID.randomUUID() + "@example.test";
        userAccountRepository.saveAndFlush(new UserAccount(
                "Existing Customer", email, passwordEncoder.encode("correct horse battery"), UserRole.CUSTOMER));

        mockMvc.perform(post("/api/auth/register")
                        .with(csrf().asHeader())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new RegisterRequest(
                                "Another Customer", email.toUpperCase(), "another correct horse battery"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REGISTRATION_UNAVAILABLE"))
                .andExpect(jsonPath("$.message").value("Unable to register with these details."));
    }

    @Test
    void registrationRequiresCsrfToken() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new RegisterRequest(
                                "Customer", "csrf-" + UUID.randomUUID() + "@example.test", "correct horse battery"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void customerCannotAccessAdminRoutes() throws Exception {
        mockMvc.perform(get("/api/admin/orders").with(user("customer").roles("CUSTOMER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void adminRolePassesAdminAuthorizationCheck() throws Exception {
        mockMvc.perform(get("/api/admin/orders").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void logoutRequiresAuthenticationAndCsrfAndReturnsNoContent() throws Exception {
        mockMvc.perform(post("/api/auth/logout")
                        .with(user("customer").roles("CUSTOMER"))
                        .with(csrf().asHeader()))
                .andExpect(status().isNoContent());
    }

    @Test
    void anonymousUserCannotReadCurrentUser() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void csrfEndpointReturnsTokenAndRequiredHeaderName() throws Exception {
        mockMvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.headerName").value("X-CSRF-TOKEN"))
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    void corsAllowsConfiguredFrontendOriginWithCredentials() throws Exception {
        mockMvc.perform(options("/api/auth/login")
                        .header(HttpHeaders.ORIGIN, "http://localhost:3000")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "content-type,x-csrf-token"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:3000"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));
    }
}
