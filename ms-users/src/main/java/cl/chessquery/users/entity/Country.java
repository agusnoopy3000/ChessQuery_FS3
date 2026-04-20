package cl.chessquery.users.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "country")
@Getter @Setter
@Builder
@NoArgsConstructor @AllArgsConstructor
public class Country {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "iso_code", nullable = false, unique = true, length = 3)
    private String isoCode;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "fide_federation", length = 10)
    private String fideFederation;
}
