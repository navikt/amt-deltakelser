package no.nav.amt.deltaker.bff.veileder.api.response

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import no.nav.amt.lib.models.arrangor.melding.EndringFraArrangor
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
import no.nav.amt.lib.models.person.NavAnsatt
import no.nav.amt.lib.models.person.NavEnhet
import no.nav.amt.lib.models.tiltakskoordinator.EndringFraTiltakskoordinator
import no.nav.amt.lib.utils.GenericCache
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes(
    JsonSubTypes.Type(value = DeltakerEndringResponse::class, name = "Endring"),
    JsonSubTypes.Type(value = VedtakResponse::class, name = "Vedtak"),
    JsonSubTypes.Type(value = ForslagResponse::class, name = "Forslag"),
    JsonSubTypes.Type(value = EndringFraArrangorResponse::class, name = "EndringFraArrangor"),
    JsonSubTypes.Type(value = ImportertFraArenaDto::class, name = "ImportertFraArena"),
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
    val forslag: ForslagResponse?,
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

data class EndringFraTiltakskoordinatorResponse(
    val endring: EndringFraTiltakskoordinator.Endring,
    val endretAv: String,
    val endretAvEnhet: String,
    val endret: LocalDateTime,
) : DeltakerHistorikkResponse

data class InnsokPaaFellesOppstartResponse(
    val innsokt: LocalDateTime,
    val innsoktAv: String,
    val innsoktAvEnhet: String,
    val deltakelsesinnholdVedInnsok: Deltakelsesinnhold?,
    val utkastDelt: LocalDateTime?,
    val utkastGodkjentAvNav: Boolean,
) : DeltakerHistorikkResponse

fun List<DeltakerHistorikk>.toResponse(
    arrangornavn: String,
    oppstartstype: Oppstartstype?,
    ansatte: GenericCache<NavAnsatt>,
    enheter: GenericCache<NavEnhet>,
): List<DeltakerHistorikkResponse> = this.map {
    when (it) {
        is DeltakerHistorikk.Endring -> it.endring.toResponse(ansatte, enheter, arrangornavn, oppstartstype)
        is DeltakerHistorikk.Vedtak -> it.vedtak.toResponse(ansatte, enheter)
        is DeltakerHistorikk.Forslag -> ForslagResponse.fromForslag(
            forslag = it.forslag,
            arrangornavn = arrangornavn,
            ansatte = ansatte,
            enheter = enheter,
        )

        is DeltakerHistorikk.EndringFraArrangor -> it.endringFraArrangor.toResponse(arrangornavn)
        is DeltakerHistorikk.ImportertFraArena -> it.importertFraArena.toResponse()
        is DeltakerHistorikk.VurderingFraArrangor -> it.data.toResponse(arrangornavn)
        is DeltakerHistorikk.EndringFraTiltakskoordinator -> it.endringFraTiltakskoordinator.toResponse(ansatte, enheter)
        is DeltakerHistorikk.InnsokPaaFellesOppstart -> it.data.toResponse(ansatte, enheter)
    }
}

fun DeltakerEndring.toResponse(
    ansatte: GenericCache<NavAnsatt>,
    enheter: GenericCache<NavEnhet>,
    arrangornavn: String,
    oppstartstype: Oppstartstype?,
) = DeltakerEndringResponse(
    endring = DeltakerEndringEndringResponse.fromEndring(endring, oppstartstype),
    endretAv = ansatte.getOrThrow(endretAv).navn,
    endretAvEnhet = enheter.getOrThrow(endretAvEnhet).navn,
    endret = endret,
    forslag = forslag?.let {
        ForslagResponse.fromForslag(
            forslag = it,
            arrangornavn = arrangornavn,
            ansatte = ansatte,
            enheter = enheter,
        )
    },
)

fun Vedtak.toResponse(
    ansatte: GenericCache<NavAnsatt>,
    enheter: GenericCache<NavEnhet>,
) = VedtakResponse(
    fattet = fattet,
    bakgrunnsinformasjon = deltakerVedVedtak.bakgrunnsinformasjon,
    deltakelsesinnhold = deltakerVedVedtak.deltakelsesinnhold,
    dagerPerUke = deltakerVedVedtak.dagerPerUke,
    deltakelsesprosent = deltakerVedVedtak.deltakelsesprosent,
    fattetAvNav = fattetAvNav,
    opprettetAv = ansatte.getOrThrow(opprettetAv).navn,
    opprettetAvEnhet = enheter.getOrThrow(opprettetAvEnhet).navn,
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

fun EndringFraTiltakskoordinator.toResponse(
    ansatte: GenericCache<NavAnsatt>,
    enheter: GenericCache<NavEnhet>,
) = EndringFraTiltakskoordinatorResponse(
    endring = endring,
    endretAv = ansatte.getOrThrow(endretAv).navn,
    endretAvEnhet = enheter.getOrThrow(endretAvEnhet).navn,
    endret = endret,
)

fun InnsokPaaFellesOppstart.toResponse(
    ansatte: GenericCache<NavAnsatt>,
    enheter: GenericCache<NavEnhet>,
) = InnsokPaaFellesOppstartResponse(
    innsokt = innsokt,
    innsoktAv = ansatte.getOrThrow(innsoktAv).navn,
    innsoktAvEnhet = enheter.getOrThrow(innsoktAvEnhet).navn,
    deltakelsesinnholdVedInnsok = deltakelsesinnholdVedInnsok,
    utkastDelt = utkastDelt,
    utkastGodkjentAvNav = utkastGodkjentAvNav,
)
