package no.nav.amt.deltaker.deltaker

import no.nav.amt.deltaker.deltaker.model.Deltaker
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.tiltakskoordinator.EndringFraTiltakskoordinator
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

object DeltakerUtils {
    fun nyDeltakerStatus(
        type: DeltakerStatus.Type,
        aarsak: DeltakerStatus.Aarsak? = null,
        gyldigFra: LocalDateTime = LocalDateTime.now(),
    ) = DeltakerStatus(
        id = UUID.randomUUID(),
        type = type,
        aarsak = aarsak,
        gyldigFra = gyldigFra,
        gyldigTil = null,
        opprettet = LocalDateTime.now(),
    )

    private fun EndringFraTiltakskoordinator.Avslag.Aarsak.toDeltakerStatusAarsak() = DeltakerStatus.Aarsak(
        type = DeltakerStatus.Aarsak.Type.valueOf(this.type.name),
        beskrivelse = beskrivelse,
    )

    fun sjekkEndringUtfall(deltaker: Deltaker, endring: EndringFraTiltakskoordinator.Endring): Result<Deltaker> {
        fun createResult(gyldigEndring: Boolean, deltakerOnSuccess: () -> Deltaker) = if (gyldigEndring) {
            Result.success(deltakerOnSuccess())
        } else {
            Result.failure(IllegalStateException("Ingen gyldig deltakerendring"))
        }

        if (deltaker.status.type == DeltakerStatus.Type.FEILREGISTRERT) {
            return Result.failure(IllegalStateException("Ingen gyldig deltakerendring"))
        }

        if (!deltaker.navBruker.harAktivOppfolgingsperiode) {
            return Result.failure(IllegalStateException("Nav-bruker mangler aktiv oppfølgingsperiode"))
        }

        return when (endring) {
            is EndringFraTiltakskoordinator.SettPaaVenteliste -> {
                createResult(deltaker.status.type != DeltakerStatus.Type.VENTELISTE) {
                    deltaker.copy(
                        status = nyDeltakerStatus(DeltakerStatus.Type.VENTELISTE),
                        startdato = null,
                        sluttdato = null,
                    )
                }
            }

            is EndringFraTiltakskoordinator.DelMedArrangor -> {
                createResult(deltaker.status.type == DeltakerStatus.Type.SOKT_INN && !deltaker.erManueltDeltMedArrangor) {
                    deltaker.copy(erManueltDeltMedArrangor = true)
                }
            }

            is EndringFraTiltakskoordinator.TildelPlass -> {
                checkNotNull(deltaker.deltakerliste.startDato) { "Kursdeltaker mangler startdato" }

                val startDateInFuture = deltaker.deltakerliste.startDato.isAfter(LocalDate.now())

                createResult(true) {
                    deltaker.copy(
                        status = nyDeltakerStatus(DeltakerStatus.Type.VENTER_PA_OPPSTART),
                        startdato = deltaker.deltakerliste.startDato.takeIf { startDateInFuture },
                        sluttdato = deltaker.deltakerliste.sluttDato?.takeIf { startDateInFuture },
                    )
                }
            }

            is EndringFraTiltakskoordinator.Avslag -> {
                createResult(
                    deltaker.status.type in listOf(
                        DeltakerStatus.Type.SOKT_INN,
                        DeltakerStatus.Type.VURDERES,
                        DeltakerStatus.Type.VENTELISTE,
                        DeltakerStatus.Type.VENTER_PA_OPPSTART,
                    ),
                ) {
                    deltaker.copy(
                        status = nyDeltakerStatus(type = DeltakerStatus.Type.IKKE_AKTUELL, endring.aarsak.toDeltakerStatusAarsak()),
                        startdato = null,
                        sluttdato = null,
                    )
                }
            }
        }
    }
}
