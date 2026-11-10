package it.example.ecommerce.service;

import it.example.ecommerce.exception.InvalidRequestException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Set;
import java.util.TreeSet;

/**
 * Limita l'ordinamento richiesto dal client a un elenco esplicito di colonne scalari e lo rende deterministico.
 * Ordinare per percorsi di collezioni (ad esempio {@code righe.quantita}) unisce la collezione e duplica le righe tra le pagine.
 */
public final class PageableRules {

    private static final String TIE_BREAKER = "id";

    private PageableRules() {
    }

    /** @throws InvalidRequestException se una proprietà di ordinamento non è inclusa in {@code allowed} */
    public static Pageable restrict(Pageable pageable, Set<String> allowed) {
        if (pageable.isUnpaged()) {
            return pageable;
        }
        Sort sort = pageable.getSort();
        for (Sort.Order order : sort) {
            if (!allowed.contains(order.getProperty())) {
                throw new InvalidRequestException("Unsupported sort property; allowed: " + new TreeSet<>(allowed));
            }
        }
        // the unique id as last criterion keeps page boundaries stable when the sorted column has ties
        Sort stable = sort.getOrderFor(TIE_BREAKER) == null ? sort.and(Sort.by(TIE_BREAKER)) : sort;
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), stable);
    }
}
