package no.nav.amt.distribusjon.varsel

import no.nav.amt.distribusjon.digitalbruker.DigitalBrukerService
import no.nav.amt.distribusjon.hendelse.HendelseRepository
import no.nav.amt.distribusjon.hendelse.model.Hendelse
import no.nav.amt.distribusjon.varsel.model.Varsel
import no.nav.amt.lib.models.hendelse.HendelseDeltaker
import no.nav.amt.lib.models.hendelse.HendelseType
import no.nav.amt.lib.utils.database.Database
import org.slf4j.LoggerFactory
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.UUID

class VarselService(
    private val varselRepository: VarselRepository,
    private val hendelseRepository: HendelseRepository,
    private val outboxHandler: VarselOutboxHandler,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun handleHendelse(hendelse: Hendelse) {
        if (skalIkkeVarsles(hendelse)) return

        when (hendelse.payload) {
            is HendelseType.OpprettUtkast -> {
                handleNyttVarsel(Varsel.nyOppgave(hendelse), true)
            }

            is HendelseType.AvbrytUtkast -> {
                inaktiverOppgave(hendelse.deltaker)
            }

            is HendelseType.InnbyggerGodkjennUtkast -> {
                utforOppgave(hendelse.deltaker)
            }

            is HendelseType.ReaktiverDeltakelse,
            is HendelseType.NavGodkjennUtkast,
            -> {
                inaktiverOppgave(hendelse.deltaker)
                val beskjed = slaSammenMedVentendeVarsel(Varsel.nyBeskjed(hendelse))
                handleNyttVarsel(beskjed, true)
            }

            is HendelseType.EndreBakgrunnsinformasjon,
            is HendelseType.EndreDeltakelsesmengde,
            is HendelseType.EndreInnhold,
            is HendelseType.EndreStartdato,
            is HendelseType.EndreSluttdato,
            is HendelseType.ForlengDeltakelse,
            is HendelseType.AvsluttDeltakelse,
            is HendelseType.EndreAvslutning,
            is HendelseType.AvbrytDeltakelse,
            is HendelseType.IkkeAktuell,
            is HendelseType.LeggTilOppstartsdato,
            is HendelseType.FjernOppstartsdato,
            -> {
                handleNyttVarsel(slaSammenMedVentendeVarsel(Varsel.nyBeskjed(hendelse)))
            }

            is HendelseType.EndreUtkast,
            is HendelseType.EndreSluttarsak,
            -> {
                log.info("Oppretter ikke varsel for hendelse ${hendelse.payload::class} for deltaker ${hendelse.deltaker.id}")
            }

            is HendelseType.DeltakerSistBesokt -> {
                utforBeskjed(hendelse.deltaker, hendelse.payload.sistBesokt)
            }

            is HendelseType.Avslag,
            is HendelseType.SettPaaVenteliste,
            is HendelseType.TildelPlass,
            -> {
                handleNyttVarsel(slaSammenMedVentendeVarsel(Varsel.nyBeskjed(hendelse)), true)
            }
        }
    }

    private fun skalIkkeVarsles(hendelse: Hendelse): Boolean = if (varselRepository.getByHendelseId(hendelse.id).isSuccess) {
        log.info("Varsel for hendelse ${hendelse.id} er allerede opprettet. Oppretter ikke nytt varsel.")
        true
    } else {
        !DigitalBrukerService.skalDistribueresDigitalt(hendelse.distribusjonskanal, hendelse.manuellOppfolging)
    }

    private fun slaSammenMedVentendeVarsel(nyttVarsel: Varsel): Varsel {
        val varsel = varselRepository.getVentendeVarsel(nyttVarsel.deltakerId).fold(
            onSuccess = { it.merge(nyttVarsel) },
            onFailure = { nyttVarsel },
        )

        return varsel
    }

    private fun handleNyttVarsel(varsel: Varsel, sendUmiddelbart: Boolean = false) {
        if (varsel.kanRevarsles || varsel.erRevarsel) {
            varselRepository.stoppRevarsler(varsel.deltakerId)
        }

        if (sendUmiddelbart) {
            sendVarsel(varsel)
        } else {
            varselRepository.upsert(varsel)
            log.info("Legger varsel ${varsel.id} klar til utsending ${varsel.aktivFra}")
        }
    }

    private fun sendVarsel(varsel: Varsel) {
        inaktiverTidligereBeskjed(varsel.deltakerId)

        val oppdatertVarsel = varsel.copy(aktivFra = nowUTC(), status = Varsel.Status.AKTIV)
        varselRepository.upsert(oppdatertVarsel)

        when (varsel.type) {
            Varsel.Type.BESKJED -> outboxHandler.opprettBeskjed(
                varsel = oppdatertVarsel,
                visEndringsmodal = skalViseHistorikkModal(oppdatertVarsel.hendelser),
            )

            Varsel.Type.OPPGAVE -> outboxHandler.opprettOppgave(oppdatertVarsel)
        }

        log.info("Sendte varsel ${varsel.id} for deltaker ${varsel.deltakerId}")
    }

    private fun ferdigstillSendtVarsel(varsel: Varsel, nyStatus: Varsel.Status) {
        if (varsel.erAktiv) {
            val revarsles = if (nyStatus == Varsel.Status.UTFORT) null else varsel.revarsles

            varselRepository.upsert(varsel.copy(aktivTil = nowUTC(), status = nyStatus, revarsles = revarsles))
            outboxHandler.inaktiver(varsel)
            log.info("Endret status på varsel ${varsel.id} til $nyStatus for deltaker ${varsel.deltakerId}")
        }
    }

    private fun inaktiverTidligereBeskjed(deltakerId: UUID) {
        val varsel = varselRepository.getAktivt(deltakerId).getOrNull()
        require(varsel?.type != Varsel.Type.OPPGAVE) {
            "deltaker-id $deltakerId: Kan ikke inaktivere oppgave ${varsel?.id} som om den var en beskjed"
        }

        if (varsel?.erAktiv == true) {
            ferdigstillSendtVarsel(varsel, Varsel.Status.INAKTIVERT)
        }
    }

    private fun inaktiverOppgave(deltaker: HendelseDeltaker) {
        varselRepository.getSisteVarsel(deltaker.id, Varsel.Type.OPPGAVE).onSuccess { varsel ->
            ferdigstillSendtVarsel(varsel, Varsel.Status.INAKTIVERT)
        }
    }

    private fun utforOppgave(deltaker: HendelseDeltaker) {
        varselRepository.getSisteVarsel(deltaker.id, Varsel.Type.OPPGAVE).onSuccess { varsel ->
            ferdigstillSendtVarsel(varsel, Varsel.Status.UTFORT)
        }
    }

    private fun utforBeskjed(deltaker: HendelseDeltaker, sistBesokt: ZonedDateTime) {
        val beskjeder = varselRepository.getAktiveEllerVentendeBeskjeder(deltaker.id)
        if (beskjeder.isEmpty()) {
            return
        }

        beskjeder.forEach {
            if (erBesokTidligereEnnBeskjed(sistBesokt, it)) {
                return
            }
            when (it.status) {
                Varsel.Status.VENTER_PA_UTSENDELSE -> {
                    val now = nowUTC()
                    varselRepository.upsert(
                        it.copy(
                            aktivFra = now,
                            aktivTil = now,
                            status = Varsel.Status.UTFORT,
                            revarsles = null,
                        ),
                    )
                }

                Varsel.Status.AKTIV -> {
                    ferdigstillSendtVarsel(it, Varsel.Status.UTFORT)
                }

                else -> {}
            }
        }

        varselRepository.stoppRevarsler(deltaker.id)
    }

    fun utlopBeskjed(varsel: Varsel) {
        require(varsel.type == Varsel.Type.BESKJED && varsel.erAktiv) {
            "Varsel må være en aktiv beskjed for å kunne utløpe. Varsel: ${varsel.id}"
        }

        require(varsel.aktivTil != null && varsel.aktivTil <= nowUTC()) {
            "Beskjed sin aktivTil må være passert for å kunne utløpe, Varsel: ${varsel.id}"
        }

        varselRepository.upsert(varsel.copy(status = Varsel.Status.UTLOPT))

        log.info("Varsel ${varsel.id} sin aktiv periode er utløpt")
    }

    private fun erBesokTidligereEnnBeskjed(sistBesokt: ZonedDateTime, sisteBeskjed: Varsel): Boolean {
        val besokForSendt = sistBesokt.withZoneSameInstant(ZoneOffset.UTC) < sisteBeskjed.aktivFra && sisteBeskjed.erAktiv
        val besokForIkkeSendt = sistBesokt.withZoneSameInstant(
            ZoneId.of("Z"),
        ) < sisteBeskjed.aktivFra.minusMinutes(Varsel.BESKJED_FORSINKELSE_MINUTTER) &&
            sisteBeskjed.venterPaUsendelse

        return besokForSendt || besokForIkkeSendt
    }

    suspend fun sendVentendeVarsler() {
        val varsler = varselRepository.getVarslerSomSkalSendes()
        require(varsler.size == varsler.distinctBy { it.deltakerId }.size) {
            "Det finnes flere enn et ventende varsel for en eller flere deltakere"
        }

        Database.transaction {
            varsler.forEach { sendVarsel(it) }
        }
    }

    suspend fun sendRevarsler() {
        val varsler = varselRepository.getVarslerSomSkalRevarsles()
        val revarsler = varsler.map { slaSammenMedVentendeVarsel(Varsel.revarsel(it)) }

        Database.transaction {
            revarsler.forEach { handleNyttVarsel(it, true) }
        }
    }

    private fun skalViseHistorikkModal(hendelseIder: List<UUID>): Boolean {
        val hendelser = hendelseRepository.getHendelser(hendelseIder)
        return hendelser.firstOrNull { it.payload !is HendelseType.NavGodkjennUtkast && it.payload !is HendelseType.TildelPlass } != null
    }
}

