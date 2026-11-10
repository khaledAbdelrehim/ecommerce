package it.example.ecommerce.repository;

import it.example.ecommerce.domain.AppUser;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    @EntityGraph(attributePaths = "customer")
    Optional<AppUser> findByUsername(String username);

    boolean existsByUsername(String username);
}
