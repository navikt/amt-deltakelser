package no.nav.amt.deltaker.bff.veileder.api.request

import io.kotest.assertions.throwables.shouldNotThrow
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import no.nav.amt.deltaker.bff.utils.TestData
import no.nav.amt.internapi.deltaker.request.OpplaringKategoriseringValgRequest
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltaker.OpplaringKategoriseringType
import no.nav.amt.lib.models.deltakerliste.GjennomforingType
import no.nav.amt.lib.models.deltakerliste.SertifiseringValg
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.UUID
import java.util.stream.Stream

class EndreOpplaringKategoriseringRequestTest {
    @Nested
    inner class ValiderTest {
        @Test
        fun `valider - status fra og med sokt inn - skal ikke kaste exception`() {
            // Arrange
            val deltaker = TestData.lagDeltakerModel(
                gjennomforingResponse = TestData.lagGjennomforingResponse().copy(type = GjennomforingType.Enkeltplass),
                status = TestData.lagDeltakerStatus(DeltakerStatus.Type.SOKT_INN),
            )
            val request = lagRequest()

            // Act & Assert
            shouldNotThrow<IllegalArgumentException> {
                request.valider(deltaker)
            }
        }

        @Test
        fun `valider - tomme kategoriseringer og sertifiseringer - skal ikke kaste exception`() {
            val deltaker = TestData.lagDeltakerModel(
                gjennomforingResponse = TestData.lagGjennomforingResponse().copy(type = GjennomforingType.Enkeltplass),
                status = TestData.lagDeltakerStatus(DeltakerStatus.Type.SOKT_INN),
            )
            val request = lagRequest(
                opplaringKategoriseringValg = emptySet(),
                sertifiseringValg = emptySet(),
            )

            shouldNotThrow<IllegalArgumentException> {
                request.valider(deltaker)
            }
        }

        @ParameterizedTest(name = "valider - status {0} - skal kaste exception")
        @MethodSource("no.nav.amt.deltaker.bff.veileder.api.request.EndreOpplaringKategoriseringRequestTest#statusFoerSoktInn")
        fun `valider - status før søkt inn - skal kaste exception`(
            status: DeltakerStatus.Type,
            expectedMessage: String,
        ) {
            val deltaker = TestData.lagDeltakerModel(
                gjennomforingResponse = TestData.lagGjennomforingResponse().copy(type = GjennomforingType.Enkeltplass),
                status = TestData.lagDeltakerStatus(status),
            )
            val request = lagRequest()

            val exception = shouldThrow<IllegalArgumentException> {
                request.valider(deltaker)
            }

            exception.message shouldContain expectedMessage
        }

        @Test
        fun `valider - ikke enkeltplass - skal kaste exception`() {
            // Arrange
            val deltaker = TestData.lagDeltakerModel(
                gjennomforingResponse = TestData.lagGjennomforingResponse().copy(type = GjennomforingType.Gruppe),
                status = TestData.lagDeltakerStatus(DeltakerStatus.Type.SOKT_INN),
            )
            val request = lagRequest()

            // Act
            val exception = shouldThrow<IllegalArgumentException> {
                request.valider(deltaker)
            }

            // Assert
            exception.message shouldContain "Kan ikke endre opplæringskategorisering for deltakere som ikke er på enkeltplass"
        }

        @Test
        fun `valider - sertifisering med ikke positiv id - skal kaste exception`() {
            val deltaker = TestData.lagDeltakerModel(
                gjennomforingResponse = TestData.lagGjennomforingResponse().copy(type = GjennomforingType.Enkeltplass),
                status = TestData.lagDeltakerStatus(DeltakerStatus.Type.SOKT_INN),
            )
            val request = lagRequest(
                opplaringKategoriseringValg = emptySet(),
                sertifiseringValg = setOf(SertifiseringValg(id = 0, navn = "Truckførerbevis")),
            )

            val exception = shouldThrow<IllegalArgumentException> {
                request.valider(deltaker)
            }

            exception.message shouldContain "Sertifisering-id må være positiv"
        }

        @Test
        fun `valider - sertifisering med tomt navn - skal kaste exception`() {
            val deltaker = TestData.lagDeltakerModel(
                gjennomforingResponse = TestData.lagGjennomforingResponse().copy(type = GjennomforingType.Enkeltplass),
                status = TestData.lagDeltakerStatus(DeltakerStatus.Type.SOKT_INN),
            )
            val request = lagRequest(
                opplaringKategoriseringValg = emptySet(),
                sertifiseringValg = setOf(SertifiseringValg(id = 1, navn = " ")),
            )

            val exception = shouldThrow<IllegalArgumentException> {
                request.valider(deltaker)
            }

            exception.message shouldContain "Sertifisering-navn kan ikke være tomt"
        }

        @Test
        fun `valider - samme sertifisering id med ulike navn - skal kaste exception`() {
            val deltaker = TestData.lagDeltakerModel(
                gjennomforingResponse = TestData.lagGjennomforingResponse().copy(type = GjennomforingType.Enkeltplass),
                status = TestData.lagDeltakerStatus(DeltakerStatus.Type.SOKT_INN),
            )
            val request = lagRequest(
                opplaringKategoriseringValg = emptySet(),
                sertifiseringValg = setOf(
                    SertifiseringValg(id = 1, navn = "Truckførerbevis"),
                    SertifiseringValg(id = 1, navn = "Varmt arbeid"),
                ),
            )

            val exception = shouldThrow<IllegalArgumentException> {
                request.valider(deltaker)
            }

            exception.message shouldContain "Samme sertifisering-id kan ikke ha flere navn"
        }

        @Test
        fun `kodeverkValg - samler valgte ider fra alle kategoriseringer`() {
            val id1 = UUID.randomUUID()
            val id2 = UUID.randomUUID()
            val id3 = UUID.randomUUID()
            val request = lagRequest(
                opplaringKategoriseringValg = setOf(
                    OpplaringKategoriseringValgRequest(
                        representerer = OpplaringKategoriseringType.BRANSJE_ID,
                        valgteIder = setOf(id1, id2),
                    ),
                    OpplaringKategoriseringValgRequest(
                        representerer = OpplaringKategoriseringType.LAREFAG,
                        valgteIder = setOf(id3),
                    ),
                ),
            )

            request.opplaringKategoriseringValg.flatMap { it.valgteIder }.toSet() shouldBe setOf(id1, id2, id3)
        }
    }

