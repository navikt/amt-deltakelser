package no.nav.tiltaksarrangor.melding

import no.nav.amt.lib.utils.unleash.CommonUnleashToggle
import no.nav.tiltaksarrangor.model.exceptions.UnauthorizedException
import no.nav.tiltaksarrangor.repositories.DeltakerRepository
import no.nav.tiltaksarrangor.repositories.model.AnsattDbo
import no.nav.tiltaksarrangor.repositories.model.DeltakerDbo
import no.nav.tiltaksarrangor.repositories.model.DeltakerlisteDbo
import no.nav.tiltaksarrangor.service.AnsattService
import no.nav.tiltaksarrangor.service.TilgangskontrollService
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class MeldingTilgangskontrollService(
    private val ansattService: AnsattService,
    private val deltakerRepository: DeltakerRepository,
    private val tilgangskontrollService: TilgangskontrollService,
    private val unleashToggle: CommonUnleashToggle,
) {
    fun <T> medTilgangTilAnsattOgDeltaker(
        deltakerId: UUID,
        personIdent: String,
        block: (ansatt: AnsattDbo, deltaker: DeltakerDbo, deltakerliste: DeltakerlisteDbo) -> T,
    ): T {
        val ansatt = ansattService.getAnsattMedRoller(personIdent)
        val deltakerMedDeltakerliste = deltakerRepository
            .getDeltakerMedDeltakerliste(deltakerId)
            ?.takeIf { it.deltakerliste.erTilgjengeligForArrangor() }
            ?: throw NoSuchElementException("Fant ikke deltaker med id $deltakerId")

        if (!unleashToggle.erKometMasterForTiltakstype(deltakerMedDeltakerliste.deltakerliste.tiltakskode)) {
            throw UnauthorizedException("Endepunkt er utilgjenglig")
        }

        tilgangskontrollService.verifiserTilgangTilDeltakerOgMeldinger(ansatt, deltakerMedDeltakerliste)

        return block(ansatt, deltakerMedDeltakerliste.deltaker, deltakerMedDeltakerliste.deltakerliste)
    }
}
