package no.nav.amt.lib.models.deltakerliste.kafka

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.readValue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import no.nav.amt.lib.models.deltakerliste.GjennomforingPameldingType
import no.nav.amt.lib.models.deltakerliste.GjennomforingStatusType
import no.nav.amt.lib.models.deltakerliste.GjennomforingType
import no.nav.amt.lib.models.deltakerliste.Oppstartstype
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import no.nav.amt.lib.utils.objectMapper
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.Year
import java.util.UUID

class GjennomforingV2KafkaPayloadDeserializingTest {
    @Test
    fun `inneholder type ved serialisering`() {
        val payload = objectMapper.readValue<GjennomforingV2KafkaPayload>(gruppeJson)
        val serialized: JsonNode = objectMapper.valueToTree(payload)
        serialized shouldBe objectMapper.readTree(gruppeJson)
    }

    @Test
    fun `deserializes Gruppe payload`() {
        val payload = objectMapper.readValue<GjennomforingV2KafkaPayload>(gruppeJson)

        payload.shouldBeInstanceOf<GjennomforingV2KafkaPayload.Gruppe>()
        payload.id shouldBe idInTest
        payload.opprettetTidspunkt shouldBe OffsetDateTime.parse(opprettet)
        payload.oppdatertTidspunkt shouldBe OffsetDateTime.parse(oppdatert)
        payload.tiltakskode shouldBe Tiltakskode.ARBEIDSFORBEREDENDE_TRENING
        payload.arrangor.organisasjonsnummer shouldBe "123456789"
        payload.navn shouldBe "Gruppe 1"
        payload.startDato shouldBe startDato
        payload.sluttDato shouldBe sluttDato
        payload.status shouldBe GjennomforingStatusType.GJENNOMFORES
        payload.oppstart shouldBe Oppstartstype.LOPENDE
        payload.tilgjengeligForArrangorFraOgMedDato shouldBe null
        payload.apentForPamelding shouldBe true
        payload.antallPlasser shouldBe 25
        payload.deltidsprosent shouldBe 50.0
        payload.oppmoteSted shouldBe "Oslo"
        payload.gjennomforingType shouldBe GjennomforingType.Gruppe
        payload.pameldingType shouldBe GjennomforingPameldingType.TRENGER_GODKJENNING
    }

    @Test
    fun `deserializes Enkeltplass payload`() {
        val json =
            """
            {
              "type": "TiltaksgjennomforingV2.Enkeltplass",
              "id": "$idInTest",
              "opprettetTidspunkt": "$opprettet",
              "oppdatertTidspunkt": "$oppdatert",
              "tiltakskode": "ENKELTPLASS_ARBEIDSMARKEDSOPPLAERING",
              "arrangor": { "organisasjonsnummer": "987654321" },
              "pameldingType": "DIREKTE_VEDTAK"
            }
            """.trimIndent()

        val payload = objectMapper.readValue<GjennomforingV2KafkaPayload>(json)

        payload.shouldBeInstanceOf<GjennomforingV2KafkaPayload.Enkeltplass>()
        payload.id shouldBe idInTest
        payload.opprettetTidspunkt shouldBe OffsetDateTime.parse(opprettet)
        payload.oppdatertTidspunkt shouldBe OffsetDateTime.parse(oppdatert)
        payload.tiltakskode shouldBe Tiltakskode.ENKELTPLASS_ARBEIDSMARKEDSOPPLAERING
        payload.arrangor.organisasjonsnummer shouldBe "987654321"
        payload.gjennomforingType shouldBe GjennomforingType.Enkeltplass
        payload.pameldingType shouldBe GjennomforingPameldingType.DIREKTE_VEDTAK
    }

    companion object {
        private val idInTest: UUID = UUID.randomUUID()

        private val opprettet = "${Year.now()}-01-02T03:04:05Z"
        private val oppdatert = "${Year.now()}-05-06T07:08:09Z"

        private val startDato: LocalDate = LocalDate.of(Year.now().value, 2, 1)
        private val sluttDato: LocalDate = LocalDate.of(Year.now().value, 3, 1)

        private val gruppeJson =
            """
            {
              "type": "TiltaksgjennomforingV2.Gruppe",
              "id": "$idInTest",
              "opprettetTidspunkt": "$opprettet",
              "oppdatertTidspunkt": "$oppdatert",
              "tiltakskode": "ARBEIDSFORBEREDENDE_TRENING",
              "arrangor": { "organisasjonsnummer": "123456789" },
              "pameldingType": "TRENGER_GODKJENNING",
              "navn": "Gruppe 1",
              "startDato": "$startDato",
              "sluttDato": "$sluttDato",
              "status": "GJENNOMFORES",
              "oppstart": "LOPENDE",
              "tilgjengeligForArrangorFraOgMedDato": null,
              "apentForPamelding": true,
              "antallPlasser": 25,
              "deltidsprosent": 50.0,
              "oppmoteSted": "Oslo"
            }
            """.trimIndent()
    }
}
