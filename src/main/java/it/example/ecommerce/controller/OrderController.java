package it.example.ecommerce.controller;

import it.example.ecommerce.dto.CreateOrderRequest;
import it.example.ecommerce.dto.OrderResponse;
import it.example.ecommerce.dto.PageResponse;
import it.example.ecommerce.security.AuthenticatedUser;
import it.example.ecommerce.service.OrderService;
import it.example.ecommerce.service.Requester;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private static final String ADMIN_OR_OWNER = "hasRole('ADMIN') or @orderSecurity.isOwner(#id, authentication)";

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> create(@Valid @RequestBody CreateOrderRequest request,
                                                @AuthenticationPrincipal AuthenticatedUser user) {
        OrderResponse created = orderService.create(requesterOf(user), request);
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(created.id()).toUri()).body(created);
    }

    /** La regola di visibilità (ADMIN: tutti, CUSTOMER: solo i propri) è applicata dal servizio. */
    @GetMapping
    public PageResponse<OrderResponse> list(Pageable pageable, @AuthenticationPrincipal AuthenticatedUser user) {
        return PageResponse.from(orderService.findAll(requesterOf(user), pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize(ADMIN_OR_OWNER)
    public OrderResponse get(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser user) {
        return orderService.findById(requesterOf(user), id);
    }

    @PostMapping("/{id}/deliver")
    @PreAuthorize("hasRole('ADMIN')")
    public OrderResponse deliver(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser user) {
        return orderService.deliver(requesterOf(user), id);
    }

    /** Transizione di stato (l'ordine viene conservato come ANNULLATO): si usa POST su una risorsa-azione, non DELETE. */
    @PostMapping("/{id}/cancel")
    @PreAuthorize(ADMIN_OR_OWNER)
    public OrderResponse cancel(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser user) {
        return orderService.cancel(requesterOf(user), id);
    }

    private static Requester requesterOf(AuthenticatedUser user) {
        return user.isAdmin() ? Requester.admin() : Requester.customer(user.getCustomerId());
    }
}
