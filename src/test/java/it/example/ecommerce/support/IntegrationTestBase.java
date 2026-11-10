package it.example.ecommerce.support;

import it.example.ecommerce.domain.AppUser;
import it.example.ecommerce.domain.Customer;
import it.example.ecommerce.domain.Product;
import it.example.ecommerce.domain.Role;
import it.example.ecommerce.dto.CreateOrderRequest;
import it.example.ecommerce.dto.OrderItemRequest;
import it.example.ecommerce.dto.OrderResponse;
import it.example.ecommerce.repository.AppUserRepository;
import it.example.ecommerce.repository.CustomerRepository;
import it.example.ecommerce.repository.OrderRepository;
import it.example.ecommerce.repository.ProductRepository;
import it.example.ecommerce.security.AuthenticatedUser;
import it.example.ecommerce.security.TokenService;
import it.example.ecommerce.service.OrderService;
import it.example.ecommerce.service.Requester;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDate;
import java.util.List;

/** Avvia l'intera applicazione su H2 e ricrea utenti e clienti prima di ogni test. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class IntegrationTestBase {

    @Autowired
    protected MockMvc mockMvc;
    @Autowired
    protected OrderService orderService;
    @Autowired
    protected OrderRepository orderRepository;
    @Autowired
    protected ProductRepository productRepository;
    @Autowired
    protected CustomerRepository customerRepository;
    @Autowired
    protected AppUserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private TokenService tokenService;

    protected Customer alice;
    protected Customer bob;

    @BeforeEach
    void resetDatabase() {
        orderRepository.deleteAll();
        userRepository.deleteAll();
        customerRepository.deleteAll();
        productRepository.deleteAll();

        alice = customerRepository.save(Customer.builder().nome("Alice").cognome("Verdi")
                .dataNascita(LocalDate.of(1990, 1, 1)).codiceFiscale("VRDLCA90A41H501A")
                .email("alice@example.com").build());
        bob = customerRepository.save(Customer.builder().nome("Bob").cognome("Neri")
                .dataNascita(LocalDate.of(1988, 5, 5)).codiceFiscale("NRIBBO88E05H501B")
                .email("bob@example.com").build());

        userRepository.save(user("admin", "admin123", Role.ADMIN, null));
        userRepository.save(user("alice", "alice123", Role.CUSTOMER, alice));
        userRepository.save(user("bob", "bob123", Role.CUSTOMER, bob));
    }

    /** Aggiunge un token bearer valido per un utente esistente. */
    protected RequestPostProcessor bearer(String username) {
        AppUser user = userRepository.findByUsername(username).orElseThrow();
        String token = tokenService.issue(new AuthenticatedUser(user)).accessToken();
        return request -> {
            request.addHeader("Authorization", "Bearer " + token);
            return request;
        };
    }

    protected Product newProduct(String code, int stock) {
        return productRepository.save(new Product(code, "Product " + code, stock));
    }

    protected OrderResponse newOrder(Customer customer, Product product, int quantity) {
        return orderService.create(Requester.customer(customer.getId()),
                new CreateOrderRequest(null, List.of(new OrderItemRequest(product.getId(), quantity))));
    }

    protected int stockOf(Product product) {
        return productRepository.findById(product.getId()).orElseThrow().getStock();
    }

    private AppUser user(String username, String password, Role role, Customer customer) {
        return AppUser.builder().username(username).password(passwordEncoder.encode(password))
                .role(role).customer(customer).build();
    }
}
