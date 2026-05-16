package no.nav.amt.deltaker.repository

import no.nav.amt.deltaker.model.Deltakerliste
import no.nav.amt.lib.models.arrangor.melding.Vurderingstype
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltaker.Kilde
import no.nav.amt.lib.models.person.address.Adresse
import no.nav.amt.lib.models.person.address.Adressebeskyttelse
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

/**
 * Flat data-klasse som representerer én rad fra den konsoliderte tiltakskoordinator-spørringen.
 *
 * Inneholder alle felt som `TiltakskoordinatorResponseBuilder` trenger for å bygge
 * `TiltakskoordinatorDeltakereResponse` — uten ekstra DB-oppslag.
 *
 * Felt relatert til låse-sjekken (vedtakFattet, innsoektDatoArena, statusGyldigFra)
 * er med slik at `DeltakerLaaseService`-logikken kan kjøres direkte fra dette resultatet.
 */
data class TiltakskoordinatorDeltakerRow(
    // Deltaker core
    val id: UUID,
    val personident: String,
    val startdato: LocalDate?,
    val sluttdato: LocalDate?,
    val sistEndret: LocalDateTime,
    val kilde: Kilde,
    val erManueltDeltMedArrangor: Boolean,
    val opprettet: LocalDateTime,
    // Status
    val status: DeltakerStatus,
    // Nav bruker
    val fornavn: String,
    val mellomnavn: String?,
    val etternavn: String,
    val erSkjermet: Boolean,
    val adresse: Adresse?,
    val adressebeskyttelse: Adressebeskyttelse?,
    // Nav veileder (fra nav_ansatt LEFT JOIN — null hvis ansatt ikke finnes i lokal DB)
    val navVeilederId: UUID?,
    val navVeilederNavn: String?,
    val navVeilederEpost: String?,
    val navVeilederTelefon: String?,
    // Nav enhet (fra nav_enhet LEFT JOIN — null hvis enhet ikke finnes i lokal DB)
    val navEnhetId: UUID?,
    val navEnhetNavn: String?,
    // Deltakerliste (hele objektet for gjennomforing-bygging)
    val deltakerliste: Deltakerliste,
    // Overordnet arrangør-navn (for getArrangorNavn-logikken)
    val overordnetArrangorNavn: String?,
    // Berikede felt — beregnet direkte i SQL
    val soktInnDato: LocalDate?,
    val harAktivtForslag: Boolean,
    val sisteVurderingstype: Vurderingstype?,
    // Digital bruker cache (null = ikke i cache / utdatert → trenger HTTP-oppslag)
    val erDigitalCached: Boolean?,
    // Felter for låse-sjekk (beregnes i Kotlin fra groupBy personident)
    val vedtakFattet: LocalDateTime?,
    val innsoektDatoArena: LocalDate?,
    val prisinformasjon: String?,
)
