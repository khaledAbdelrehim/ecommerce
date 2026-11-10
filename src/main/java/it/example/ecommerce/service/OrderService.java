package it.example.ecommerce.service;

import it.example.ecommerce.dto.CreateOrderRequest;
import it.example.ecommerce.dto.OrderResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface OrderService {

    /**
     * Crea un ordine e decrementa le scorte in modo atomico.
     * ADMIN deve indicare il cliente nella richiesta; CUSTOMER può ordinare solo per sé
     * (un customerId diverso viene rifiutato con {@code ForbiddenOperationException}).
     */
    OrderResponse create(Requester requester, CreateOrderRequest request);

    OrderResponse findById(Requester requester, Long id);

    /** ADMIN vede tutti gli ordini, CUSTOMER solo i propri. */
    Page<OrderResponse> findAll(Requester requester, Pageable pageable);

    /** ORDINATO -> CONSEGNATO; solo ADMIN. */
    OrderResponse deliver(Requester requester, Long id);

    /** ORDINATO -> ANNULLATO: l'ordine viene conservato nello storico e le scorte vengono ripristinate. ADMIN o il titolare. */
    OrderResponse cancel(Requester requester, Long id);
}
