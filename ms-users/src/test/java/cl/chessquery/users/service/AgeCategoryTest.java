package cl.chessquery.users.service;

import cl.chessquery.users.entity.AgeCategory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests para AgeCategory.fromBirthDate().
 * Función pura: no requiere Spring context.
 */
class AgeCategoryTest {

    @ParameterizedTest(name = "edad={0} → {1}")
    @CsvSource({
            "7,  SUB_8",
            "8,  SUB_10",
            "9,  SUB_10",
            "10, SUB_12",
            "11, SUB_12",
            "12, SUB_14",
            "13, SUB_14",
            "14, SUB_16",
            "15, SUB_16",
            "16, SUB_18",
            "17, SUB_18",
            "18, SUB_20",
            "19, SUB_20",
            "20, ADULTO",
            "35, ADULTO",
            "49, ADULTO",
            "50, SENIOR",
            "75, SENIOR"
    })
    void fromBirthDate_correctCategory(int age, AgeCategory expected) {
        LocalDate birthDate = LocalDate.now().minusYears(age);
        assertThat(AgeCategory.fromBirthDate(birthDate)).isEqualTo(expected);
    }

    @Test
    void fromBirthDate_nullReturnsAdulto() {
        assertThat(AgeCategory.fromBirthDate(null)).isEqualTo(AgeCategory.ADULTO);
    }

    @Test
    void minMaxBirthDate_adultoCategoryIsConsistent() {
        AgeCategory cat = AgeCategory.ADULTO;
        LocalDate today = LocalDate.now();
        // El rango de ADULTO debe capturar a alguien con exactamente 20 años
        LocalDate born20 = today.minusYears(20);
        assertThat(born20).isBetween(cat.minBirthDate(), cat.maxBirthDate());
        // Y no debe capturar a alguien con 50 años
        LocalDate born50 = today.minusYears(50);
        assertThat(born50).isNotBetween(cat.minBirthDate(), cat.maxBirthDate());
    }

    @Test
    void sub18_birthDateRange_isCorrect() {
        AgeCategory cat = AgeCategory.SUB_18;
        LocalDate today = LocalDate.now();
        // Alguien que cumple 16 hoy → debe estar en SUB_18
        LocalDate born16 = today.minusYears(16);
        assertThat(born16).isBetween(cat.minBirthDate(), cat.maxBirthDate());
        // Alguien que cumple 18 hoy → NO debe estar en SUB_18
        LocalDate born18 = today.minusYears(18);
        assertThat(born18).isNotBetween(cat.minBirthDate(), cat.maxBirthDate());
    }
}
