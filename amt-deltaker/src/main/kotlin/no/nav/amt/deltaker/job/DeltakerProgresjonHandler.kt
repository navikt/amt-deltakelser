package no.nav.amt.deltaker.job

import no.nav.amt.deltaker.deltaker.db.DeltakerStatusRepository
import no.nav.amt.deltaker.deltaker.extensions.harIkkeStartet
import no.nav.amt.deltaker.deltaker.model.Deltaker
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

object DeltakerProgresjonHandler {
    private val log = LoggerFactory.getLogger(javaClass)

    fun getAvsluttendeStatusUtfall(deltakere: List<Deltaker>): List<Deltaker> {
        if (deltakere.isEmpty()) {
            return emptyList()
        }

        val fremtidigAvsluttendeStatusList = DeltakerStatusRepository.getAvsluttendeDeltakerStatuserForOppdatering(
            deltakere.map { it.id }.toSet(),
        )

        val deltakereMedFremtidigeAvsluttendeStatus = deltakere
            .mapNotNull { deltaker ->
                fremtidigAvsluttendeStatusList
                    .find { status -> status.deltakerId == deltaker.id }
                    ?.let {
                        log.info("Endret status for ${deltaker.id} til ${it.deltakerStatus.type}.")
                        // Denne forutsetter at endring på deltakerliste er upsertet noe den ikke er
                        deltaker.copy(status = it.deltakerStatus, sluttdato = getOppdatertSluttdato(deltaker))
                    }
            }

        val deltakereUtenFremtidigStatus = deltakere
            .filter { deltaker -> deltakereMedFremtidigeAvsluttendeStatus.none { it.id == deltaker.id } }

        val deltakereMedAvsluttendeStatus = listOf(
            getDeltakereSomSkalBliAvbrytUtkast(deltakereUtenFremtidigStatus),
            getDeltakereSomSkalBliIkkeAktuell(deltakereUtenFremtidigStatus),
            getDeltakereSomSkalAvbrytesForAvbruttDeltakerliste(deltakereUtenFremtidigStatus),
            getDeltakereSomHarSluttet(deltakereUtenFremtidigStatus),
            getDeltakereSomSkalFullfores(deltakereUtenFremtidigStatus),
        ).flatten()

        val deltakerEndringsUtfall = deltakereMedAvsluttendeStatus + deltakereMedFremtidigeAvsluttendeStatus
        require(deltakerEndringsUtfall.size == deltakerEndringsUtfall.distinctBy { it.id }.size) {
            "Deltakere kunne ikke få avsluttende status fordi de fikk to statuser: ${deltakerEndringsUtfall.map { it.id }}"
        }

        return deltakerEndringsUtfall
    }

    fun tilDeltar(deltakere: List<Deltaker>): List<Deltaker> = deltakere
        .map { deltaker -> deltaker.medNyStatus(DeltakerStatus.Type.DELTAR) }
        .also { log.info("Endret status til DELTAR for ${deltakere.size}") }

    private fun getDeltakereSomSkalFullfores(deltakere: List<Deltaker>): List<Deltaker> {
        val skalBliFullfort = deltakere
            .filter { it.status.type == DeltakerStatus.Type.DELTAR }
            .filter { it.deltakerliste.harFellesAvslutning }
            .filter { !it.deltakerliste.erAvlystEllerAvbrutt() }
            .map {
                it
                    .medNyStatus(DeltakerStatus.Type.FULLFORT, getSluttarsak(it))
                    .medNySluttdato(getOppdatertSluttdato(it))
            }
        log.info("Endret status til FULLFØRT for ${skalBliFullfort.size}")

        return skalBliFullfort
    }

