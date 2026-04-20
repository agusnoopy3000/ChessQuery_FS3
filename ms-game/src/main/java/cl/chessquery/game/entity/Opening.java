package cl.chessquery.game.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "opening")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Opening {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "eco_code", length = 10, unique = true, nullable = false)
    private String ecoCode;

    @Column(name = "name", length = 200, nullable = false)
    private String name;

    @Column(name = "variation", length = 200)
    private String variation;

    @Column(name = "pgn_moves", columnDefinition = "TEXT")
    private String pgnMoves;
}
