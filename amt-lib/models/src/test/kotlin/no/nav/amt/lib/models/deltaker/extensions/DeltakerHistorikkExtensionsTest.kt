package no.nav.amt.lib.models.deltaker.extensions

import io.kotest.matchers.shouldBe
import no.nav.amt.lib.models.deltaker.DeltakerEndring
import no.nav.amt.lib.models.deltaker.DeltakerHistorikk
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltaker.DeltakerVedImport
import no.nav.amt.lib.models.deltaker.DeltakerVedVedtak
import no.nav.amt.lib.models.deltaker.ImportertFraArena
import no.nav.amt.lib.models.deltaker.Innsok
import no.nav.amt.lib.models.deltaker.Vedtak
import no.nav.amt.lib.testing.shouldBeCloseTo
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

class DeltakerHistorikkExtensionsTest {
    @Nested
    inner class GetInnsoktDatoTests {
        @Test
        fun `tom historikk - returnerer null`() {
            emptyList<DeltakerHistorikk>().getInnsoktDato() shouldBe null
        }

        @Test
        fun `kun Endring i historikk - returnerer null`() {
            val historikk = listOf(lagEndringHistorikk())
            historikk.getInnsoktDato() shouldBe null
        }

        @Test
        fun `ImportertFraArena - returnerer innsoktDato ved start of day`() {
            val innsoktDato = LocalDate.now().minusMonths(2)
            val historikk = listOf(lagArenaHistorikk(innsoktDato = innsoktDato))

            historikk.getInnsoktDato() shouldBe innsoktDato.atStartOfDay()
        }

        @Test
        fun `flere ImportertFraArena med samme innsoktDato - returnerer samme resultat uavhengig av rekkefolge`() {
            val innsoktDato = LocalDate.now().minusMonths(2)
            val historikk = listOf(
                lagArenaHistorikk(innsoktDato = innsoktDato),
                lagArenaHistorikk(innsoktDato = innsoktDato),
            )
            val historikkMedOmvendtRekkefolge = historikk.reversed()

            historikk.getInnsoktDato() shouldBe innsoktDato.atStartOfDay()
            historikkMedOmvendtRekkefolge.getInnsoktDato() shouldBe innsoktDato.atStartOfDay()
        }

        @Test
        fun `InnsokPaaFellesOppstart - returnerer innsokt`() {
            val innsokt = LocalDateTime.now().minusDays(7)
            val historikk = listOf(lagInnsokHistorikk(innsokt = innsokt))

            historikk.getInnsoktDato() shouldBe innsokt
        }

        @Test
        fun `flere InnsokPaaFellesOppstart - returnerer tidligste innsokt uavhengig av rekkefolge`() {
            val tidligsteInnsokt = LocalDateTime.now().minusDays(30)
            val senesteInnsokt = LocalDateTime.now().minusDays(2)
            val historikkMedTidligsteForst = listOf(
                lagInnsokHistorikk(innsokt = tidligsteInnsokt),
                lagInnsokHistorikk(innsokt = senesteInnsokt),
            )
            val historikkMedSenesteForst = listOf(
                lagInnsokHistorikk(innsokt = senesteInnsokt),
                lagInnsokHistorikk(innsokt = tidligsteInnsokt),
            )

            historikkMedTidligsteForst.getInnsoktDato() shouldBeCloseTo tidligsteInnsokt
            historikkMedSenesteForst.getInnsoktDato() shouldBeCloseTo tidligsteInnsokt
        }

        @Test
        fun `kun Vedtak - returnerer tidligste opprettet`() {
            val tidligst = LocalDateTime.now().minusMonths(1)
            val senere = LocalDateTime.now().minusDays(4)
            val historikk = listOf(
                lagVedtakHistorikk(opprettet = senere),
                lagVedtakHistorikk(opprettet = tidligst),
            )

            historikk.getInnsoktDato() shouldBe tidligst
        }

        @Test
        fun `ett Vedtak - returnerer opprettet`() {
            val opprettet = LocalDateTime.now().minusDays(5)
            val historikk = listOf(lagVedtakHistorikk(opprettet = opprettet))

            historikk.getInnsoktDato() shouldBe opprettet
        }

        @Test
        fun `ImportertFraArena prioriteres over InnsokPaaFellesOppstart`() {
            val innsoktDato = LocalDate.now().minusMonths(3)
            val innsokt = LocalDateTime.now().minusDays(2)
            val historikk = listOf(
                lagInnsokHistorikk(innsokt = innsokt),
                lagArenaHistorikk(innsoktDato = innsoktDato),
            )

            historikk.getInnsoktDato() shouldBe innsoktDato.atStartOfDay()
        }

        @Test
        fun `ImportertFraArena prioriteres over Vedtak`() {
            val innsoktDato = LocalDate.now().minusMonths(3)
            val historikk = listOf(
                lagVedtakHistorikk(opprettet = LocalDateTime.now().minusDays(10)),
                lagArenaHistorikk(innsoktDato = innsoktDato),
            )

            historikk.getInnsoktDato() shouldBe innsoktDato.atStartOfDay()
        }

        @Test
        fun `InnsokPaaFellesOppstart prioriteres over Vedtak`() {
            val innsokt = LocalDateTime.now().minusDays(2)
            val historikk = listOf(
                lagVedtakHistorikk(opprettet = LocalDateTime.now().minusMonths(1)),
                lagInnsokHistorikk(innsokt = innsokt),
            )

            historikk.getInnsoktDato() shouldBe innsokt
        }
    }

