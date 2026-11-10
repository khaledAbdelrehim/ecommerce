package it.example.ecommerce.domain;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;

/**
 * Fabbrica per {@link Order}: crea un aggregato coerente per il quale le scorte sono già state prenotate.
 * È una classe semplice, senza annotazioni del framework, registrata come bean nella configurazione.
 */
public class OrderFactory {

    private final Clock clock;

    public OrderFactory(Clock clock) {
        this.clock = clock;
    }

    /**
     * @param quantities quantità richiesta per ogni ID prodotto (già accorpata e sempre maggiore di zero)
     * @param products   prodotti indicizzati per ID; deve contenere tutti gli ID richiesti
     */
    public Order create(Customer customer, Map<Long, Integer> quantities, Map<Long, Product> products) {
        Order order = new Order(customer, Instant.now(clock));
        quantities.forEach((productId, quantity) -> order.addItem(products.get(productId), quantity));
        return order;
    }
}
