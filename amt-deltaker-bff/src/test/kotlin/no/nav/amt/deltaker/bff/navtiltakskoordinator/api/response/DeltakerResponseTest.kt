package no.nav.amt.deltaker.bff.navtiltakskoordinator.api.response

import io.kotest.matchers.shouldBe
import no.nav.amt.internapi.tiltakskoordinator.HandlingFilterValg
import no.nav.amt.internapi.tiltakskoordinator.request.TiltaksKoordinatorDeltakerlisteRequest
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.person.Beskyttelsesmarkering
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.UUID

class DeltakerResponseTest {
    @Test
    fun `matchesHandlingFilter - tomt filter - returnerer true`() {
        val request = request()
        val response = deltakerResponse()

        response.matchesHandlingFilter(request) shouldBe true
    }

    @Test
    fun `matchesHandlingFilter - matcher nye deltakere - returnerer true`() {
        val request = request(handlingFilterValg = setOf(HandlingFilterValg.NyeDeltakere))
        val response = deltakerResponse(erNyDeltaker = true)

        response.matchesHandlingFilter(request) shouldBe true
    }

    @Test
    fun `matchesHandlingFilter - matcher oppdatering fra nav - returnerer true`() {
        val request = request(handlingFilterValg = setOf(HandlingFilterValg.OppdateringFraNav))
        val response = deltakerResponse(harOppdateringFraNav = true)

        response.matchesHandlingFilter(request) shouldBe true
    }

    @Test
    fun `matchesHandlingFilter - matcher aktive forslag - returnerer true`() {
        val request = request(handlingFilterValg = setOf(HandlingFilterValg.AktiveForslag))
        val response = deltakerResponse(harAktiveForslag = true)

        response.matchesHandlingFilter(request) shouldBe true
    }

    @Test
    fun `matchesHandlingFilter - ingen match - returnerer false`() {
        val request = request(
            handlingFilterValg = setOf(
                HandlingFilterValg.NyeDeltakere,
                HandlingFilterValg.OppdateringFraNav,
                HandlingFilterValg.AktiveForslag,
            ),
        )
        val response = deltakerResponse()

        response.matchesHandlingFilter(request) shouldBe false
    }

    private fun request(handlingFilterValg: Set<HandlingFilterValg> = emptySet()) = TiltaksKoordinatorDeltakerlisteRequest(
        gjennomforingId = UUID.randomUUID(),
        handlingFilterValg = handlingFilterValg,
    )

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
        beskyttelsesmarkering = emptyList<Beskyttelsesmarkering>(),
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
