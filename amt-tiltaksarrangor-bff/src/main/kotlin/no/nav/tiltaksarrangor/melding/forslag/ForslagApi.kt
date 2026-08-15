package no.nav.tiltaksarrangor.melding.forslag

import no.nav.tiltaksarrangor.melding.MeldingTilgangskontrollService
import no.nav.tiltaksarrangor.melding.forslag.request.AvsluttDeltakelseRequest
import no.nav.tiltaksarrangor.melding.forslag.request.DeltakelsesmengdeRequest
import no.nav.tiltaksarrangor.melding.forslag.request.EndreAvslutningRequest
import no.nav.tiltaksarrangor.melding.forslag.request.FjernOppstartsdatoRequest
import no.nav.tiltaksarrangor.melding.forslag.request.ForlengDeltakelseRequest
import no.nav.tiltaksarrangor.melding.forslag.request.ForslagRequest
import no.nav.tiltaksarrangor.melding.forslag.request.IkkeAktuellRequest
import no.nav.tiltaksarrangor.melding.forslag.request.SluttarsakRequest
import no.nav.tiltaksarrangor.melding.forslag.request.SluttdatoRequest
import no.nav.tiltaksarrangor.melding.forslag.request.StartdatoRequest
import no.nav.tiltaksarrangor.utils.personIdent
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/tiltaksarrangor/deltaker/{deltakerId}/forslag")
class ForslagApi(
    private val tilgangskontrollService: MeldingTilgangskontrollService,
    private val forslagService: ForslagService,
) {
    @PostMapping("/forleng")
    fun forleng(
        @PathVariable deltakerId: UUID,
        @RequestBody request: ForlengDeltakelseRequest,
        @AuthenticationPrincipal jwt: Jwt,
    ): AktivtForslagResponse = opprettForslag(
        deltakerId = deltakerId,
        request = request,
        personIdent = jwt.personIdent(),
    )

    @PostMapping("/avslutt")
    fun avslutt(
        @PathVariable deltakerId: UUID,
        @RequestBody request: AvsluttDeltakelseRequest,
        @AuthenticationPrincipal jwt: Jwt,
    ): AktivtForslagResponse = opprettForslag(
        deltakerId = deltakerId,
        request = request,
        personIdent = jwt.personIdent(),
    )

    @PostMapping("/endre-avslutning")
    fun avslutt(
        @PathVariable deltakerId: UUID,
        @RequestBody request: EndreAvslutningRequest,
        @AuthenticationPrincipal jwt: Jwt,
    ): AktivtForslagResponse = opprettForslag(
        deltakerId = deltakerId,
        request = request,
        personIdent = jwt.personIdent(),
    )

    @PostMapping("/ikke-aktuell")
    fun ikkeAktuell(
        @PathVariable deltakerId: UUID,
        @RequestBody request: IkkeAktuellRequest,
        @AuthenticationPrincipal jwt: Jwt,
    ): AktivtForslagResponse = opprettForslag(
        deltakerId = deltakerId,
        request = request,
        personIdent = jwt.personIdent(),
    )

    @PostMapping("/deltakelsesmengde")
    fun deltakelsesmengde(
        @PathVariable deltakerId: UUID,
        @RequestBody request: DeltakelsesmengdeRequest,
        @AuthenticationPrincipal jwt: Jwt,
    ): AktivtForslagResponse = opprettForslag(
        deltakerId = deltakerId,
        request = request,
        personIdent = jwt.personIdent(),
    )

    @PostMapping("/sluttdato")
    fun sluttdato(
        @PathVariable deltakerId: UUID,
        @RequestBody request: SluttdatoRequest,
        @AuthenticationPrincipal jwt: Jwt,
    ): AktivtForslagResponse = opprettForslag(
        deltakerId = deltakerId,
        request = request,
        personIdent = jwt.personIdent(),
    )

    @PostMapping("/startdato")
    fun sluttdato(
        @PathVariable deltakerId: UUID,
        @RequestBody request: StartdatoRequest,
        @AuthenticationPrincipal jwt: Jwt,
    ): AktivtForslagResponse = opprettForslag(
        deltakerId = deltakerId,
        request = request,
        personIdent = jwt.personIdent(),
    )

    @PostMapping("/sluttarsak")
    fun sluttdato(
        @PathVariable deltakerId: UUID,
        @RequestBody request: SluttarsakRequest,
        @AuthenticationPrincipal jwt: Jwt,
    ): AktivtForslagResponse = opprettForslag(
        deltakerId = deltakerId,
        request = request,
        personIdent = jwt.personIdent(),
    )

    @PostMapping("/fjern-oppstartsdato")
    fun fjernOppstartsdato(
        @PathVariable deltakerId: UUID,
        @RequestBody request: FjernOppstartsdatoRequest,
        @AuthenticationPrincipal jwt: Jwt,
    ): AktivtForslagResponse = opprettForslag(
        deltakerId = deltakerId,
        request = request,
        personIdent = jwt.personIdent(),
    )

    @PostMapping("/{forslagId}/tilbakekall")
    fun tilbakekall(
        @PathVariable deltakerId: UUID,
        @PathVariable forslagId: UUID,
        @AuthenticationPrincipal jwt: Jwt,
    ) {
        tilgangskontrollService.medTilgangTilAnsattOgDeltaker(
            deltakerId = deltakerId,
            personIdent = jwt.personIdent(),
        ) { ansatt, _, _ ->
            forslagService.tilbakekall(
                id = forslagId,
                ansatt = ansatt,
            )
        }
    }

    private fun opprettForslag(
        deltakerId: UUID,
        request: ForslagRequest,
        personIdent: String,
    ) = tilgangskontrollService.medTilgangTilAnsattOgDeltaker(
        deltakerId = deltakerId,
        personIdent = personIdent,
    ) { ansatt, deltaker, _ ->
        val forslag = forslagService.opprettForslag(
            request = request,
            ansatt = ansatt,
            deltaker = deltaker,
        )
        forslag.tilAktivtForslagResponse()
    }
}
