package no.nav.amt.distribusjon.varsel.hendelse

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import no.nav.tms.varsel.action.Varseltype

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, visible = true, property = "@event_name")
@JsonSubTypes(
    JsonSubTypes.Type(value = OpprettetVarselHendelse::class, name = "opprettet"),
    JsonSubTypes.Type(value = InaktivertVarselHendelse::class, name = "inaktivert"),
    JsonSubTypes.Type(value = SlettetVarselHendelse::class, name = "slettet"),
    JsonSubTypes.Type(value = EksternStatusHendelse::class, name = "eksternStatusOppdatert"),
)
sealed interface VarselHendelseDto {
    val varselId: String
    val varseltype: Varseltype
    val namespace: String
    val appnavn: String
}

data class EksternStatusHendelse(
    override val varselId: String,
    override val varseltype: Varseltype,
    override val namespace: String,
    override val appnavn: String,
    val status: String,
    val kanal: String?,
    val renotifikasjon: Boolean?,
    val feilmelding: String?,
) : VarselHendelseDto {
    companion object Status {
        const val SENDT = "sendt"
        const val FEILET = "feilet"
        const val BESTILT = "bestilt"
    }
}

data class InaktivertVarselHendelse(
    override val varselId: String,
    override val varseltype: Varseltype,
    override val namespace: String,
    override val appnavn: String,
) : VarselHendelseDto

data class OpprettetVarselHendelse(
    override val varselId: String,
    override val varseltype: Varseltype,
    override val namespace: String,
    override val appnavn: String,
) : VarselHendelseDto

data class SlettetVarselHendelse(
    override val varselId: String,
    override val varseltype: Varseltype,
    override val namespace: String,
    override val appnavn: String,
) : VarselHendelseDto
