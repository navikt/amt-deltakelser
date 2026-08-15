package no.nav.tiltaksarrangor.koordinator.api

import no.nav.tiltaksarrangor.koordinator.service.DeltakerlisteAdminService
import no.nav.tiltaksarrangor.utils.personIdent
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/tiltaksarrangor/koordinator/admin")
class DeltakerlisteAdminApi(
    private val deltakerlisteAdminService: DeltakerlisteAdminService,
) {
    @GetMapping("/deltakerlister")
    fun getAlleDeltakerlister(
        @AuthenticationPrincipal jwt: Jwt,
    ) = deltakerlisteAdminService.getAlleDeltakerlister(
        personIdent = jwt.personIdent(),
    )

    @PostMapping("/deltakerliste/{deltakerlisteId}")
    fun leggTilDeltakerliste(
        @PathVariable deltakerlisteId: UUID,
        @AuthenticationPrincipal jwt: Jwt,
    ) = deltakerlisteAdminService.leggTilDeltakerliste(
        deltakerlisteId = deltakerlisteId,
        personIdent = jwt.personIdent(),
    )

    @DeleteMapping("/deltakerliste/{deltakerlisteId}")
    fun fjernDeltakerliste(
        @PathVariable deltakerlisteId: UUID,
        @AuthenticationPrincipal jwt: Jwt,
    ) = deltakerlisteAdminService.fjernDeltakerliste(
        deltakerlisteId = deltakerlisteId,
        personIdent = jwt.personIdent(),
    )
}
