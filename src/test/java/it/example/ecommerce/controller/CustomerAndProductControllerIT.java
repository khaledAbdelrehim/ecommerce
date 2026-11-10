package it.example.ecommerce.controller;

import it.example.ecommerce.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CustomerAndProductControllerIT extends IntegrationTestBase {

    private static final String CUSTOMER_JSON = """
            {"nome": "Carlo", "cognome": "Gialli", "dataNascita": "1980-02-03",
             "codiceFiscale": "glLCRL80B03H501K", "email": "carlo@example.com"}
            """;

    @Test
    void givenAdmin_whenCreateCustomer_thenCreatedWithLocationAndUppercaseCodiceFiscale() throws Exception {
        mockMvc.perform(post("/api/v1/customers").with(bearer("admin"))
                        .contentType(MediaType.APPLICATION_JSON).content(CUSTOMER_JSON))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.codiceFiscale").value("GLLCRL80B03H501K"));
    }

    @Test
    void givenExistingCodiceFiscale_whenCreateCustomer_thenConflict() throws Exception {
        String body = CUSTOMER_JSON.replace("glLCRL80B03H501K", "VRDLCA90A41H501A");

        mockMvc.perform(post("/api/v1/customers").with(bearer("admin"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void givenExistingEmail_whenCreateCustomer_thenConflict() throws Exception {
        String body = CUSTOMER_JSON.replace("carlo@example.com", "ALICE@example.com");

        mockMvc.perform(post("/api/v1/customers").with(bearer("admin"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void givenMixedCaseEmail_whenCreateCustomer_thenStoredLowerCaseAndDuplicateRejected() throws Exception {
        String body = CUSTOMER_JSON.replace("carlo@example.com", "Carlo@Example.COM");

        mockMvc.perform(post("/api/v1/customers").with(bearer("admin"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("carlo@example.com"));

        mockMvc.perform(post("/api/v1/customers").with(bearer("admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body.replace("glLCRL80B03H501K", "VRDLCA90A41H501B").replace("Carlo@Example.COM", "CARLO@example.com")))
                .andExpect(status().isConflict());
    }

    @Test
    void givenInvalidCodiceFiscaleAndEmail_whenCreateCustomer_thenBadRequestListsFields() throws Exception {
        String body = CUSTOMER_JSON.replace("glLCRL80B03H501K", "123").replace("carlo@example.com", "nope");

        mockMvc.perform(post("/api/v1/customers").with(bearer("admin"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors.codiceFiscale").exists())
                .andExpect(jsonPath("$.errors.email").exists());
    }

    @Test
    void givenMalformedJson_whenCreateCustomer_thenBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/customers").with(bearer("admin"))
                        .contentType(MediaType.APPLICATION_JSON).content("{oops"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void givenAdmin_whenListCustomers_thenPagedResponse() throws Exception {
        mockMvc.perform(get("/api/v1/customers").param("size", "1").param("sort", "cognome,asc")
                        .with(bearer("admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].cognome").value("Neri"))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    void givenCustomer_whenListCustomers_thenForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/customers").with(bearer("alice")))
                .andExpect(status().isForbidden());
    }

    @Test
    void givenCustomer_whenGetOwnOrOtherCustomer_thenOkOrForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/customers/{id}", alice.getId()).with(bearer("alice")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/customers/{id}", bob.getId()).with(bearer("alice")))
                .andExpect(status().isForbidden());
    }

    @Test
    void givenAdmin_whenCreateProduct_thenCreated() throws Exception {
        mockMvc.perform(post("/api/v1/products").with(bearer("admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codice\": \"P-1\", \"nome\": \"Phone\", \"stock\": 7}"))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.stock").value(7));
    }

    @Test
    void givenCustomer_whenCreateProduct_thenForbidden() throws Exception {
        mockMvc.perform(post("/api/v1/products").with(bearer("alice"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codice\": \"P-1\", \"nome\": \"Phone\", \"stock\": 7}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void givenExistingCode_whenCreateProduct_thenConflict() throws Exception {
        newProduct("P-1", 1);

        mockMvc.perform(post("/api/v1/products").with(bearer("admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codice\": \"P-1\", \"nome\": \"Phone\", \"stock\": 7}"))
                .andExpect(status().isConflict());
    }

    @Test
    void givenNegativeStock_whenCreateProduct_thenBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/products").with(bearer("admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codice\": \"P-1\", \"nome\": \"Phone\", \"stock\": -1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.stock").exists());
    }

    @Test
    void givenCustomer_whenListProducts_thenOkWithPageMetadata() throws Exception {
        newProduct("P-1", 1);
        newProduct("P-2", 2);

        mockMvc.perform(get("/api/v1/products").param("page", "1").param("size", "1")
                        .with(bearer("alice")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.last").value(true));
    }

    @Test
    void givenUnknownProduct_whenGet_thenNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/products/{id}", 987654).with(bearer("alice")))
                .andExpect(status().isNotFound());
    }

    @Test
    void givenUnknownSortProperty_whenListProducts_thenBadRequestProblemDetail() throws Exception {
        mockMvc.perform(get("/api/v1/products").param("sort", "foo").with(bearer("alice")))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status").value(400));
    }
}
