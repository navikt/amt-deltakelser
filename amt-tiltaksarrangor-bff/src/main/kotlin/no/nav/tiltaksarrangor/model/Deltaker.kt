package no.nav.tiltaksarrangor.model

import no.nav.amt.lib.models.deltaker.Deltakelsesinnhold
import no.nav.amt.lib.models.deltaker.DeltakerHistorikk
import no.nav.amt.lib.models.deltaker.Kilde
import no.nav.amt.lib.models.deltaker.deltakelsesmengde.Deltakelsesmengde
import no.nav.amt.lib.models.deltakerliste.GjennomforingPameldingType
import no.nav.amt.lib.models.deltakerliste.Oppstartstype
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import no.nav.tiltaksarrangor.api.response.UlestEndringResponse
import no.nav.tiltaksarrangor.melding.forslag.AktivtForslagResponse
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

data class Deltaker(
	val id: UUID,
	val deltakerliste: Deltakerliste,
	val fornavn: String,
	val mellomnavn: String?,
	val etternavn: String,
	val fodselsnummer: String,
	val telefonnummer: String?,
	val epost: String?,
	val status: DeltakerStatusInternalModel,
	val startDato: LocalDate?,
	val sluttDato: LocalDate?,
	val deltakelseProsent: Int?,
	val dagerPerUke: Float?,
	val soktInnPa: String,
	val soktInnDato: LocalDateTime,
	val tiltakskode: Tiltakskode,
	val bestillingTekst: String?,
	val innhold: Deltakelsesinnhold?,
	val fjernesDato: LocalDateTime?,
	val navInformasjon: NavInformasjon,
	val veiledere: List<Veileder>,
	val aktiveForslag: List<AktivtForslagResponse>,
	val aktiveEndringsmeldinger: List<Endringsmelding>,
	val historiskeEndringsmeldinger: List<Endringsmelding>,
	val adresse: Adresse?,
	val gjeldendeVurderingFraArrangor: Vurdering?,
	val adressebeskyttet: Boolean,
	val kilde: Kilde,
	val historikk: List<DeltakerHistorikk>,
	val deltakelsesmengder: DeltakelsesmengderDto?,
	val ulesteEndringer: List<UlestEndringResponse>,
	val erManueltDeltMedArrangor: Boolean,
	val erUnderOppfolging: Boolean,
) {
	data class Deltakerliste(
		val id: UUID,
		val startDato: LocalDate?,
		val sluttDato: LocalDate?,
		val oppstartstype: Oppstartstype,
		val tiltakskode: Tiltakskode,
		val pameldingstype: GjennomforingPameldingType,
	)
}

data class Adresse(
	val adressetype: Adressetype,
	val postnummer: String,
	val poststed: String,
	val tilleggsnavn: String?,
	val adressenavn: String?,
)

enum class Adressetype {
	KONTAKTADRESSE,
	OPPHOLDSADRESSE,
	BOSTEDSADRESSE,
}

data class DeltakelsesmengderDto(
	val nesteDeltakelsesmengde: DeltakelsesmengdeDto?,
	val sisteDeltakelsesmengde: DeltakelsesmengdeDto?,
)

data class DeltakelsesmengdeDto(
	val deltakelsesprosent: Float,
	val dagerPerUke: Float?,
	val gyldigFra: LocalDate,
)

fun Deltakelsesmengde.toDto() = DeltakelsesmengdeDto(
	deltakelsesprosent,
	dagerPerUke,
	gyldigFra,
)
