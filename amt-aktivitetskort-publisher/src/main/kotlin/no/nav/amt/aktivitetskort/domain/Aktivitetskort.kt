package no.nav.amt.aktivitetskort.domain

import no.nav.amt.aktivitetskort.kafka.producer.dto.AktivitetskortDto
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakstype.Companion.tiltakMedDeltakelsesmengder
import java.text.DecimalFormat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Objects
import java.util.UUID

data class Aktivitetskort(
    val id: UUID,
    val personident: String,
    val tittel: String,
    val aktivitetStatus: AktivitetStatus,
    val startDato: LocalDate?,
    val sluttDato: LocalDate?,
    val beskrivelse: String?,
    val endretAv: EndretAv,
    val endretTidspunkt: LocalDateTime,
    val avtaltMedNav: Boolean,
    val oppgave: OppgaveWrapper? = null,
    val handlinger: List<Handling>? = null,
    val detaljer: List<Detalj>,
    val etiketter: List<Tag>,
    val tiltakstype: AktivitetskortTiltakstype,
) {
    fun toAktivitetskortDto(): AktivitetskortDto = AktivitetskortDto(
        id = id,
        personIdent = personident,
        tittel = tittel,
        aktivitetStatus = aktivitetStatus,
        startDato = startDato,
        sluttDato = sluttDato,
        beskrivelse = beskrivelse,
        endretAv = endretAv,
        endretTidspunkt = endretTidspunkt.atZone(ZoneId.systemDefault()),
        avtaltMedNav = avtaltMedNav,
        oppgave = oppgave,
        handlinger = handlinger,
        detaljer = detaljer,
        etiketter = etiketter,
    )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Aktivitetskort) return false

        // Sammenligner ikke endretTidspunkt for de vil ofte være forskjellig, selv om alt annet er likt
        return id == other.id &&
            personident == other.personident &&
            tittel == other.tittel &&
            aktivitetStatus == other.aktivitetStatus &&
            startDato == other.startDato &&
            sluttDato == other.sluttDato &&
            beskrivelse == other.beskrivelse &&
            endretAv == other.endretAv &&
            avtaltMedNav == other.avtaltMedNav &&
            oppgave == other.oppgave &&
            handlinger == other.handlinger &&
            detaljer == other.detaljer &&
            etiketter == other.etiketter
    }

    override fun hashCode(): Int = Objects.hash(
        id,
        personident,
        tittel,
        aktivitetStatus,
        startDato,
        sluttDato,
        beskrivelse,
        endretAv,
        avtaltMedNav,
        oppgave,
        handlinger,
        detaljer,
        etiketter,
    )

    companion object {
        fun lagTittel(
            deltakerliste: Deltakerliste,
            arrangor: Arrangor,
        ): String = when (deltakerliste.tiltak.tiltakskode) {
            Tiltakskode.VARIG_TILRETTELAGT_ARBEID_SKJERMET -> "Tilrettelagt arbeid hos ${arrangor.navn}"
            Tiltakskode.TILRETTELAGT_ARBEID_ORDINAER -> "Tilrettelagt arbeid med oppfølging hos ${arrangor.navn}"

            Tiltakskode.JOBBKLUBB -> "Jobbsøkerkurs hos ${arrangor.navn}"

            Tiltakskode.ENKELTPLASS_ARBEIDSMARKEDSOPPLAERING,
            Tiltakskode.ENKELTPLASS_FAG_OG_YRKESOPPLAERING,
            Tiltakskode.HOYERE_UTDANNING,
            -> "${deltakerliste.tiltak.navn} hos ${arrangor.navn}"

            Tiltakskode.GRUPPE_ARBEIDSMARKEDSOPPLAERING,
            Tiltakskode.GRUPPE_FAG_OG_YRKESOPPLAERING,
            Tiltakskode.ARBEIDSMARKEDSOPPLAERING,
            Tiltakskode.NORSKOPPLAERING_GRUNNLEGGENDE_FERDIGHETER_FOV,
            Tiltakskode.STUDIESPESIALISERING,
            Tiltakskode.FAG_OG_YRKESOPPLAERING,
            Tiltakskode.HOYERE_YRKESFAGLIG_UTDANNING,
            -> deltakerliste.navn

            else -> "${deltakerliste.tiltak.navn} hos ${arrangor.navn}"
        }

        fun lagDetaljer(
            deltaker: Deltaker,
            deltakerliste: Deltakerliste,
            arrangor: Arrangor,
        ): List<Detalj> {
            val detaljer = mutableListOf<Detalj>()

            detaljer.add(Detalj("Status for deltakelse", displayText(deltaker.status)))

            if (deltakerliste.tiltak.tiltakskode in tiltakMedDeltakelsesmengder) {
                deltakelseMengdeDetalj(deltaker)?.let { detaljer.add(it) }
            }

            detaljer.add(Detalj("Arrangør", arrangor.navn))

            return detaljer
        }

        private fun deltakelseMengdeDetalj(deltaker: Deltaker): Detalj? {
            val harDagerPerUke = deltaker.dagerPerUke?.let { it in 1.0f..5.0f } == true
            val harProsentStilling = deltaker.prosentStilling?.let { it in 1.0..100.0 } == true

            val label = "Deltakelsesmengde"

            fun fmtProsent(pct: Double) = "${DecimalFormat("#.#").format(pct)}%"

            fun fmtDager(antall: Float) =
                "fordelt på ${DecimalFormat("#.#").format(antall)} ${if (antall == 1.0f) "dag" else "dager"} i uka"

            return when {
                !harProsentStilling && !harDagerPerUke -> {
                    null
                }

                deltaker.prosentStilling == 100.0 || !harDagerPerUke -> {
                    deltaker.prosentStilling?.let { Detalj(label, fmtProsent(it)) }
                }

                !harProsentStilling -> {
                    Detalj(label, fmtDager(deltaker.dagerPerUke))
                }

                else -> {
                    Detalj(label, "${fmtProsent(deltaker.prosentStilling)} ${fmtDager(deltaker.dagerPerUke)}")
                }
            }
        }
    }

    fun erAktivDeltaker(): Boolean = sluttDato == null || sluttDato.isAfter(LocalDate.now().minusWeeks(2))
}

data class Tag(
    val tekst: String,
    val sentiment: Sentiment,
    val kode: Kode,
) {
    enum class Kode {
        SOKT_INN,
        VURDERES,
        VENTER_PA_OPPSTART,
        VENTELISTE,
        IKKE_AKTUELL,
        UTKAST_TIL_PAMELDING,
        AVBRUTT_UTKAST,
        FEILREGISTRERT,
        AVBRUTT,
        FULLFORT,
    }

    enum class Sentiment {
        POSITIVE,
        NEGATIVE,
        NEUTRAL,
    }
}

data class Detalj(
    val label: String,
    val verdi: String,
)

data class Handling(
    val tekst: String,
    val subtekst: String,
    val url: String,
    val lenkeType: LenkeType,
)

enum class LenkeType {
    EKSTERN,
    INTERN,
    FELLES,
}

data class OppgaveWrapper(
    val ekstern: Oppgave?,
    val intern: Oppgave?,
)

data class Oppgave(
    val tekst: String,
    val subtekst: String,
    val url: String,
)

data class EndretAv(
    val ident: String,
    val identType: IdentType,
)

enum class IdentType {
    ARENAIDENT,
    NAVIDENT,
    PERSONBRUKER,
    TILTAKSARRAGOER,
    ARBEIDSGIVER,
    SYSTEM,
}
