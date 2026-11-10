package it.example.ecommerce.service;

import it.example.ecommerce.dto.CustomerRequest;
import it.example.ecommerce.dto.CustomerResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface CustomerService {

    CustomerResponse create(CustomerRequest request);

    CustomerResponse findById(Long id);

    Page<CustomerResponse> findAll(Pageable pageable);
}
