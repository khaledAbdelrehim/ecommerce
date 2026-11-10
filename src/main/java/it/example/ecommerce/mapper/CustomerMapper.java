package it.example.ecommerce.mapper;

import it.example.ecommerce.domain.Customer;
import it.example.ecommerce.dto.CustomerRequest;
import it.example.ecommerce.dto.CustomerResponse;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface CustomerMapper {

    Customer toEntity(CustomerRequest request);

    CustomerResponse toResponse(Customer customer);
}
