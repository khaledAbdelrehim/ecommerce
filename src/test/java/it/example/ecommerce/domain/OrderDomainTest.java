package it.example.ecommerce.domain;

import it.example.ecommerce.exception.InsufficientStockException;
import it.example.ecommerce.exception.InvalidOrderStateException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderDomainTest {

    private final OrderFactory factory =
            new OrderFactory(Clock.fixed(Instant.parse("2025-06-01T08:00:00Z"), ZoneOffset.UTC));

    @Test
    void givenOrdinato_whenDeliver_thenConsegnato() {
        assertThat(OrderStatus.ORDINATO.deliver()).isEqualTo(OrderStatus.CONSEGNATO);
    }

    @Test
    void givenConsegnato_whenDeliver_thenInvalidState() {
        assertThatThrownBy(OrderStatus.CONSEGNATO::deliver).isInstanceOf(InvalidOrderStateException.class);
    }

    @Test
    void givenOrdinato_whenCancel_thenAnnullato() {
        assertThat(OrderStatus.ORDINATO.cancel()).isEqualTo(OrderStatus.ANNULLATO);
    }

    @Test
    void givenTerminalStates_whenCancelOrDeliver_thenInvalidState() {
        assertThatThrownBy(OrderStatus.CONSEGNATO::cancel).isInstanceOf(InvalidOrderStateException.class);
        assertThatThrownBy(OrderStatus.ANNULLATO::cancel).isInstanceOf(InvalidOrderStateException.class);
        assertThatThrownBy(OrderStatus.ANNULLATO::deliver).isInstanceOf(InvalidOrderStateException.class);
    }

    @Test
    void givenProducts_whenFactoryCreates_thenOrderIsOrdinatoWithItems() {
        Product p = new Product("P", "Prod", 4);
        Map<Long, Product> products = Map.of(1L, p);

        Order order = factory.create(null, Map.of(1L, 3), products);

        assertThat(order.getStato()).isEqualTo(OrderStatus.ORDINATO);
        assertThat(order.getDataCreazione()).isEqualTo(Instant.parse("2025-06-01T08:00:00Z"));
        assertThat(order.getRighe()).singleElement().satisfies(r -> assertThat(r.getQuantita()).isEqualTo(3));
    }

    @Test
    void givenProduct_whenDecreaseBelowZero_thenInsufficientStock() {
        Product p = new Product("P", "Prod", 1);

        assertThatThrownBy(() -> p.decreaseStock(2)).isInstanceOf(InsufficientStockException.class);
        assertThat(p.getStock()).isEqualTo(1);
    }

    @Test
    void givenProduct_whenIncreaseStock_thenStockGrows() {
        Product p = new Product("P", "Prod", 1);

        p.increaseStock(4);

        assertThat(p.getStock()).isEqualTo(5);
    }

    @Test
    void givenNonPositiveQuantity_whenChangeStock_thenIllegalArgument() {
        Product p = new Product("P", "Prod", 1);

        assertThatThrownBy(() -> p.decreaseStock(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> p.increaseStock(-1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void givenNegativeInitialStock_whenCreateProduct_thenIllegalArgument() {
        assertThatThrownBy(() -> new Product("P", "Prod", -1)).isInstanceOf(IllegalArgumentException.class);
    }
}
