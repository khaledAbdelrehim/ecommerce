package it.example.ecommerce.repository;

import it.example.ecommerce.domain.Product;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ProductRepository extends JpaRepository<Product, Long> {

    boolean existsByCodice(String codice);

    /**
     * Acquisisce i lock PESSIMISTIC_WRITE in ordine crescente di ID, uguale per ogni transazione,
     * evitando così deadlock dovuti a ordini di acquisizione diversi.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.id in :ids order by p.id asc")
    List<Product> findAllByIdForUpdate(@Param("ids") Collection<Long> ids);

    /**
     * Verifica e decrementa le scorte in modo atomico: il controllo della disponibilità e la scrittura
     * avvengono in un'unica istruzione, senza intervalli tra lettura e scrittura. Restituisce il numero
     * di righe aggiornate (0 = prodotto inesistente o scorte insufficienti).
     * Il lock sulla riga resta attivo fino al commit, quindi va invocato in ordine crescente di ID.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Product p set p.stock = p.stock - :quantity, p.version = p.version + 1 "
            + "where p.id = :id and p.stock >= :quantity")
    int decreaseStockIfAvailable(@Param("id") Long id, @Param("quantity") int quantity);

    /** Incrementa le scorte in modo atomico evitando un overflow; restituisce 0 se si supererebbe il valore massimo. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Product p set p.stock = p.stock + :quantity, p.version = p.version + 1 "
            + "where p.id = :id and p.stock <= " + Integer.MAX_VALUE + " - :quantity")
    int increaseStockIfRoom(@Param("id") Long id, @Param("quantity") int quantity);
}
