package no.nav.amt.distribusjon.tiltakshendelse

import no.nav.amt.distribusjon.amtdeltaker.AmtDeltakerClient
import no.nav.amt.distribusjon.hendelse.model.Hendelse
import no.nav.amt.distribusjon.tiltakshendelse.model.Tiltakshendelse
import no.nav.amt.internapi.hendelse.HendelseType
import no.nav.amt.lib.models.arrangor.melding.Forslag
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
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
        const val PRISINFO_TIL_GODKJENNING_TEKST = "Prisinfo til godkjenning"
    }

    fun handleHendelse(hendelse: Hendelse) {
        if (tiltakshendelseRepository.getByHendelseId(hendelse.id).isSuccess) {
            log.info("Tiltakshendelse for hendelse ${hendelse.id} er allerede håndtert.")
            return
        }

        when (hendelse.payload) {
            is HendelseType.OpprettUtkast -> opprettStartHendelse(
                hendelse = hendelse,
                type = Tiltakshendelse.Type.UTKAST,
                tekst = UTKAST_TIL_PAMELDING_TEKST,
            )

            is HendelseType.EnkeltplassEndrePrisinfo -> opprettStartHendelse(
                hendelse = hendelse,
                type = Tiltakshendelse.Type.PRISENDRING,
                tekst = PRISINFO_TIL_GODKJENNING_TEKST,
            )

            is HendelseType.AvbrytUtkast,
            is HendelseType.InnbyggerGodkjennUtkast,
            is HendelseType.NavGodkjennUtkast,
            -> stoppHendelse(
                hendelse = hendelse,
                hendelseType = Tiltakshendelse.Type.UTKAST,
            )

            is HendelseType.EnkeltplassGodkjennPrisendring,
            is HendelseType.EnkeltplassTilbakekallPrisendring,
            -> stoppHendelse(
                hendelse = hendelse,
                hendelseType = Tiltakshendelse.Type.PRISENDRING,
            )

            else -> Unit
        }
    }

    suspend fun handleForslag(forslag: Forslag) {
        when (forslag.status) {
            is Forslag.Status.VenterPaSvar -> {
                if (tiltakshendelseRepository.getForslagHendelse(forslag.id).isSuccess) {
                    log.info("Tiltakshendelse for forslag ${forslag.id} finnes allerede. Ignorerer duplikat VenterPaSvar.")
                    return
                }

                opprettStartHendelse(forslag)
            }

            is Forslag.Status.Godkjent,
            is Forslag.Status.Avvist,
            is Forslag.Status.Tilbakekalt,
            is Forslag.Status.Erstattet,
            -> stoppForslagHendelse(forslag.id)
        }
    }

    fun stoppForslagHendelse(forslagId: UUID) {
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

    fun reproduserOgSettAktivFalse(id: UUID) {
        val tiltakshendelse = tiltakshendelseRepository.get(id).getOrThrow()
        tiltakshendelseProducer.produce(tiltakshendelse.copy(aktiv = false))
        log.info("Reproduserte tiltakshendelse med $id og aktiv=false for deltakerId ${tiltakshendelse.deltakerId}")
    }

    private fun opprettStartHendelse(
        hendelse: Hendelse,
        type: Tiltakshendelse.Type,
        tekst: String,
    ) {
        lagreOgDistribuer(
            hendelse.toTiltakshendelse(
                type = type,
                tekst = tekst,
            ),
        )
    }

    private suspend fun opprettStartHendelse(forslag: Forslag) {
        val deltaker = amtDeltakerClient.getDeltaker(forslag.deltakerId)

        Database.transaction {
            lagreOgDistribuer(
                forslag.toHendelse(
                    personIdent = deltaker.navBruker.personident,
                    tiltakskode = deltaker.gjennomforing.tiltakstype.tiltakskode,
                    aktiv = true,
                ),
            )
        }
    }

    private fun stoppHendelse(
        hendelse: Hendelse,
        hendelseType: Tiltakshendelse.Type,
    ) {
        tiltakshendelseRepository
            .getHendelse(
                deltakerId = hendelse.deltaker.id,
                hendelseType = hendelseType,
            ).onSuccess { hendelseFraDb ->
                val inaktivertHendelse = hendelseFraDb.copy(
                    aktiv = false,
                    hendelser = hendelseFraDb.hendelser.plus(hendelse.id),
                )
                lagreOgDistribuer(inaktivertHendelse)
            }
    }

    private fun lagreOgDistribuer(tiltakshendelse: Tiltakshendelse) {
        val lagretTiltakshendelse = tiltakshendelseRepository.upsert(tiltakshendelse)
        tiltakshendelseProducer.produce(lagretTiltakshendelse)
        log.info("Upsertet tiltakshendelse ${lagretTiltakshendelse.id}")
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

fun Hendelse.toTiltakshendelse(
    type: Tiltakshendelse.Type,
    tekst: String,
): Tiltakshendelse = Tiltakshendelse(
    id = UUID.randomUUID(),
    type = type,
    deltakerId = this.deltaker.id,
    forslagId = null,
    hendelser = listOf(this.id),
    personident = this.deltaker.personident,
    aktiv = true,
    tekst = tekst,
    tiltakskode = this.deltaker.deltakerliste.tiltak.tiltakskode,
    opprettet = this.opprettet,
)

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
