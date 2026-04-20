package cl.chessquery.users.entity;

import java.time.LocalDate;
import java.time.Period;

/**
 * Categorías por edad para el ranking nacional.
 * El límite de edad se evalúa al día de hoy.
 */
public enum AgeCategory {

    SUB_8(0, 7),
    SUB_10(8, 9),
    SUB_12(10, 11),
    SUB_14(12, 13),
    SUB_16(14, 15),
    SUB_18(16, 17),
    SUB_20(18, 19),
    ADULTO(20, 49),
    SENIOR(50, Integer.MAX_VALUE);

    private final int minAge;
    private final int maxAge;

    AgeCategory(int minAge, int maxAge) {
        this.minAge = minAge;
        this.maxAge = maxAge;
    }

    public int getMinAge() { return minAge; }
    public int getMaxAge() { return maxAge; }

    /**
     * Calcula la categoría a partir de la fecha de nacimiento.
     * Si birthDate es null, retorna ADULTO como valor por defecto.
     */
    public static AgeCategory fromBirthDate(LocalDate birthDate) {
        if (birthDate == null) return ADULTO;
        int age = Period.between(birthDate, LocalDate.now()).getYears();
        for (AgeCategory cat : values()) {
            if (age >= cat.minAge && age <= cat.maxAge) return cat;
        }
        return SENIOR;
    }

    /**
     * Fecha mínima de nacimiento para pertenecer a esta categoría hoy.
     * Útil para filtros en queries JPA por rango de fecha.
     */
    public LocalDate minBirthDate() {
        if (maxAge == Integer.MAX_VALUE) return LocalDate.of(1900, 1, 1);
        return LocalDate.now().minusYears(maxAge + 1L).plusDays(1);
    }

    /**
     * Fecha máxima de nacimiento para pertenecer a esta categoría hoy.
     */
    public LocalDate maxBirthDate() {
        return LocalDate.now().minusYears(minAge);
    }
}
