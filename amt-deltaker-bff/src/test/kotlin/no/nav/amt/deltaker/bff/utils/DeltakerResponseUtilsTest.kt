package no.nav.amt.deltaker.bff.utils

import io.kotest.matchers.shouldBe
import no.nav.amt.deltaker.bff.model.NavBrukerModel
import no.nav.amt.deltaker.bff.navtiltakskoordinator.api.response.DeltakerResponseUtils
import no.nav.amt.lib.models.person.address.Adressebeskyttelse
import org.junit.jupiter.api.Test

class DeltakerResponseUtilsTest {
    @Test
    fun `visningsnavn - adressebeskyttet og ikke tilgang - sensurerer navn`() {
        val navBruker = lagNavBruker(adressebeskyttelse = Adressebeskyttelse.FORTROLIG)
        val (fornavn, mellomnavn, etternavn) = navBruker.getVisningsnavn(false)

        fornavn shouldBe DeltakerResponseUtils.ADRESSEBESKYTTET_PLACEHOLDER_NAVN
        mellomnavn shouldBe null
        etternavn shouldBe ""
    }

    @Test
    fun `visningsnavn - adressebeskyttet og tilgang - sensurerer ikke navn`() {
        val navBruker = lagNavBruker(adressebeskyttelse = Adressebeskyttelse.FORTROLIG)

        val (fornavn, mellomnavn, etternavn) = navBruker.getVisningsnavn(true)

        fornavn shouldBe navBruker.fornavn
        mellomnavn shouldBe navBruker.mellomnavn
        etternavn shouldBe navBruker.etternavn
    }

    @Test
    fun `visningsnavn - ikke adressebeskyttet og tilgang - sensurerer ikke navn`() {
        val navBruker = lagNavBruker()
        val (fornavn, mellomnavn, etternavn) = navBruker.getVisningsnavn(true)

        fornavn shouldBe navBruker.fornavn
        mellomnavn shouldBe navBruker.mellomnavn
        etternavn shouldBe navBruker.etternavn
    }

    @Test
    fun `visningsnavn - skjermet og ikke tilgang - sensurerer navn`() {
        val navBruker = lagNavBruker(erSkjermet = true)
        val (fornavn, mellomnavn, etternavn) = navBruker.getVisningsnavn(false)

        fornavn shouldBe DeltakerResponseUtils.SKJERMET_PERSON_PLACEHOLDER_NAVN
        mellomnavn shouldBe null
        etternavn shouldBe ""
    }

    @Test
    fun `visningsnavn - skjermet og tilgang - sensurerer ikke navn`() {
        val navBruker = lagNavBruker(erSkjermet = true)

        val (fornavn, mellomnavn, etternavn) = navBruker.getVisningsnavn(true)

        fornavn shouldBe navBruker.fornavn
        mellomnavn shouldBe navBruker.mellomnavn
        etternavn shouldBe navBruker.etternavn
    }

    @Test
    fun `visningsnavn - ikke skjermet og tilgang - sensurerer ikke navn`() {
        val navBruker = lagNavBruker()
        val (fornavn, mellomnavn, etternavn) = navBruker.getVisningsnavn(true)

        fornavn shouldBe navBruker.fornavn
        mellomnavn shouldBe navBruker.mellomnavn
        etternavn shouldBe navBruker.etternavn
    }

    private fun lagNavBruker(
        erSkjermet: Boolean = false,
        adressebeskyttelse: Adressebeskyttelse? = null,
    ): NavBrukerModel = TestData.lagNavBrukerModel(
        erSkjermet = erSkjermet,
        adressebeskyttelse = adressebeskyttelse,
    )
}
