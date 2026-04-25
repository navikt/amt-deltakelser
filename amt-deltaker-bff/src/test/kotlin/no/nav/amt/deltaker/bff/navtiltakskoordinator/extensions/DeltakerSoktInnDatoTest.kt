package no.nav.amt.deltaker.bff.navtiltakskoordinator.extensions

import io.kotest.matchers.shouldBe
import no.nav.amt.deltaker.bff.utils.data.TestData.lagDeltaker
import no.nav.amt.deltaker.bff.utils.data.TestData.lagDeltakerEndring
import no.nav.amt.deltaker.bff.utils.data.TestData.lagVedtak
import no.nav.amt.lib.models.deltaker.DeltakerHistorikk
import no.nav.amt.lib.models.deltaker.DeltakerVedImport
import no.nav.amt.lib.models.deltaker.ImportertFraArena
import no.nav.amt.lib.models.deltaker.InnsokPaaFellesOppstart
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

class DeltakerSoktInnDatoTest {
    @Test
    fun `tom historikk - returnerer null`() {
        // Arrange
        val deltaker = lagDeltaker(historikk = false)

        // Act & Assert
        deltaker.soktInnDato() shouldBe null
    }

    @Test
    fun `historikk uten relevante typer - returnerer null`() {
        // Arrange
        val baseDeltaker = lagDeltaker(historikk = false)
        val endring = lagDeltakerEndring(deltakerId = baseDeltaker.id)
        val deltaker = baseDeltaker.copy(
            historikk = listOf(DeltakerHistorikk.Endring(endring)),
        )

        // Act & Assert
        deltaker.soktInnDato() shouldBe null
    }

    @Test
    fun `kun InnsokPaaFellesOppstart - returnerer innsokt-dato`() {
        // Arrange
        val innsokt = LocalDateTime.now().minusDays(7)
        val baseDeltaker = lagDeltaker(historikk = false)
        val deltaker = baseDeltaker.copy(
            historikk = listOf(lagInnsokHistorikk(baseDeltaker.id, innsokt)),
        )

        // Act & Assert
        deltaker.soktInnDato() shouldBe innsokt.toLocalDate()
    }

    @Test
    fun `kun fattet vedtak - returnerer fattet-dato`() {
        // Arrange
        val fattet = LocalDateTime.now().minusDays(5)
        val baseDeltaker = lagDeltaker(historikk = false)
        val vedtak = lagVedtak(deltakerVedVedtak = baseDeltaker, fattet = fattet)
        val deltaker = baseDeltaker.copy(historikk = listOf(DeltakerHistorikk.Vedtak(vedtak)))

        // Act & Assert
        deltaker.soktInnDato() shouldBe fattet.toLocalDate()
    }

    @Test
    fun `kun vedtak uten fattet - returnerer null`() {
        // Arrange
        val baseDeltaker = lagDeltaker(historikk = false)
        val vedtak = lagVedtak(deltakerVedVedtak = baseDeltaker, fattet = null)
        val deltaker = baseDeltaker.copy(historikk = listOf(DeltakerHistorikk.Vedtak(vedtak)))

        // Act & Assert
        deltaker.soktInnDato() shouldBe null
    }

    @Test
    fun `kun ImportertFraArena - returnerer innsoktDato fra import`() {
        // Arrange
        val innsoktFraArena = LocalDate.now().minusMonths(2)
        val baseDeltaker = lagDeltaker(historikk = false)
        val deltaker = baseDeltaker.copy(
            historikk = listOf(lagArenaHistorikk(baseDeltaker, innsoktFraArena)),
        )

        // Act & Assert
        deltaker.soktInnDato() shouldBe innsoktFraArena
    }

    @Test
    fun `flere typer i historikken - velger nyeste basert paa sistEndret`() {
        // Arrange
        val baseDeltaker = lagDeltaker(historikk = false)

        val gammelInnsokt = LocalDateTime.now().minusDays(30)
        val nyFattetDato = LocalDateTime.now().minusDays(2)
        val vedtak = lagVedtak(
            deltakerVedVedtak = baseDeltaker,
            fattet = nyFattetDato,
            opprettet = nyFattetDato,
            sistEndret = nyFattetDato,
        )
        val deltaker = baseDeltaker.copy(
            historikk = listOf(
                lagInnsokHistorikk(baseDeltaker.id, gammelInnsokt),
                DeltakerHistorikk.Vedtak(vedtak),
            ),
        )

        // Act & Assert
        deltaker.soktInnDato() shouldBe nyFattetDato.toLocalDate()
    }

    @Test
    fun `flere innsokninger - velger den nyeste`() {
        // Arrange
        val gammel = LocalDateTime.now().minusDays(30)
        val ny = LocalDateTime.now().minusDays(2)
        val baseDeltaker = lagDeltaker(historikk = false)
        val deltaker = baseDeltaker.copy(
            historikk = listOf(
                lagInnsokHistorikk(baseDeltaker.id, gammel),
                lagInnsokHistorikk(baseDeltaker.id, ny),
            ),
        )

        // Act & Assert
        deltaker.soktInnDato() shouldBe ny.toLocalDate()
    }

    @Test
    fun `nyeste vedtak uten fattet - returnerer null selv om eldre innsok finnes`() {
        // Dokumenterer kant: vi velger nyeste sistEndret. Hvis det er et ufattet vedtak,
        // returnerer vi null fremfor aa falle tilbake paa eldre entries.
        val baseDeltaker = lagDeltaker(historikk = false)
        val gammelInnsokt = LocalDateTime.now().minusDays(30)
        val nyttUfattetVedtak = lagVedtak(
            deltakerVedVedtak = baseDeltaker,
            fattet = null,
            opprettet = LocalDateTime.now().minusDays(1),
            sistEndret = LocalDateTime.now().minusDays(1),
        )
        val deltaker = baseDeltaker.copy(
            historikk = listOf(
                lagInnsokHistorikk(baseDeltaker.id, gammelInnsokt),
                DeltakerHistorikk.Vedtak(nyttUfattetVedtak),
            ),
        )

        // Act & Assert
        deltaker.soktInnDato() shouldBe null
    }

    companion object {
        private fun lagInnsokHistorikk(
            deltakerId: UUID,
            innsokt: LocalDateTime,
        ) = DeltakerHistorikk.InnsokPaaFellesOppstart(
            InnsokPaaFellesOppstart(
                id = UUID.randomUUID(),
                deltakerId = deltakerId,
                innsokt = innsokt,
                innsoktAv = UUID.randomUUID(),
                innsoktAvEnhet = UUID.randomUUID(),
                deltakelsesinnholdVedInnsok = null,
                utkastDelt = null,
                utkastGodkjentAvNav = false,
            ),
        )

        private fun lagArenaHistorikk(
            deltaker: no.nav.amt.deltaker.bff.deltaker.model.Deltaker,
            innsoktDato: LocalDate,
            importertDato: LocalDateTime = LocalDateTime.now().minusMonths(2),
        ) = DeltakerHistorikk.ImportertFraArena(
            ImportertFraArena(
                deltakerId = deltaker.id,
                importertDato = importertDato,
                deltakerVedImport = DeltakerVedImport(
                    deltakerId = deltaker.id,
                    innsoktDato = innsoktDato,
                    startdato = deltaker.startdato,
                    sluttdato = deltaker.sluttdato,
                    dagerPerUke = deltaker.dagerPerUke,
                    deltakelsesprosent = deltaker.deltakelsesprosent,
                    status = deltaker.status,
                ),
            ),
        )
    }
}
