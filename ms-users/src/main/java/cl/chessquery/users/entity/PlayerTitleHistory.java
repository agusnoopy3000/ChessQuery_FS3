package cl.chessquery.users.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "player_title_history")
@Getter @Setter
@Builder
@NoArgsConstructor @AllArgsConstructor
public class PlayerTitleHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "player_id", nullable = false)
    private Player player;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ChessTitle title;

    @Column(name = "title_date", nullable = false)
    private LocalDate titleDate;

    @Column(name = "is_current", nullable = false)
    private boolean isCurrent;

    @Column(length = 50)
    private String source;
}