    companion object {
        @JvmStatic
        fun statusFoerSoktInn(): Stream<Arguments> = Stream.of(
            Arguments.of(
                DeltakerStatus.Type.KLADD,
                "Kan ikke endre opplæringskategorisering for deltaker med status KLADD",
            ),
            Arguments.of(
                DeltakerStatus.Type.UTKAST_TIL_PAMELDING,
                "Kan ikke endre opplæringskategorisering for deltaker med status UTKAST_TIL_PAMELDING",
            ),
            Arguments.of(
                DeltakerStatus.Type.AVBRUTT_UTKAST,
                "Kan ikke endre opplæringskategorisering for deltaker med status AVBRUTT_UTKAST",
            ),
        )

        private fun lagRequest(
            opplaringKategoriseringValg: Set<OpplaringKategoriseringValgRequest> = setOf(
                OpplaringKategoriseringValgRequest(
                    representerer = OpplaringKategoriseringType.BRANSJE_ID,
                    valgteIder = setOf(UUID.randomUUID()),
                ),
            ),
            sertifiseringValg: Set<SertifiseringValg> = emptySet(),
        ) = EndreOpplaringKategoriseringRequest(
            opplaringKategoriseringValg = opplaringKategoriseringValg,
            sertifiseringValg = sertifiseringValg,
            beskrivelse = "begrunnelse",
            pavirkerPris = false,
        )
    }
}
