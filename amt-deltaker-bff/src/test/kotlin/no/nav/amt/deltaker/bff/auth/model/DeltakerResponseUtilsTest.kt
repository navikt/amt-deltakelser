package no.nav.amt.deltaker.bff.auth.model

import io.kotest.matchers.shouldBe
import no.nav.amt.deltaker.bff.auth.TiltakskoordinatorTilgangContext
import no.nav.amt.deltaker.bff.tiltakskoordinator.DeltakerResponseUtils
import org.junit.jupiter.api.Test

class DeltakerResponseUtilsTest {
    @Test
    fun `visningsnavn - adressebeskyttet og ikke tilgang - sensurerer navn`(): Unit = with(TiltakskoordinatorTilgangContext()) {
        medFortroligDeltaker()
        val (fornavn, mellomnavn, etternavn) = deltaker.navBruker.getVisningsnavn(false)

        fornavn shouldBe DeltakerResponseUtils.ADRESSEBESKYTTET_PLACEHOLDER_NAVN
        mellomnavn shouldBe null
        etternavn shouldBe ""
    }

    @Test
    fun `visningsnavn - adressebeskyttet og tilgang - sensurerer ikke navn`(): Unit = with(TiltakskoordinatorTilgangContext()) {
        medFortroligDeltaker()

        val (fornavn, mellomnavn, etternavn) = deltaker.navBruker.getVisningsnavn(true)

        fornavn shouldBe deltaker.navBruker.fornavn
        mellomnavn shouldBe deltaker.navBruker.mellomnavn
        etternavn shouldBe deltaker.navBruker.etternavn
    }

    @Test
    fun `visningsnavn - ikke adressebeskyttet og tilgang - sensurerer ikke navn`(): Unit = with(TiltakskoordinatorTilgangContext()) {
        val (fornavn, mellomnavn, etternavn) = deltaker.navBruker.getVisningsnavn(true)

        fornavn shouldBe deltaker.navBruker.fornavn
        mellomnavn shouldBe deltaker.navBruker.mellomnavn
        etternavn shouldBe deltaker.navBruker.etternavn
    }

    @Test
    fun `visningsnavn - skjermet og ikke tilgang - sensurerer navn`(): Unit = with(TiltakskoordinatorTilgangContext()) {
        medSkjermetDeltaker()

        val (fornavn, mellomnavn, etternavn) = deltaker.navBruker.getVisningsnavn(false)

        fornavn shouldBe DeltakerResponseUtils.SKJERMET_PERSON_PLACEHOLDER_NAVN
        mellomnavn shouldBe null
        etternavn shouldBe ""
    }

    @Test
    fun `visningsnavn - skjermet og tilgang - sensurerer ikke navn`(): Unit = with(TiltakskoordinatorTilgangContext()) {
        medSkjermetDeltaker()

        val (fornavn, mellomnavn, etternavn) = deltaker.navBruker.getVisningsnavn(true)

        fornavn shouldBe deltaker.navBruker.fornavn
        mellomnavn shouldBe deltaker.navBruker.mellomnavn
        etternavn shouldBe deltaker.navBruker.etternavn
    }

    @Test
    fun `visningsnavn - ikke skjermet og tilgang - sensurerer ikke navn`(): Unit = with(TiltakskoordinatorTilgangContext()) {
        val (fornavn, mellomnavn, etternavn) = deltaker.navBruker.getVisningsnavn(true)

        fornavn shouldBe deltaker.navBruker.fornavn
        mellomnavn shouldBe deltaker.navBruker.mellomnavn
        etternavn shouldBe deltaker.navBruker.etternavn
    }
}
