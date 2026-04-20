package cl.chessquery.users.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "rating_history")
@Getter @Setter
@Builder
@NoArgsConstructor @AllArgsConstructor
public class RatingHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "player_id", nullable = false)
    private Player player;

    @Enumerated(EnumType.STRING)
    @Column(name = "rating_type", nullable = false, length = 30)
    private RatingType ratingType;

    @Column(name = "rating_value", nullable = false)
    private Integer ratingValue;

    /** Valor previo. Desnormalización documentada para consultas analíticas sin self-JOIN. */
    @Column(name = "rating_prev_value")
    private Integer ratingPrevValue;

    /** delta = ratingValue - ratingPrevValue. */
    private Short delta;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    @Column(length = 50)
    private String source;
}
