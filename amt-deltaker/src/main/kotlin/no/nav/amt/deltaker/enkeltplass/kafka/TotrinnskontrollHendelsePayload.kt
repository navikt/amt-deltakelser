package no.nav.amt.deltaker.enkeltplass.kafka

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import java.time.Instant
import java.util.UUID

data class TotrinnskontrollHendelsePayload(
    val id: UUID,
    val entityId: UUID,
    val type: TotrinnskontrollType,
    val behandletAv: TotrinnskontrollAgent,
    val behandletTidspunkt: Instant,
    val besluttetAv: TotrinnskontrollAgent?,
    val besluttetTidspunkt: Instant?,
    val besluttelse: TotrinnskontrollBesluttelse?, // Skal slettes
    val status: Status,
    val aarsaker: List<String>,
    val forklaring: String?,
) {
    enum class Status {
        TIL_BEHANDLING, // Kommer med en gang /ack på meldingen
        SATT_PA_VENT,
        GODKJENT,
        RETURNERT, // Forslaget returneres til avsender(avvist), brukes også for erstattning(besluttet av system)
    }

    enum class TotrinnskontrollType {
        TILSAGN_OPPRETTELSE,
        TILSAGN_ANNULLERING,
        TILSAGN_OPPGJOR,
        UTBETALING_LINJE_OPPRETTELSE,
        ENKELTPLASS_OKONOMI,
        ENKELTPLASS_PRISENDRING,
        TILSKUDD_OPPRETTELSE,
    }

    enum class TotrinnskontrollBesluttelse {
        GODKJENT,
        AVVIST,
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
    @JsonSubTypes(
        JsonSubTypes.Type(value = TotrinnskontrollAgent.NavAnsatt::class, name = "NAV_ANSATT"),
        JsonSubTypes.Type(value = TotrinnskontrollAgent.System::class, name = "SYSTEM"),
        JsonSubTypes.Type(value = TotrinnskontrollAgent.Arrangor::class, name = "ARRANGOR"),
    )
    sealed interface TotrinnskontrollAgent {
        data class NavAnsatt(
            val navIdent: String,
        ) : TotrinnskontrollAgent

        data class System(
            val system: String,
        ) : TotrinnskontrollAgent

        data object Arrangor : TotrinnskontrollAgent
    }
}
