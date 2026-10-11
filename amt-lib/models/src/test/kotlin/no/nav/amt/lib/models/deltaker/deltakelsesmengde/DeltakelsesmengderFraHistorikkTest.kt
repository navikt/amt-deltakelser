package no.nav.amt.lib.models.deltaker.deltakelsesmengde

import io.kotest.matchers.shouldBe
import no.nav.amt.lib.models.deltaker.DeltakerHistorikk
import no.nav.amt.lib.models.deltaker.deltakelsesmengde.utils.TestData
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class DeltakelsesmengderFraHistorikkTest {
    @Nested
    inner class ToDeltakelsesmengder {
        @Test
        fun `usortert historikk behandles i tidsrekkefølge og beholder fremtidige perioder`() {
            // Arrange
            val vedtak = TestData.lagVedtak(fattet = "2024-01-01".toDateTime())
            val startdato = TestData.lagLeggTilOppstartsdato(
                startdato = "2024-01-10".toDate(),
                opprettet = "2024-01-02".toDateTime(),
            )
            val korrigering = TestData.lagEndreDeltakelsesmengde(
                deltakelsesprosent = 50,
                gyldigFra = "2024-01-05".toDate(),
                opprettet = "2024-01-03".toDateTime(),
            )
            val fremtidig = TestData.lagEndreDeltakelsesmengde(
                deltakelsesprosent = 75,
                gyldigFra = "2024-01-20".toDate(),
                opprettet = "2024-01-04".toDateTime(),
            )
            val historikk = listOf(
                DeltakerHistorikk.Endring(fremtidig),
                DeltakerHistorikk.Endring(korrigering),
                DeltakerHistorikk.EndringFraArrangor(startdato),
                DeltakerHistorikk.Vedtak(vedtak),
            )

            for (ekstern in listOf(false, true)) {
                // Act
                val perioder = historikk.toDeltakelsesmengder(isForDeltakerExternalTopic = ekstern).perioder

                // Assert
                perioder.map { it.gyldigFra } shouldBe listOf("2024-01-10".toDate(), "2024-01-20".toDate())
                perioder.map { it.deltakelsesprosent } shouldBe listOf(50F, 75F)
            }
        }

        @Test
        fun `fjerning av startdato gjenoppretter råhistorikk for senere endring`() {
            // Arrange
            val vedtak = TestData.lagVedtak(fattet = "2024-01-01".toDateTime())
            val forsteEndring = TestData.lagEndreDeltakelsesmengde(50, "2024-01-05".toDate(), "2024-01-02".toDateTime())
            val senereEndring = TestData.lagEndreDeltakelsesmengde(75, "2024-01-07".toDate(), "2024-01-03".toDateTime())
            val startdato = TestData.lagLeggTilOppstartsdato(
                startdato = "2024-01-10".toDate(),
                opprettet = "2024-01-04".toDateTime(),
            )
            val fjernStartdato = TestData.lagFjernOppstartsdato(opprettet = "2024-01-05".toDateTime())
            val nyMengde = Deltakelsesmengde(60F, null, "2024-01-06".toDate(), "2024-01-06".toDateTime())
            val historikk = TestData.lagDeltakerHistorikk(
                vedtak = listOf(vedtak),
                endringer = listOf(forsteEndring, senereEndring, fjernStartdato),
                endringerFraArrangor = listOf(startdato),
            )

            for (ekstern in listOf(false, true)) {
                // Act
                val mengder = historikk.toDeltakelsesmengder(isForDeltakerExternalTopic = ekstern)
                val perioder = mengder.medNyDeltakelsesmengde(nyMengde).perioder

                // Assert
                perioder.map { it.gyldigFra } shouldBe listOf(
                    "2024-01-01".toDate(),
                    "2024-01-05".toDate(),
                    "2024-01-06".toDate(),
                    "2024-01-07".toDate(),
                )
                perioder.map { it.deltakelsesprosent } shouldBe listOf(100F, 50F, 60F, 75F)
            }
        }

        @Test
        fun `startdato fra vedtak og arenaimport brukes uten egen startdatoendring`() {
            // Arrange
            val vedtak = TestData
                .lagVedtak(fattet = "2024-01-01".toDateTime())
                .let { it.copy(deltakerVedVedtak = it.deltakerVedVedtak.copy(startdato = "2024-01-10".toDate())) }
            val import = TestData
                .lagImportertFraArena(innsoktDato = "2024-01-01".toDate())
                .let { it.copy(deltakerVedImport = it.deltakerVedImport.copy(startdato = "2024-01-10".toDate())) }
            val historikker: List<List<DeltakerHistorikk>> = listOf(
                listOf(DeltakerHistorikk.Vedtak(vedtak)),
                listOf(DeltakerHistorikk.ImportertFraArena(import)),
            )

            for (historikk in historikker) {
                for (ekstern in listOf(false, true)) {
                    // Act
                    val perioder = historikk.toDeltakelsesmengder(isForDeltakerExternalTopic = ekstern).perioder

                    // Assert
                    perioder.map { it.gyldigFra } shouldBe listOf("2024-01-10".toDate())
                    perioder.map { it.deltakelsesprosent } shouldBe listOf(100F)
                }
            }
        }

        @Test
        fun `fjernet startdato i vedtak eller arenaimport med ny mengde gjenoppretter alle perioder`() {
            // Arrange
            val startdato = TestData.lagLeggTilOppstartsdato(
                startdato = "2024-01-10".toDate(),
                opprettet = "2024-01-02".toDateTime(),
            )
            val vedtakForStartdato = TestData.lagVedtak(fattet = "2024-01-01".toDateTime())
            val vedtakEtterStartdato = TestData.lagVedtak(
                deltakelsesprosent = 60F,
                dagerPerUke = 3F,
                fattet = "2024-01-03".toDateTime(),
            )
            val importEtterStartdato = TestData.lagImportertFraArena(
                deltakelsesprosent = 60F,
                dagerPerUke = 3F,
                innsoktDato = "2024-01-03".toDate(),
                opprettet = "2024-01-03".toDateTime(),
            )
            val historikker = listOf(
                listOf(
                    DeltakerHistorikk.Vedtak(vedtakForStartdato),
                    DeltakerHistorikk.EndringFraArrangor(startdato),
                    DeltakerHistorikk.Vedtak(vedtakEtterStartdato),
                ),
                listOf(
                    DeltakerHistorikk.Vedtak(vedtakForStartdato),
                    DeltakerHistorikk.EndringFraArrangor(startdato),
                    DeltakerHistorikk.ImportertFraArena(importEtterStartdato),
                ),
            )

            for (historikk in historikker) {
                // Act
                val perioder = historikk.toDeltakelsesmengder(isForDeltakerExternalTopic = false).perioder

                // Assert
                perioder.map { it.gyldigFra } shouldBe listOf("2024-01-01".toDate(), "2024-01-03".toDate())
                perioder.map { it.deltakelsesprosent } shouldBe listOf(100F, 60F)
            }
        }
    }
}
