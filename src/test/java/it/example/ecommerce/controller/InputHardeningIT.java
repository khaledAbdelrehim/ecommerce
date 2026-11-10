package it.example.ecommerce.controller;

import it.example.ecommerce.domain.Product;
import it.example.ecommerce.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class InputHardeningIT extends IntegrationTestBase {

    @Autowired
    private TransactionTemplate transactionTemplate;

    private static String registerBody(String username, String cf, String email) {
        return """
                {"username":"%s","password":"secret123","nome":"Carla","cognome":"Gialli",
                 "dataNascita":"1995-03-10","codiceFiscale":"%s","email":"%s"}
                """.formatted(username, cf, email);
    }

    private void createProduct(String codice, int expectedStatus) throws Exception {
        mockMvc.perform(post("/api/v1/products").with(bearer("admin")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codice\": \"" + codice + "\", \"nome\": \"Laptop\", \"stock\": 5}"))
                .andExpect(status().is(expectedStatus));
    }

    @Test
    void givenSortOnCollectionPath_whenListOrders_thenBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/orders?sort=righe.quantita").with(bearer("admin")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void givenSortOnAllowedField_whenListOrders_thenOk() throws Exception {
        mockMvc.perform(get("/api/v1/orders?sort=dataCreazione,desc").with(bearer("admin")))
                .andExpect(status().isOk());
    }

    @Test
    void givenSortOnSensitiveCustomerField_whenListCustomers_thenBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/customers?sort=users").with(bearer("admin")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void givenTiesOnSortedColumn_whenPaging_thenEveryProductAppearsExactlyOnce() throws Exception {
        for (int i = 0; i < 6; i++) {
            productRepository.save(new Product("T-" + i, "Same name", 5));
        }
        StringBuilder seen = new StringBuilder();
        for (int page = 0; page < 3; page++) {
            seen.append(mockMvc.perform(get("/api/v1/products?sort=nome&size=2&page=" + page)
                            .with(bearer("admin")))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString());
        }
        for (int i = 0; i < 6; i++) {
            assertThat(seen.toString().split("\"codice\":\"T-" + i + "\"", -1)).hasSize(2);
        }
    }

    @Test
    void givenExistingUsernameOrEmail_whenRegister_thenSameGenericMessage() throws Exception {
        String byUsername = mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody("alice", "GLLCRL95C50H501Z", "carla@example.com")))
                .andExpect(status().isConflict())
                .andReturn().getResponse().getContentAsString();
        String byEmail = mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody("carla", "GLLCRL95C50H501Z", "alice@example.com")))
                .andExpect(status().isConflict())
                .andReturn().getResponse().getContentAsString();
        String byCodiceFiscale = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody("carla", "VRDLCA90A41H501A", "carla@example.com")))
                .andExpect(status().isConflict())
                .andReturn().getResponse().getContentAsString();

        assertThat(byUsername).isEqualTo(byEmail).isEqualTo(byCodiceFiscale)
                .doesNotContain("alice").doesNotContain("VRDLCA90A41H501A");
    }

    @Test
    void givenUsernameDifferingOnlyByCase_whenRegister_thenConflict() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody("ALICE", "GLLCRL95C50H501Z", "carla@example.com")))
                .andExpect(status().isConflict());
    }

    @Test
    void givenMixedCaseUsername_whenRegisterAndLogin_thenStoredLowerCaseAndLoginIsCaseInsensitive()
            throws Exception {
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody("Carla", "GLLCRL95C50H501Z", "carla@example.com")))
                .andExpect(status().isCreated());

        assertThat(userRepository.existsByUsername("carla")).isTrue();
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"CARLA\",\"password\":\"secret123\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void givenUsernameWithNewline_whenRegister_thenBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody("carla\\nFAKE", "GLLCRL95C50H501Z", "carla@example.com")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.username").exists());
    }

    @Test
    void givenCodiceWithNewline_whenCreateProduct_thenBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/products").with(bearer("admin")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codice\": \"LAP\\n001\", \"nome\": \"Laptop\", \"stock\": 5}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.codice").exists());
    }

    @Test
    void givenCodiceDifferingOnlyByCase_whenCreateProduct_thenConflict() throws Exception {
        createProduct("LAP-001", 201);
        createProduct("lap-001", 409);
    }

    @Test
    void givenLowerCaseCodice_whenCreateProduct_thenStoredUpperCase() throws Exception {
        mockMvc.perform(post("/api/v1/products").with(bearer("admin")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codice\": \"lap-002\", \"nome\": \"Laptop\", \"stock\": 5}"))
                .andExpect(status().isCreated())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("LAP-002")));
    }

    @Test
    void givenProductReadBeforeBulkUpdate_whenOrderedInSameTransaction_thenReReadSeesNewStock() {
        Product product = newProduct("FRESH-1", 10);

        int stockAfter = transactionTemplate.execute(tx -> {
            Product loaded = productRepository.findById(product.getId()).orElseThrow();
            assertThat(loaded.getStock()).isEqualTo(10);
            productRepository.decreaseStockIfAvailable(product.getId(), 4);
            return productRepository.findById(product.getId()).orElseThrow().getStock();
        });

        assertThat(stockAfter).isEqualTo(6);
    }
}
