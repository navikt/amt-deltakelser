package no.nav.amt.deltaker.bff.veileder.api.response

import com.fasterxml.jackson.annotation.JsonTypeInfo
import no.nav.amt.lib.models.arrangor.melding.EndringAarsak
import no.nav.amt.lib.models.arrangor.melding.EndringFraArrangor
import no.nav.amt.lib.models.arrangor.melding.Forslag
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.tiltakskoordinator.EndringFraTiltakskoordinator
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

data class DeltakerStatusResponse(
    val id: UUID,
    val type: DeltakerStatus.Type,
    val aarsak: Aarsak?,
    val gyldigFra: LocalDateTime,
    val gyldigTil: LocalDateTime?,
    val opprettet: LocalDateTime,
) {
    data class Aarsak(
        val type: DeltakerStatus.Aarsak.Type,
        val beskrivelse: String?,
    ) {
        constructor(model: DeltakerStatus.Aarsak) : this(
            type = model.type,
            beskrivelse = model.beskrivelse,
        )
    }

    constructor(model: DeltakerStatus) : this(
        id = model.id,
        type = model.type,
        aarsak = model.aarsak?.let(::Aarsak),
        gyldigFra = model.gyldigFra,
        gyldigTil = model.gyldigTil,
        opprettet = model.opprettet,
    )
}

fun DeltakerStatus.toDeltakerStatusResponse() = DeltakerStatusResponse(this)

data class DeltakelsesmengdeResponse(
    val deltakelsesprosent: Float,
    val dagerPerUke: Float?,
    val gyldigFra: LocalDate,
) {
    constructor(model: no.nav.amt.internapi.deltaker.response.DeltakelsesmengdeResponse) : this(
        deltakelsesprosent = model.deltakelsesprosent,
        dagerPerUke = model.dagerPerUke,
        gyldigFra = model.gyldigFra,
    )
}

data class DeltakelsesmengderResponse(
    val nesteDeltakelsesmengde: DeltakelsesmengdeResponse? = null,
    val sisteDeltakelsesmengde: DeltakelsesmengdeResponse? = null,
) {
    constructor(model: no.nav.amt.internapi.deltaker.response.DeltakelsesmengderResponse) : this(
        nesteDeltakelsesmengde = model.nesteDeltakelsesmengde?.let(::DeltakelsesmengdeResponse),
        sisteDeltakelsesmengde = model.sisteDeltakelsesmengde?.let(::DeltakelsesmengdeResponse),
    )
}

@JsonTypeInfo(use = JsonTypeInfo.Id.SIMPLE_NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
sealed interface EndringFraArrangorEndringResponse {
    data class LeggTilOppstartsdato(
        val startdato: LocalDate,
        val sluttdato: LocalDate?,
    ) : EndringFraArrangorEndringResponse {
        constructor(model: EndringFraArrangor.LeggTilOppstartsdato) : this(
            startdato = model.startdato,
            sluttdato = model.sluttdato,
        )
    }

    companion object {
        fun fromModel(model: EndringFraArrangor.Endring) = when (model) {
            is EndringFraArrangor.LeggTilOppstartsdato -> LeggTilOppstartsdato(model)
        }
    }
}

@JsonTypeInfo(use = JsonTypeInfo.Id.SIMPLE_NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
sealed interface EndringFraTiltakskoordinatorEndringResponse {
    data object DelMedArrangor : EndringFraTiltakskoordinatorEndringResponse

    data object SettPaaVenteliste : EndringFraTiltakskoordinatorEndringResponse

    data object TildelPlass : EndringFraTiltakskoordinatorEndringResponse

    data class Avslag(
        val aarsak: Aarsak,
        val begrunnelse: String?,
    ) : EndringFraTiltakskoordinatorEndringResponse {
        data class Aarsak(
            val type: EndringFraTiltakskoordinator.Avslag.Aarsak.Type,
            val beskrivelse: String? = null,
        ) {
            constructor(model: EndringFraTiltakskoordinator.Avslag.Aarsak) : this(
                type = model.type,
                beskrivelse = model.beskrivelse,
            )
        }

        constructor(model: EndringFraTiltakskoordinator.Avslag) : this(
            aarsak = Aarsak(model.aarsak),
            begrunnelse = model.begrunnelse,
        )
    }

    companion object {
        fun fromModel(model: EndringFraTiltakskoordinator.Endring): EndringFraTiltakskoordinatorEndringResponse = when (model) {
            EndringFraTiltakskoordinator.DelMedArrangor -> DelMedArrangor
            EndringFraTiltakskoordinator.SettPaaVenteliste -> SettPaaVenteliste
            EndringFraTiltakskoordinator.TildelPlass -> TildelPlass
            is EndringFraTiltakskoordinator.Avslag -> Avslag(model)
        }
    }
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
        val sluttdato: LocalDate? = null,
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
