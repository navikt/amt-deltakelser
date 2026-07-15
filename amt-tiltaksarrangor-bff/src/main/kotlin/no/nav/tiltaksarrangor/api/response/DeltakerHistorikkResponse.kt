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
import no.nav.amt.lib.models.deltaker.Innsok
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
sealed interface DeltakerHistorikkResponse {
    companion object {
        fun fromModels(
            models: List<DeltakerHistorikk>,
            ansatte: Map<UUID, NavAnsatt>,
            arrangornavn: String,
            enheter: Map<UUID, NavEnhet>,
            oppstartstype: Oppstartstype,
        ): List<DeltakerHistorikkResponse> = models.map {
            when (it) {
                is DeltakerHistorikk.Endring -> DeltakerEndringResponse(it.endring, ansatte, enheter, arrangornavn, oppstartstype)
                is DeltakerHistorikk.Vedtak -> VedtakResponse(it.vedtak, ansatte, enheter)
                is DeltakerHistorikk.Forslag -> ForslagHistorikkResponse(it.forslag, arrangornavn, ansatte, enheter)
                is DeltakerHistorikk.EndringFraArrangor -> EndringFraArrangorResponse(it.endringFraArrangor, arrangornavn)
                is DeltakerHistorikk.ImportertFraArena -> ImportertFraArenaResponse(it.importertFraArena)
                is DeltakerHistorikk.VurderingFraArrangor -> VurderingFraArrangorResponse(it.data, arrangornavn)
                is DeltakerHistorikk.InnsokPaaFellesOppstart -> InnsokPaaFellesOppstartResponse(it.data, ansatte, enheter)
                is DeltakerHistorikk.EndringFraTiltakskoordinator -> EndringFraTiltakskoordinatorResponse(
                    it.endringFraTiltakskoordinator,
                    ansatte,
                    enheter,
                )
            }
        }
    }
}

data class DeltakerEndringResponse(
    val endring: DeltakerEndringEndringResponse,
    val endretAv: String,
    val endretAvEnhet: String,
    val endret: LocalDateTime,
    val forslag: ForslagHistorikkResponse?,
) : DeltakerHistorikkResponse {
    constructor(
        model: DeltakerEndring,
        ansatte: Map<UUID, NavAnsatt>,
        enheter: Map<UUID, NavEnhet>,
        arrangornavn: String,
        deltakerlisteOppstartstype: Oppstartstype,
    ) : this(
        endring = DeltakerEndringEndringResponse.fromModel(model.endring, deltakerlisteOppstartstype),
        endretAv = ansatte[model.endretAv]!!.navn,
        endretAvEnhet = enheter[model.endretAvEnhet]!!.navn,
        endret = model.endret,
        forslag = model.forslag?.let { ForslagHistorikkResponse(it, arrangornavn) },
    )
}

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
) : DeltakerHistorikkResponse {
    constructor(
        model: Vedtak,
        ansatte: Map<UUID, NavAnsatt>,
        enheter: Map<UUID, NavEnhet>,
    ) : this(
        fattet = model.fattet,
        bakgrunnsinformasjon = model.deltakerVedVedtak.bakgrunnsinformasjon,
        deltakelsesinnhold = model.deltakerVedVedtak.deltakelsesinnhold,
        dagerPerUke = model.deltakerVedVedtak.dagerPerUke,
        deltakelsesprosent = model.deltakerVedVedtak.deltakelsesprosent,
        fattetAvNav = model.fattetAvNav,
        opprettetAv = ansatte[model.opprettetAv]!!.navn,
        opprettetAvEnhet = enheter[model.opprettetAvEnhet]!!.navn,
        opprettet = model.opprettet,
    )
}

data class EndringFraArrangorResponse(
    val id: UUID,
    val opprettet: LocalDateTime,
    val arrangorNavn: String,
    val endring: EndringFraArrangor.Endring,
) : DeltakerHistorikkResponse {
    constructor(model: EndringFraArrangor, arrangornavn: String) : this(
        id = model.id,
        opprettet = model.opprettet,
        arrangorNavn = arrangornavn,
        endring = model.endring,
    )
}

