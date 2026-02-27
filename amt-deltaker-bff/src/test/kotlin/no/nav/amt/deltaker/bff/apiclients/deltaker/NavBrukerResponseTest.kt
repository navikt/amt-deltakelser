package no.nav.amt.deltaker.bff.apiclients.deltaker

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import no.nav.amt.deltaker.bff.utils.data.TestData.lagNavBrukerResponse
import no.nav.amt.lib.models.person.address.Adressebeskyttelse
import org.junit.jupiter.api.Test

class NavBrukerResponseTest {
    @Test
    fun `skal mapppe response til model korrekt`() {
        val response =
            lagNavBrukerResponse(
                telefon = "~telefon~",
                epost = "a@b.c",
                adressebeskyttelse = Adressebeskyttelse.FORTROLIG,
            )

        val model = ModelMapper.toNavBruker(response)

        assertSoftly(model) {
            personident shouldBe response.personident
            fornavn shouldBe response.fornavn
            mellomnavn shouldBe response.mellomnavn.shouldNotBeNull()
            etternavn shouldBe response.etternavn
            navVeileder shouldBe response.navVeileder.shouldNotBeNull()
            navEnhet shouldBe response.navEnhet.shouldNotBeNull()
            telefon shouldBe response.telefon.shouldNotBeNull()
            epost shouldBe response.epost.shouldNotBeNull()
            erSkjermet shouldBe response.erSkjermet
            adresse shouldBe response.adresse.shouldNotBeNull()
            adressebeskyttelse shouldBe response.adressebeskyttelse.shouldNotBeNull()
            oppfolgingsperioder shouldBe response.oppfolgingsperioder.shouldNotBeEmpty()
            innsatsgruppe shouldBe response.innsatsgruppe.shouldNotBeNull()
            erDigital shouldBe response.erDigital
        }
    }
}
