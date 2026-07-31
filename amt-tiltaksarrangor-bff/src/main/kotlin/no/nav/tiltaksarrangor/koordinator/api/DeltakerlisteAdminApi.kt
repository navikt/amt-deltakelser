package no.nav.tiltaksarrangor.koordinator.api

import no.nav.security.token.support.core.api.ProtectedWithClaims
import no.nav.tiltaksarrangor.koordinator.model.AdminDeltakerliste
import no.nav.tiltaksarrangor.koordinator.service.DeltakerlisteAdminService
import no.nav.tiltaksarrangor.service.TokenService
import no.nav.tiltaksarrangor.utils.Issuer
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/tiltaksarrangor/koordinator/admin")
@ProtectedWithClaims(issuer = Issuer.TOKEN_X)
class DeltakerlisteAdminApi(
    private val deltakerlisteAdminService: DeltakerlisteAdminService,
    private val tokenService: TokenService,
) {
    @GetMapping("/deltakerlister")
    fun getAlleDeltakerlister(): List<AdminDeltakerliste> {
        val personIdent = tokenService.getPersonligIdentTilInnloggetAnsatt()
        return deltakerlisteAdminService.getAlleDeltakerlister(personIdent)
    }

    @PostMapping("/deltakerliste/{deltakerlisteId}")
    fun leggTilDeltakerliste(
        @PathVariable deltakerlisteId: UUID,
    ) {
        val personIdent = tokenService.getPersonligIdentTilInnloggetAnsatt()
        return deltakerlisteAdminService.leggTilDeltakerliste(deltakerlisteId, personIdent)
    }

    @DeleteMapping("/deltakerliste/{deltakerlisteId}")
    fun fjernDeltakerliste(
        @PathVariable deltakerlisteId: UUID,
    ) {
        val personIdent = tokenService.getPersonligIdentTilInnloggetAnsatt()
        return deltakerlisteAdminService.fjernDeltakerliste(deltakerlisteId, personIdent)
    }
}
