package cl.chessquery.analytics.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "game_record")
public class GameRecord {

    /** ID de la partida proveniente de MS-Game (no auto-generado). */
    @Id
    @Column(name = "game_id")
    private Long gameId;

    @Column(name = "white_player_id", nullable = false)
    private Long whitePlayerId;

    @Column(name = "black_player_id", nullable = false)
    private Long blackPlayerId;

    /** "1-0", "0-1" o "1/2-1/2" */
    @Column(name = "result", nullable = false, length = 10)
    private String result;

    @Column(name = "opening_id")
    private Integer openingId;

    @Column(name = "played_at", nullable = false)
    private Instant playedAt;
}
