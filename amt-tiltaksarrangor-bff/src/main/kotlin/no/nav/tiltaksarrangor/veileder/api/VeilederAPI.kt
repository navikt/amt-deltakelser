package no.nav.tiltaksarrangor.veileder.api

import no.nav.security.token.support.core.api.ProtectedWithClaims
import no.nav.tiltaksarrangor.service.TokenService
import no.nav.tiltaksarrangor.utils.Issuer
import no.nav.tiltaksarrangor.veileder.model.Deltaker
import no.nav.tiltaksarrangor.veileder.service.VeilederService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/tiltaksarrangor/veileder")
class VeilederAPI(
	private val tokenService: TokenService,
	private val veilederService: VeilederService,
) {
	@GetMapping("/mine-deltakere")
	@ProtectedWithClaims(issuer = Issuer.TOKEN_X)
	fun getMineDeltakere(): List<Deltaker> {
		val personIdent = tokenService.getPersonligIdentTilInnloggetAnsatt()
		return veilederService.getMineDeltakere(personIdent)
	}
}
