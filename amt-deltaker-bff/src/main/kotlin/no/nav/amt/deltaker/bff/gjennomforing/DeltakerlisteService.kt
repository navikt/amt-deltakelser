package no.nav.amt.deltaker.bff.gjennomforing

import no.nav.amt.deltaker.bff.model.Deltakerliste
import java.time.LocalDate
import java.time.Period
import java.util.UUID

class DeltakerlisteService(
    private val deltakerlisteRepository: DeltakerlisteRepository,
) {
    companion object {
        val tiltakskoordinatorGraceperiode: Period = Period.ofDays(14)
    }

    fun verifiserTilgjengeligDeltakerliste(id: UUID): Deltakerliste {
        val deltakerliste = deltakerlisteRepository.get(id).getOrThrow()

        deltakerliste.sluttDato?.let { sluttdato ->
            if (LocalDate.now().isAfter(sluttdato.plus(tiltakskoordinatorGraceperiode))) {
                throw DeltakerlisteStengtException("Deltakerlisten $id er stengt for tiltakskoordinator")
            }
        }

        return deltakerliste
    }
}
