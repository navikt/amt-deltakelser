package no.nav.tiltaksarrangor.koordinator.api

import no.nav.tiltaksarrangor.koordinator.model.Deltakerliste
import no.nav.tiltaksarrangor.koordinator.model.LeggTilVeiledereRequest
import no.nav.tiltaksarrangor.koordinator.model.MineDeltakerlister
import no.nav.tiltaksarrangor.koordinator.model.TilgjengeligVeileder
import no.nav.tiltaksarrangor.koordinator.service.KoordinatorService
import no.nav.tiltaksarrangor.utils.personIdent
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/tiltaksarrangor/koordinator")
class KoordinatorApi(
    private val koordinatorService: KoordinatorService,
) {
    @GetMapping("/mine-deltakerlister")
    fun getMineDeltakerlister(
        @AuthenticationPrincipal jwt: Jwt,
    ): MineDeltakerlister = koordinatorService.getMineDeltakerlister(
        personIdent = jwt.personIdent(),
    )

    @GetMapping("/deltakerliste/{deltakerlisteId}")
    fun getDeltakerliste(
        @PathVariable deltakerlisteId: UUID,
        @AuthenticationPrincipal jwt: Jwt,
    ): Deltakerliste = koordinatorService.getDeltakerliste(
        deltakerlisteId = deltakerlisteId,
        personIdent = jwt.personIdent(),
    )

    @GetMapping("/{deltakerlisteId}/veiledere")
    fun getTilgjengeligeVeiledere(
        @PathVariable deltakerlisteId: UUID,
        @AuthenticationPrincipal jwt: Jwt,
    ): List<TilgjengeligVeileder> = koordinatorService.getTilgjengeligeVeiledere(
        deltakerlisteId = deltakerlisteId,
        personIdent = jwt.personIdent(),
    )

    @PostMapping("/veiledere", params = ["deltakerId"])
    fun tildelVeiledereForDeltaker(
        @RequestParam("deltakerId") deltakerId: UUID,
        @RequestBody request: LeggTilVeiledereRequest,
        @AuthenticationPrincipal jwt: Jwt,
    ) {
        koordinatorService.tildelVeiledereForDeltaker(
            deltakerId = deltakerId,
            request = request,
            personIdent = jwt.personIdent(),
        )
    }
}
