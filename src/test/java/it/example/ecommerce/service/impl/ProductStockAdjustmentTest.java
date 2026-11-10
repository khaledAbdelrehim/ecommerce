package it.example.ecommerce.service.impl;

import it.example.ecommerce.domain.Product;
import it.example.ecommerce.exception.InsufficientStockException;
import it.example.ecommerce.exception.InvalidRequestException;
import it.example.ecommerce.exception.ResourceNotFoundException;
import it.example.ecommerce.mapper.ProductMapperImpl;
import it.example.ecommerce.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductStockAdjustmentTest {

    @Mock
    private ProductRepository productRepository;

    private ProductServiceImpl service() {
        return new ProductServiceImpl(productRepository, new ProductMapperImpl());
    }

    @Test
    void givenPositiveDelta_whenAdjustStock_thenStockIncreasedUsingLockingQuery() {
        when(productRepository.findAllByIdForUpdate(List.of(1L))).thenReturn(List.of(new Product("P-1", "Phone", 5)));

        assertThat(service().adjustStock(1L, 3).stock()).isEqualTo(8);
    }

    @Test
    void givenNegativeDelta_whenAdjustStock_thenStockDecreased() {
        when(productRepository.findAllByIdForUpdate(List.of(1L))).thenReturn(List.of(new Product("P-1", "Phone", 5)));

        assertThat(service().adjustStock(1L, -5).stock()).isZero();
    }

    @Test
    void givenDeltaExceedingStock_whenAdjustStock_thenInsufficientStockAndStockKept() {
        Product product = new Product("P-1", "Phone", 2);
        when(productRepository.findAllByIdForUpdate(List.of(1L))).thenReturn(List.of(product));

        assertThatThrownBy(() -> service().adjustStock(1L, -3)).isInstanceOf(InsufficientStockException.class);

        assertThat(product.getStock()).isEqualTo(2);
    }

    @Test
    void givenIncreaseOverflowingInteger_whenAdjustStock_thenInvalidRequest() {
        Product product = new Product("P-1", "Phone", Integer.MAX_VALUE);
        when(productRepository.findAllByIdForUpdate(List.of(1L))).thenReturn(List.of(product));

        assertThatThrownBy(() -> service().adjustStock(1L, 1)).isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void givenZeroDelta_whenAdjustStock_thenInvalidRequest() {
        when(productRepository.findAllByIdForUpdate(List.of(1L))).thenReturn(List.of(new Product("P-1", "Phone", 2)));

        assertThatThrownBy(() -> service().adjustStock(1L, 0)).isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void givenUnknownProduct_whenAdjustStock_thenNotFound() {
        when(productRepository.findAllByIdForUpdate(List.of(9L))).thenReturn(List.of());

        assertThatThrownBy(() -> service().adjustStock(9L, 1)).isInstanceOf(ResourceNotFoundException.class);
    }
}
