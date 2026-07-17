package no.nav.amt.deltaker.enkeltplass.kafka

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import no.nav.amt.lib.models.deltakerliste.SertifiseringValg
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import no.nav.amt.lib.utils.objectMapper
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import tools.jackson.module.kotlin.readValue
import java.util.UUID

class GjennomforingRequestPayloadTest {
    @Nested
    inner class SerializationTest {
        @Test
        fun `EnkeltplassSoktInn med Anskaffelse serialiseres med korrekt type-diskriminator`() {
            val payload = lagEnkeltplassSoktInn(
                prisinformasjon = GjennomforingRequestPayload.Prisinformasjon.Anskaffelse(pris = 50000),
            )

            val json = objectMapper.writeValueAsString(payload)

            json shouldContain "\"type\":\"EnkeltplassSoktInn\""
            json shouldContain "\"pris\":50000"
        }

        @Test
        fun `EnkeltplassUtkast med Tilskudd serialiseres med korrekt type-diskriminator`() {
            val payload = lagEnkeltplassUtkast(
                prisinformasjon = GjennomforingRequestPayload.Prisinformasjon.Tilskudd(
                    tilskudd = mapOf(
                        GjennomforingRequestPayload.Prisinformasjon.Tilskudd.Tilskuddstype.SKOLEPENGER to 10000,
                    ),
                    tilleggsopplysninger = "Test",
                ),
            )

            val json = objectMapper.writeValueAsString(payload)

            json shouldContain "\"type\":\"EnkeltplassUtkast\""
            json shouldContain "\"SKOLEPENGER\":10000"
        }

        @Test
        fun `EnkeltplassEndrePrisinformasjon med IngenKostnader serialiseres korrekt`() {
            val gjennomforingId = UUID.randomUUID()
            val payload = GjennomforingRequestPayload.EnkeltplassEndrePrisinformasjon(
                gjennomforingId = gjennomforingId,
                payload = GjennomforingRequestPayload.Prisinformasjon.IngenKostnader(
                    aarsak = GjennomforingRequestPayload.Prisinformasjon.IngenKostnader.Aarsak.OPPLAERINGEN_ER_KOSTNADSFRI,
                    tilleggsopplysninger = null,
                ),
                totrinnskontroll = GjennomforingRequestPayload.Totrinnskontroll(
                    id = UUID.randomUUID(),
                    behandletAv = "Z123456",
                ),
            )

            val json = objectMapper.writeValueAsString(payload)

            json shouldContain "\"type\":\"EnkeltplassEndrePrisinformasjon\""
            json shouldContain "\"aarsak\":\"OPPLAERINGEN_ER_KOSTNADSFRI\""
        }

        @Test
        fun `EnkeltplassEndreInnhold serialiseres korrekt`() {
            val gjennomforingId = UUID.randomUUID()
            val payload = GjennomforingRequestPayload.EnkeltplassEndreInnhold(
                gjennomforingId = gjennomforingId,
                payload = GjennomforingRequestPayload.UpsertEnkeltplass.OpplaringKategorisering(
                    verdier = emptyMap(),
                    sertifiseringer = setOf(SertifiseringValg(id = 1, navn = "Truckfører T1")),
                ),
            )

            val json = objectMapper.writeValueAsString(payload)

            json shouldContain "\"type\":\"EnkeltplassEndreInnhold\""
            json shouldContain "\"Truckfører T1\""
        }
    }

