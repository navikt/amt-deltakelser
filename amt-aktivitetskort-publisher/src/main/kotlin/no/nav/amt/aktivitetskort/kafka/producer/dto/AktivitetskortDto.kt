package no.nav.amt.aktivitetskort.kafka.producer.dto

import no.nav.amt.aktivitetskort.domain.AktivitetStatus
import no.nav.amt.aktivitetskort.domain.Detalj
import no.nav.amt.aktivitetskort.domain.EndretAv
import no.nav.amt.aktivitetskort.domain.Handling
import no.nav.amt.aktivitetskort.domain.OppgaveWrapper
import no.nav.amt.aktivitetskort.domain.Tag
import java.time.LocalDate
import java.time.ZonedDateTime
import java.util.UUID

data class AktivitetskortDto(
    val id: UUID,
    val personIdent: String,
    val tittel: String,
    val aktivitetStatus: AktivitetStatus,
    val startDato: LocalDate?,
    val sluttDato: LocalDate?,
    val beskrivelse: String?,
    val endretAv: EndretAv,
    val endretTidspunkt: ZonedDateTime,
    val avtaltMedNav: Boolean,
    val oppgave: OppgaveWrapper? = null,
    val handlinger: List<Handling>? = null,
    val detaljer: List<Detalj>,
    val etiketter: List<Tag>,
)
