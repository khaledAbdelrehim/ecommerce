package it.example.ecommerce.controller;

import it.example.ecommerce.domain.OrderStatus;
import it.example.ecommerce.domain.Product;
import it.example.ecommerce.dto.OrderResponse;
import it.example.ecommerce.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OrderControllerIT extends IntegrationTestBase {

    private Product laptop;
    private Product mouse;

    @BeforeEach
    void createProducts() {
        laptop = newProduct("LAP", 5);
        mouse = newProduct("MOU", 10);
    }

    @Test
    void givenAdmin_whenCreateOrderForCustomer_thenCreatedWithLocationAndStockDecremented() throws Exception {
        String body = """
                {"customerId": %d, "items": [{"productId": %d, "quantita": 2}, {"productId": %d, "quantita": 3}]}
                """.formatted(alice.getId(), laptop.getId(), mouse.getId());

        postOrder(body, "admin")
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.matchesPattern(".*/api/v1/orders/\\d+")))
                .andExpect(jsonPath("$.stato").value("ORDINATO"))
                .andExpect(jsonPath("$.customerId").value(alice.getId()))
                .andExpect(jsonPath("$.righe.length()").value(2));

        assertThat(stockOf(laptop)).isEqualTo(3);
        assertThat(stockOf(mouse)).isEqualTo(7);
    }

    @Test
    void givenCustomer_whenCreateOrderWithoutCustomerId_thenOrderBelongsToCustomer() throws Exception {
        postOrder(itemsBody(laptop, 1), "alice")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.customerId").value(alice.getId()));
    }

    @Test
    void givenCustomer_whenCreateOrderForAnotherCustomer_thenForbidden() throws Exception {
        String body = """
                {"customerId": %d, "items": [{"productId": %d, "quantita": 1}]}
                """.formatted(bob.getId(), laptop.getId());

        postOrder(body, "alice")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));

        assertThat(stockOf(laptop)).isEqualTo(5);
    }

    @Test
    void givenAdmin_whenCreateOrderWithoutCustomerId_thenBadRequest() throws Exception {
        postOrder(itemsBody(laptop, 1), "admin")
                .andExpect(status().isBadRequest());
    }

    @Test
    void givenInsufficientStockOnSecondLine_whenCreateOrder_thenConflictAndNothingChanges() throws Exception {
        String body = """
                {"items": [{"productId": %d, "quantita": 2}, {"productId": %d, "quantita": 11}]}
                """.formatted(laptop.getId(), mouse.getId());

        postOrder(body, "alice")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("MOU")));

        assertThat(stockOf(laptop)).isEqualTo(5);
        assertThat(stockOf(mouse)).isEqualTo(10);
        assertThat(orderRepository.count()).isZero();
    }

    @Test
    void givenInvalidQuantity_whenCreateOrder_thenBadRequestWithFieldErrors() throws Exception {
        postOrder(itemsBody(laptop, 0), "alice")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").isNotEmpty());
    }

    @Test
    void givenNullItem_whenCreateOrder_thenBadRequest() throws Exception {
        postOrder("{\"items\": [null]}", "alice")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void givenQuantityAboveMaximum_whenCreateOrder_thenBadRequest() throws Exception {
        postOrder("{\"items\": [{\"productId\": %d, \"quantita\": 10001}]}".formatted(laptop.getId()), "alice")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").isNotEmpty());
    }

    @Test
    void givenMoreThanMaxItems_whenCreateOrder_thenBadRequest() throws Exception {
        String item = "{\"productId\": %d, \"quantita\": 1}".formatted(laptop.getId());
        String body = "{\"items\": [" + String.join(",", java.util.Collections.nCopies(101, item)) + "]}";

        postOrder(body, "alice")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").isNotEmpty());
    }

    @Test
    void givenEmptyItems_whenCreateOrder_thenBadRequest() throws Exception {
        postOrder("{\"items\": []}", "alice")
                .andExpect(status().isBadRequest());
    }

    @Test
    void givenUnknownProduct_whenCreateOrder_thenNotFound() throws Exception {
        postOrder("{\"items\": [{\"productId\": 999999, \"quantita\": 1}]}", "alice")
                .andExpect(status().isNotFound());
    }

    @Test
    void givenOrdersOfDifferentCustomers_whenAdminLists_thenSeesAllWithPageMetadata() throws Exception {
        newOrder(alice, laptop, 1);
        newOrder(bob, mouse, 1);
        newOrder(bob, mouse, 1);

        mockMvc.perform(get("/api/v1/orders").param("size", "2").param("sort", "id,desc")
                        .with(bearer("admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.first").value(true))
                .andExpect(jsonPath("$.last").value(false));
    }

    @Test
    void givenOrdersOfDifferentCustomers_whenCustomerLists_thenSeesOnlyOwn() throws Exception {
        newOrder(alice, laptop, 1);
        newOrder(bob, mouse, 1);

        mockMvc.perform(get("/api/v1/orders").with(bearer("alice")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].customerId").value(alice.getId()));
    }

    @Test
    void givenOwnOrder_whenCustomerGetsIt_thenOk() throws Exception {
        OrderResponse order = newOrder(alice, laptop, 1);

        mockMvc.perform(get("/api/v1/orders/{id}", order.id()).with(bearer("alice")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(order.id()));
    }

    @Test
    void givenAnotherCustomersOrder_whenCustomerGetsIt_thenForbidden() throws Exception {
        OrderResponse order = newOrder(alice, laptop, 1);

        mockMvc.perform(get("/api/v1/orders/{id}", order.id()).with(bearer("bob")))
                .andExpect(status().isForbidden());
    }

    @Test
    void givenUnknownOrder_whenCustomerGetsIt_thenForbiddenLikeAnotherCustomersOrder() throws Exception {
        mockMvc.perform(get("/api/v1/orders/{id}", 424242).with(bearer("alice")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void givenUnknownOrder_whenAdminGetsIt_thenNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/orders/{id}", 424242).with(bearer("admin")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void givenOrderedOrder_whenAdminDelivers_thenConsegnatoAndSecondDeliverConflicts() throws Exception {
        OrderResponse order = newOrder(alice, laptop, 1);

        mockMvc.perform(post("/api/v1/orders/{id}/deliver", order.id()).with(bearer("admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stato").value("CONSEGNATO"));

        mockMvc.perform(post("/api/v1/orders/{id}/deliver", order.id()).with(bearer("admin")))
                .andExpect(status().isConflict());
    }

    @Test
    void givenOwnOrder_whenCustomerDelivers_thenForbidden() throws Exception {
        OrderResponse order = newOrder(alice, laptop, 1);

        mockMvc.perform(post("/api/v1/orders/{id}/deliver", order.id()).with(bearer("alice")))
                .andExpect(status().isForbidden());
    }

    @Test
    void givenOwnOrder_whenCustomerCancels_thenOkAnnullatoAndStockRestored() throws Exception {
        OrderResponse order = newOrder(alice, laptop, 3);
        assertThat(stockOf(laptop)).isEqualTo(2);

        mockMvc.perform(post("/api/v1/orders/{id}/cancel", order.id()).with(bearer("alice")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stato").value("ANNULLATO"));

        assertThat(stockOf(laptop)).isEqualTo(5);
        assertThat(orderRepository.findById(order.id()).orElseThrow().getStato()).isEqualTo(OrderStatus.ANNULLATO);
    }

    @Test
    void givenCancelledOrder_whenCancelAgain_thenConflictAndStockRestoredOnce() throws Exception {
        OrderResponse order = newOrder(alice, laptop, 3);
        mockMvc.perform(post("/api/v1/orders/{id}/cancel", order.id()).with(bearer("alice")))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/orders/{id}/cancel", order.id()).with(bearer("alice")))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/v1/orders/{id}/deliver", order.id()).with(bearer("admin")))
                .andExpect(status().isConflict());

        assertThat(stockOf(laptop)).isEqualTo(5);
        mockMvc.perform(get("/api/v1/orders/{id}", order.id()).with(bearer("alice")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stato").value("ANNULLATO"));
    }

    @Test
    void givenAnotherCustomersOrder_whenCustomerCancels_thenForbiddenAndOrderKept() throws Exception {
        OrderResponse order = newOrder(alice, laptop, 1);

        mockMvc.perform(post("/api/v1/orders/{id}/cancel", order.id()).with(bearer("bob")))
                .andExpect(status().isForbidden());

        assertThat(orderRepository.existsById(order.id())).isTrue();
        assertThat(stockOf(laptop)).isEqualTo(4);
    }

    @Test
    void givenAnyOrder_whenAdminCancels_thenOk() throws Exception {
        OrderResponse order = newOrder(alice, laptop, 1);

        mockMvc.perform(post("/api/v1/orders/{id}/cancel", order.id()).with(bearer("admin")))
                .andExpect(status().isOk());

        assertThat(stockOf(laptop)).isEqualTo(5);
    }

    @Test
    void givenDeliveredOrder_whenCancel_thenConflictAndStockUnchanged() throws Exception {
        OrderResponse order = newOrder(alice, laptop, 2);
        orderService.deliver(it.example.ecommerce.service.Requester.admin(), order.id());

        mockMvc.perform(post("/api/v1/orders/{id}/cancel", order.id()).with(bearer("alice")))
                .andExpect(status().isConflict());

        assertThat(orderRepository.existsById(order.id())).isTrue();
        assertThat(stockOf(laptop)).isEqualTo(3);
    }

    private ResultActions postOrder(String body, String user) throws Exception {
        return mockMvc.perform(post("/api/v1/orders")
                .with(bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private static String itemsBody(Product product, int quantity) {
        return "{\"items\": [{\"productId\": %d, \"quantita\": %d}]}".formatted(product.getId(), quantity);
    }
}
