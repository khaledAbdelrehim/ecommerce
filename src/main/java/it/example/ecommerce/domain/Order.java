package it.example.ecommerce.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.BatchSize;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Entity
@Table(name = "orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Enumerated(EnumType.STRING)
    @Column(name = "stato", nullable = false, length = 20)
    private OrderStatus stato;

    @Column(name = "data_creazione", nullable = false)
    private Instant dataCreazione;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @BatchSize(size = 50)
    private List<OrderItem> righe = new ArrayList<>();

    Order(Customer customer, Instant dataCreazione) {
        this.customer = customer;
        this.dataCreazione = dataCreazione;
        this.stato = OrderStatus.ORDINATO;
    }

    void addItem(Product product, int quantita) {
        righe.add(new OrderItem(this, product, quantita));
    }

    public List<OrderItem> getRighe() {
        return Collections.unmodifiableList(righe);
    }

    public void deliver() {
        this.stato = stato.deliver();
    }

    public void cancel() {
        this.stato = stato.cancel();
    }
}
