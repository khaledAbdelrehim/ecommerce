package it.example.ecommerce.service.impl;

import it.example.ecommerce.domain.Customer;
import it.example.ecommerce.domain.Product;
import it.example.ecommerce.dto.CustomerRequest;
import it.example.ecommerce.dto.ProductRequest;
import it.example.ecommerce.exception.DuplicateResourceException;
import it.example.ecommerce.exception.ResourceNotFoundException;
import it.example.ecommerce.mapper.CustomerMapperImpl;
import it.example.ecommerce.mapper.ProductMapperImpl;
import it.example.ecommerce.repository.CustomerRepository;
import it.example.ecommerce.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CatalogServicesTest {

    @Mock
    private CustomerRepository customerRepository;
    @Mock
    private ProductRepository productRepository;

    private static final CustomerRequest CUSTOMER =
            new CustomerRequest("Anna", "Blu", LocalDate.of(1990, 1, 1), "blunna90a41h501a", "anna@example.com");

    @Test
    void givenNewCustomer_whenCreate_thenCodiceFiscaleStoredUppercase() {
        CustomerServiceImpl service = new CustomerServiceImpl(customerRepository, new CustomerMapperImpl());
        when(customerRepository.save(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));

        service.create(CUSTOMER);

        ArgumentCaptor<Customer> captor = ArgumentCaptor.forClass(Customer.class);
        verify(customerRepository).save(captor.capture());
        assertThat(captor.getValue().getCodiceFiscale()).isEqualTo("BLUNNA90A41H501A");
    }

    @Test
    void givenDuplicatedCodiceFiscale_whenCreateCustomer_thenDuplicateResource() {
        CustomerServiceImpl service = new CustomerServiceImpl(customerRepository, new CustomerMapperImpl());
        when(customerRepository.existsByCodiceFiscale("BLUNNA90A41H501A")).thenReturn(true);

        assertThatThrownBy(() -> service.create(CUSTOMER)).isInstanceOf(DuplicateResourceException.class);

        verify(customerRepository, never()).save(any());
    }

    @Test
    void givenDuplicatedEmail_whenCreateCustomer_thenDuplicateResource() {
        CustomerServiceImpl service = new CustomerServiceImpl(customerRepository, new CustomerMapperImpl());
        when(customerRepository.existsByEmail("anna@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service.create(CUSTOMER)).isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void givenMixedCaseEmail_whenCreateCustomer_thenCheckedAndStoredLowerCase() {
        CustomerServiceImpl service = new CustomerServiceImpl(customerRepository, new CustomerMapperImpl());
        when(customerRepository.save(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));
        CustomerRequest request = new CustomerRequest(CUSTOMER.nome(), CUSTOMER.cognome(), CUSTOMER.dataNascita(),
                CUSTOMER.codiceFiscale(), "  Anna@Example.COM ");

        service.create(request);

        verify(customerRepository).existsByEmail("anna@example.com");
        ArgumentCaptor<Customer> captor = ArgumentCaptor.forClass(Customer.class);
        verify(customerRepository).save(captor.capture());
        assertThat(captor.getValue().getEmail()).isEqualTo("anna@example.com");
    }

    @Test
    void givenUnknownCustomer_whenFindById_thenNotFound() {
        CustomerServiceImpl service = new CustomerServiceImpl(customerRepository, new CustomerMapperImpl());
        when(customerRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(1L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void givenDuplicatedCode_whenCreateProduct_thenDuplicateResource() {
        ProductServiceImpl service = new ProductServiceImpl(productRepository, new ProductMapperImpl());
        when(productRepository.existsByCodice("P-1")).thenReturn(true);

        assertThatThrownBy(() -> service.create(new ProductRequest("P-1", "Prod", 3)))
                .isInstanceOf(DuplicateResourceException.class);

        verify(productRepository, never()).save(any());
    }

    @Test
    void givenNewProduct_whenCreate_thenSavedWithRequestedStock() {
        ProductServiceImpl service = new ProductServiceImpl(productRepository, new ProductMapperImpl());
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        var response = service.create(new ProductRequest("P-1", "Prod", 3));

        assertThat(response.stock()).isEqualTo(3);
        assertThat(response.codice()).isEqualTo("P-1");
    }
}
