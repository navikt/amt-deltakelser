package no.nav.amt.lib.models.deltaker

import java.time.LocalDateTime
import java.util.UUID

data class DeltakerStatus(
    val id: UUID,
    val type: Type,
    val aarsak: Aarsak?,
    val gyldigFra: LocalDateTime,
    val gyldigTil: LocalDateTime?,
    val opprettet: LocalDateTime,
) {
    data class Aarsak(
        val type: Type,
        val beskrivelse: String?,
    ) {
        init {
            if (beskrivelse != null && type != Type.ANNET) {
                error("Aarsak $type skal ikke ha beskrivelse")
            }
        }

        enum class Type {
            SYK,
            FATT_JOBB,
            TRENGER_ANNEN_STOTTE,
            FIKK_IKKE_PLASS,
            IKKE_MOTT,
            ANNET,
            AVLYST_KONTRAKT,
            UTDANNING,
            SAMARBEIDET_MED_ARRANGOREN_ER_AVBRUTT,
            KRAV_IKKE_OPPFYLT,
            KURS_FULLT,
        }
    }

    enum class Type {
        KLADD,
        UTKAST_TIL_PAMELDING,
        AVBRUTT_UTKAST,
        VENTER_PA_OPPSTART,
        DELTAR,
        HAR_SLUTTET,
        IKKE_AKTUELL,
        FEILREGISTRERT,
        SOKT_INN,
        VURDERES,
        VENTELISTE,
        AVBRUTT,
        FULLFORT,
        PABEGYNT_REGISTRERING,
    }

    companion object {
        val avsluttendeStatuser = setOf(
            Type.AVBRUTT,
            Type.AVBRUTT_UTKAST,
            Type.FEILREGISTRERT,
            Type.FULLFORT,
            Type.HAR_SLUTTET,
            Type.IKKE_AKTUELL,
        )
    }
}
