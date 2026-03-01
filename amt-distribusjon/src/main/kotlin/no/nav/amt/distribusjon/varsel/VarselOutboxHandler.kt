package no.nav.amt.distribusjon.varsel

import no.nav.amt.distribusjon.Environment
import no.nav.amt.distribusjon.varsel.model.Varsel
import no.nav.amt.lib.outbox.OutboxService

class VarselOutboxHandler(
    private val outboxService: OutboxService,
) {
    fun inaktiver(varsel: Varsel) {
        outboxService.insertRecord(
            key = varsel.id,
            value = varsel.toInaktiverDto(),
            topic = Environment.MINSIDE_VARSEL_TOPIC,
        )
    }

    fun opprettOppgave(varsel: Varsel) {
        require(varsel.type == Varsel.Type.OPPGAVE) {
            "Kan ikke opprette oppgave, feil varseltype ${varsel.type}"
        }

        outboxService.insertRecord(
            key = varsel.id,
            value = varsel.toOppgaveDto(),
            topic = Environment.MINSIDE_VARSEL_TOPIC,
        )
    }

    fun opprettBeskjed(varsel: Varsel, visEndringsmodal: Boolean) {
        require(varsel.type == Varsel.Type.BESKJED) {
            "Kan ikke opprette beskjed, feil varseltype ${varsel.type}"
        }

        outboxService.insertRecord(
            key = varsel.id,
            value = varsel.toBeskjedDto(visEndringsmodal),
            topic = Environment.MINSIDE_VARSEL_TOPIC,
        )
    }
}
