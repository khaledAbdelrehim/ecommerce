package it.example.ecommerce.service;

import it.example.ecommerce.domain.Product;
import it.example.ecommerce.dto.CreateOrderRequest;
import it.example.ecommerce.dto.OrderItemRequest;
import it.example.ecommerce.exception.InsufficientStockException;
import it.example.ecommerce.service.Requester;
import it.example.ecommerce.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class OrderConcurrencyIT extends IntegrationTestBase {

    private static final int THREADS = 20;

    @Test
    void givenLimitedStock_whenManyThreadsOrderConcurrently_thenExactlyAvailableOrdersSucceed() throws Exception {
        Product product = newProduct("HOT", 5);

        Outcome outcome = runConcurrently(THREADS, i -> List.of(new OrderItemRequest(product.getId(), 1)));

        assertThat(outcome.successes()).isEqualTo(5);
        assertThat(outcome.insufficientStock()).isEqualTo(THREADS - 5);
        assertThat(outcome.unexpectedErrors()).isZero();
        assertThat(stockOf(product)).isZero();
        assertThat(orderRepository.count()).isEqualTo(5);
    }

    @Test
    void givenLastItemInStock_whenManyThreadsOrderIt_thenOnlyOneSucceeds() throws Exception {
        Product product = newProduct("LAST", 1);

        Outcome outcome = runConcurrently(THREADS, i -> List.of(new OrderItemRequest(product.getId(), 1)));

        assertThat(outcome.successes()).isEqualTo(1);
        assertThat(outcome.unexpectedErrors()).isZero();
        assertThat(stockOf(product)).isZero();
    }

    @Test
    void givenOrdersListingProductsInOppositeOrder_whenConcurrent_thenNoDeadlockAndStockNeverNegative()
            throws Exception {
        Product a = newProduct("A", 10);
        Product b = newProduct("B", 10);

        Outcome outcome = runConcurrently(THREADS, i -> i % 2 == 0
                ? List.of(new OrderItemRequest(a.getId(), 1), new OrderItemRequest(b.getId(), 1))
                : List.of(new OrderItemRequest(b.getId(), 1), new OrderItemRequest(a.getId(), 1)));

        assertThat(outcome.successes()).isEqualTo(10);
        assertThat(outcome.unexpectedErrors()).isZero();
        assertThat(stockOf(a)).isZero();
        assertThat(stockOf(b)).isZero();
    }

    private Outcome runConcurrently(int threads, java.util.function.IntFunction<List<OrderItemRequest>> itemsFor)
            throws Exception {
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger insufficient = new AtomicInteger();
        AtomicInteger unexpected = new AtomicInteger();
        CountDownLatch startGate = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        try {
            List<Future<?>> futures = IntStream.range(0, threads).<Future<?>>mapToObj(i -> executor.submit(() -> {
                try {
                    startGate.await();
                    orderService.create(Requester.customer(alice.getId()),
                            new CreateOrderRequest(null, itemsFor.apply(i)));
                    successes.incrementAndGet();
                } catch (InsufficientStockException e) {
                    insufficient.incrementAndGet();
                } catch (Exception e) {
                    unexpected.incrementAndGet();
                }
            })).toList();
            startGate.countDown();
            for (Future<?> future : futures) {
                future.get(60, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }
        return new Outcome(successes.get(), insufficient.get(), unexpected.get());
    }

    private record Outcome(int successes, int insufficientStock, int unexpectedErrors) {
    }
}
