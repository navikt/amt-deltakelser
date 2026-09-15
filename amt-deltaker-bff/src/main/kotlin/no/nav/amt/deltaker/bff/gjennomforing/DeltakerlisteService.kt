package no.nav.amt.deltaker.bff.gjennomforing

import no.nav.amt.deltaker.bff.model.Deltakerliste
import java.time.LocalDate
import java.time.Period
import java.util.UUID

class DeltakerlisteService(
    private val deltakerlisteRepository: DeltakerlisteRepository,
) {
    companion object {
        val tiltakskoordinatorGraceperiode: Period = Period.ofMonths(6)
    }

    fun verifiserTilgjengeligDeltakerliste(
        id: UUID,
        today: LocalDate = LocalDate.now(),
    ): Deltakerliste {
        val deltakerliste = deltakerlisteRepository.get(id).getOrThrow()

        deltakerliste.sluttDato?.let { sluttdato ->
            if (today.isAfter(sluttdato.plus(tiltakskoordinatorGraceperiode))) {
                throw DeltakerlisteStengtException("Deltakerlisten $id er stengt for tiltakskoordinator")
            }
        }

        return deltakerliste
    }
}
