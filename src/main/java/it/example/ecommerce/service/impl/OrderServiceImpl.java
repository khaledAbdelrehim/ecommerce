package it.example.ecommerce.service.impl;

import it.example.ecommerce.domain.Customer;
import it.example.ecommerce.domain.Order;
import it.example.ecommerce.domain.OrderFactory;
import it.example.ecommerce.domain.OrderItem;
import it.example.ecommerce.domain.Product;
import it.example.ecommerce.dto.CreateOrderRequest;
import it.example.ecommerce.dto.OrderItemRequest;
import it.example.ecommerce.dto.OrderResponse;
import it.example.ecommerce.exception.InsufficientStockException;
import it.example.ecommerce.exception.InvalidRequestException;
import it.example.ecommerce.exception.ResourceNotFoundException;
import it.example.ecommerce.mapper.OrderMapper;
import it.example.ecommerce.repository.CustomerRepository;
import it.example.ecommerce.repository.OrderRepository;
import it.example.ecommerce.repository.ProductRepository;
import it.example.ecommerce.service.OrderService;
import it.example.ecommerce.service.PageableRules;
import it.example.ecommerce.service.Requester;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import it.example.ecommerce.exception.ForbiddenOperationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Facciata per repository, fabbrica degli ordini e mapper: i controller interagiscono solo con questo servizio.
 * Ogni operazione di scrittura avviene in un'unica transazione, così un errore annulla tutte le modifiche.
 */
@Slf4j
@Service
@Transactional(readOnly = true)
public class OrderServiceImpl implements OrderService {

    private static final Set<String> SORTABLE = Set.of("id", "stato", "dataCreazione");

    private final OrderRepository orderRepository;
    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;
    private final OrderFactory orderFactory;
    private final OrderMapper orderMapper;

    public OrderServiceImpl(OrderRepository orderRepository, CustomerRepository customerRepository,
                            ProductRepository productRepository, OrderFactory orderFactory,
                            OrderMapper orderMapper) {
        this.orderRepository = orderRepository;
        this.customerRepository = customerRepository;
        this.productRepository = productRepository;
        this.orderFactory = orderFactory;
        this.orderMapper = orderMapper;
    }

    @Override
    @Transactional
    public OrderResponse create(Requester requester, CreateOrderRequest request) {
        Long customerId = resolveCustomerId(requester, request);
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", customerId));

        Map<Long, Integer> quantities = mergeQuantities(request.items());
        quantities.forEach(this::reserveStock);
        Map<Long, Product> products = productRepository.findAllById(quantities.keySet()).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        Order order = orderFactory.create(customer, quantities, products);
        Order saved = orderRepository.save(order);
        log.info("Order {} created for customer {} ({} products)", saved.getId(), customerId, quantities.size());
        return orderMapper.toResponse(saved);
    }

    @Override
    public OrderResponse findById(Requester requester, Long id) {
        Order order = requester.isAdmin() ? orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order", id))
                : authorizeCustomer(requester, orderRepository.findById(id));
        return orderMapper.toResponse(order);
    }

    @Override
    public Page<OrderResponse> findAll(Requester requester, Pageable pageable) {
        Pageable safe = PageableRules.restrict(pageable, SORTABLE);
        if (requester.isAdmin()) {
            return orderRepository.findAll(safe).map(orderMapper::toResponse);
        }
        if (requester.customerId() == null) {
            throw new ForbiddenOperationException("User is not linked to a customer");
        }
        return orderRepository.findByCustomerId(requester.customerId(), safe).map(orderMapper::toResponse);
    }

    @Override
    @Transactional
    public OrderResponse deliver(Requester requester, Long id) {
        if (!requester.isAdmin()) {
            throw new ForbiddenOperationException("Only administrators can deliver orders");
        }
        Order order = loadForUpdate(requester, id);
        order.deliver();
        log.info("Order {} delivered", id);
        return orderMapper.toResponse(order);
    }

    @Override
    @Transactional
    public OrderResponse cancel(Requester requester, Long id) {
        Order order = loadForUpdate(requester, id);
        order.cancel();
        // mapped before the bulk updates, which clear the persistence context
        OrderResponse response = orderMapper.toResponse(order);

        Map<Long, Integer> quantities = new TreeMap<>();
        for (OrderItem item : order.getRighe()) {
            quantities.merge(item.getProduct().getId(), item.getQuantita(), OrderServiceImpl::sumQuantities);
        }
        quantities.forEach(this::releaseStock);
        log.info("Order {} cancelled, stock restored for {} products", id, quantities.size());
        return response;
    }

    /**
     * I clienti possono intervenire solo sui propri ordini. Un ordine inesistente viene segnalato come uno altrui (403),
     * impedendo di enumerare gli ID degli ordini; per gli amministratori viene restituito il normale 404.
     */
    private static Order authorizeCustomer(Requester requester, Optional<Order> order) {
        Long ownerId = order.map(o -> o.getCustomer().getId()).orElse(null);
        if (requester.customerId() == null || !requester.customerId().equals(ownerId)) {
            throw new ForbiddenOperationException("Access to this order is not allowed");
        }
        return order.get();
    }

    private Order loadForUpdate(Requester requester, Long id) {
        Optional<Order> order = orderRepository.findByIdForUpdate(id);
        if (requester.isAdmin()) {
            return order.orElseThrow(() -> new ResourceNotFoundException("Order", id));
        }
        return authorizeCustomer(requester, order);
    }

    /** Regola aziendale: chi può effettuare un ordine per quale cliente. */
    private static Long resolveCustomerId(Requester requester, CreateOrderRequest request) {
        if (requester.isAdmin()) {
            if (request.customerId() == null) {
                throw new InvalidRequestException("customerId is required when creating an order as ADMIN");
            }
            return request.customerId();
        }
        if (requester.customerId() == null) {
            throw new ForbiddenOperationException("User is not linked to a customer");
        }
        if (request.customerId() != null && !request.customerId().equals(requester.customerId())) {
            throw new ForbiddenOperationException("Customers can only create orders for themselves");
        }
        return requester.customerId();
    }

    /** Viene invocato in ordine crescente di ID (TreeMap); un errore annulla l'intera transazione. */
    private void reserveStock(Long productId, int quantity) {
        if (productRepository.decreaseStockIfAvailable(productId, quantity) == 0) {
            Product product = productRepository.findById(productId)
                    .orElseThrow(() -> new ResourceNotFoundException("Product", productId));
            throw new InsufficientStockException(product.getCodice(), quantity, product.getStock());
        }
    }

    private void releaseStock(Long productId, int quantity) {
        if (productRepository.increaseStockIfRoom(productId, quantity) == 0) {
            throw new InvalidRequestException("Stock would exceed the maximum allowed value");
        }
    }

    /** Accorpa le righe duplicate dello stesso prodotto; TreeMap mantiene gli ID ordinati. */
    private static Map<Long, Integer> mergeQuantities(List<OrderItemRequest> items) {
        Map<Long, Integer> quantities = new TreeMap<>();
        items.forEach(item -> quantities.merge(item.productId(), item.quantita(), OrderServiceImpl::sumQuantities));
        return quantities;
    }

    private static int sumQuantities(int a, int b) {
        try {
            return Math.addExact(a, b);
        } catch (ArithmeticException e) {
            throw new InvalidRequestException("Total quantity for a product exceeds the maximum allowed value");
        }
    }
}
