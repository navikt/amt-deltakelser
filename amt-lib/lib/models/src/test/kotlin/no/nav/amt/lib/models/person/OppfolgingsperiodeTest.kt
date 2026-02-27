package no.nav.amt.lib.models.person

import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

class OppfolgingsperiodeTest {
    @Test
    fun `erAktiv - start i dag, ingen sluttdato`() {
        oppfolgingsperiodeInTest.erAktiv().shouldBeTrue()
    }

    @Test
    fun `erAktiv - start i fortid, ingen sluttdato`() {
        val periode = oppfolgingsperiodeInTest.copy(
            startdato = now.minusDays(5),
        )

        periode.erAktiv().shouldBeTrue()
    }

    @Test
    fun `erAktiv - start i framtid`() {
        val periode = oppfolgingsperiodeInTest.copy(
            startdato = now.plusDays(1),
        )

        periode.erAktiv().shouldBeFalse()
    }

    @Test
    fun `erAktiv - innenfor start og slutt`() {
        val periode = oppfolgingsperiodeInTest.copy(
            startdato = now.minusDays(1),
            sluttdato = now.plusDays(1),
        )

        periode.erAktiv().shouldBeTrue()
    }

    @Test
    fun `erAktiv - etter sluttdato`() {
        val periode = oppfolgingsperiodeInTest.copy(
            startdato = now.minusDays(5),
            sluttdato = now.minusDays(1),
        )

        periode.erAktiv().shouldBeFalse()
    }

    @Test
    fun `erAktiv - pa sluttdato`() {
        val periode = oppfolgingsperiodeInTest.copy(
            startdato = now.minusDays(5),
            sluttdato = LocalDateTime.of(today, now.toLocalTime()),
        )

        periode.erAktiv().shouldBeFalse()
    }

    companion object {
        private val today = LocalDate.now()
        private val now = LocalDateTime.now()

        private val oppfolgingsperiodeInTest = Oppfolgingsperiode(
            id = UUID.randomUUID(),
            startdato = now,
            sluttdato = null,
        )
    }
}
