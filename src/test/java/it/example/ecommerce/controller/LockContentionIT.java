package it.example.ecommerce.controller;

import it.example.ecommerce.domain.Product;
import it.example.ecommerce.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LockContentionIT extends IntegrationTestBase {

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void givenProductRowLockedByAnotherTransaction_whenCreateOrder_thenServiceUnavailableWithRetryAfter()
            throws Exception {
        Product laptop = newProduct("LAP-LOCK", 5);
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        CompletableFuture<Void> holder = CompletableFuture.runAsync(() ->
                new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
                    productRepository.decreaseStockIfAvailable(laptop.getId(), 1);
                    locked.countDown();
                    await(release);
                }));
        assertThat(locked.await(5, TimeUnit.SECONDS)).isTrue();

        try {
            mockMvc.perform(post("/api/v1/orders").with(bearer("alice"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"items\":[{\"productId\":" + laptop.getId() + ",\"quantita\":1}]}"))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(header().string("Retry-After", "1"))
                    .andExpect(jsonPath("$.status").value(503));
        } finally {
            release.countDown();
            holder.get(5, TimeUnit.SECONDS);
        }

        assertThat(stockOf(laptop)).isEqualTo(4);
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
