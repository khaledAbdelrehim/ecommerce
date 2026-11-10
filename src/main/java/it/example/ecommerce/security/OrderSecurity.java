package it.example.ecommerce.security;

import it.example.ecommerce.repository.OrderRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/** Controlli di titolarità usati nelle espressioni {@code @PreAuthorize}. */
@Component("orderSecurity")
public class OrderSecurity {

    private final OrderRepository orderRepository;

    public OrderSecurity(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    /** Per i clienti restituisce false sia per ordini altrui sia per ordini inesistenti, così il 403 non rivela se un ID esiste. */
    public boolean isOwner(Long orderId, Authentication authentication) {
        if (!(authentication.getPrincipal() instanceof AuthenticatedUser user) || user.getCustomerId() == null) {
            return false;
        }
        return orderRepository.existsByIdAndCustomerId(orderId, user.getCustomerId());
    }
}
