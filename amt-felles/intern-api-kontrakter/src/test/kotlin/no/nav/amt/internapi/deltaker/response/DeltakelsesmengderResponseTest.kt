package no.nav.amt.internapi.deltaker.response

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import no.nav.amt.lib.models.deltaker.deltakelsesmengde.Deltakelsesmengde
import no.nav.amt.lib.models.deltaker.deltakelsesmengde.Deltakelsesmengder
import no.nav.amt.lib.utils.objectMapper
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import tools.jackson.databind.exc.MismatchedInputException
import tools.jackson.module.kotlin.readValue
import java.time.LocalDate

class DeltakelsesmengderResponseTest {
    @Nested
    inner class NesteTilVisning {
        private val iDag = LocalDate.of(2026, 12, 1)

        @Test
        fun `tom historikk gir ingen neste periode`() {
            // Arrange
            val mengder = Deltakelsesmengder(emptyList())

            // Act
            val nestePeriode = mengder.nesteTilVisning(startdato = iDag.plusDays(10), iDag = iDag)

            // Assert
            nestePeriode shouldBe null
        }

        @Test
        fun `mengden fra fremtidig oppstart vises ikke som neste periode`() {
            // Arrange
            val startdato = iDag.plusDays(10)
            val oppstartsmengde = Deltakelsesmengde(100F, 5F, startdato, iDag.atStartOfDay())
            val mengder = Deltakelsesmengder(listOf(oppstartsmengde))

            // Act
            val nestePeriode = mengder.nesteTilVisning(startdato, iDag)

            // Assert
            nestePeriode shouldBe null
            mengder.single() shouldBe oppstartsmengde
        }

        @Test
        fun `første endring etter fremtidig oppstart vises som neste periode`() {
            // Arrange
            val startdato = iDag.plusDays(10)
            val oppstartsmengde = Deltakelsesmengde(100F, 5F, startdato, iDag.atStartOfDay())
            val neste = Deltakelsesmengde(60F, 3F, startdato.plusDays(7), iDag.atTime(1, 0))
            val siste = Deltakelsesmengde(40F, 2F, startdato.plusDays(14), iDag.atTime(2, 0))
            val mengder = Deltakelsesmengder(listOf(oppstartsmengde, neste, siste))

            // Act
            val nestePeriode = mengder.nesteTilVisning(startdato, iDag)

            // Assert
            nestePeriode shouldBe neste
        }

        @Test
        fun `uten fremtidig startdato velges første endring etter i dag`() {
            // Arrange
            val gjeldende = Deltakelsesmengde(100F, 5F, iDag, iDag.atStartOfDay())
            val neste = Deltakelsesmengde(60F, 3F, iDag.plusDays(1), iDag.atTime(1, 0))
            val mengder = Deltakelsesmengder(listOf(gjeldende, neste))

            listOf(null, iDag.minusDays(10), iDag).forEach { startdato ->
                // Act
                val nestePeriode = mengder.nesteTilVisning(startdato, iDag)

                // Assert
                nestePeriode shouldBe neste
            }
        }

        @Test
        fun `første mengde etter oppstart beholdes når mengden ved oppstart mangler`() {
            // Arrange
            val startdato = iDag.plusDays(10)
            val neste = Deltakelsesmengde(60F, 3F, startdato.plusDays(7), iDag.atStartOfDay())
            val mengder = Deltakelsesmengder(listOf(neste))

            // Act
            val nestePeriode = mengder.nesteTilVisning(startdato, iDag)

            // Assert
            nestePeriode shouldBe neste
        }

        @Test
        fun `planlagt tømming etter oppstart beholdes som neste periode`() {
            // Arrange
            val startdato = iDag.plusDays(10)
            val oppstartsmengde = Deltakelsesmengde(100F, 5F, startdato, iDag.atStartOfDay())

            listOf(3F, null).forEach { dager ->
                val neste = Deltakelsesmengde(null, dager, startdato.plusDays(7), iDag.atTime(1, 0))
                val mengder = Deltakelsesmengder(listOf(oppstartsmengde, neste))

                // Act
                val nestePeriode = mengder.nesteTilVisning(startdato, iDag)

                // Assert
                nestePeriode shouldBe neste
            }
        }
    }

    @Nested
    inner class Deserialisering {
        @Test
        fun `siste periode bevares selv uten prosent og neste periode`() {
            listOf(3F, null).forEach { dager ->
                // Arrange
                val response = DeltakelsesmengderResponse(
                    sisteDeltakelsesmengde = DeltakelsesmengdeResponse(null, dager, LocalDate.of(2026, 12, 1)),
                )
                val json = objectMapper.writeValueAsString(response)

                // Act
                val deserialisert = objectMapper.readValue<DeltakelsesmengderResponse>(json)

                // Assert
                deserialisert shouldBe response
                deserialisert.nesteDeltakelsesmengde shouldBe null
                deserialisert.sisteDeltakelsesmengde.deltakelsesprosent shouldBe null
                deserialisert.sisteDeltakelsesmengde.dagerPerUke shouldBe dager
            }
        }

        @Test
        fun `manglende eller null siste periode avvises`() {
            // Arrange
            val ugyldigeResponser = listOf("{}", """{"sisteDeltakelsesmengde":null}""")

            // Act
            val resultater = ugyldigeResponser.map { json ->
                runCatching {
                    objectMapper.readValue<DeltakelsesmengderResponse>(json)
                }
            }

            // Assert
            resultater.forEach { it.exceptionOrNull().shouldBeInstanceOf<MismatchedInputException>() }
        }
    }
}
