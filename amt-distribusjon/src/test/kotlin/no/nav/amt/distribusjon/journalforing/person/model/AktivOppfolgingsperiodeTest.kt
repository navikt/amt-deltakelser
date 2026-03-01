package no.nav.amt.distribusjon.journalforing.person.model

import io.kotest.matchers.shouldBe
import no.nav.amt.distribusjon.utils.data.Persondata
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class AktivOppfolgingsperiodeTest {
    @Test
    fun `getAktivOppfolgingsperiode - har ingen oppfolgingsperioder - returnerer null`() {
        val navBruker = Persondata.lagNavBruker(oppfolgingsperioder = emptyList())

        navBruker.getAktivOppfolgingsperiode() shouldBe null
    }

    @Test
    fun `getAktivOppfolgingsperiode - har ikke startet - returnerer null`() {
        val oppfolgingsperiode = Persondata.lagOppfolgingsperiode(
            startdato = LocalDateTime.now().plusDays(2),
            sluttdato = null,
        )
        val navBruker = Persondata.lagNavBruker(oppfolgingsperioder = listOf(oppfolgingsperiode))

        navBruker.getAktivOppfolgingsperiode() shouldBe null
    }

    @Test
    fun `getAktivOppfolgingsperiode - startdato passert, sluttdato null - returnerer oppfolgingsperiode`() {
        val oppfolgingsperiode = Persondata.lagOppfolgingsperiode(
            startdato = LocalDateTime.now().minusDays(2),
            sluttdato = null,
        )
        val navBruker = Persondata.lagNavBruker(oppfolgingsperioder = listOf(oppfolgingsperiode))

        navBruker.getAktivOppfolgingsperiode() shouldBe oppfolgingsperiode
    }

    @Test
    fun `getAktivOppfolgingsperiode - startdato passert, sluttdato om en uke - returnerer oppfolgingsperiode`() {
        val oppfolgingsperiode = Persondata.lagOppfolgingsperiode(
            startdato = LocalDateTime.now().minusDays(2),
            sluttdato = LocalDateTime.now().plusWeeks(1),
        )
        val navBruker = Persondata.lagNavBruker(oppfolgingsperioder = listOf(oppfolgingsperiode))

        navBruker.getAktivOppfolgingsperiode() shouldBe oppfolgingsperiode
    }

    @Test
    fun `getAktivOppfolgingsperiode - startdato passert, sluttdato for 25 dager siden - returnerer oppfolgingsperiode`() {
        val oppfolgingsperiode = Persondata.lagOppfolgingsperiode(
            startdato = LocalDateTime.now().minusYears(1),
            sluttdato = LocalDateTime.now().minusDays(25),
        )
        val navBruker = Persondata.lagNavBruker(oppfolgingsperioder = listOf(oppfolgingsperiode))

        navBruker.getAktivOppfolgingsperiode() shouldBe oppfolgingsperiode
    }

    @Test
    fun `getAktivOppfolgingsperiode - startdato passert, sluttdato for 29 dager siden - returnerer null`() {
        val oppfolgingsperiode = Persondata.lagOppfolgingsperiode(
            startdato = LocalDateTime.now().minusYears(1),
            sluttdato = LocalDateTime.now().minusDays(29),
        )
        val navBruker = Persondata.lagNavBruker(oppfolgingsperioder = listOf(oppfolgingsperiode))

        navBruker.getAktivOppfolgingsperiode() shouldBe null
    }
}
