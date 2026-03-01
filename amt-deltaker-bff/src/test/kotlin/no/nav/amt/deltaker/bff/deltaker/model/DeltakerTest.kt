package no.nav.amt.deltaker.bff.deltaker.model

import io.kotest.matchers.shouldBe
import no.nav.amt.deltaker.bff.deltaker.DeltakerTestUtils.sammenlignDeltakereVedVedtak
import no.nav.amt.deltaker.bff.deltaker.DeltakerTestUtils.sammenlignVedtak
import no.nav.amt.deltaker.bff.kafka.utils.sammenlignForslagStatus
import no.nav.amt.deltaker.bff.utils.data.TestData
import no.nav.amt.lib.models.arrangor.melding.Forslag
import no.nav.amt.lib.models.deltaker.DeltakerHistorikk
import no.nav.amt.lib.testing.shouldBeCloseTo
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

class DeltakerTest {
    @Test
    fun `getDeltakerHistorikkSortert - ett vedtak flere endringer - returner liste riktig sortert`() {
        val baseDeltaker = TestData.lagDeltaker(historikk = false)
        val vedtak = TestData.lagVedtak(
            deltakerId = baseDeltaker.id,
            fattet = LocalDateTime.now().minusMonths(1),
            sistEndret = LocalDateTime.now().minusMonths(1),
        )
        val gammelEndring = TestData.lagDeltakerEndring(
            deltakerId = baseDeltaker.id,
            endret = LocalDateTime.now().minusDays(20),
        )
        val forslag = TestData.lagForslag(
            deltakerId = baseDeltaker.id,
            status = Forslag.Status.Tilbakekalt(
                tilbakekaltAvArrangorAnsattId = UUID.randomUUID(),
                tilbakekalt = LocalDateTime.now().minusDays(18),
            ),
        )
        val nyEndring = TestData.lagDeltakerEndring(
            deltakerId = baseDeltaker.id,
            endret = LocalDateTime.now().minusDays(1),
        )
        val deltaker = TestData.leggTilHistorikk(baseDeltaker, listOf(vedtak), listOf(gammelEndring, nyEndring), listOf(forslag))

        val historikk = deltaker.getDeltakerHistorikkForVisning()

        historikk.size shouldBe 4
        sammenlignHistorikk(historikk[0], DeltakerHistorikk.Endring(nyEndring))
        sammenlignHistorikk(historikk[1], DeltakerHistorikk.Forslag(forslag))
        sammenlignHistorikk(historikk[2], DeltakerHistorikk.Endring(gammelEndring))
        sammenlignHistorikk(historikk[3], DeltakerHistorikk.Vedtak(vedtak))
    }

    @Test
    fun `getDeltakerHistorikkSortert - ingen historikk - returner tom liste`() {
        val deltaker = TestData.lagDeltaker(historikk = false)
        deltaker.getDeltakerHistorikkForVisning() shouldBe emptyList()
    }

    @Test
    fun `fattetVedtak - flere vedtak - henter vedtaket som er gyldig og fattet`() {
        val deltaker = TestData.lagDeltaker(historikk = false)
        val fattet = TestData.lagVedtak(
            deltakerId = deltaker.id,
            fattet = LocalDateTime.now().minusMonths(2),
            deltakerVedVedtak = deltaker,
            gyldigTil = LocalDateTime.now().minusMonths(1),
        )
        val fattet2 = TestData.lagVedtak(
            deltakerVedVedtak = deltaker,
            fattet = LocalDateTime.now().minusMonths(1),
            gyldigTil = null,
        )

        val deltakerMedVedtak = TestData.leggTilHistorikk(deltaker, listOf(fattet, fattet2))

        sammenlignVedtak(deltakerMedVedtak.fattetVedtak!!, fattet2)
    }

    @Test
    fun `fattetVedtak - ingen fattet vedtak - returnere null`() {
        val deltaker = TestData.lagDeltaker(historikk = false)

        deltaker.fattetVedtak shouldBe null
    }

    @Test
    fun `getIkkeFattetVedtak - deltaker har ikke fattet vedtak - returnerer vedtak`() {
        val deltaker = TestData.lagDeltaker(historikk = false)
        val vedtak = TestData.lagVedtak(
            deltakerVedVedtak = deltaker,
            fattet = null,
        )
        val deltakerMedVedtak = TestData.leggTilHistorikk(deltaker, listOf(vedtak))
        sammenlignVedtak(deltakerMedVedtak.ikkeFattetVedtak!!, vedtak)
    }

    @Test
    fun `getIkkeFattetVedtak - deltaker har kun fattet vedtak - returnerer null`() {
        val deltaker = TestData.lagDeltaker(historikk = false)
        val fattet = TestData.lagVedtak(
            deltakerVedVedtak = deltaker,
            fattet = LocalDateTime.now().minusMonths(2),
        )
        val deltakerMedVedtak = TestData.leggTilHistorikk(deltaker, listOf(fattet))
        deltakerMedVedtak.ikkeFattetVedtak shouldBe null
    }
}

