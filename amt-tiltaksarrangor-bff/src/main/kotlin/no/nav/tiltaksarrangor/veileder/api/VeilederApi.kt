package no.nav.tiltaksarrangor.veileder.api

import no.nav.tiltaksarrangor.utils.personIdent
import no.nav.tiltaksarrangor.veileder.model.Deltaker
import no.nav.tiltaksarrangor.veileder.service.VeilederService
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/tiltaksarrangor/veileder")
class VeilederApi(
    private val veilederService: VeilederService,
) {
    @GetMapping("/mine-deltakere")
    fun getMineDeltakere(
        @AuthenticationPrincipal jwt: Jwt,
    ): List<Deltaker> = veilederService.getMineDeltakere(
        personIdent = jwt.personIdent(),
    )
}