data class ForslagHistorikkResponse(
    val id: UUID,
    val opprettet: LocalDateTime,
    val begrunnelse: String?,
    val arrangorNavn: String,
    val endring: Forslag.Endring,
    val status: ForslagHistorikkResponseStatus,
) : DeltakerHistorikkResponse {
    constructor(model: Forslag, arrangornavn: String) : this(
        model = model,
        arrangornavn = arrangornavn,
        ansatte = emptyMap(),
        enheter = emptyMap(),
    )

    constructor(
        model: Forslag,
        arrangornavn: String,
        ansatte: Map<UUID, NavAnsatt>,
        enheter: Map<UUID, NavEnhet>,
    ) : this(
        id = model.id,
        opprettet = model.opprettet,
        begrunnelse = model.begrunnelse ?: "",
        arrangorNavn = arrangornavn,
        endring = model.endring,
        status = model.getForslagResponseStatus(ansatte, enheter),
    )
}

data class ImportertFraArenaResponse(
    val importertDato: LocalDateTime,
    val startdato: LocalDate?,
    val sluttdato: LocalDate?,
    val dagerPerUke: Float?,
    val deltakelsesprosent: Float?,
    val status: DeltakerStatus,
) : DeltakerHistorikkResponse {
    constructor(model: ImportertFraArena) : this(
        importertDato = model.importertDato,
        startdato = model.deltakerVedImport.startdato,
        sluttdato = model.deltakerVedImport.sluttdato,
        dagerPerUke = model.deltakerVedImport.dagerPerUke,
        deltakelsesprosent = model.deltakerVedImport.deltakelsesprosent,
        status = model.deltakerVedImport.status,
    )
}

data class VurderingFraArrangorResponse(
    val vurderingstype: Vurderingstype,
    val begrunnelse: String?,
    val opprettetDato: LocalDateTime,
    val endretAv: String,
) : DeltakerHistorikkResponse {
    constructor(model: VurderingFraArrangorData, arrangornavn: String) : this(
        vurderingstype = model.vurderingstype,
        begrunnelse = model.begrunnelse,
        opprettetDato = model.opprettet,
        endretAv = arrangornavn,
    )
}

data class InnsokPaaFellesOppstartResponse(
    val innsokt: LocalDateTime,
    val innsoktAv: String,
    val innsoktAvEnhet: String,
    val deltakelsesinnholdVedInnsok: Deltakelsesinnhold?,
    val utkastDelt: LocalDateTime?,
    val utkastGodkjentAvNav: Boolean,
) : DeltakerHistorikkResponse {
    constructor(
        model: Innsok,
        ansatte: Map<UUID, NavAnsatt>,
        enheter: Map<UUID, NavEnhet>,
    ) : this(
        innsokt = model.innsokt,
        innsoktAv = ansatte[model.innsoktAv]!!.navn,
        innsoktAvEnhet = enheter[model.innsoktAvEnhet]!!.navn,
        deltakelsesinnholdVedInnsok = model.deltakelsesinnholdVedInnsok,
        utkastDelt = model.utkastDelt,
        utkastGodkjentAvNav = model.utkastGodkjentAvNav,
    )
}

data class EndringFraTiltakskoordinatorResponse(
    val endring: EndringFraTiltakskoordinator.Endring,
    val endretAv: String,
    val endretAvEnhet: String,
    val endret: LocalDateTime,
) : DeltakerHistorikkResponse {
    constructor(
        model: EndringFraTiltakskoordinator,
        ansatte: Map<UUID, NavAnsatt>,
        enheter: Map<UUID, NavEnhet>,
    ) : this(
        endring = model.endring,
        endretAv = ansatte[model.endretAv]!!.navn,
        endretAvEnhet = enheter[model.endretAvEnhet]!!.navn,
        endret = model.endret,
    )
}

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

private fun Forslag.getForslagResponseStatus(
    ansatte: Map<UUID, NavAnsatt>,
    enheter: Map<UUID, NavEnhet>,
): ForslagHistorikkResponseStatus = when (val status = status) {
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
