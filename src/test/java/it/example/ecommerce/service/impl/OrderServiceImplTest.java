package it.example.ecommerce.service.impl;

import it.example.ecommerce.domain.Customer;
import it.example.ecommerce.domain.Order;
import it.example.ecommerce.domain.OrderFactory;
import it.example.ecommerce.domain.OrderStatus;
import it.example.ecommerce.domain.Product;
import it.example.ecommerce.dto.CreateOrderRequest;
import it.example.ecommerce.dto.OrderItemRequest;
import it.example.ecommerce.dto.OrderResponse;
import it.example.ecommerce.exception.InsufficientStockException;
import it.example.ecommerce.exception.InvalidOrderStateException;
import it.example.ecommerce.exception.InvalidRequestException;
import it.example.ecommerce.exception.ResourceNotFoundException;
import it.example.ecommerce.mapper.OrderMapperImpl;
import it.example.ecommerce.repository.CustomerRepository;
import it.example.ecommerce.repository.OrderRepository;
import it.example.ecommerce.repository.ProductRepository;
import it.example.ecommerce.service.Requester;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import it.example.ecommerce.exception.ForbiddenOperationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private CustomerRepository customerRepository;
    @Mock
    private ProductRepository productRepository;

    private OrderFactory orderFactory;
    private OrderServiceImpl service;
    private Customer customer;
    private Product laptop;
    private Product mouse;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2025-01-01T10:00:00Z"), ZoneOffset.UTC);
        orderFactory = new OrderFactory(clock);
        service = new OrderServiceImpl(orderRepository, customerRepository, productRepository,
                orderFactory, new OrderMapperImpl());

        customer = Customer.builder().nome("Mario").cognome("Rossi").build();
        ReflectionTestUtils.setField(customer, "id", 1L);
        laptop = product(10L, "LAP", 5);
        mouse = product(20L, "MOU", 3);
    }

    @Test
    void givenEnoughStock_whenCreate_thenStockDecrementedInIdOrderAndOrderSaved() {
        stubReservationSuccess();
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderResponse response = service.create(Requester.customer(1L), request(item(20L, 1), item(10L, 2)));

        InOrder inOrder = inOrder(productRepository);
        inOrder.verify(productRepository).decreaseStockIfAvailable(10L, 2);
        inOrder.verify(productRepository).decreaseStockIfAvailable(20L, 1);
        assertThat(response.stato()).isEqualTo(OrderStatus.ORDINATO);
        assertThat(response.customerId()).isEqualTo(1L);
        assertThat(response.righe()).hasSize(2);
    }

    @Test
    void givenDuplicatedProductLines_whenCreate_thenQuantitiesAreMerged() {
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer));
        when(productRepository.decreaseStockIfAvailable(10L, 3)).thenReturn(1);
        when(productRepository.findAllById(anyIterable())).thenReturn(List.of(laptop));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderResponse response = service.create(Requester.customer(1L), request(item(10L, 2), item(10L, 1)));

        verify(productRepository).decreaseStockIfAvailable(10L, 3);
        assertThat(response.righe()).singleElement().satisfies(r -> assertThat(r.quantita()).isEqualTo(3));
    }

    @Test
    void givenInsufficientStockOnOneLine_whenCreate_thenRejectedAndNoOrderSaved() {
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer));
        when(productRepository.decreaseStockIfAvailable(10L, 2)).thenReturn(1);
        when(productRepository.decreaseStockIfAvailable(20L, 4)).thenReturn(0);
        when(productRepository.findById(20L)).thenReturn(Optional.of(mouse));

        assertThatThrownBy(() -> service.create(Requester.customer(1L), request(item(10L, 2), item(20L, 4))))
                .isInstanceOf(InsufficientStockException.class)
                .hasMessageContaining("MOU");

        // the transaction rolls back the decrement of the first line: see OrderControllerIT
        verify(orderRepository, never()).save(any());
    }

    @Test
    void givenDuplicatedLinesOverflowingInteger_whenCreate_thenRejectedWithoutChanges() {
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer));

        assertThatThrownBy(() -> service.create(Requester.customer(1L),
                request(item(10L, Integer.MAX_VALUE), item(10L, 2))))
                .isInstanceOf(InvalidRequestException.class);

        verify(productRepository, never()).decreaseStockIfAvailable(anyLong(), anyInt());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void givenUnknownCustomer_whenCreate_thenResourceNotFound() {
        when(customerRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(Requester.customer(99L), request(item(10L, 1))))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(productRepository, never()).decreaseStockIfAvailable(anyLong(), anyInt());
    }

    @Test
    void givenUnknownProduct_whenCreate_thenResourceNotFound() {
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer));
        when(productRepository.decreaseStockIfAvailable(10L, 1)).thenReturn(1);
        when(productRepository.decreaseStockIfAvailable(77L, 1)).thenReturn(0);
        when(productRepository.findById(77L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(Requester.customer(1L), request(item(10L, 1), item(77L, 1))))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("77");

        verify(orderRepository, never()).save(any());
    }

    @Test
    void givenAdminWithoutCustomerId_whenCreate_thenInvalidRequest() {
        assertThatThrownBy(() -> service.create(Requester.admin(), request(item(10L, 1))))
                .isInstanceOf(InvalidRequestException.class);

        verify(productRepository, never()).decreaseStockIfAvailable(anyLong(), anyInt());
    }

    @Test
    void givenAdminWithCustomerId_whenCreate_thenOrderCreatedForThatCustomer() {
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer));
        when(productRepository.decreaseStockIfAvailable(10L, 1)).thenReturn(1);
        when(productRepository.findAllById(anyIterable())).thenReturn(List.of(laptop));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderResponse response = service.create(Requester.admin(), new CreateOrderRequest(1L, List.of(item(10L, 1))));

        assertThat(response.customerId()).isEqualTo(1L);
    }

    @Test
    void givenCustomerOrderingForSomeoneElse_whenCreate_thenAccessDeniedAndStockUntouched() {
        assertThatThrownBy(() -> service.create(Requester.customer(1L),
                new CreateOrderRequest(2L, List.of(item(10L, 1)))))
                .isInstanceOf(ForbiddenOperationException.class);

        verify(productRepository, never()).decreaseStockIfAvailable(anyLong(), anyInt());
    }

    @Test
    void givenCustomerWithoutLinkedCustomer_whenCreate_thenAccessDenied() {
        assertThatThrownBy(() -> service.create(Requester.customer(null), request(item(10L, 1))))
                .isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    void givenAdmin_whenFindAll_thenAllOrdersReturned() {
        Pageable pageable = PageRequest.of(0, 10);
        Pageable stable = PageRequest.of(0, 10, org.springframework.data.domain.Sort.by("id"));
        when(orderRepository.findAll(stable)).thenReturn(Page.empty());

        service.findAll(Requester.admin(), pageable);

        verify(orderRepository).findAll(stable);
        verify(orderRepository, never()).findByCustomerId(anyLong(), any());
    }

    @Test
    void givenCustomer_whenFindAll_thenOnlyOwnOrdersQueried() {
        Pageable pageable = PageRequest.of(0, 10);
        Pageable stable = PageRequest.of(0, 10, org.springframework.data.domain.Sort.by("id"));
        when(orderRepository.findByCustomerId(1L, stable)).thenReturn(Page.empty());

        service.findAll(Requester.customer(1L), pageable);

        verify(orderRepository).findByCustomerId(1L, stable);
        verify(orderRepository, never()).findAll(any(Pageable.class));
    }

    @Test
    void givenCustomerWithoutLinkedCustomer_whenFindAll_thenForbidden() {
        assertThatThrownBy(() -> service.findAll(Requester.customer(null), PageRequest.of(0, 10)))
                .isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    void givenOrderedOrder_whenDeliver_thenStatusBecomesConsegnato() {
        Order order = existingOrder(OrderStatus.ORDINATO, laptop, 2);
        when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));

        OrderResponse response = service.deliver(Requester.admin(), 1L);

        assertThat(response.stato()).isEqualTo(OrderStatus.CONSEGNATO);
    }

    @Test
    void givenDeliveredOrder_whenDeliverAgain_thenInvalidState() {
        Order order = existingOrder(OrderStatus.CONSEGNATO, laptop, 2);
        when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.deliver(Requester.admin(), 1L)).isInstanceOf(InvalidOrderStateException.class);
    }

    @Test
    void givenUnknownOrder_whenDeliver_thenResourceNotFound() {
        when(orderRepository.findByIdForUpdate(5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deliver(Requester.admin(), 5L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void givenOrderedOrder_whenCancel_thenStockRestoredAndOrderKeptAsAnnullato() {
        Order order = existingOrder(OrderStatus.ORDINATO, laptop, 2);
        when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));
        when(productRepository.increaseStockIfRoom(10L, 2)).thenReturn(1);

        OrderResponse response = service.cancel(Requester.admin(), 1L);

        verify(productRepository).increaseStockIfRoom(10L, 2);
        assertThat(response.stato()).isEqualTo(OrderStatus.ANNULLATO);
        assertThat(order.getStato()).isEqualTo(OrderStatus.ANNULLATO);
        verify(orderRepository, never()).delete(any());
    }

    @Test
    void givenStockAtMaximum_whenCancel_thenInvalidRequest() {
        Order order = existingOrder(OrderStatus.ORDINATO, laptop, 2);
        when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));
        when(productRepository.increaseStockIfRoom(10L, 2)).thenReturn(0);

        assertThatThrownBy(() -> service.cancel(Requester.admin(), 1L)).isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void givenCancelledOrder_whenCancelAgain_thenInvalidStateAndStockNotRestoredTwice() {
        Order order = existingOrder(OrderStatus.ANNULLATO, laptop, 2);
        when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.cancel(Requester.admin(), 1L)).isInstanceOf(InvalidOrderStateException.class);

        verify(productRepository, never()).increaseStockIfRoom(anyLong(), anyInt());
    }

    @Test
    void givenCancelledOrder_whenDeliver_thenInvalidState() {
        Order order = existingOrder(OrderStatus.ANNULLATO, laptop, 2);
        when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.deliver(Requester.admin(), 1L)).isInstanceOf(InvalidOrderStateException.class);
    }

    @Test
    void givenDeliveredOrder_whenCancel_thenInvalidStateAndStockUntouched() {
        Order order = existingOrder(OrderStatus.CONSEGNATO, laptop, 2);
        when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.cancel(Requester.admin(), 1L)).isInstanceOf(InvalidOrderStateException.class);

        verify(orderRepository, never()).delete(any());
        verify(productRepository, never()).increaseStockIfRoom(anyLong(), anyInt());
    }

    @Test
    void givenUnknownOrder_whenFindById_thenResourceNotFound() {
        when(orderRepository.findById(5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->         service.findById(Requester.admin(), 5L)).isInstanceOf(ResourceNotFoundException.class);
    }

            @Test
            void givenForeignOrder_whenCustomerFindsIt_thenForbidden() {
                Order order = existingOrder(OrderStatus.ORDINATO, laptop, 2);
                when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

                assertThatThrownBy(() -> service.findById(Requester.customer(999L), 1L))
                        .isInstanceOf(ForbiddenOperationException.class);
            }

            @Test
            void givenUnknownOrder_whenCustomerFindsIt_thenForbiddenLikeForeignOnes() {
                when(orderRepository.findById(5L)).thenReturn(Optional.empty());

                assertThatThrownBy(() -> service.findById(Requester.customer(1L), 5L))
                        .isInstanceOf(ForbiddenOperationException.class);
            }

            @Test
            void givenForeignOrder_whenCustomerCancels_thenForbiddenAndStockUntouched() {
                Order order = existingOrder(OrderStatus.ORDINATO, laptop, 2);
                when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));

                assertThatThrownBy(() -> service.cancel(Requester.customer(999L), 1L))
                        .isInstanceOf(ForbiddenOperationException.class);

                verify(productRepository, never()).increaseStockIfRoom(anyLong(), anyInt());
            }

            @Test
            void givenCustomer_whenDeliver_thenForbidden() {
                assertThatThrownBy(() -> service.deliver(Requester.customer(1L), 1L))
                        .isInstanceOf(ForbiddenOperationException.class);
            }

    private void stubReservationSuccess() {
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer));
        when(productRepository.decreaseStockIfAvailable(anyLong(), anyInt())).thenReturn(1);
        when(productRepository.findAllById(anyIterable())).thenReturn(List.of(laptop, mouse));
    }

    /** Un ordine già salvato per il prodotto indicato. */
    private Order existingOrder(OrderStatus status, Product product, int quantity) {
        Order order = orderFactory.create(customer, Map.of(product.getId(), quantity),
                Map.of(product.getId(), product));
        ReflectionTestUtils.setField(order, "id", 1L);
        ReflectionTestUtils.setField(order, "stato", status);
        return order;
    }

    private static Product product(Long id, String code, int stock) {
        Product p = new Product(code, "name " + code, stock);
        ReflectionTestUtils.setField(p, "id", id);
        return p;
    }

    private static OrderItemRequest item(Long productId, int quantity) {
        return new OrderItemRequest(productId, quantity);
    }

    private static CreateOrderRequest request(OrderItemRequest... items) {
        return new CreateOrderRequest(null, List.of(items));
    }
}
