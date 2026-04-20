package cl.chessquery.users.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "club")
@Getter @Setter
@Builder
@NoArgsConstructor @AllArgsConstructor
public class Club {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, length = 200)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "country_id")
    private Country country;

    @Column(length = 100)
    private String city;

    @Column(name = "federation_code", length = 20)
    private String federationCode;
}
