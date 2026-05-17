package no.nav.amt.deltaker.repository

import no.nav.amt.lib.models.arrangor.melding.Vurderingstype
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.person.address.Adressebeskyttelse
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

/**
 * Flat data-klasse som representerer én deltaker-rad fra tiltakskoordinator-spørringen.
 *
 * Deltakerliste-/arrangør-/tiltakstype-data hentes via [DeltakerlisteRepository.get]
 * for å unngå å gjenta identiske kolonner for alle deltakere (kan være 2000+).
 */
data class TiltakskoordinatorDeltakerRow(
    // Deltaker core
    val id: UUID,
    val personident: String,
    val startdato: LocalDate?,
    val sluttdato: LocalDate?,
    val erManueltDeltMedArrangor: Boolean,
    val status: DeltakerStatus,
    // Nav bruker
    val fornavn: String,
    val mellomnavn: String?,
    val etternavn: String,
    val erSkjermet: Boolean,
    val harAdresse: Boolean,
    val adressebeskyttelse: Adressebeskyttelse?,
    // Nav enhet (fra nav_enhet LEFT JOIN — null hvis enhet ikke finnes i lokal DB)
    val navEnhetNavn: String?,
    // Berikede felt — beregnet direkte i SQL
    val soktInnDato: LocalDate?,
    val harAktivtForslag: Boolean,
    val sisteVurderingstype: Vurderingstype?,
    // Digital bruker cache (null = ikke i cache / utdatert → trenger HTTP-oppslag)
    val erDigitalCached: Boolean?,
    // Felter for låse-sjekk (beregnes i Kotlin fra groupBy personident)
    val vedtakFattet: LocalDateTime?,
    val innsoektDatoArena: LocalDate?,
)
