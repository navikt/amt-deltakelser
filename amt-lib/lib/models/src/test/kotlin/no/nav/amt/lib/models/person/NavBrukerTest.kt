package no.nav.amt.lib.models.person

import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import no.nav.amt.lib.models.person.address.Adressebeskyttelse
import no.nav.amt.lib.testing.testdata.person.PersonModelsTestData.brukerInTest
import no.nav.amt.lib.testing.testdata.person.PersonModelsTestData.oppfolgingsperiodeInTest
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.time.LocalDateTime

class NavBrukerTest {
    @Nested
    inner class FulltNavn {
        @Test
        fun `fulltNavn skal returnere navn med mellomnavn`() {
            val fulltNavn = brukerInTest.fulltNavn

            fulltNavn shouldBe "~fornavn~ ~mellomnavn~ ~etternavn~"
        }

        @Test
        fun `fulltNavn skal returnere navn uten mellomnavn`() {
            val fulltNavn = brukerInTest.copy(mellomnavn = null).fulltNavn

            fulltNavn shouldBe "~fornavn~ ~etternavn~"
        }
    }

    @Nested
    inner class ErAdressebeskyttet {
        @Test
        fun `erAdressebeskyttet skal returnere false nar adressebeskyttelse er null`() {
            brukerInTest.erAdressebeskyttet.shouldBeFalse()
        }

        @Test
        fun `erAdressebeskyttet skal returnere true nar adressebeskyttelse ikke er null`() {
            val bruker = brukerInTest.copy(adressebeskyttelse = Adressebeskyttelse.STRENGT_FORTROLIG)
            bruker.erAdressebeskyttet.shouldBeTrue()
        }
    }

    @Nested
    inner class GetBeskyttelsesmarkeringer {
        @Test
        fun `getBeskyttelsesmarkeringer skal returnere tom liste nar adressebeskyttelse er null`() {
            brukerInTest.beskyttelsesmarkeringer shouldBe emptyList()
        }

        @Test
        fun `getBeskyttelsesmarkeringer skal returnere en liste med FORTROLIG`() {
            val bruker = brukerInTest.copy(adressebeskyttelse = Adressebeskyttelse.FORTROLIG)
            bruker.beskyttelsesmarkeringer shouldBe listOf(Beskyttelsesmarkering.FORTROLIG)
        }

        @Test
        fun `getBeskyttelsesmarkeringer skal returnere en liste med STRENGT_FORTROLIG`() {
            val bruker = brukerInTest.copy(adressebeskyttelse = Adressebeskyttelse.STRENGT_FORTROLIG)
            bruker.beskyttelsesmarkeringer shouldBe listOf(Beskyttelsesmarkering.STRENGT_FORTROLIG)
        }

        @Test
        fun `getBeskyttelsesmarkeringer skal returnere en liste med STRENGT_FORTROLIG_UTLAND`() {
            val bruker = brukerInTest.copy(adressebeskyttelse = Adressebeskyttelse.STRENGT_FORTROLIG_UTLAND)
            bruker.beskyttelsesmarkeringer shouldBe listOf(Beskyttelsesmarkering.STRENGT_FORTROLIG_UTLAND)
        }

        @Test
        fun `getBeskyttelsesmarkeringer skal returnere en liste med FORTROLIG og SKJERMET`() {
            val bruker = brukerInTest.copy(
                adressebeskyttelse = Adressebeskyttelse.STRENGT_FORTROLIG_UTLAND,
                erSkjermet = true,
            )

            bruker.beskyttelsesmarkeringer shouldBe listOf(
                Beskyttelsesmarkering.STRENGT_FORTROLIG_UTLAND,
                Beskyttelsesmarkering.SKJERMET,
            )
        }
    }

    @Nested
    inner class GetVisningsnavn {
        @ParameterizedTest
        @ValueSource(booleans = [true, false])
        fun `getVisningsnavn skal returnere visningsnavn med mellomnavn`(tilgangTilBruker: Boolean) {
            val visningsnavn = brukerInTest.getVisningsnavn(tilgangTilBruker = tilgangTilBruker)

            visningsnavn shouldBe Triple(
                "~fornavn~",
                "~mellomnavn~",
                "~etternavn~",
            )
        }

        @Test
        fun `getVisningsnavn skal returnere visningsnavn nar erAdressebeskyttet og tilgangTilBruker`() {
            val visningsnavn = brukerInTest
                .copy(adressebeskyttelse = Adressebeskyttelse.FORTROLIG)
                .getVisningsnavn(tilgangTilBruker = true)

            visningsnavn shouldBe Triple(
                "~fornavn~",
                "~mellomnavn~",
                "~etternavn~",
            )
        }

        @Test
        fun `getVisningsnavn skal returnere Adressebeskyttet nar erAdressebeskyttet og ikke tilgangTilBruker`() {
            val visningsnavn = brukerInTest
                .copy(adressebeskyttelse = Adressebeskyttelse.FORTROLIG)
                .getVisningsnavn(tilgangTilBruker = false)

            visningsnavn shouldBe Triple(
                "Adressebeskyttet",
                null,
                "",
            )
        }

        @Test
        fun `getVisningsnavn skal returnere visningsnavn nar erSkjermet og tilgangTilBruker`() {
            val visningsnavn = brukerInTest
                .copy(erSkjermet = true)
                .getVisningsnavn(tilgangTilBruker = true)

            visningsnavn shouldBe Triple(
                "~fornavn~",
                "~mellomnavn~",
                "~etternavn~",
            )
        }

        @Test
        fun `getVisningsnavn skal returnere Skjermet person nar erSkjermet og ikke tilgangTilBruker`() {
            val visningsnavn = brukerInTest
                .copy(erSkjermet = true)
                .getVisningsnavn(tilgangTilBruker = false)

            visningsnavn shouldBe Triple(
                "Skjermet person",
                null,
                "",
            )
        }
    }

    @Nested
    inner class HarAktivOppfolgingsperiode {
        @Test
        fun `harAktivOppfolgingsperiode skal returnere false nar oppfolgingsperioder er tom`() {
            brukerInTest.harAktivOppfolgingsperiode.shouldBeFalse()
        }

        @Test
        fun `harAktivOppfolgingsperiode skal returnere true nar aktiv oppfolgingsperiode`() {
            brukerInTest
                .copy(oppfolgingsperioder = listOf(oppfolgingsperiodeInTest))
                .harAktivOppfolgingsperiode
                .shouldBeTrue()
        }

        @Test
        fun `harAktivOppfolgingsperiode skal returnere false nar inaktiv oppfolgingsperiode`() {
            val oppfolgingsperiode = oppfolgingsperiodeInTest.copy(sluttdato = LocalDateTime.now())

            brukerInTest
                .copy(oppfolgingsperioder = listOf(oppfolgingsperiode))
                .harAktivOppfolgingsperiode
                .shouldBeFalse()
        }
    }
}
