package it.example.ecommerce.config;

import it.example.ecommerce.domain.AppUser;
import it.example.ecommerce.domain.Customer;
import it.example.ecommerce.domain.Product;
import it.example.ecommerce.domain.Role;
import it.example.ecommerce.dto.CreateOrderRequest;
import it.example.ecommerce.dto.OrderItemRequest;
import it.example.ecommerce.repository.AppUserRepository;
import it.example.ecommerce.repository.CustomerRepository;
import it.example.ecommerce.repository.ProductRepository;
import it.example.ecommerce.service.OrderService;
import it.example.ecommerce.service.Requester;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/** Crea utenti, clienti, prodotti e un ordine di esempio. Si attiva con {@code app.seed.enabled=true}. */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true")
public class DataSeeder implements ApplicationRunner {

    private final AppUserRepository userRepository;
    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;
    private final OrderService orderService;
    private final PasswordEncoder passwordEncoder;

    public DataSeeder(AppUserRepository userRepository, CustomerRepository customerRepository,
                      ProductRepository productRepository, OrderService orderService,
                      PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.customerRepository = customerRepository;
        this.productRepository = productRepository;
        this.orderService = orderService;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.count() > 0) {
            return;
        }

        Customer mario = customerRepository.save(Customer.builder()
                .nome("Mario").cognome("Rossi").dataNascita(LocalDate.of(1985, 3, 12))
                .codiceFiscale("RSSMRA85C12H501X").email("mario.rossi@example.com").build());
        Customer lucia = customerRepository.save(Customer.builder()
                .nome("Lucia").cognome("Bianchi").dataNascita(LocalDate.of(1992, 11, 5))
                .codiceFiscale("BNCLCU92S45F205Z").email("lucia.bianchi@example.com").build());

        userRepository.saveAll(List.of(
                AppUser.builder().username("admin").password(passwordEncoder.encode("admin123"))
                        .role(Role.ADMIN).build(),
                AppUser.builder().username("mario").password(passwordEncoder.encode("mario123"))
                        .role(Role.CUSTOMER).customer(mario).build(),
                AppUser.builder().username("lucia").password(passwordEncoder.encode("lucia123"))
                        .role(Role.CUSTOMER).customer(lucia).build()));

        Product laptop = productRepository.save(new Product("LAP-001", "Laptop 14\"", 25));
        Product mouse = productRepository.save(new Product("MOU-001", "Wireless mouse", 100));
        productRepository.save(new Product("KEY-001", "Mechanical keyboard", 40));

        orderService.create(Requester.admin(), new CreateOrderRequest(mario.getId(), List.of(
                new OrderItemRequest(laptop.getId(), 1),
                new OrderItemRequest(mouse.getId(), 2))));
        log.info("Demo data seeded (users: admin, mario, lucia)");
    }
}
