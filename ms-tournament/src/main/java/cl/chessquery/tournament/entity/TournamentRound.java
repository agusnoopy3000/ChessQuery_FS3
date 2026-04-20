package cl.chessquery.tournament.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(
    name = "tournament_round",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_round_tournament_number",
        columnNames = {"tournament_id", "round_number"}
    )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TournamentRound {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tournament_id", nullable = false)
    private Tournament tournament;

    @Column(name = "round_number", nullable = false)
    private Integer roundNumber;

    @Column(name = "round_date")
    private LocalDate roundDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private RoundStatus status = RoundStatus.PENDING;

    @PrePersist
    protected void onCreate() {
        if (this.status == null) {
            this.status = RoundStatus.PENDING;
        }
    }
}
