package no.nav.tiltaksarrangor.api.response

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import no.nav.amt.lib.models.arrangor.melding.EndringFraArrangor
import no.nav.amt.lib.models.arrangor.melding.Forslag
import no.nav.amt.lib.models.arrangor.melding.Vurderingstype
import no.nav.amt.lib.models.deltaker.Deltakelsesinnhold
import no.nav.amt.lib.models.deltaker.DeltakerEndring
import no.nav.amt.lib.models.deltaker.DeltakerHistorikk
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltaker.ImportertFraArena
import no.nav.amt.lib.models.deltaker.InnsokPaaFellesOppstart
import no.nav.amt.lib.models.deltaker.Vedtak
import no.nav.amt.lib.models.deltaker.VurderingFraArrangorData
import no.nav.amt.lib.models.deltakerliste.Oppstartstype
import no.nav.amt.lib.models.tiltakskoordinator.EndringFraTiltakskoordinator
import no.nav.tiltaksarrangor.consumer.model.NavAnsatt
import no.nav.tiltaksarrangor.consumer.model.NavEnhet
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes(
	JsonSubTypes.Type(value = DeltakerEndringResponse::class, name = "Endring"),
	JsonSubTypes.Type(value = VedtakResponse::class, name = "Vedtak"),
	JsonSubTypes.Type(value = ForslagHistorikkResponse::class, name = "Forslag"),
	JsonSubTypes.Type(value = EndringFraArrangorResponse::class, name = "EndringFraArrangor"),
	JsonSubTypes.Type(value = ImportertFraArenaResponse::class, name = "ImportertFraArena"),
	JsonSubTypes.Type(value = VurderingFraArrangorResponse::class, name = "VurderingFraArrangor"),
	JsonSubTypes.Type(value = EndringFraTiltakskoordinatorResponse::class, name = "EndringFraTiltakskoordinator"),
	JsonSubTypes.Type(value = InnsokPaaFellesOppstartResponse::class, name = "InnsokPaaFellesOppstart"),
)
sealed interface DeltakerHistorikkResponse

data class DeltakerEndringResponse(
	val endring: DeltakerEndringEndringResponse,
	val endretAv: String,
	val endretAvEnhet: String,
	val endret: LocalDateTime,
	val forslag: ForslagHistorikkResponse?,
) : DeltakerHistorikkResponse

data class VedtakResponse(
	val fattet: LocalDateTime?,
	val bakgrunnsinformasjon: String?,
	val fattetAvNav: Boolean,
	val deltakelsesinnhold: Deltakelsesinnhold?,
	val dagerPerUke: Float?,
	val deltakelsesprosent: Float?,
	val opprettetAv: String,
	val opprettetAvEnhet: String,
	val opprettet: LocalDateTime,
) : DeltakerHistorikkResponse

data class EndringFraArrangorResponse(
	val id: UUID,
	val opprettet: LocalDateTime,
	val arrangorNavn: String,
	val endring: EndringFraArrangor.Endring,
) : DeltakerHistorikkResponse

data class ForslagHistorikkResponse(
	val id: UUID,
	val opprettet: LocalDateTime,
	val begrunnelse: String?,
	val arrangorNavn: String,
	val endring: Forslag.Endring,
	val status: ForslagHistorikkResponseStatus,
) : DeltakerHistorikkResponse

data class ImportertFraArenaResponse(
	val importertDato: LocalDateTime,
	val startdato: LocalDate?,
	val sluttdato: LocalDate?,
	val dagerPerUke: Float?,
	val deltakelsesprosent: Float?,
	val status: DeltakerStatus,
) : DeltakerHistorikkResponse

data class VurderingFraArrangorResponse(
	val vurderingstype: Vurderingstype,
	val begrunnelse: String?,
	val opprettetDato: LocalDateTime,
	val endretAv: String,
) : DeltakerHistorikkResponse

data class InnsokPaaFellesOppstartResponse(
	val innsokt: LocalDateTime,
	val innsoktAv: String,
	val innsoktAvEnhet: String,
	val deltakelsesinnholdVedInnsok: Deltakelsesinnhold?,
	val utkastDelt: LocalDateTime?,
	val utkastGodkjentAvNav: Boolean,
) : DeltakerHistorikkResponse

data class EndringFraTiltakskoordinatorResponse(
	val endring: EndringFraTiltakskoordinator.Endring,
	val endretAv: String,
	val endretAvEnhet: String,
	val endret: LocalDateTime,
) : DeltakerHistorikkResponse