    @Nested
    inner class RoundtripTest {
        @Test
        fun `EnkeltplassSoktInn med Anskaffelse overlever serialisering og deserialisering`() {
            val original = lagEnkeltplassSoktInn(
                prisinformasjon = GjennomforingRequestPayload.Prisinformasjon.Anskaffelse(pris = 42000),
            )

            val json = objectMapper.writeValueAsString(original)
            val deserialized = objectMapper.readValue<GjennomforingRequestPayload>(json)

            deserialized shouldBe original
        }

        @Test
        fun `EnkeltplassUtkast med Tilskudd overlever serialisering og deserialisering`() {
            val original = lagEnkeltplassUtkast(
                prisinformasjon = GjennomforingRequestPayload.Prisinformasjon.Tilskudd(
                    tilskudd = mapOf(
                        GjennomforingRequestPayload.Prisinformasjon.Tilskudd.Tilskuddstype.SKOLEPENGER to 5000,
                        GjennomforingRequestPayload.Prisinformasjon.Tilskudd.Tilskuddstype.EKSAMENSGEBYR to 2000,
                    ),
                    tilleggsopplysninger = "Tillegg",
                ),
            )

            val json = objectMapper.writeValueAsString(original)
            val deserialized = objectMapper.readValue<GjennomforingRequestPayload>(json)

            deserialized shouldBe original
        }

        @Test
        fun `EnkeltplassEndrePrisinformasjon med IngenKostnader overlever serialisering og deserialisering`() {
            val original = GjennomforingRequestPayload.EnkeltplassEndrePrisinformasjon(
                gjennomforingId = UUID.randomUUID(),
                payload = GjennomforingRequestPayload.Prisinformasjon.IngenKostnader(
                    aarsak = GjennomforingRequestPayload.Prisinformasjon.IngenKostnader.Aarsak.OPPLAERINGEN_ER_EGENFINANSIERT,
                    tilleggsopplysninger = "Forklaring",
                ),
                totrinnskontroll = GjennomforingRequestPayload.Totrinnskontroll(
                    id = UUID.randomUUID(),
                    behandletAv = "Z123456",
                ),
            )

            val json = objectMapper.writeValueAsString(original)
            val deserialized = objectMapper.readValue<GjennomforingRequestPayload>(json)

            deserialized shouldBe original
        }

        @Test
        fun `EnkeltplassEndrePrisinformasjon med Anskaffelse overlever serialisering og deserialisering`() {
            val original = GjennomforingRequestPayload.EnkeltplassEndrePrisinformasjon(
                gjennomforingId = UUID.randomUUID(),
                payload = GjennomforingRequestPayload.Prisinformasjon.Anskaffelse(pris = 99000),
                totrinnskontroll = GjennomforingRequestPayload.Totrinnskontroll(
                    id = UUID.randomUUID(),
                    behandletAv = "Z123456",
                ),
            )

            val json = objectMapper.writeValueAsString(original)
            val deserialized = objectMapper.readValue<GjennomforingRequestPayload>(json)

            deserialized shouldBe original
        }

        @Test
        fun `EnkeltplassEndreInnhold med null payload overlever serialisering og deserialisering`() {
            val original = GjennomforingRequestPayload.EnkeltplassEndreInnhold(
                gjennomforingId = UUID.randomUUID(),
                payload = null,
            )

            val json = objectMapper.writeValueAsString(original)
            val deserialized = objectMapper.readValue<GjennomforingRequestPayload>(json)

            deserialized shouldBe original
        }
    }

    companion object {
        private fun lagEnkeltplassSoktInn(prisinformasjon: GjennomforingRequestPayload.Prisinformasjon) =
            GjennomforingRequestPayload.EnkeltplassSoktInn(
                gjennomforingId = UUID.randomUUID(),
                payload = GjennomforingRequestPayload.UpsertEnkeltplass(
                    tiltakskode = Tiltakskode.ARBEIDSMARKEDSOPPLAERING,
                    organisasjonsnummer = "987654321",
                    prisinformasjon = prisinformasjon,
                    ansvarligEnhet = "1234",
                    opprettetAv = "Z123456",
                    kategorisering = null,
                ),
                totrinnskontroll = GjennomforingRequestPayload.Totrinnskontroll(
                    id = UUID.randomUUID(),
                    behandletAv = "Z123456",
                ),
            )

        private fun lagEnkeltplassUtkast(prisinformasjon: GjennomforingRequestPayload.Prisinformasjon) =
            GjennomforingRequestPayload.EnkeltplassUtkast(
                gjennomforingId = UUID.randomUUID(),
                payload = GjennomforingRequestPayload.UpsertEnkeltplass(
                    tiltakskode = Tiltakskode.ARBEIDSMARKEDSOPPLAERING,
                    organisasjonsnummer = "987654321",
                    prisinformasjon = prisinformasjon,
                    ansvarligEnhet = "1234",
                    opprettetAv = "Z123456",
                    kategorisering = null,
                ),
            )
    }
}
