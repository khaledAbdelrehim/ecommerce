package it.example.ecommerce.domain;

import it.example.ecommerce.exception.InvalidOrderStateException;

/**
 * Pattern State: ogni stato definisce le transizioni consentite e restituisce lo stato successivo.
 * ORDINATO -> CONSEGNATO (consegna) oppure ORDINATO -> ANNULLATO (annullamento); gli altri stati sono terminali.
 */
public enum OrderStatus {

    ORDINATO {
        @Override
        public OrderStatus deliver() {
            return CONSEGNATO;
        }

        @Override
        public OrderStatus cancel() {
            return ANNULLATO;
        }
    },

    CONSEGNATO {
        @Override
        public OrderStatus deliver() {
            throw new InvalidOrderStateException("Order is already " + this + " and cannot be delivered again");
        }

        @Override
        public OrderStatus cancel() {
            throw new InvalidOrderStateException("Order is " + this + " and cannot be cancelled");
        }
    },

    ANNULLATO {
        @Override
        public OrderStatus deliver() {
            throw new InvalidOrderStateException("Order is " + this + " and cannot be delivered");
        }

        @Override
        public OrderStatus cancel() {
            throw new InvalidOrderStateException("Order is already " + this);
        }
    };

    public abstract OrderStatus deliver();

    public abstract OrderStatus cancel();
}
