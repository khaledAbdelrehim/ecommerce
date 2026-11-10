package it.example.ecommerce.controller;

import it.example.ecommerce.support.IntegrationTestBase;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.RequestDispatcher;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SecurityIT extends IntegrationTestBase {

    @Test
    void givenNoCredentials_whenCallApi_thenUnauthorizedProblemDetail() throws Exception {
        mockMvc.perform(get("/api/v1/products"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists("WWW-Authenticate"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void givenErrorDispatchWithoutCredentials_whenForwardedToErrorPage_thenNotMaskedByUnauthorized() throws Exception {
        mockMvc.perform(get("/error").with(request -> {
                    request.setDispatcherType(DispatcherType.ERROR);
                    request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, 400);
                    return request;
                }))
                .andExpect(status().isBadRequest());
    }

    @Test
    void givenMalformedToken_whenCallApi_thenUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/products").header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void givenTokenOfDeletedUser_whenCallApi_thenUnauthorized() throws Exception {
        var token = bearer("bob");
        userRepository.delete(userRepository.findByUsername("bob").orElseThrow());

        mockMvc.perform(get("/api/v1/products").with(token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void givenBasicCredentials_whenCallApi_thenUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/products").header("Authorization", "Basic YWRtaW46YWRtaW4xMjM="))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void givenCustomerRole_whenCallAdminEndpoint_thenForbiddenProblemDetail() throws Exception {
        mockMvc.perform(get("/api/v1/customers").with(bearer("bob")))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void givenAdmin_whenCallAdminEndpoint_thenOk() throws Exception {
        mockMvc.perform(get("/api/v1/customers").with(bearer("admin")))
                .andExpect(status().isOk());
    }

    @Test
    void givenSession_whenAuthenticatedCall_thenNoSessionCookieIsIssued() throws Exception {
        mockMvc.perform(get("/api/v1/products").with(bearer("admin")))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Set-Cookie"));
    }
}
