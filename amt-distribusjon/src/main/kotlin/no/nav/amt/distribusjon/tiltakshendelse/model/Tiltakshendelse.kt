package no.nav.amt.distribusjon.tiltakshendelse.model

import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import java.time.LocalDateTime
import java.util.UUID

data class Tiltakshendelse(
    val id: UUID,
    val type: Type,
    val deltakerId: UUID,
    val forslagId: UUID?,
    val hendelser: List<UUID>,
    val personident: String,
    val aktiv: Boolean,
    val tekst: String,
    val tiltakskode: Tiltakskode,
    val opprettet: LocalDateTime,
) {
    val lenke get() = "/arbeidsmarkedstiltak/deltakelse/deltaker/$deltakerId"

    enum class Type {
        FORSLAG,
        UTKAST,
    }
}
