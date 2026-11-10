package it.example.ecommerce.controller;

import it.example.ecommerce.support.IntegrationTestBase;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class OrderQueryCountIT extends IntegrationTestBase {

    private static final int ORDERS = 12;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Test
    void givenManyOrdersWithDifferentProducts_whenListOrders_thenQueryCountDoesNotGrowWithOrders() throws Exception {
        List<it.example.ecommerce.domain.Product> products = new ArrayList<>();
        for (int i = 0; i < ORDERS; i++) {
            products.add(newProduct("QC-" + i, 10));
        }
        products.forEach(product -> newOrder(alice, product, 1));
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        mockMvc.perform(get("/api/v1/orders").param("size", "50").with(bearer("admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(ORDERS))
                .andExpect(jsonPath("$.content[0].righe[0].productCodice").exists());

        assertThat(statistics.getPrepareStatementCount())
                .as("SQL statements issued while listing %d orders", ORDERS)
                .isLessThan(ORDERS);
    }
}
