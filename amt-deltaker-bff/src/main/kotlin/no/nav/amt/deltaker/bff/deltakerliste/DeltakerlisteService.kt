package no.nav.amt.deltaker.bff.deltakerliste

import java.time.LocalDate
import java.time.Period
import java.util.UUID

class DeltakerlisteService(
    private val repository: DeltakerlisteRepository,
) {
    companion object {
        val tiltakskoordinatorGraceperiode: Period = Period.ofDays(14)
    }

    fun get(id: UUID) = repository.get(id)

    fun verifiserTilgjengeligDeltakerliste(id: UUID): Deltakerliste {
        val deltakerliste = get(id).getOrThrow()

        deltakerliste.sluttDato?.let { sluttdato ->
            if (LocalDate.now().isAfter(sluttdato.plus(tiltakskoordinatorGraceperiode))) {
                throw DeltakerlisteStengtException("Deltakerlisten $id er stengt for tiltakskoordinator")
            }
        }

        return deltakerliste
    }
}
