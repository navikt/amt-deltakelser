package no.nav.amt.distribusjon.tiltakshendelse

import no.nav.amt.distribusjon.amtdeltaker.AmtDeltakerClient
import no.nav.amt.distribusjon.hendelse.model.Hendelse
import no.nav.amt.distribusjon.tiltakshendelse.TiltakshendelseService.Companion.UTKAST_TIL_PAMELDING_TEKST
import no.nav.amt.distribusjon.tiltakshendelse.model.Tiltakshendelse
import no.nav.amt.lib.models.arrangor.melding.Forslag
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import no.nav.amt.lib.models.hendelse.HendelseType
import no.nav.amt.lib.utils.database.Database
import org.slf4j.LoggerFactory
import java.util.UUID

class TiltakshendelseService(
    private val tiltakshendelseRepository: TiltakshendelseRepository,
    private val amtDeltakerClient: AmtDeltakerClient,
    private val tiltakshendelseProducer: TiltakshendelseProducer,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        const val UTKAST_TIL_PAMELDING_TEKST = "Utkast til påmelding"
    }

    fun handleHendelse(hendelse: Hendelse) {
        if (tiltakshendelseRepository.getByHendelseId(hendelse.id).isSuccess) {
            log.info("Tiltakshendelse for hendelse ${hendelse.id} er allerede håndtert.")
            return
        }

        when (hendelse.payload) {
            is HendelseType.OpprettUtkast -> {
                opprettStartHendelse(hendelse)
            }

            is HendelseType.AvbrytUtkast,
            is HendelseType.InnbyggerGodkjennUtkast,
            is HendelseType.NavGodkjennUtkast,
            -> {
                stoppUtkastHendelse(hendelse)
            }

            else -> {}
        }
    }

    suspend fun handleForslag(forslag: Forslag) {
        when (forslag.status) {
            is Forslag.Status.VenterPaSvar -> opprettStartHendelse(forslag)

            is Forslag.Status.Godkjent,
            is Forslag.Status.Avvist,
            is Forslag.Status.Tilbakekalt,
            is Forslag.Status.Erstattet,
            -> stoppForslagHendelse(forslag.id)
        }
    }

    suspend fun stoppForslagHendelse(forslagId: UUID) {
        tiltakshendelseRepository.getForslagHendelse(forslagId).onSuccess {
            val inaktivertHendelse = it.copy(
                aktiv = false,
            )
            Database.transaction {
                lagreOgDistribuer(inaktivertHendelse)
            }
        }
    }

    fun reproduser(id: UUID) {
        val tiltakshendelse = tiltakshendelseRepository.get(id).getOrThrow()
        tiltakshendelseProducer.produce(tiltakshendelse)
        log.info("Reproduserte tiltakshendelse $id")
    }

    private fun opprettStartHendelse(hendelse: Hendelse) {
        lagreOgDistribuer(hendelse.toTiltakshendelse())
    }

    private suspend fun opprettStartHendelse(forslag: Forslag) {
        val deltaker = amtDeltakerClient.getDeltaker(forslag.deltakerId)

        Database.transaction {
            lagreOgDistribuer(
                forslag.toHendelse(
                    personIdent = deltaker.navBruker.personident,
                    tiltakskode = deltaker.deltakerliste.tiltakstype.tiltakskode,
                    aktiv = true,
                ),
            )
        }
    }

    private fun stoppUtkastHendelse(hendelse: Hendelse) {
        tiltakshendelseRepository.getHendelse(hendelse.deltaker.id, Tiltakshendelse.Type.UTKAST).onSuccess {
            val inaktivertHendelse = it.copy(
                aktiv = false,
                hendelser = it.hendelser.plus(hendelse.id),
            )
            lagreOgDistribuer(inaktivertHendelse)
        }
    }

    private fun lagreOgDistribuer(tiltakshendelse: Tiltakshendelse) {
        tiltakshendelseRepository.upsert(tiltakshendelse)
        tiltakshendelseProducer.produce(tiltakshendelse)
        log.info("Upsertet tiltakshendelse ${tiltakshendelse.id}")
    }
}

fun Forslag.toHendelse(
    personIdent: String,
    tiltakskode: Tiltakskode,
    aktiv: Boolean,
) = Tiltakshendelse(
    id = UUID.randomUUID(),
    type = Tiltakshendelse.Type.FORSLAG,
    deltakerId = deltakerId,
    forslagId = id,
    hendelser = emptyList(),
    personident = personIdent,
    aktiv = aktiv,
    tekst = getForslagHendelseTekst(this),
    tiltakskode = tiltakskode,
    opprettet = opprettet,
)

fun Hendelse.toTiltakshendelse() = when (this.payload) {
    is HendelseType.OpprettUtkast -> Tiltakshendelse(
        id = UUID.randomUUID(),
        type = Tiltakshendelse.Type.UTKAST,
        deltakerId = this.deltaker.id,
        forslagId = null,
        hendelser = listOf(this.id),
        personident = this.deltaker.personident,
        aktiv = true,
        tekst = UTKAST_TIL_PAMELDING_TEKST,
        tiltakskode = this.deltaker.deltakerliste.tiltak.tiltakskode,
        opprettet = this.opprettet,
    )

    else -> throw IllegalArgumentException(
        "Kan ikke lage tiltakshendelse for hendelse ${this.id} av type ${this.payload.javaClass.simpleName}",
    )
}

fun getForslagHendelseTekst(forslag: Forslag): String {
    val forslagtekst = "Forslag:"
    return when (forslag.endring) {
        is Forslag.ForlengDeltakelse -> "$forslagtekst Forleng deltakelse"
        is Forslag.AvsluttDeltakelse -> "$forslagtekst Avslutt deltakelse"
        is Forslag.IkkeAktuell -> "$forslagtekst Er ikke aktuell"
        is Forslag.Deltakelsesmengde -> "$forslagtekst Deltakelsesmengde"
        is Forslag.Startdato -> "$forslagtekst Oppstartsdato"
        is Forslag.Sluttdato -> "$forslagtekst Sluttdato"
        is Forslag.Sluttarsak -> "$forslagtekst Sluttårsak"
        is Forslag.FjernOppstartsdato -> "$forslagtekst Fjern oppstartsdato"
        is Forslag.EndreAvslutning -> "$forslagtekst Endre avslutning"
    }
}
