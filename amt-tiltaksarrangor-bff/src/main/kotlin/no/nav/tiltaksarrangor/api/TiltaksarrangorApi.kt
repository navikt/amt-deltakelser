package no.nav.tiltaksarrangor.api

import no.nav.tiltaksarrangor.api.request.RegistrerVurderingRequest
import no.nav.tiltaksarrangor.model.Deltaker
import no.nav.tiltaksarrangor.service.TiltaksarrangorService
import no.nav.tiltaksarrangor.utils.objectMapper
import no.nav.tiltaksarrangor.utils.personIdent
import no.nav.tiltaksarrangor.utils.writePolymorphicListAsString
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/tiltaksarrangor")
class TiltaksarrangorApi(
    private val tiltaksarrangorService: TiltaksarrangorService,
) {
    @GetMapping("/meg/roller")
    fun getMineRoller(): List<String> = tiltaksarrangorService.getMineRoller()

    @GetMapping("/deltaker/{deltakerId}")
    fun getDeltaker(
        @PathVariable deltakerId: UUID,
        @AuthenticationPrincipal jwt: Jwt,
    ): Deltaker = tiltaksarrangorService.getDeltaker(
        personIdent = jwt.personIdent(),
        deltakerId = deltakerId,
    )

    @GetMapping("/deltaker/{deltakerId}/historikk")
    fun getDeltakerhistorikk(
        @PathVariable deltakerId: UUID,
        @AuthenticationPrincipal jwt: Jwt,
    ): String {
        val historikk = tiltaksarrangorService.getDeltakerHistorikk(
            personIdent = jwt.personIdent(),
            deltakerId = deltakerId,
        )
        return objectMapper.writePolymorphicListAsString(historikk)
    }

    @PostMapping("/deltaker/{deltakerId}/endring/{ulestEndringId}/marker-som-lest")
    fun markerSomLest(
        @PathVariable deltakerId: UUID,
        @PathVariable ulestEndringId: UUID,
        @AuthenticationPrincipal jwt: Jwt,
    ) {
        tiltaksarrangorService.markerEndringSomLest(
            personIdent = jwt.personIdent(),
            deltakerId = deltakerId,
            ulestEndringId = ulestEndringId,
        )
    }

    @PostMapping("/deltaker/{deltakerId}/vurdering")
    fun registrerVurdering(
        @PathVariable deltakerId: UUID,
        @RequestBody request: RegistrerVurderingRequest,
        @AuthenticationPrincipal jwt: Jwt,
    ) {
        tiltaksarrangorService.registrerVurdering(
            personIdent = jwt.personIdent(),
            deltakerId = deltakerId,
            request = request,
        )
    }

    @DeleteMapping("/deltaker/{deltakerId}")
    fun fjernDeltaker(
        @PathVariable deltakerId: UUID,
        @AuthenticationPrincipal jwt: Jwt,
    ) {
        tiltaksarrangorService.fjernDeltaker(
            personIdent = jwt.personIdent(),
            deltakerId = deltakerId,
        )
    }
}
