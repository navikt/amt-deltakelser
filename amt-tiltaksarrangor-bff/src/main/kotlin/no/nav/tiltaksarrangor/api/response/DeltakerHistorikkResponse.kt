package no.nav.tiltaksarrangor.api.response

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import no.nav.amt.lib.models.arrangor.melding.EndringAarsak
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
    val endring: ForslagEndringResponse,
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
        endring = ForslagEndringResponse.fromModel(model.endring),
        status = model.getForslagResponseStatus(ansatte, enheter),
    )
}

@JsonTypeInfo(use = JsonTypeInfo.Id.SIMPLE_NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
sealed interface EndringAarsakResponse {
    data object Syk : EndringAarsakResponse

    data object FattJobb : EndringAarsakResponse

    data object TrengerAnnenStotte : EndringAarsakResponse

    data object Utdanning : EndringAarsakResponse

    data object IkkeMott : EndringAarsakResponse

    data class Annet(
        val beskrivelse: String,
    ) : EndringAarsakResponse {
        constructor(model: EndringAarsak.Annet) : this(
            beskrivelse = model.beskrivelse,
        )
    }

    companion object {
        fun fromModel(model: EndringAarsak): EndringAarsakResponse = when (model) {
            EndringAarsak.Syk -> Syk
            EndringAarsak.FattJobb -> FattJobb
            EndringAarsak.TrengerAnnenStotte -> TrengerAnnenStotte
            EndringAarsak.Utdanning -> Utdanning
            EndringAarsak.IkkeMott -> IkkeMott
            is EndringAarsak.Annet -> Annet(model)
        }
    }
}

@JsonTypeInfo(use = JsonTypeInfo.Id.SIMPLE_NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
sealed interface ForslagEndringResponse {
    data class ForlengDeltakelse(
        val sluttdato: LocalDate,
    ) : ForslagEndringResponse {
        constructor(model: Forslag.ForlengDeltakelse) : this(
            sluttdato = model.sluttdato,
        )
    }

    data class AvsluttDeltakelse(
        val sluttdato: LocalDate?,
        val aarsak: EndringAarsakResponse?,
        val harDeltatt: Boolean?,
        val harFullfort: Boolean?,
    ) : ForslagEndringResponse {
        constructor(model: Forslag.AvsluttDeltakelse) : this(
            sluttdato = model.sluttdato,
            aarsak = model.aarsak?.let(EndringAarsakResponse::fromModel),
            harDeltatt = model.harDeltatt,
            harFullfort = model.harFullfort,
        )
    }

    data class EndreAvslutning(
        val aarsak: EndringAarsakResponse?,
        val harDeltatt: Boolean?,
        val harFullfort: Boolean?,
        val sluttdato: LocalDate?,
    ) : ForslagEndringResponse {
        constructor(model: Forslag.EndreAvslutning) : this(
            aarsak = model.aarsak?.let(EndringAarsakResponse::fromModel),
            harDeltatt = model.harDeltatt,
            harFullfort = model.harFullfort,
            sluttdato = model.sluttdato,
        )
    }

    data class IkkeAktuell(
        val aarsak: EndringAarsakResponse,
    ) : ForslagEndringResponse {
        constructor(model: Forslag.IkkeAktuell) : this(
            aarsak = EndringAarsakResponse.fromModel(model.aarsak),
        )
    }

    data class Deltakelsesmengde(
        val deltakelsesprosent: Int,
        val dagerPerUke: Int?,
        val gyldigFra: LocalDate?,
    ) : ForslagEndringResponse {
        constructor(model: Forslag.Deltakelsesmengde) : this(
            deltakelsesprosent = model.deltakelsesprosent,
            dagerPerUke = model.dagerPerUke,
            gyldigFra = model.gyldigFra,
        )
    }

    data class Startdato(
        val startdato: LocalDate,
        val sluttdato: LocalDate?,
    ) : ForslagEndringResponse {
        constructor(model: Forslag.Startdato) : this(
            startdato = model.startdato,
            sluttdato = model.sluttdato,
        )
    }

    data class Sluttdato(
        val sluttdato: LocalDate,
    ) : ForslagEndringResponse {
        constructor(model: Forslag.Sluttdato) : this(
            sluttdato = model.sluttdato,
        )
    }

    data class Sluttarsak(
        val aarsak: EndringAarsakResponse,
    ) : ForslagEndringResponse {
        constructor(model: Forslag.Sluttarsak) : this(
            aarsak = EndringAarsakResponse.fromModel(model.aarsak),
        )
    }

    data object FjernOppstartsdato : ForslagEndringResponse

    companion object {
        fun fromModel(model: Forslag.Endring): ForslagEndringResponse = when (model) {
            is Forslag.ForlengDeltakelse -> ForlengDeltakelse(model)
            is Forslag.AvsluttDeltakelse -> AvsluttDeltakelse(model)
            is Forslag.EndreAvslutning -> EndreAvslutning(model)
            is Forslag.IkkeAktuell -> IkkeAktuell(model)
            is Forslag.Deltakelsesmengde -> Deltakelsesmengde(model)
            is Forslag.Startdato -> Startdato(model)
            is Forslag.Sluttdato -> Sluttdato(model)
            is Forslag.Sluttarsak -> Sluttarsak(model)
            Forslag.FjernOppstartsdato -> FjernOppstartsdato
        }
    }
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
