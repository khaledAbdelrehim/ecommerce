package it.example.ecommerce.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.example.ecommerce.domain.AppUser;
import it.example.ecommerce.domain.Role;
import it.example.ecommerce.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthControllerIT extends IntegrationTestBase {

    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private static String loginBody(String username, String password) {
        return "{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, password);
    }

    private static String registerBody(String username, String cf, String email) {
        return """
                {"username":"%s","password":"secret123","nome":"Carla","cognome":"Gialli",
                 "dataNascita":"1995-03-10","codiceFiscale":"%s","email":"%s"}
                """.formatted(username, cf, email);
    }

    @Test
    void givenValidCredentials_whenLogin_thenBearerTokenWorksOnApi() throws Exception {
        String response = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("admin", "admin123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(3600))
                .andReturn().getResponse().getContentAsString();
        String token = objectMapper.readTree(response).get("accessToken").asText();

        mockMvc.perform(get("/api/v1/customers").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void givenWrongPassword_whenLogin_thenUnauthorizedProblemDetail() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("admin", "wrong")))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists("WWW-Authenticate"))
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void givenUnknownUser_whenLogin_thenUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("ghost", "whatever1")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void givenVeryLongMultiByteLoginPassword_whenLogin_thenUnauthorizedProblemDetail() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("admin", "è".repeat(40))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void givenBlankCredentials_whenLogin_thenBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("", "")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void givenNewCustomer_whenRegister_thenCreatedAndCanLoginAndSeeOnlyOwnData() throws Exception {
        String response = mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody("carla", "GLLCRL95C50H501Z", "carla@example.com")))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.containsString("/api/v1/customers/")))
                .andExpect(jsonPath("$.email").value("carla@example.com"))
                .andReturn().getResponse().getContentAsString();
        long customerId = objectMapper.readTree(response).get("id").asLong();

        AppUser user = userRepository.findByUsername("carla").orElseThrow();
        assertThat(user.getRole()).isEqualTo(Role.CUSTOMER);
        assertThat(user.getCustomer().getId()).isEqualTo(customerId);
        assertThat(passwordEncoder.matches("secret123", user.getPassword())).isTrue();

        JsonNode token = objectMapper.readTree(mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(loginBody("carla", "secret123")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        String bearer = "Bearer " + token.get("accessToken").asText();

        mockMvc.perform(get("/api/v1/customers/{id}", customerId).header("Authorization", bearer))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/customers/{id}", alice.getId()).header("Authorization", bearer))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/customers").header("Authorization", bearer))
                .andExpect(status().isForbidden());
    }

    @Test
    void givenRoleInPayload_whenRegister_thenStillCustomer() throws Exception {
        String body = registerBody("sneaky", "GLLCRL95C50H501Z", "sneaky@example.com")
                .replace("{", "{\"role\":\"ADMIN\",");
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());

        assertThat(userRepository.findByUsername("sneaky").orElseThrow().getRole()).isEqualTo(Role.CUSTOMER);
    }

    @Test
    void givenExistingUsername_whenRegister_thenConflictAndNoCustomerCreated() throws Exception {
        long before = customerRepository.count();

        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody("alice", "GLLCRL95C50H501Z", "carla@example.com")))
                .andExpect(status().isConflict());

        assertThat(customerRepository.count()).isEqualTo(before);
    }

    @Test
    void givenExistingEmail_whenRegister_thenConflictAndNoUserCreated() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody("carla", "GLLCRL95C50H501Z", "alice@example.com")))
                .andExpect(status().isConflict());

        assertThat(userRepository.existsByUsername("carla")).isFalse();
    }

    @Test
    void givenPasswordOverBcryptByteLimit_whenRegister_thenBadRequestWithFieldError() throws Exception {
        String multiBytePassword = "è".repeat(40);

        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody("carla", "GLLCRL95C50H501Z", "carla@example.com")
                                .replace("secret123", multiBytePassword)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.password").exists());

        assertThat(userRepository.existsByUsername("carla")).isFalse();
    }

    @Test
    void givenInvalidPayload_whenRegister_thenBadRequestWithFieldErrors() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody("ab", "INVALID", "not-an-email").replace("secret123", "short")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.username").exists())
                .andExpect(jsonPath("$.errors.password").exists())
                .andExpect(jsonPath("$.errors.codiceFiscale").exists())
                .andExpect(jsonPath("$.errors.email").exists());
    }
}
