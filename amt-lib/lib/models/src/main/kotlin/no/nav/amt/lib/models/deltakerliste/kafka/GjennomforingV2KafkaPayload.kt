package no.nav.amt.lib.models.deltakerliste.kafka

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import no.nav.amt.lib.models.deltakerliste.GjennomforingPameldingType
import no.nav.amt.lib.models.deltakerliste.GjennomforingStatusType
import no.nav.amt.lib.models.deltakerliste.GjennomforingType
import no.nav.amt.lib.models.deltakerliste.Oppstartstype
import no.nav.amt.lib.models.deltakerliste.kafka.GjennomforingV2KafkaPayload.Companion.ENKELTPLASS_V2_TYPE
import no.nav.amt.lib.models.deltakerliste.kafka.GjennomforingV2KafkaPayload.Companion.GRUPPE_V2_TYPE
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "type",
)
@JsonSubTypes(
    JsonSubTypes.Type(value = GjennomforingV2KafkaPayload.Gruppe::class, name = GRUPPE_V2_TYPE),
    JsonSubTypes.Type(value = GjennomforingV2KafkaPayload.Enkeltplass::class, name = ENKELTPLASS_V2_TYPE),
)
sealed interface GjennomforingV2KafkaPayload {
    val id: UUID
    val opprettetTidspunkt: OffsetDateTime
    val oppdatertTidspunkt: OffsetDateTime
    val tiltakskode: Tiltakskode
    val arrangor: Arrangor
    val pameldingType: GjennomforingPameldingType?

    @get:JsonIgnore
    val gjennomforingType: GjennomforingType

    fun assertValidChanges(
        antallDeltakere: Int,
        eksisterendePameldingstype: GjennomforingPameldingType?,
        eksisterendeOppstartstype: Oppstartstype?,
    ) {
        if (antallDeltakere == 0) return

        require(pameldingType == eksisterendePameldingstype) {
            "Påmeldingstype kan ikke endres for deltakerliste $id med deltakere"
        }

        if (this !is Gruppe) return

        require(oppstart == eksisterendeOppstartstype) {
            "Oppstartstype kan ikke endres for deltakerliste $id med deltakere"
        }
    }

    fun assertPameldingstypeIsValid() {
        when {
            tiltakskode in direktetiltak -> {
                require(pameldingType == GjennomforingPameldingType.DIREKTE_VEDTAK) {
                    "$tiltakskode krever DIREKTE_VEDTAK"
                }
            }

            this is Gruppe &&
                tiltakskode in gruppetiltak &&
                oppstart == Oppstartstype.FELLES &&
                tiltakskode != Tiltakskode.JOBBKLUBB -> {
                require(pameldingType == GjennomforingPameldingType.TRENGER_GODKJENNING) {
                    "FELLES oppstart for $tiltakskode krever TRENGER_GODKJENNING"
                }
            }

            this is Gruppe &&
                tiltakskode in gruppetiltak &&
                oppstart == Oppstartstype.LOPENDE &&
                tiltakskode != Tiltakskode.JOBBKLUBB -> {
                require(pameldingType == GjennomforingPameldingType.DIREKTE_VEDTAK) {
                    "LOPENDE oppstart for $tiltakskode krever DIREKTE_VEDTAK"
                }
            }
        }
    }

    data class Arrangor(
        val organisasjonsnummer: String,
    )

    data class Gruppe(
        override val id: UUID,
        override val opprettetTidspunkt: OffsetDateTime,
        override val oppdatertTidspunkt: OffsetDateTime,
        override val tiltakskode: Tiltakskode,
        override val arrangor: Arrangor,
        override val pameldingType: GjennomforingPameldingType? = null,
        val navn: String,
        val startDato: LocalDate,
        val sluttDato: LocalDate?,
        val status: GjennomforingStatusType,
        val oppstart: Oppstartstype,
        val tilgjengeligForArrangorFraOgMedDato: LocalDate?,
        val apentForPamelding: Boolean,
        val antallPlasser: Int,
        val deltidsprosent: Double,
        val oppmoteSted: String?,
        override val gjennomforingType: GjennomforingType = GjennomforingType.Gruppe,
    ) : GjennomforingV2KafkaPayload

    data class Enkeltplass(
        override val id: UUID,
        override val opprettetTidspunkt: OffsetDateTime,
        override val oppdatertTidspunkt: OffsetDateTime,
        override val tiltakskode: Tiltakskode,
        override val arrangor: Arrangor,
        override val pameldingType: GjennomforingPameldingType? = null,
        override val gjennomforingType: GjennomforingType = GjennomforingType.Enkeltplass,
    ) : GjennomforingV2KafkaPayload

    fun <T : Any> toModel(gruppeMapper: (Gruppe) -> T, enkeltplassMapper: (Enkeltplass) -> T): T = when (this) {
        is Gruppe -> gruppeMapper(this)
        is Enkeltplass -> enkeltplassMapper(this)
    }

    companion object {
        const val GRUPPE_V2_TYPE = "TiltaksgjennomforingV2.Gruppe"
        const val ENKELTPLASS_V2_TYPE = "TiltaksgjennomforingV2.Enkeltplass"

        val direktetiltak = setOf(
            Tiltakskode.ARBEIDSFORBEREDENDE_TRENING,
            Tiltakskode.ARBEIDSRETTET_REHABILITERING,
            Tiltakskode.AVKLARING,
            Tiltakskode.DIGITALT_OPPFOLGINGSTILTAK,
            Tiltakskode.OPPFOLGING,
            Tiltakskode.VARIG_TILRETTELAGT_ARBEID_SKJERMET,
        )

        val gruppetiltak = setOf(
            Tiltakskode.GRUPPE_ARBEIDSMARKEDSOPPLAERING,
            Tiltakskode.GRUPPE_FAG_OG_YRKESOPPLAERING,
            Tiltakskode.JOBBKLUBB,
        )
    }
}
