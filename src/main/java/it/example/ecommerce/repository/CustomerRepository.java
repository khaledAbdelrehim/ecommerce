package it.example.ecommerce.repository;

import it.example.ecommerce.domain.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    boolean existsByCodiceFiscale(String codiceFiscale);

    boolean existsByEmail(String email);
}
