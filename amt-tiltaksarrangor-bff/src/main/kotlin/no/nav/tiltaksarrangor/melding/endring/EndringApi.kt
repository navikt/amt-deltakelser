package no.nav.tiltaksarrangor.melding.endring

import no.nav.tiltaksarrangor.melding.MeldingTilgangskontrollService
import no.nav.tiltaksarrangor.melding.endring.request.EndringFraArrangorRequest
import no.nav.tiltaksarrangor.melding.endring.request.LeggTilOppstartsdatoRequest
import no.nav.tiltaksarrangor.repositories.model.DeltakerDbo
import no.nav.tiltaksarrangor.repositories.model.DeltakerlisteDbo
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
@RequestMapping("/tiltaksarrangor/deltaker/{deltakerId}/endring")
class EndringApi(
    private val tilgangskontrollService: MeldingTilgangskontrollService,
    private val endringService: EndringService,
) {
    @PostMapping("/legg-til-oppstartsdato")
    fun leggTilOppstartsdato(
        @PathVariable deltakerId: UUID,
        @RequestBody request: LeggTilOppstartsdatoRequest,
        @AuthenticationPrincipal jwt: Jwt,
    ) = opprettEndring(
        deltakerId = deltakerId,
        request = request,
        personIdent = jwt.personIdent(),
    )

    private fun opprettEndring(
        deltakerId: UUID,
        request: EndringFraArrangorRequest,
        personIdent: String,
    ) = tilgangskontrollService.medTilgangTilAnsattOgDeltaker(
        deltakerId = deltakerId,
        personIdent = personIdent,
    ) { ansatt, deltaker, deltakerliste ->
        valider(
            request = request,
            deltaker = deltaker,
            deltakerliste = deltakerliste,
        )
        endringService.endreDeltaker(
            deltaker = deltaker,
            deltakerliste = deltakerliste,
            ansatt = ansatt,
            request = request,
        )
    }

    private fun valider(
        request: EndringFraArrangorRequest,
        deltaker: DeltakerDbo,
        deltakerliste: DeltakerlisteDbo,
    ) {
        when (request) {
            is LeggTilOppstartsdatoRequest -> validerLeggTilOppstartsdato(
                request.startdato,
                request.sluttdato,
                deltaker,
                deltakerliste,
            )
        }
    }
}
