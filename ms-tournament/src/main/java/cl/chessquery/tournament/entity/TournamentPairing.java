package cl.chessquery.tournament.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "tournament_pairing")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TournamentPairing {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "round_id", nullable = false)
    private TournamentRound round;

    @Column(name = "white_player_id", nullable = false)
    private Long whitePlayerId;

    @Column(name = "black_player_id", nullable = false)
    private Long blackPlayerId;

    @Column(name = "result", length = 10)
    private String result;

    @Column(name = "board_number")
    private Integer boardNumber;
}