@JsonTypeInfo(use = JsonTypeInfo.Id.SIMPLE_NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
sealed interface ForslagHistorikkResponseStatus {
	data object VenterPaSvar : ForslagHistorikkResponseStatus

	data class Godkjent(
		val godkjent: LocalDateTime,
	) : ForslagHistorikkResponseStatus

	data class Avvist(
		val avvistAv: String,
		val avvistAvEnhet: String,
		val avvist: LocalDateTime,
		val begrunnelseFraNav: String,
	) : ForslagHistorikkResponseStatus

	data class Tilbakekalt(
		val tilbakekalt: LocalDateTime,
	) : ForslagHistorikkResponseStatus

	data class Erstattet(
		val erstattet: LocalDateTime,
	) : ForslagHistorikkResponseStatus
}

fun List<DeltakerHistorikk>.toResponse(
	ansatte: Map<UUID, NavAnsatt>,
	arrangornavn: String,
	enheter: Map<UUID, NavEnhet>,
	oppstartstype: Oppstartstype,
): List<DeltakerHistorikkResponse> = this.map {
	when (it) {
		is DeltakerHistorikk.Endring -> it.endring.toResponse(ansatte, enheter, arrangornavn, oppstartstype)
		is DeltakerHistorikk.Vedtak -> it.vedtak.toResponse(ansatte, enheter)
		is DeltakerHistorikk.Forslag -> it.forslag.toResponse(arrangornavn, ansatte, enheter)
		is DeltakerHistorikk.EndringFraArrangor -> it.endringFraArrangor.toResponse(arrangornavn)
		is DeltakerHistorikk.ImportertFraArena -> it.importertFraArena.toResponse()
		is DeltakerHistorikk.VurderingFraArrangor -> it.data.toResponse(arrangornavn)
		is DeltakerHistorikk.InnsokPaaFellesOppstart -> it.data.toResponse(ansatte, enheter)
		is DeltakerHistorikk.EndringFraTiltakskoordinator -> it.endringFraTiltakskoordinator.toResponse(ansatte, enheter)
	}
}

fun DeltakerEndring.toResponse(
	ansatte: Map<UUID, NavAnsatt>,
	enheter: Map<UUID, NavEnhet>,
	arrangornavn: String,
	deltakerlisteOppstartstype: Oppstartstype,
) = DeltakerEndringResponse(
	endring = endring.toResponse(deltakerlisteOppstartstype),
	endretAv = ansatte[endretAv]!!.navn,
	endretAvEnhet = enheter[endretAvEnhet]!!.navn,
	endret = endret,
	forslag = forslag?.toResponse(arrangornavn),
)

fun DeltakerEndring.Endring.toResponse(oppstartstype: Oppstartstype): DeltakerEndringEndringResponse = when (this) {
	is DeltakerEndring.Endring.AvsluttDeltakelse -> DeltakerEndringEndringResponse.AvsluttDeltakelse(
		aarsak = aarsak,
		sluttdato = sluttdato,
		begrunnelse = begrunnelse,
		harFullfort = true,
		oppstartstype = oppstartstype,
	)

	is DeltakerEndring.Endring.EndreAvslutning -> DeltakerEndringEndringResponse.EndreAvslutning(
		aarsak = aarsak,
		begrunnelse = begrunnelse,
		harFullfort = harFullfort,
		sluttdato = sluttdato,
	)

	is DeltakerEndring.Endring.AvbrytDeltakelse -> DeltakerEndringEndringResponse.AvsluttDeltakelse(
		aarsak = aarsak,
		sluttdato = sluttdato,
		begrunnelse = begrunnelse,
		harFullfort = false,
		oppstartstype = oppstartstype,
	)
	is DeltakerEndring.Endring.EndreBakgrunnsinformasjon -> DeltakerEndringEndringResponse.EndreBakgrunnsinformasjon(bakgrunnsinformasjon)
	is DeltakerEndring.Endring.EndreDeltakelsesmengde -> DeltakerEndringEndringResponse.EndreDeltakelsesmengde(
		deltakelsesprosent,
		dagerPerUke,
		gyldigFra,
		begrunnelse,
	)
	is DeltakerEndring.Endring.EndreInnhold -> DeltakerEndringEndringResponse.EndreInnhold(ledetekst, innhold)
	is DeltakerEndring.Endring.EndreSluttarsak -> DeltakerEndringEndringResponse.EndreSluttarsak(aarsak, begrunnelse)
	is DeltakerEndring.Endring.EndreSluttdato -> DeltakerEndringEndringResponse.EndreSluttdato(sluttdato, begrunnelse)
	is DeltakerEndring.Endring.EndreStartdato -> DeltakerEndringEndringResponse.EndreStartdato(startdato, sluttdato, begrunnelse)
	is DeltakerEndring.Endring.FjernOppstartsdato -> DeltakerEndringEndringResponse.FjernOppstartsdato(begrunnelse)
	is DeltakerEndring.Endring.ForlengDeltakelse -> DeltakerEndringEndringResponse.ForlengDeltakelse(sluttdato, begrunnelse)
	is DeltakerEndring.Endring.IkkeAktuell -> DeltakerEndringEndringResponse.IkkeAktuell(aarsak, begrunnelse)
	is DeltakerEndring.Endring.ReaktiverDeltakelse -> DeltakerEndringEndringResponse.ReaktiverDeltakelse(reaktivertDato, begrunnelse)
}

fun Vedtak.toResponse(ansatte: Map<UUID, NavAnsatt>, enheter: Map<UUID, NavEnhet>) = VedtakResponse(
	fattet = fattet,
	bakgrunnsinformasjon = deltakerVedVedtak.bakgrunnsinformasjon,
	deltakelsesinnhold = deltakerVedVedtak.deltakelsesinnhold,
	dagerPerUke = deltakerVedVedtak.dagerPerUke,
	deltakelsesprosent = deltakerVedVedtak.deltakelsesprosent,
	fattetAvNav = fattetAvNav,
	opprettetAv = ansatte[opprettetAv]!!.navn,
	opprettetAvEnhet = enheter[opprettetAvEnhet]!!.navn,
	opprettet = opprettet,
)

fun EndringFraArrangor.toResponse(arrangornavn: String) = EndringFraArrangorResponse(
	id = id,
	opprettet = opprettet,
	arrangorNavn = arrangornavn,
	endring = endring,
)

fun ImportertFraArena.toResponse() = ImportertFraArenaResponse(
	importertDato = importertDato,
	startdato = deltakerVedImport.startdato,
	sluttdato = deltakerVedImport.sluttdato,
	dagerPerUke = deltakerVedImport.dagerPerUke,
	deltakelsesprosent = deltakerVedImport.deltakelsesprosent,
	status = deltakerVedImport.status,
)

fun VurderingFraArrangorData.toResponse(arrangornavn: String) = VurderingFraArrangorResponse(
	vurderingstype = vurderingstype,
	begrunnelse = begrunnelse,
	opprettetDato = opprettet,
	endretAv = arrangornavn,
)

fun Forslag.toResponse(arrangornavn: String) = this.toResponse(arrangornavn, emptyMap(), emptyMap())

fun Forslag.toResponse(
	arrangornavn: String,
	ansatte: Map<UUID, NavAnsatt>,
	enheter: Map<UUID, NavEnhet>,
): ForslagHistorikkResponse = ForslagHistorikkResponse(
	id = id,
	opprettet = opprettet,
	begrunnelse = begrunnelse ?: "",
	arrangorNavn = arrangornavn,
	endring = endring,
	status = getForslagResponseStatus(ansatte, enheter),
)

fun InnsokPaaFellesOppstart.toResponse(ansatte: Map<UUID, NavAnsatt>, enheter: Map<UUID, NavEnhet>) = InnsokPaaFellesOppstartResponse(
	innsokt,
	ansatte[innsoktAv]!!.navn,
	enheter[innsoktAvEnhet]!!.navn,
	deltakelsesinnholdVedInnsok,
	utkastDelt,
	utkastGodkjentAvNav,
)

fun EndringFraTiltakskoordinator.toResponse(ansatte: Map<UUID, NavAnsatt>, enheter: Map<UUID, NavEnhet>) =
	EndringFraTiltakskoordinatorResponse(
		endring,
		ansatte[endretAv]!!.navn,
		enheter[endretAvEnhet]!!.navn,
		endret,
	)

private fun Forslag.getForslagResponseStatus(ansatte: Map<UUID, NavAnsatt>, enheter: Map<UUID, NavEnhet>): ForslagHistorikkResponseStatus =
	when (val status = status) {
		is Forslag.Status.VenterPaSvar -> ForslagHistorikkResponseStatus.VenterPaSvar
		is Forslag.Status.Godkjent -> ForslagHistorikkResponseStatus.Godkjent(status.godkjent)
		is Forslag.Status.Avvist -> {
			val avvist = status
			ForslagHistorikkResponseStatus.Avvist(
				avvistAv = ansatte[avvist.avvistAv.id]!!.navn,
				avvistAvEnhet = enheter[avvist.avvistAv.enhetId]!!.navn,
				avvist = avvist.avvist,
				begrunnelseFraNav = avvist.begrunnelseFraNav,
			)
		}
		is Forslag.Status.Tilbakekalt -> ForslagHistorikkResponseStatus.Tilbakekalt(status.tilbakekalt)
		is Forslag.Status.Erstattet -> ForslagHistorikkResponseStatus.Erstattet(status.erstattet)
	}