    private fun getDeltakereSomSkalAvbrytesForAvbruttDeltakerliste(deltakere: List<Deltaker>): List<Deltaker> {
        val skalBliAvbrutt = deltakere
            .filter { it.status.type == DeltakerStatus.Type.DELTAR }
            .filter { it.deltakerliste.harFellesAvslutning }
            .filter { it.deltakerliste.erAvlystEllerAvbrutt() }
            .map {
                it
                    .medNyStatus(DeltakerStatus.Type.AVBRUTT, getSluttarsak(it))
                    .medNySluttdato(getOppdatertSluttdato(it))
            }
        log.info("Endret status til AVBRUTT for ${skalBliAvbrutt.size}")

        return skalBliAvbrutt
    }

    private fun getDeltakereSomHarSluttet(deltakere: List<Deltaker>): List<Deltaker> {
        val skalBliHarSluttet = deltakere
            .filter { it.status.type == DeltakerStatus.Type.DELTAR }
            .filter { !it.deltakerliste.harFellesAvslutning }
            .map {
                it
                    .medNyStatus(DeltakerStatus.Type.HAR_SLUTTET, getSluttarsak(it))
                    .medNySluttdato(getOppdatertSluttdato(it))
            }

        log.info("Endret status til HAR SLUTTET for ${skalBliHarSluttet.size}")

        return skalBliHarSluttet
    }

    private fun getDeltakereSomSkalBliIkkeAktuell(deltakere: List<Deltaker>): List<Deltaker> {
        val skalBliIkkeAktuell = deltakere
            .filter { it.status.harIkkeStartet() }
            .map {
                it
                    .medNyStatus(DeltakerStatus.Type.IKKE_AKTUELL, getSluttarsak(it))
                    .medNySluttdato(null)
                    .copy(startdato = null)
            }
        log.info("Endret status til IKKE AKTUELL for ${skalBliIkkeAktuell.size}")

        return skalBliIkkeAktuell
    }

    private fun getDeltakereSomSkalBliAvbrytUtkast(deltakere: List<Deltaker>): List<Deltaker> {
        val utkastSomSkalAvbrytes = deltakere
            .filter { it.status.type == DeltakerStatus.Type.UTKAST_TIL_PAMELDING }
            .map {
                it.medNyStatus(
                    DeltakerStatus.Type.AVBRUTT_UTKAST,
                    // Årsak skal settes selv om gjennomføringen blir avsluttet normalt
                    DeltakerStatus.Aarsak(
                        type = DeltakerStatus.Aarsak.Type.SAMARBEIDET_MED_ARRANGOREN_ER_AVBRUTT,
                        beskrivelse = null,
                    ),
                )
            }

        return utkastSomSkalAvbrytes
    }

    private fun getOppdatertSluttdato(deltaker: Deltaker): LocalDate? =
        if (deltaker.sluttdato == null || deltaker.sluttdato.isAfter(LocalDate.now())) {
            if (deltaker.deltakerliste.sluttDato != null && !deltaker.deltakerliste.sluttDato.isAfter(LocalDate.now())) {
                deltaker.deltakerliste.sluttDato
            } else {
                LocalDate.now()
            }
        } else {
            deltaker.sluttdato
        }

    private fun getSluttarsak(deltaker: Deltaker): DeltakerStatus.Aarsak? = if (deltaker.deltakerliste.erAvlystEllerAvbrutt()) {
        DeltakerStatus.Aarsak(
            type = DeltakerStatus.Aarsak.Type.SAMARBEIDET_MED_ARRANGOREN_ER_AVBRUTT,
            beskrivelse = null,
        )
    } else {
        null
    }

    private fun Deltaker.medNyStatus(status: DeltakerStatus.Type, aarsak: DeltakerStatus.Aarsak? = null) = this.copy(
        status = DeltakerStatus(
            id = UUID.randomUUID(),
            type = status,
            aarsak = aarsak,
            gyldigFra = LocalDateTime.now(),
            gyldigTil = null,
            opprettet = LocalDateTime.now(),
        ),
    )

    private fun Deltaker.medNySluttdato(sluttdato: LocalDate?) = this.copy(sluttdato = sluttdato)
}
