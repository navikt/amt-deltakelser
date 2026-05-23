package no.nav.amt.deltaker.bff.navtiltakskoordinator.api.response

import io.kotest.matchers.shouldBe
import no.nav.amt.internapi.tiltakskoordinator.HandlingFilterValg
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.UUID

class DeltakerResponseTest {
    @Test
    fun `skal returnere true når handlingsfilteret er tomt`() {
        val response = deltakerResponse()

        response.matchesHandlingFilter(emptySet()) shouldBe true
    }

    @Test
    fun `skal returnere true når deltaker matcher NyeDeltakere`() {
        val response = deltakerResponse(erNyDeltaker = true)

        response.matchesHandlingFilter(setOf(HandlingFilterValg.NyeDeltakere)) shouldBe true
    }

    @Test
    fun `skal returnere true når deltaker matcher OppdateringFraNav`() {
        val response = deltakerResponse(harOppdateringFraNav = true)

        response.matchesHandlingFilter(setOf(HandlingFilterValg.OppdateringFraNav)) shouldBe true
    }

    @Test
    fun `skal returnere true når deltaker matcher AktiveForslag`() {
        val response = deltakerResponse(harAktiveForslag = true)

        response.matchesHandlingFilter(setOf(HandlingFilterValg.AktiveForslag)) shouldBe true
    }

    @Test
    fun `skal returnere true når deltaker matcher ett av flere valgte filtre`() {
        val response = deltakerResponse(erNyDeltaker = true)

        response.matchesHandlingFilter(
            setOf(
                HandlingFilterValg.NyeDeltakere,
                HandlingFilterValg.OppdateringFraNav,
            ),
        ) shouldBe true
    }

    @Test
    fun `skal returnere false når deltaker ikke matcher noen valgte filtre`() {
        val handlingFilterValg = setOf(
            HandlingFilterValg.NyeDeltakere,
            HandlingFilterValg.OppdateringFraNav,
            HandlingFilterValg.AktiveForslag,
        )

        val response = deltakerResponse()

        response.matchesHandlingFilter(handlingFilterValg) shouldBe false
    }

    private fun deltakerResponse(
        erNyDeltaker: Boolean = false,
        harOppdateringFraNav: Boolean = false,
        harAktiveForslag: Boolean = false,
    ) = DeltakerResponse(
        id = UUID.randomUUID(),
        fornavn = "Ola",
        mellomnavn = null,
        etternavn = "Nordmann",
        status = DeltakerStatusResponse(type = DeltakerStatus.Type.DELTAR, aarsak = null),
        beskyttelsesmarkering = emptyList(),
        vurdering = null,
        navEnhet = null,
        erManueltDeltMedArrangor = false,
        ikkeDigitalOgManglerAdresse = false,
        harAktiveForslag = harAktiveForslag,
        harOppdateringFraNav = harOppdateringFraNav,
        erNyDeltaker = erNyDeltaker,
        kanEndres = true,
        soktInnDato = LocalDate.now(),
        startdato = null,
        sluttdato = null,
    )
}
