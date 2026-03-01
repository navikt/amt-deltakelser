package no.nav.amt.deltaker.bff.deltaker.api.model

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import no.nav.amt.deltaker.bff.utils.data.TestData.lagDeltaker
import no.nav.amt.deltaker.bff.utils.data.TestData.lagDeltakerStatus
import no.nav.amt.lib.models.deltaker.DeltakerEndring
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.ValueSource
import java.time.LocalDate
import java.util.UUID

class AvsluttDeltakelseRequestTest {
    @Nested
    inner class HarDeltatt {
        @Test
        fun `harDeltatt - deltaker har deltatt = null - returnerer true`() {
            val avsluttDeltakelseRequest = avsluttDeltakelseRequestInTest.copy(
                harDeltatt = null,
            )
            avsluttDeltakelseRequest.harDeltatt() shouldBe true
        }

        @ParameterizedTest
        @ValueSource(booleans = [true, false])
        fun `harDeltatt - deltaker har deltatt`(harDeltatt: Boolean) {
            val avsluttDeltakelseRequest = avsluttDeltakelseRequestInTest.copy(
                harDeltatt = harDeltatt,
            )
            avsluttDeltakelseRequest.harDeltatt() shouldBe harDeltatt
        }
    }

    @Nested
    inner class HarFullfort {
        @Test
        fun `harFullfort - deltaker har fullfort = null - returnerer true`() {
            val avsluttDeltakelseRequest = avsluttDeltakelseRequestInTest.copy(
                harFullfort = null,
            )
            avsluttDeltakelseRequest.harFullfort() shouldBe true
        }

        @ParameterizedTest
        @ValueSource(booleans = [true, false])
        fun `harFullfort - deltaker har fullfort`(harFullfort: Boolean) {
            val avsluttDeltakelseRequest = avsluttDeltakelseRequestInTest.copy(
                harFullfort = harFullfort,
            )
            avsluttDeltakelseRequest.harFullfort() shouldBe harFullfort
        }
    }

    @Nested
    inner class Valider {
        @ParameterizedTest
        @EnumSource(DeltakerStatus.Type::class, names = ["DELTAR", "HAR_SLUTTET", "AVBRUTT", "FULLFORT"], mode = EnumSource.Mode.EXCLUDE)
        fun `valider - ugyldig deltakerstatus - feiler`(status: DeltakerStatus.Type) {
            val deltaker = deltakerInTest.copy(
                status = lagDeltakerStatus(status),
                sluttdato = yesterday.minusDays(1),
            )

            val thrown = shouldThrow<IllegalArgumentException> {
                avsluttDeltakelseRequestInTest.valider(deltaker)
            }

            if (status == DeltakerStatus.Type.FEILREGISTRERT) {
                thrown.message shouldBe "Kan ikke endre feilregistrert deltaker"
            } else {
                thrown.message shouldBe
                    "Avslutte deltakelse for deltaker krever en av følgende statuser: DELTAR, HAR_SLUTTET, AVBRUTT, FULLFORT"
            }
        }

        @ParameterizedTest
        @EnumSource(DeltakerStatus.Type::class, names = ["DELTAR", "HAR_SLUTTET", "AVBRUTT", "FULLFORT"])
        fun `valider - skal ikke feile med gyldig deltakerstatus`(status: DeltakerStatus.Type) {
            val deltaker = deltakerInTest.copy(
                status = lagDeltakerStatus(status),
                sluttdato = yesterday.minusDays(1),
            )

            shouldNotThrowAny {
                avsluttDeltakelseRequestInTest.valider(deltaker)
            }
        }

        @Test
        fun `skal kaste feil dersom harDeltatt og sluttdato er null`() {
            val request = avsluttDeltakelseRequestInTest.copy(
                harDeltatt = true,
                sluttdato = null,
            )

            val thrown = shouldThrow<IllegalArgumentException> {
                request.valider(deltakerInTest)
            }

            thrown.message shouldBe "Må angi sluttdato for deltaker som har deltatt"
        }

        @Test
        fun `skal ikke kaste feil dersom harDeltatt og sluttdato forskjellig fra null`() {
            val request = avsluttDeltakelseRequestInTest.copy(
                harDeltatt = true,
                sluttdato = yesterday.minusDays(1),
            )

            shouldNotThrowAny {
                request.valider(deltakerInTest)
            }
        }

        @Test
        fun `skal ikke kaste feil dersom harDeltatt = false og status er DELTAR`() {
            val request = avsluttDeltakelseRequestInTest.copy(
                harDeltatt = false,
            )

            val deltaker = deltakerInTest.copy(status = lagDeltakerStatus(DeltakerStatus.Type.DELTAR))

            shouldNotThrowAny {
                request.valider(deltaker)
            }
        }

        @Test
        fun `valider - deltaker er uendret - feiler`() {
            val thrown = shouldThrow<IllegalArgumentException> {
                avsluttDeltakelseRequestInTest.valider(deltakerInTest)
            }

            thrown.message shouldBe "Kan ikke avslutte deltakelse med uendret sluttdato og årsak"
        }

        @Test
        fun `valider - deltaker har annen sluttdato - ok`() {
            val deltaker = deltakerInTest.copy(sluttdato = yesterday.minusDays(1))

            shouldNotThrowAny {
                avsluttDeltakelseRequestInTest.valider(deltaker)
            }
        }

        @Test
        fun `valider - deltaker har annen sluttaarsak - ok`() {
            val deltaker = deltakerInTest.copy(sluttdato = yesterday.minusDays(1))

            val request = avsluttDeltakelseRequestInTest.copy(
                aarsak = DeltakerEndring.Aarsak(DeltakerEndring.Aarsak.Type.IKKE_MOTT, null),
                sluttdato = yesterday.minusDays(1),
            )

            shouldNotThrowAny {
                request.valider(deltaker)
            }
        }
    }

    companion object {
        private val yesterday: LocalDate = LocalDate.now().minusDays(1)

        private val deltakerInTest = lagDeltaker(
            status = lagDeltakerStatus(
                statusType = DeltakerStatus.Type.HAR_SLUTTET,
                aarsakType = DeltakerStatus.Aarsak.Type.FATT_JOBB,
            ),
            sluttdato = yesterday,
        )

        private val avsluttDeltakelseRequestInTest = AvsluttDeltakelseRequest(
            aarsak = DeltakerEndring.Aarsak(DeltakerEndring.Aarsak.Type.FATT_JOBB, null),
            sluttdato = yesterday,
            begrunnelse = "Begrunnelse",
            forslagId = UUID.randomUUID(),
        )
    }
}
