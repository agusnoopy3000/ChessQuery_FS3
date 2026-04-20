package cl.chessquery.tournament.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(
    name = "tournament_registration",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_registration_tournament_player",
        columnNames = {"tournament_id", "player_id"}
    )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TournamentRegistration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tournament_id", nullable = false)
    private Tournament tournament;

    @Column(name = "player_id", nullable = false)
    private Long playerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private RegistrationStatus status = RegistrationStatus.CONFIRMED;

    @Column(name = "registered_at", nullable = false)
    private Instant registeredAt;

    @Column(name = "seed_rating")
    private Integer seedRating;

    @PrePersist
    protected void onCreate() {
        if (this.registeredAt == null) {
            this.registeredAt = Instant.now();
        }
        if (this.status == null) {
            this.status = RegistrationStatus.CONFIRMED;
        }
    }
}
