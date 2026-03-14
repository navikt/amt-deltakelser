package no.nav.tiltaksarrangor.melding.forslag

import no.nav.security.token.support.core.api.ProtectedWithClaims
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
import no.nav.tiltaksarrangor.utils.Issuer
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/tiltaksarrangor/deltaker/{deltakerId}/forslag")
class ForslagAPI(
	private val tilgangskontrollService: MeldingTilgangskontrollService,
	private val forslagService: ForslagService,
) {
	@PostMapping("/forleng")
	@ProtectedWithClaims(issuer = Issuer.TOKEN_X)
	fun forleng(
		@PathVariable deltakerId: UUID,
		@RequestBody request: ForlengDeltakelseRequest,
	): AktivtForslagResponse = opprettForslag(deltakerId, request)

	@PostMapping("/avslutt")
	@ProtectedWithClaims(issuer = Issuer.TOKEN_X)
	fun avslutt(
		@PathVariable deltakerId: UUID,
		@RequestBody request: AvsluttDeltakelseRequest,
	): AktivtForslagResponse = opprettForslag(deltakerId, request)

	@PostMapping("/endre-avslutning")
	@ProtectedWithClaims(issuer = Issuer.TOKEN_X)
	fun avslutt(
		@PathVariable deltakerId: UUID,
		@RequestBody request: EndreAvslutningRequest,
	): AktivtForslagResponse = opprettForslag(deltakerId, request)

	@PostMapping("/ikke-aktuell")
	@ProtectedWithClaims(issuer = Issuer.TOKEN_X)
	fun ikkeAktuell(
		@PathVariable deltakerId: UUID,
		@RequestBody request: IkkeAktuellRequest,
	): AktivtForslagResponse = opprettForslag(deltakerId, request)

	@PostMapping("/deltakelsesmengde")
	@ProtectedWithClaims(issuer = Issuer.TOKEN_X)
	fun deltakelsesmengde(
		@PathVariable deltakerId: UUID,
		@RequestBody request: DeltakelsesmengdeRequest,
	): AktivtForslagResponse = opprettForslag(deltakerId, request)

	@PostMapping("/sluttdato")
	@ProtectedWithClaims(issuer = Issuer.TOKEN_X)
	fun sluttdato(
		@PathVariable deltakerId: UUID,
		@RequestBody request: SluttdatoRequest,
	): AktivtForslagResponse = opprettForslag(deltakerId, request)

	@PostMapping("/startdato")
	@ProtectedWithClaims(issuer = Issuer.TOKEN_X)
	fun sluttdato(
		@PathVariable deltakerId: UUID,
		@RequestBody request: StartdatoRequest,
	): AktivtForslagResponse = opprettForslag(deltakerId, request)

	@PostMapping("/sluttarsak")
	@ProtectedWithClaims(issuer = Issuer.TOKEN_X)
	fun sluttdato(
		@PathVariable deltakerId: UUID,
		@RequestBody request: SluttarsakRequest,
	): AktivtForslagResponse = opprettForslag(deltakerId, request)

	@PostMapping("/fjern-oppstartsdato")
	@ProtectedWithClaims(issuer = Issuer.TOKEN_X)
	fun fjernOppstartsdato(
		@PathVariable deltakerId: UUID,
		@RequestBody request: FjernOppstartsdatoRequest,
	): AktivtForslagResponse = opprettForslag(deltakerId, request)

	@PostMapping("/{forslagId}/tilbakekall")
	@ProtectedWithClaims(issuer = Issuer.TOKEN_X)
	fun tilbakekall(
		@PathVariable deltakerId: UUID,
		@PathVariable forslagId: UUID,
	) {
		tilgangskontrollService.medTilgangTilAnsattOgDeltaker(deltakerId) { ansatt, _, _ ->
			forslagService.tilbakekall(forslagId, ansatt)
		}
	}

	private fun opprettForslag(deltakerId: UUID, request: ForslagRequest) =
		tilgangskontrollService.medTilgangTilAnsattOgDeltaker(deltakerId) { ansatt, deltaker, _ ->
			val forslag = forslagService.opprettForslag(
				request,
				ansatt,
				deltaker,
			)
			forslag.tilAktivtForslagResponse()
		}
}
