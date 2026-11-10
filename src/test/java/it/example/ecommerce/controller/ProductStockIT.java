package it.example.ecommerce.controller;

import it.example.ecommerce.domain.Product;
import it.example.ecommerce.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProductStockIT extends IntegrationTestBase {

    private ResultActions adjust(Object id, String user, String body) throws Exception {
        var request = patch("/api/v1/products/{id}/stock", id)
                .contentType(MediaType.APPLICATION_JSON).content(body);
        return mockMvc.perform(user == null ? request : request.with(bearer(user)));
    }

    @Test
    void givenAdmin_whenPositiveDelta_thenStockIncreased() throws Exception {
        Product product = newProduct("P-1", 5);

        adjust(product.getId(), "admin", "{\"delta\": 7}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stock").value(12));

        assertThat(stockOf(product)).isEqualTo(12);
    }

    @Test
    void givenAdmin_whenNegativeDelta_thenStockDecreased() throws Exception {
        Product product = newProduct("P-1", 5);

        adjust(product.getId(), "admin", "{\"delta\": -5}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stock").value(0));

        assertThat(stockOf(product)).isZero();
    }

    @Test
    void givenDeltaBelowAvailableStock_whenAdjust_thenConflictAndStockUnchanged() throws Exception {
        Product product = newProduct("P-1", 5);

        adjust(product.getId(), "admin", "{\"delta\": -6}").andExpect(status().isConflict());

        assertThat(stockOf(product)).isEqualTo(5);
    }

    @Test
    void givenZeroOrMissingDelta_whenAdjust_thenBadRequest() throws Exception {
        Product product = newProduct("P-1", 5);

        adjust(product.getId(), "admin", "{\"delta\": 0}").andExpect(status().isBadRequest());
        adjust(product.getId(), "admin", "{}").andExpect(status().isBadRequest());
        adjust(product.getId(), "admin", "{\"delta\": 2000000}").andExpect(status().isBadRequest());

        assertThat(stockOf(product)).isEqualTo(5);
    }

    @Test
    void givenUnknownProduct_whenAdjust_thenNotFound() throws Exception {
        adjust(987654, "admin", "{\"delta\": 1}").andExpect(status().isNotFound());
    }

    @Test
    void givenCustomer_whenAdjust_thenForbiddenAndStockUnchanged() throws Exception {
        Product product = newProduct("P-1", 5);

        adjust(product.getId(), "alice", "{\"delta\": 1}").andExpect(status().isForbidden());

        assertThat(stockOf(product)).isEqualTo(5);
    }

    @Test
    void givenNoToken_whenAdjust_thenUnauthorized() throws Exception {
        Product product = newProduct("P-1", 5);

        adjust(product.getId(), null, "{\"delta\": 1}").andExpect(status().isUnauthorized());
    }
}
