package cl.chessquery.users.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "player")
@Getter @Setter
@Builder
@NoArgsConstructor @AllArgsConstructor
public class Player {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    /** RUT chileno. Formato: "12345678-9". Nullable para jugadores extranjeros. */
    @Column(length = 12, unique = true)
    private String rut;

    @Column(length = 255, unique = true)
    private String email;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "country_id")
    private Country country;

    @Column(length = 100)
    private String region;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "club_id")
    private Club club;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    /** 'M', 'F' o 'O'. CHECK constraint en BD. */
    @Column(length = 1)
    private String gender;

    @Column(name = "fide_id", length = 20, unique = true)
    private String fideId;

    @Column(name = "federation_id", length = 50, unique = true)
    private String federationId;

    @Column(name = "lichess_username", length = 100, unique = true)
    private String lichessUsername;

    // ── ELO snapshots (desnormalización documentada) ──────────────────────────
    // Historial completo en RATING_HISTORY. Estos campos se actualizan
    // vía evento elo.updated (consumidor) o endpoint PUT /users/{id}/elo.

    @Column(name = "elo_national")
    private Integer eloNational;

    @Column(name = "elo_fide_standard")
    private Integer eloFideStandard;

    @Column(name = "elo_fide_rapid")
    private Integer eloFideRapid;

    @Column(name = "elo_fide_blitz")
    private Integer eloFideBlitz;

    @Column(name = "elo_platform")
    private Integer eloPlatform;

    /** Fuente de la última enriquecimiento federado: AJEFECH, LICHESS, FIDE, null. */
    @Column(name = "enrichment_source", length = 20)
    private String enrichmentSource;

    @Column(name = "enriched_at")
    private Instant enrichedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    /** Nombre completo para logs y eventos. */
    public String fullName() {
        return firstName + " " + lastName;
    }
}
