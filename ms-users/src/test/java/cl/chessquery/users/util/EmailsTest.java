package cl.chessquery.users.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EmailsTest {

    @Test
    void normalizaTrimYLowercase() {
        assertThat(Emails.normalize("  Foo.Bar@Mail.COM ")).isEqualTo("foo.bar@mail.com");
    }

    @Test
    void nullYVacioDevuelvenNull() {
        assertThat(Emails.normalize(null)).isNull();
        assertThat(Emails.normalize("")).isNull();
        assertThat(Emails.normalize("   ")).isNull();
    }

    @Test
    void emailYaNormalizadoQuedaIgual() {
        assertThat(Emails.normalize("user@mail.com")).isEqualTo("user@mail.com");
    }
}
