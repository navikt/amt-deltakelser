package no.nav.amt.deltaker.deltaker.kafka.dto

import no.nav.amt.lib.models.arrangor.melding.Vurdering
import no.nav.amt.lib.models.deltaker.Deltakelsesinnhold
import no.nav.amt.lib.models.deltaker.DeltakerHistorikk
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltaker.Kilde
import no.nav.amt.lib.models.person.Oppfolgingsperiode
import no.nav.amt.lib.models.person.address.Adresse
import no.nav.amt.lib.models.person.address.Adressebeskyttelse
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

// Brukes i test
data class DeltakerV2Dto(
    val id: UUID,
    val deltakerlisteId: UUID,
    val personalia: DeltakerPersonaliaDto,
    val status: DeltakerStatusDto,
    val dagerPerUke: Float?,
    val prosentStilling: Double?,
    val oppstartsdato: LocalDate?,
    val sluttdato: LocalDate?,
    val innsoktDato: LocalDate,
    val forsteVedtakFattet: LocalDate?,
    val bestillingTekst: String?,
    val navKontor: String?,
    val navVeileder: DeltakerNavVeilederDto?,
    val deltarPaKurs: Boolean,
    val kilde: Kilde?,
    val innhold: Deltakelsesinnhold?,
    val historikk: List<DeltakerHistorikk>?,
    val vurderingerFraArrangor: List<Vurdering>?,
    val sistEndretAv: UUID?,
    val sistEndretAvEnhet: UUID?,
    val sistEndret: LocalDateTime?,
    val forcedUpdate: Boolean? = false,
    val erManueltDeltMedArrangor: Boolean = false,
    val oppfolgingsperioder: List<Oppfolgingsperiode> = emptyList(),
) {
    data class DeltakerPersonaliaDto(
        val personId: UUID?,
        val personident: String,
        val navn: Navn,
        val kontaktinformasjon: DeltakerKontaktinformasjonDto,
        val skjermet: Boolean,
        val adresse: Adresse?,
        val adressebeskyttelse: Adressebeskyttelse?,
    )

    data class Navn(
        val fornavn: String,
        val mellomnavn: String?,
        val etternavn: String,
    )

    data class DeltakerStatusDto(
        val id: UUID?,
        val type: DeltakerStatus.Type,
        val aarsak: DeltakerStatus.Aarsak.Type?,
        val aarsaksbeskrivelse: String?,
        val gyldigFra: LocalDateTime,
        val opprettetDato: LocalDateTime,
    )

    data class DeltakerKontaktinformasjonDto(
        val telefonnummer: String?,
        val epost: String?,
    )

    data class DeltakerNavVeilederDto(
        val id: UUID,
        val navn: String,
        val epost: String?,
        val telefonnummer: String?,
    )
}
