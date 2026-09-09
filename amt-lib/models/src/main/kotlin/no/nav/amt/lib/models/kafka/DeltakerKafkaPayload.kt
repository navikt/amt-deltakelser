package no.nav.amt.lib.models.kafka

import no.nav.amt.lib.models.arrangor.melding.Vurdering
import no.nav.amt.lib.models.deltaker.Deltakelsesinnhold
import no.nav.amt.lib.models.deltaker.DeltakerHistorikk
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltaker.Kilde
import no.nav.amt.lib.models.person.NavAnsatt
import no.nav.amt.lib.models.person.Oppfolgingsperiode
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

/*
    Deltaker core modell som brukes til deltaker-v2
 */
data class DeltakerKafkaPayload(
    val id: UUID,
    val deltakerliste: DeltakerlistePayload,
    val personalia: Personalia,
    val status: DeltakerStatusPayload,
    val dagerPerUke: Float?,
    val prosentStilling: Double?,
    val oppstartsdato: LocalDate?,
    val sluttdato: LocalDate?,
    val innsoktDato: LocalDate,
    val forsteVedtakFattet: LocalDate?,
    val bestillingTekst: String?,
    val kilde: Kilde?,
    val innhold: Deltakelsesinnhold?,
    val historikk: List<DeltakerHistorikk>?,
    val vurderingerFraArrangor: List<Vurdering>?,
    val erManueltDeltMedArrangor: Boolean = false,
    val sisteEndring: SisteEndring?,
    /*
        Felter som bør deprecates og flyttes inn i relevante strukturer:
     */
    @Deprecated("Bruk deltakerliste.id")
    val deltakerlisteId: UUID,
    // @Deprecated("bruk personalia.navenhet.navn")
    val navKontor: String?,
    // @Deprecated("bruk personalia.navVeileder")
    val navVeileder: NavAnsatt?,
    // @Deprecated("bruk deltakerliste.oppstartstype")
    val deltarPaKurs: Boolean,
    // @Deprecated("Bruk personalia.oppfolgingsperioder")
    val oppfolgingsperioder: List<Oppfolgingsperiode> = emptyList(),
    // @Deprecated("Bruk sisteEndring.timestamp")
    val sistEndret: LocalDateTime?,
    // @Deprecated("Bruk sisteEndring.utfortav")
    val sistEndretAv: UUID?,
    // @Deprecated("Bruk sisteEndring.enhet")
    val sistEndretAvEnhet: UUID?,
    val forcedUpdate: Boolean? = false,
)

data class DeltakerStatusPayload(
    // Kan ikke bytte til DeltakerStatus siden denne har en annen struktur på aarsak
    val id: UUID?,
    val type: DeltakerStatus.Type,
    val aarsak: DeltakerStatus.Aarsak.Type?,
    val aarsaksbeskrivelse: String?,
    val gyldigFra: LocalDateTime,
    val opprettetDato: LocalDateTime,
)

data class SisteEndring(
    val utfortAvNavAnsattId: UUID,
    val navEnhetId: UUID?,
    val timestamp: LocalDateTime,
)
