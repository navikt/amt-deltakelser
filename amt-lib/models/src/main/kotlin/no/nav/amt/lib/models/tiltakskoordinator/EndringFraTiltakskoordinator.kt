package no.nav.amt.lib.models.tiltakskoordinator

import com.fasterxml.jackson.annotation.JsonTypeInfo
import java.time.LocalDateTime
import java.util.UUID

data class EndringFraTiltakskoordinator(
    val id: UUID,
    val deltakerId: UUID,
    val endring: Endring,
    val endretAv: UUID,
    val endretAvEnhet: UUID,
    val endret: LocalDateTime,
) {
    @JsonTypeInfo(use = JsonTypeInfo.Id.SIMPLE_NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
    sealed interface Endring

    data object DelMedArrangor : Endring

    data object SettPaaVenteliste : Endring

    data object TildelPlass : Endring

    data class Avslag(
        val aarsak: Aarsak,
        val begrunnelse: String?,
    ) : Endring {
        data class Aarsak(
            val type: Type,
            val beskrivelse: String? = null,
        ) {
            init {
                if (beskrivelse != null && type != Type.ANNET) {
                    error("Aarsak $type skal ikke ha beskrivelse")
                }
            }

            enum class Type {
                KURS_FULLT,
                KRAV_IKKE_OPPFYLT,
                ANNET,
            }
        }
    }
}