    companion object {
        private val now: LocalDateTime = LocalDateTime.now()

        private fun lagDeltakerStatus() = DeltakerStatus(
            id = UUID.randomUUID(),
            type = DeltakerStatus.Type.VENTER_PA_OPPSTART,
            aarsak = null,
            gyldigFra = now.minusDays(1),
            gyldigTil = null,
            opprettet = now.minusDays(1),
        )

        private fun lagInnsokHistorikk(innsokt: LocalDateTime) = DeltakerHistorikk.InnsokPaaFellesOppstart(
            Innsok(
                id = UUID.randomUUID(),
                deltakerId = UUID.randomUUID(),
                innsokt = innsokt,
                innsoktAv = UUID.randomUUID(),
                innsoktAvEnhet = UUID.randomUUID(),
                deltakelsesinnholdVedInnsok = null,
                utkastDelt = null,
                utkastGodkjentAvNav = false,
                opplaringKategoriseringVedInnsok = null,
            ),
        )

        private fun lagArenaHistorikk(
            innsoktDato: LocalDate,
            importertDato: LocalDateTime = now.minusMonths(2),
        ) = DeltakerHistorikk.ImportertFraArena(
            ImportertFraArena(
                deltakerId = UUID.randomUUID(),
                importertDato = importertDato,
                deltakerVedImport = DeltakerVedImport(
                    deltakerId = UUID.randomUUID(),
                    innsoktDato = innsoktDato,
                    startdato = null,
                    sluttdato = null,
                    dagerPerUke = null,
                    deltakelsesprosent = null,
                    status = lagDeltakerStatus(),
                ),
            ),
        )

        private fun lagVedtakHistorikk(
            opprettet: LocalDateTime = now.minusDays(6),
            fattet: LocalDateTime? = null,
            sistEndret: LocalDateTime = opprettet,
        ) = DeltakerHistorikk.Vedtak(
            Vedtak(
                id = UUID.randomUUID(),
                deltakerId = UUID.randomUUID(),
                fattet = fattet,
                gyldigTil = null,
                deltakerVedVedtak = DeltakerVedVedtak(
                    id = UUID.randomUUID(),
                    startdato = null,
                    sluttdato = null,
                    deltakelsesprosent = null,
                    dagerPerUke = null,
                    bakgrunnsinformasjon = null,
                    deltakelsesinnhold = null,
                    status = lagDeltakerStatus(),
                ),
                fattetAvNav = true,
                opprettet = opprettet,
                opprettetAv = UUID.randomUUID(),
                opprettetAvEnhet = UUID.randomUUID(),
                sistEndret = sistEndret,
                sistEndretAv = UUID.randomUUID(),
                sistEndretAvEnhet = UUID.randomUUID(),
            ),
        )

        private fun lagEndringHistorikk() = DeltakerHistorikk.Endring(
            DeltakerEndring(
                id = UUID.randomUUID(),
                deltakerId = UUID.randomUUID(),
                endring = DeltakerEndring.Endring.EndreBakgrunnsinformasjon("info"),
                endretAv = UUID.randomUUID(),
                endretAvEnhet = UUID.randomUUID(),
                endret = now.minusDays(3),
                forslag = null,
            ),
        )
    }
}
