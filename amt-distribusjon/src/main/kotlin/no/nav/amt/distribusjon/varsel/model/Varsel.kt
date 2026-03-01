package no.nav.amt.distribusjon.varsel.model

import com.fasterxml.jackson.databind.JsonNode
import no.nav.amt.distribusjon.Environment
import no.nav.amt.distribusjon.hendelse.model.Hendelse
import no.nav.amt.distribusjon.varsel.nowUTC
import no.nav.amt.distribusjon.varsel.skalVarslesEksternt
import no.nav.amt.lib.utils.objectMapper
import no.nav.tms.varsel.action.EksternKanal
import no.nav.tms.varsel.action.Produsent
import no.nav.tms.varsel.action.Sensitivitet
import no.nav.tms.varsel.action.Tekst
import no.nav.tms.varsel.action.Varseltype
import no.nav.tms.varsel.builder.VarselActionBuilder
import java.time.Duration
import java.time.ZonedDateTime
import java.util.UUID

data class Varsel(
    val id: UUID,
    val type: Type,
    val hendelser: List<UUID>,
    val status: Status,
    val erEksterntVarsel: Boolean,
    val aktivFra: ZonedDateTime,
    val aktivTil: ZonedDateTime?,
    val deltakerId: UUID,
    val personident: String,
    val tekst: String,
    val revarselForVarsel: UUID?,
    val revarsles: ZonedDateTime?,
) {
    companion object {
        const val BESKJED_FORSINKELSE_MINUTTER = 30L

        val beskjedAktivLengde: Duration = Duration.ofDays(21).plusMinutes(BESKJED_FORSINKELSE_MINUTTER)

        fun nyOppgave(hendelse: Hendelse) = Varsel(
            id = UUID.randomUUID(),
            type = Type.OPPGAVE,
            hendelser = listOf(hendelse.id),
            status = Status.VENTER_PA_UTSENDELSE,
            erEksterntVarsel = true,
            aktivFra = nowUTC(),
            aktivTil = null,
            deltakerId = hendelse.deltaker.id,
            personident = hendelse.deltaker.personident,
            tekst = oppgaveTekst(hendelse),
            revarselForVarsel = null,
            revarsles = null,
        )

        fun nyBeskjed(hendelse: Hendelse) = Varsel(
            id = UUID.randomUUID(),
            type = Type.BESKJED,
            hendelser = listOf(hendelse.id),
            status = Status.VENTER_PA_UTSENDELSE,
            erEksterntVarsel = hendelse.skalVarslesEksternt(),
            aktivFra = nesteUtsendingstidspunkt(),
            aktivTil = nesteUtsendingstidspunkt().plus(beskjedAktivLengde),
            deltakerId = hendelse.deltaker.id,
            personident = hendelse.deltaker.personident,
            tekst = beskjedTekst(hendelse),
            revarselForVarsel = null,
            revarsles = if (hendelse.skalVarslesEksternt()) revarslingstidspunkt() else null,
        )

        fun revarsel(varsel: Varsel): Varsel {
            require(varsel.kanRevarsles) {
                "Kan ikke lage et revarsel for varsel ${varsel.id}"
            }

            return varsel.copy(
                id = UUID.randomUUID(),
                status = Status.VENTER_PA_UTSENDELSE,
                aktivFra = nowUTC(),
                aktivTil = nowUTC().plus(beskjedAktivLengde),
                revarselForVarsel = varsel.id,
                revarsles = null,
            )
        }

        fun nesteUtsendingstidspunkt(): ZonedDateTime = nowUTC().plusMinutes(BESKJED_FORSINKELSE_MINUTTER)

        fun revarslingstidspunkt(): ZonedDateTime = nesteUtsendingstidspunkt().plusDays(7)
    }

    val erAktiv: Boolean get() = status == Status.AKTIV

    val venterPaUsendelse: Boolean get() = status == Status.VENTER_PA_UTSENDELSE

    val kanRevarsles: Boolean get() = revarsles != null

    val erRevarsel: Boolean get() = revarselForVarsel != null

    fun merge(varsel: Varsel): Varsel {
        require(this.type == Type.BESKJED && status == Status.VENTER_PA_UTSENDELSE) {
            error("Kan ikke slå sammen andre varsler enn beskjeder som ikke er sendt")
        }

        val eksterntVarsel = this.erEksterntVarsel || varsel.erEksterntVarsel

        return this.copy(
            hendelser = this.hendelser.plus(varsel.hendelser),
            erEksterntVarsel = eksterntVarsel,
            aktivFra = nesteUtsendingstidspunkt(),
            aktivTil = nesteUtsendingstidspunkt().plus(beskjedAktivLengde),
            revarsles = if (eksterntVarsel) revarslingstidspunkt() else null,
        )
    }

    enum class Type {
        BESKJED,
        OPPGAVE,
    }

    enum class Status {
        VENTER_PA_UTSENDELSE,
        AKTIV,
        UTFORT,
        INAKTIVERT,
        UTLOPT,
    }

    fun toOppgaveDto(): JsonNode = objectMapper.readTree(
        VarselActionBuilder.opprett {
            varselConfig(Varseltype.Oppgave, visEndringsmodal = false)
        },
    )

    fun toBeskjedDto(visEndringsmodal: Boolean): JsonNode = objectMapper.readTree(
        VarselActionBuilder.opprett {
            varselConfig(Varseltype.Beskjed, visEndringsmodal)
        },
    )

    fun toInaktiverDto(): JsonNode = objectMapper.readTree(
        VarselActionBuilder.inaktiver {
            varselId = this@Varsel.id.toString()
            produsent = produsent()
        },
    )

    private fun VarselActionBuilder.OpprettVarselInstance.varselConfig(varseltype: Varseltype, visEndringsmodal: Boolean) {
        varselId = this@Varsel.id.toString()
        type = varseltype
        sensitivitet = Sensitivitet.High
        ident = personident
        tekster += Tekst(
            spraakkode = "nb",
            tekst = this@Varsel.tekst,
            default = true,
        )
        aktivFremTil = aktivTil
        link = innbyggerDeltakerUrl(deltakerId, visEndringsmodal)
        produsent = produsent()

        if (erEksterntVarsel) {
            eksternVarsling {
                preferertKanal = getPreferertKanal(varseltype)
            }
        }
    }

    private fun getPreferertKanal(varseltype: Varseltype): EksternKanal = if (varseltype == Varseltype.Oppgave) {
        EksternKanal.SMS
    } else {
        EksternKanal.EPOST
    }

    private fun produsent() = Produsent(
        cluster = Environment.cluster,
        namespace = Environment.namespace,
        appnavn = Environment.appName,
    )
}

fun innbyggerDeltakerUrl(deltakerId: UUID, visEndringsmodal: Boolean): String {
    val url = if (Environment.isProd()) {
        "https://www.nav.no/arbeidsmarkedstiltak/$deltakerId"
    } else {
        "https://amt.intern.dev.nav.no/arbeidsmarkedstiltak/$deltakerId"
    }

    return if (visEndringsmodal) {
        "$url?vis_endringer"
    } else {
        url
    }
}
