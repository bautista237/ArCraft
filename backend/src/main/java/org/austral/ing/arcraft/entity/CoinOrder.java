package org.austral.ing.arcraft.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * A pending/completed purchase of coins via MercadoPago. One row is created when the player
 * starts checkout; it is marked PAID exactly once (idempotently) when MercadoPago confirms the
 * payment, at which point the coins are credited. The {@code id} is used as MercadoPago's
 * {@code external_reference}, so the return URL and the webhook both resolve back to this row.
 */
@Entity
@Table(name = "coin_order")
@Getter @Setter @NoArgsConstructor
public class CoinOrder {

    public enum Status { PENDING, PAID, FAILED }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID playerId;

    @Column(nullable = false)
    private String username;

    /** Coins to credit when paid. */
    @Column(nullable = false)
    private long coins;

    /** Price charged, in the configured currency (e.g. ARS). */
    @Column(nullable = false)
    private double amount;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private Status status = Status.PENDING;

    /** MercadoPago preference id (the checkout) and, once paid, the payment id. */
    @Column
    private String preferenceId;

    @Column
    private String paymentId;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    @Column
    private Instant paidAt;

    public CoinOrder(Player player, long coins, double amount) {
        this.playerId = player.getId();
        this.username = player.getUsername();
        this.coins = coins;
        this.amount = amount;
    }
}
