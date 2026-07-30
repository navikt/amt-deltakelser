package no.nav.tiltaksarrangor.koordinator.api

import no.nav.tiltaksarrangor.koordinator.model.Deltakerliste
import no.nav.tiltaksarrangor.koordinator.model.LeggTilVeiledereRequest
import no.nav.tiltaksarrangor.koordinator.model.MineDeltakerlister
import no.nav.tiltaksarrangor.koordinator.model.TilgjengeligVeileder
import no.nav.tiltaksarrangor.koordinator.service.KoordinatorService
import no.nav.tiltaksarrangor.service.TokenService
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
    private val tokenService: TokenService,
    private val koordinatorService: KoordinatorService,
) {
    @GetMapping("/mine-deltakerlister")
    fun getMineDeltakerlister(): MineDeltakerlister {
        val personIdent = tokenService.getPersonligIdentTilInnloggetAnsatt()
        return koordinatorService.getMineDeltakerlister(personIdent)
    }

    @GetMapping("/deltakerliste/{deltakerlisteId}")
    fun getDeltakerliste(
        @PathVariable deltakerlisteId: UUID,
    ): Deltakerliste {
        val personIdent = tokenService.getPersonligIdentTilInnloggetAnsatt()
        return koordinatorService.getDeltakerliste(deltakerlisteId, personIdent)
    }

    @GetMapping("/{deltakerlisteId}/veiledere")
    fun getTilgjengeligeVeiledere(
        @PathVariable deltakerlisteId: UUID,
    ): List<TilgjengeligVeileder> {
        val personIdent = tokenService.getPersonligIdentTilInnloggetAnsatt()
        return koordinatorService.getTilgjengeligeVeiledere(deltakerlisteId, personIdent)
    }

    @PostMapping("/veiledere", params = ["deltakerId"])
    fun tildelVeiledereForDeltaker(
        @RequestParam("deltakerId") deltakerId: UUID,
        @RequestBody request: LeggTilVeiledereRequest,
    ) {
        val personIdent = tokenService.getPersonligIdentTilInnloggetAnsatt()
        koordinatorService.tildelVeiledereForDeltaker(deltakerId, request, personIdent)
    }
}
