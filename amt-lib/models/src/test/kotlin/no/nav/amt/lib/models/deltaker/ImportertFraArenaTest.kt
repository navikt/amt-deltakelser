package no.nav.amt.lib.models.deltaker

import io.kotest.matchers.shouldBe
import no.nav.amt.lib.testing.utils.TestData
import no.nav.amt.lib.testing.utils.TestData.lagDeltakerVedImport
import org.junit.jupiter.api.Test
import java.time.LocalDate

class ImportertFraArenaTest {
    @Test
    fun `innsoktDato er dagen foer importertDato, returnerer innsoktDato`() {
        val innsoktDato = LocalDate.now().minusDays(1)
        val importertFraArena = TestData.lagImportertFraArena(
            deltakerVedImport = lagDeltakerVedImport(innsoktDato = innsoktDato),
        )

        importertFraArena.innsoktDatoAsLocalDateTime shouldBe innsoktDato.atStartOfDay()
    }

    @Test
    fun `innsoktDato er samme dag som importertDato, returnerer importertDato`() {
        val importertFraArena = TestData.lagImportertFraArena(
            deltakerVedImport = lagDeltakerVedImport(innsoktDato = LocalDate.now()),
        )

        importertFraArena.innsoktDatoAsLocalDateTime shouldBe importertFraArena.importertDato
    }
}
