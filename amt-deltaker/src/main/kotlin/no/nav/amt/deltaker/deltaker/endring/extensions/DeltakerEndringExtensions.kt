package no.nav.amt.deltaker.deltaker.endring.extensions

import no.nav.amt.deltaker.deltaker.DeltakerUtils.nyDeltakerStatus
import no.nav.amt.deltaker.deltaker.endring.VellykketEndring
import no.nav.amt.deltaker.deltaker.model.Deltaker
import no.nav.amt.lib.models.deltaker.DeltakerEndring
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltaker.deltakelsesmengde.Deltakelsesmengder
import java.util.UUID

fun DeltakerEndring.Endring.oppdaterDeltaker(
    deltaker: Deltaker,
    getDeltakelsemengder: (deltakerId: UUID) -> Deltakelsesmengder,
): Result<VellykketEndring> = runCatching {
    when (this) {
        is DeltakerEndring.Endring.AvsluttDeltakelse -> {
            handleEndring(
                deltaker = deltaker,
                hasChanges = DeltakerEndring.Endring.AvsluttDeltakelse::hasChanges,
                apply = DeltakerEndring.Endring.AvsluttDeltakelse::avsluttDeltakelse,
            )
        }

        is DeltakerEndring.Endring.EndreAvslutning -> {
            handleEndring(
                deltaker = deltaker,
                hasChanges = DeltakerEndring.Endring.EndreAvslutning::hasChanges,
                apply = DeltakerEndring.Endring.EndreAvslutning::endreAvslutning,
            )
        }

        is DeltakerEndring.Endring.AvbrytDeltakelse -> {
            handleEndring(
                deltaker = deltaker,
                hasChanges = DeltakerEndring.Endring.AvbrytDeltakelse::hasChanges,
                apply = DeltakerEndring.Endring.AvbrytDeltakelse::avbrytDeltakelse,
            )
        }

        is DeltakerEndring.Endring.EndreBakgrunnsinformasjon -> {
            handleEndring(
                deltaker = deltaker,
                hasChanges = DeltakerEndring.Endring.EndreBakgrunnsinformasjon::hasChanges,
                apply = DeltakerEndring.Endring.EndreBakgrunnsinformasjon::endreBakgrunnsinformasjon,
            )
        }

        is DeltakerEndring.Endring.EndreDeltakelsesmengde -> {
            handleEndring(
                deltaker = deltaker,
                hasChanges = { this.hasChanges(getDeltakelsemengder(deltaker.id)) },
                apply = DeltakerEndring.Endring.EndreDeltakelsesmengde::endreDeltakelsesmengde,
            )
        }

        is DeltakerEndring.Endring.EndreInnhold -> {
            handleEndring(
                deltaker = deltaker,
                hasChanges = DeltakerEndring.Endring.EndreInnhold::hasChanges,
                apply = DeltakerEndring.Endring.EndreInnhold::endreInnhold,
            )
        }

        is DeltakerEndring.Endring.EndreSluttdato -> {
            handleEndring(
                deltaker = deltaker,
                hasChanges = DeltakerEndring.Endring.EndreSluttdato::hasChanges,
                apply = DeltakerEndring.Endring.EndreSluttdato::endreSluttdato,
            )
        }

        is DeltakerEndring.Endring.EndreSluttarsak -> {
            handleEndring(
                deltaker = deltaker,
                hasChanges = DeltakerEndring.Endring.EndreSluttarsak::hasChanges,
                apply = DeltakerEndring.Endring.EndreSluttarsak::endreSluttarsak,
            )
        }

        is DeltakerEndring.Endring.EndreStartdato -> {
            handleEndring(
                deltaker = deltaker,
                hasChanges = DeltakerEndring.Endring.EndreStartdato::hasChanges,
                apply = {
                    this.endreStartdato(
                        deltaker = deltaker,
                        deltakelsesmengder = getDeltakelsemengder(deltaker.id),
                    )
                },
            )
        }

        is DeltakerEndring.Endring.ForlengDeltakelse -> {
            handleEndring(
                deltaker = deltaker,
                hasChanges = DeltakerEndring.Endring.ForlengDeltakelse::hasChanges,
                apply = DeltakerEndring.Endring.ForlengDeltakelse::forlengDeltakelse,
            )
        }

        is DeltakerEndring.Endring.IkkeAktuell -> {
            handleEndring(
                deltaker = deltaker,
                hasChanges = DeltakerEndring.Endring.IkkeAktuell::hasChanges,
                apply = DeltakerEndring.Endring.IkkeAktuell::ikkeAktuell,
            )
        }

        is DeltakerEndring.Endring.ReaktiverDeltakelse -> {
            handleEndring(
                deltaker = deltaker,
                hasChanges = { deltaker.status.type == DeltakerStatus.Type.IKKE_AKTUELL },
                apply = {
                    val nyStatus = if (deltaker.deltakerliste.deltakelserMaaGodkjennes) {
                        nyDeltakerStatus(DeltakerStatus.Type.SOKT_INN)
                    } else {
                        nyDeltakerStatus(DeltakerStatus.Type.VENTER_PA_OPPSTART)
                    }

                    VellykketEndring(
                        deltaker.copy(
                            status = nyStatus,
                            startdato = null,
                            sluttdato = null,
                        ),
                    )
                },
            )
        }

        is DeltakerEndring.Endring.FjernOppstartsdato -> {
            handleEndring(
                deltaker = deltaker,
                hasChanges = { deltaker.startdato != null },
                apply = {
                    VellykketEndring(
                        deltaker.copy(
                            startdato = null,
                            sluttdato = null,
                        ),
                    )
                },
            )
        }
    }
}

private inline fun <T : DeltakerEndring.Endring> T.handleEndring(
    deltaker: Deltaker,
    hasChanges: T.(Deltaker) -> Boolean,
    apply: T.(Deltaker) -> VellykketEndring,
): VellykketEndring = if (this.hasChanges(deltaker)) {
    this.apply(deltaker)
} else {
    // denne blir fanget av runCatching og blir ikke kastet videre i klientkoden
    throw IllegalStateException("Ingen gyldig endring")
}
