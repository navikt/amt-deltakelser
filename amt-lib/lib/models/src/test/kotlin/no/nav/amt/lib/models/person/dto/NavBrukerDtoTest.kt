package no.nav.amt.lib.models.person.dto

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import no.nav.amt.lib.testing.testdata.person.PersonDtoTestsData.brukerDtoInTest
import no.nav.amt.lib.testing.testdata.person.PersonDtoTestsData.enhetDtoInTest
import org.junit.jupiter.api.Test

class NavBrukerDtoTest {
    @Test
    fun `Skal mappe NavBrukerDto til NavBruker nar alle verdier er satt`() {
        val navBruker = brukerDtoInTest.toModel()

        assertSoftly(navBruker) {
            personId shouldBe brukerDtoInTest.personId
            personident shouldBe brukerDtoInTest.personident
            fornavn shouldBe brukerDtoInTest.fornavn
            mellomnavn shouldBe brukerDtoInTest.mellomnavn
            etternavn shouldBe brukerDtoInTest.etternavn
            navVeilederId shouldBe brukerDtoInTest.navVeilederId
            navEnhetId shouldBe enhetDtoInTest.id
            telefon shouldBe brukerDtoInTest.telefon
            epost shouldBe brukerDtoInTest.epost
            erSkjermet shouldBe brukerDtoInTest.erSkjermet
            adresse shouldBe brukerDtoInTest.adresse
            adressebeskyttelse shouldBe brukerDtoInTest.adressebeskyttelse
            oppfolgingsperioder shouldBe brukerDtoInTest.oppfolgingsperioder
            innsatsgruppe shouldBe brukerDtoInTest.innsatsgruppe
        }
    }

    @Test
    fun `Skal mappe NavBrukerDto til NavBruker nar kun pakrevde felter er satt`() {
        val navBruker = brukerDtoInTest
            .copy(
                mellomnavn = null,
                navVeilederId = null,
                navEnhet = null,
                telefon = null,
                epost = null,
                adresse = null,
                adressebeskyttelse = null,
                oppfolgingsperioder = emptyList(),
                innsatsgruppe = null,
            ).toModel()

        assertSoftly(navBruker) {
            mellomnavn shouldBe null
            navVeilederId shouldBe null
            navEnhetId shouldBe null
            telefon shouldBe null
            epost shouldBe null
            adresse shouldBe null
            adressebeskyttelse shouldBe null
            oppfolgingsperioder shouldBe emptyList()
            innsatsgruppe shouldBe null
        }
    }
}
