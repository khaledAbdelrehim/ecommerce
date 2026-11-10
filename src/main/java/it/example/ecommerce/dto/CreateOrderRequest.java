package it.example.ecommerce.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * @param customerId obbligatorio per ADMIN; ignorato per gli utenti CUSTOMER (oppure deve corrispondere al cliente associato)
 */
public record CreateOrderRequest(
        Long customerId,
        @NotEmpty @Size(max = CreateOrderRequest.MAX_ITEMS) List<@NotNull @Valid OrderItemRequest> items) {

    public static final int MAX_ITEMS = 100;
}
