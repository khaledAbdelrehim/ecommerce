package it.example.ecommerce.service.impl;

import it.example.ecommerce.domain.Customer;
import it.example.ecommerce.dto.CustomerRequest;
import it.example.ecommerce.dto.CustomerResponse;
import it.example.ecommerce.exception.DuplicateResourceException;
import it.example.ecommerce.exception.ResourceNotFoundException;
import it.example.ecommerce.mapper.CustomerMapper;
import it.example.ecommerce.repository.CustomerRepository;
import it.example.ecommerce.service.CustomerService;
import it.example.ecommerce.service.PageableRules;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Slf4j
@Service
@Transactional(readOnly = true)
public class CustomerServiceImpl implements CustomerService {

    private final CustomerRepository customerRepository;
    private final CustomerMapper customerMapper;

    public CustomerServiceImpl(CustomerRepository customerRepository, CustomerMapper customerMapper) {
        this.customerRepository = customerRepository;
        this.customerMapper = customerMapper;
    }

    @Override
    @Transactional
    public CustomerResponse create(CustomerRequest request) {
        // the Customer constructor normalizes codice fiscale and email: single source of truth
        Customer customer = customerMapper.toEntity(request);
        if (customerRepository.existsByCodiceFiscale(customer.getCodiceFiscale())) {
            throw new DuplicateResourceException(
                    "Customer with codice fiscale " + customer.getCodiceFiscale() + " already exists");
        }
        if (customerRepository.existsByEmail(customer.getEmail())) {
            throw new DuplicateResourceException("Customer with email " + customer.getEmail() + " already exists");
        }
        Customer saved = customerRepository.save(customer);
        log.info("Customer created (id={})", saved.getId());
        return customerMapper.toResponse(saved);
    }

    @Override
    public CustomerResponse findById(Long id) {
        return customerRepository.findById(id)
                .map(customerMapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", id));
    }

    private static final Set<String> SORTABLE =
            Set.of("id", "nome", "cognome", "dataNascita", "codiceFiscale", "email");

    @Override
    public Page<CustomerResponse> findAll(Pageable pageable) {
        return customerRepository.findAll(PageableRules.restrict(pageable, SORTABLE))
                .map(customerMapper::toResponse);
    }

}