fun nowUTC(): ZonedDateTime = ZonedDateTime.now(ZoneId.of("Z"))

fun Hendelse.skalVarslesEksternt() = when (payload) {
    is HendelseType.AvbrytUtkast,
    is HendelseType.EndreBakgrunnsinformasjon,
    is HendelseType.EndreDeltakelsesmengde,
    is HendelseType.EndreInnhold,
    is HendelseType.EndreSluttarsak,
    is HendelseType.EndreStartdato,
    is HendelseType.EndreUtkast,
    is HendelseType.EndreAvslutning,
    is HendelseType.ForlengDeltakelse,
    is HendelseType.InnbyggerGodkjennUtkast,
    is HendelseType.DeltakerSistBesokt,
    is HendelseType.LeggTilOppstartsdato,
    is HendelseType.FjernOppstartsdato,
    -> false

    is HendelseType.EndreSluttdato,
    is HendelseType.IkkeAktuell,
    is HendelseType.NavGodkjennUtkast,
    is HendelseType.OpprettUtkast,
    is HendelseType.AvsluttDeltakelse,
    is HendelseType.AvbrytDeltakelse,
    is HendelseType.ReaktiverDeltakelse,
    is HendelseType.SettPaaVenteliste,
    is HendelseType.TildelPlass,
    is HendelseType.Avslag,
    -> true
}
