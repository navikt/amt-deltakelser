package no.nav.amt.deltaker.enkeltplass.kafka

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import no.nav.amt.deltaker.utils.data.TestData.lagDeltaker
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import org.junit.jupiter.api.Test
import java.time.LocalDate

class TotrinnskontrollConsumerNyDeltakerStatusTest {
    @Test
    fun `nyDeltakerStatus - returnerer FULLFORT når sluttdato er i fortiden`() {
        // Arrange
        val deltakerInTest = lagDeltakerForNyStatus(
            startdato = LocalDate.now().minusWeeks(2),
            sluttdato = LocalDate.now().minusDays(1),
        )

        // Act
        val status = TotrinnskontrollConsumer.nyDeltakerStatus(deltakerInTest)

        // Assert
        status shouldBe DeltakerStatus.Type.FULLFORT
    }

    @Test
    fun `nyDeltakerStatus - returnerer VENTER_PA_OPPSTART når startdato er i fremtiden`() {
        // Arrange
        val deltakerInTest = lagDeltakerForNyStatus(
            startdato = LocalDate.now().plusDays(5),
            sluttdato = LocalDate.now().plusWeeks(4),
        )

        // Act
        val status = TotrinnskontrollConsumer.nyDeltakerStatus(deltakerInTest)

        // Assert
        status shouldBe DeltakerStatus.Type.VENTER_PA_OPPSTART
    }

    @Test
    fun `nyDeltakerStatus - returnerer DELTAR når startdato er idag`() {
        // Arrange
        val deltakerInTest = lagDeltakerForNyStatus(
            startdato = LocalDate.now(),
            sluttdato = LocalDate.now().plusWeeks(4),
        )

        // Act
        val status = TotrinnskontrollConsumer.nyDeltakerStatus(deltakerInTest)

        // Assert
        status shouldBe DeltakerStatus.Type.DELTAR
    }

    @Test
    fun `nyDeltakerStatus - returnerer DELTAR når startdato er i fortiden og sluttdato er i fremtiden`() {
        // Arrange
        val deltakerInTest = lagDeltakerForNyStatus(
            startdato = LocalDate.now().minusDays(3),
            sluttdato = LocalDate.now().plusWeeks(2),
        )

        // Act
        val status = TotrinnskontrollConsumer.nyDeltakerStatus(deltakerInTest)

        // Assert
        status shouldBe DeltakerStatus.Type.DELTAR
    }

    @Test
    fun `nyDeltakerStatus - returnerer DELTAR når sluttdato er idag`() {
        // Arrange
        val deltakerInTest = lagDeltakerForNyStatus(
            startdato = LocalDate.now().minusDays(5),
            sluttdato = LocalDate.now(),
        )

        // Act
        val status = TotrinnskontrollConsumer.nyDeltakerStatus(deltakerInTest)

        // Assert
        status shouldBe DeltakerStatus.Type.DELTAR
    }

    @Test
    fun `nyDeltakerStatus - kaster feil når startdato mangler`() {
        // Arrange
        val deltakerInTest = lagDeltakerForNyStatus(
            startdato = null,
            sluttdato = LocalDate.now().plusWeeks(4),
        )

        // Act
        val exception = shouldThrow<IllegalStateException> {
            TotrinnskontrollConsumer.nyDeltakerStatus(deltakerInTest)
        }

        // Assert
        exception.message shouldBe "Startdato mangler for deltaker ${deltakerInTest.id}"
    }

    @Test
    fun `nyDeltakerStatus - kaster feil når sluttdato mangler`() {
        // Arrange
        val deltakerInTest = lagDeltakerForNyStatus(
            startdato = LocalDate.now(),
            sluttdato = null,
        )

        // Act
        val exception = shouldThrow<IllegalStateException> {
            TotrinnskontrollConsumer.nyDeltakerStatus(deltakerInTest)
        }

        // Assert
        exception.message shouldBe "Sluttdato mangler for deltaker ${deltakerInTest.id}"
    }

    private companion object {
        fun lagDeltakerForNyStatus(
            startdato: LocalDate? = LocalDate.now().minusDays(1),
            sluttdato: LocalDate? = LocalDate.now().plusWeeks(4),
        ) = lagDeltaker(
            startdato = startdato,
            sluttdato = sluttdato,
        )
    }
}