fun sammenlignHistorikk(a: DeltakerHistorikk, b: DeltakerHistorikk) {
    when (a) {
        is DeltakerHistorikk.Endring -> {
            b as DeltakerHistorikk.Endring
            a.endring.id shouldBe b.endring.id
            a.endring.endring shouldBe b.endring.endring
            a.endring.endretAv shouldBe b.endring.endretAv
            a.endring.endretAvEnhet shouldBe b.endring.endretAvEnhet
            a.endring.endret shouldBeCloseTo b.endring.endret
        }

        is DeltakerHistorikk.Vedtak -> {
            b as DeltakerHistorikk.Vedtak
            a.vedtak.id shouldBe b.vedtak.id
            a.vedtak.deltakerId shouldBe b.vedtak.deltakerId
            a.vedtak.fattet shouldBeCloseTo b.vedtak.fattet
            a.vedtak.gyldigTil shouldBeCloseTo b.vedtak.gyldigTil
            sammenlignDeltakereVedVedtak(a.vedtak.deltakerVedVedtak, b.vedtak.deltakerVedVedtak)
            a.vedtak.opprettetAv shouldBe b.vedtak.opprettetAv
            a.vedtak.opprettetAvEnhet shouldBe b.vedtak.opprettetAvEnhet
            a.vedtak.opprettet shouldBeCloseTo b.vedtak.opprettet
        }

        is DeltakerHistorikk.Forslag -> {
            b as DeltakerHistorikk.Forslag
            a.forslag.id shouldBe b.forslag.id
            a.forslag.deltakerId shouldBe b.forslag.deltakerId
            a.forslag.opprettet shouldBeCloseTo b.forslag.opprettet
            a.forslag.begrunnelse shouldBe b.forslag.begrunnelse
            a.forslag.opprettetAvArrangorAnsattId shouldBe b.forslag.opprettetAvArrangorAnsattId
            a.forslag.endring shouldBe b.forslag.endring
            sammenlignForslagStatus(a.forslag.status, b.forslag.status)
        }

        is DeltakerHistorikk.EndringFraArrangor -> {
            b as DeltakerHistorikk.EndringFraArrangor
            a.endringFraArrangor.id shouldBe b.endringFraArrangor.id
            a.endringFraArrangor.deltakerId shouldBe b.endringFraArrangor.deltakerId
            a.endringFraArrangor.opprettet shouldBeCloseTo b.endringFraArrangor.opprettet
            a.endringFraArrangor.opprettetAvArrangorAnsattId shouldBe b.endringFraArrangor.opprettetAvArrangorAnsattId
            a.endringFraArrangor.endring shouldBe b.endringFraArrangor.endring
        }

        is DeltakerHistorikk.ImportertFraArena -> {
            b as DeltakerHistorikk.ImportertFraArena
            a.importertFraArena.deltakerId shouldBe b.importertFraArena.deltakerId
            a.importertFraArena.importertDato shouldBeCloseTo b.importertFraArena.importertDato
            a.importertFraArena.deltakerVedImport.innsoktDato shouldBe b.importertFraArena.deltakerVedImport.innsoktDato
            a.importertFraArena.deltakerVedImport.deltakerId shouldBe b.importertFraArena.deltakerVedImport.deltakerId
            a.importertFraArena.deltakerVedImport.status.type shouldBe b.importertFraArena.deltakerVedImport.status.type
            a.importertFraArena.deltakerVedImport.startdato shouldBe b.importertFraArena.deltakerVedImport.startdato
            a.importertFraArena.deltakerVedImport.sluttdato shouldBe b.importertFraArena.deltakerVedImport.sluttdato
        }

        is DeltakerHistorikk.VurderingFraArrangor -> {
            b as DeltakerHistorikk.VurderingFraArrangor
            a.data.begrunnelse shouldBe b.data.begrunnelse
            a.data.vurderingstype shouldBe b.data.vurderingstype
            a.data.deltakerId shouldBe b.data.deltakerId
            a.data.id shouldBe b.data.id
            a.data.opprettetAvArrangorAnsattId shouldBe b.data.opprettetAvArrangorAnsattId
        }

        is DeltakerHistorikk.EndringFraTiltakskoordinator -> {
            b as DeltakerHistorikk.EndringFraTiltakskoordinator
            a.endringFraTiltakskoordinator.id shouldBe b.endringFraTiltakskoordinator.id
            a.endringFraTiltakskoordinator.deltakerId shouldBe b.endringFraTiltakskoordinator.deltakerId
            a.endringFraTiltakskoordinator.endring shouldBe b.endringFraTiltakskoordinator.endring
            a.endringFraTiltakskoordinator.endretAv shouldBe b.endringFraTiltakskoordinator.endretAv
            a.endringFraTiltakskoordinator.endret shouldBeCloseTo b.endringFraTiltakskoordinator.endret
        }

        is DeltakerHistorikk.InnsokPaaFellesOppstart -> {
            b as DeltakerHistorikk.InnsokPaaFellesOppstart
            a.data.id shouldBe b.data.id
            a.data.deltakerId shouldBe b.data.deltakerId
            a.data.deltakelsesinnholdVedInnsok shouldBe b.data.deltakelsesinnholdVedInnsok
            a.data.innsokt shouldBeCloseTo b.data.innsokt
            a.data.innsoktAv shouldBe b.data.innsoktAv
            a.data.innsoktAvEnhet shouldBe b.data.innsoktAvEnhet
            a.data.utkastDelt shouldBeCloseTo b.data.utkastDelt
            a.data.utkastGodkjentAvNav shouldBe b.data.utkastGodkjentAvNav
        }
    }
}
