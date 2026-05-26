package no.nav.amt.lib.models.deltaker.deltakelsesmengde

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import no.nav.amt.lib.models.deltaker.deltakelsesmengde.utils.TestData
import org.junit.jupiter.api.Test

class DeltakelsesmengderPeriodeTest {
    @Test
    fun `DeltakelsesmengderPeriode - kun vedtak - returnerer riktig deltakelsesmengder`() {
        val fraDato = "2024-01-01".toDate()
        val tilDato = "2024-01-31".toDate()

        val vedtak = TestData.lagVedtak(fattet = fraDato.atStartOfDay())
        val historikk = TestData.lagDeltakerHistorikk(listOf(vedtak))

        val deltakelsesmengder = historikk.toDeltakelsesmengder().periode(fraDato, tilDato)

        deltakelsesmengder.size shouldBe 1
        assertSoftly(deltakelsesmengder.first()) {
            deltakelsesprosent shouldBe vedtak.deltakerVedVedtak.deltakelsesprosent
            dagerPerUke shouldBe vedtak.deltakerVedVedtak.dagerPerUke
            gyldigFra shouldBe vedtak.fattet.shouldNotBeNull().toLocalDate()
            opprettet shouldBe vedtak.fattet
        }
    }

    @Test
    fun `DeltakelsesmengderPeriode - kun importert fra arena - returnerer riktig deltakelsesmengder`() {
        val importertFraArena = TestData.lagImportertFraArena()
        val historikk = TestData.lagDeltakerHistorikk(importertFraArena = listOf(importertFraArena))

        val fraDato = importertFraArena.deltakerVedImport.innsoktDato
        val tilDato = fraDato.plusMonths(1)

        val deltakelsesmengder = historikk.toDeltakelsesmengder().periode(fraDato, tilDato)

        deltakelsesmengder.size shouldBe 1

        assertSoftly(deltakelsesmengder.first()) {
            deltakelsesprosent shouldBe importertFraArena.deltakerVedImport.deltakelsesprosent
            dagerPerUke shouldBe importertFraArena.deltakerVedImport.dagerPerUke
            gyldigFra shouldBe importertFraArena.deltakerVedImport.innsoktDato
            opprettet shouldBe importertFraArena.deltakerVedImport.innsoktDato.atStartOfDay()
        }
    }

    @Test
    fun `DeltakelsesmengderPeriode - vedtak og endring - returnerer riktig deltakelsesmengder`() {
        val fraDato = "2024-01-01".toDate()
        val tilDato = "2024-01-31".toDate()

        val vedtak = TestData.lagVedtak(fattet = fraDato.atStartOfDay())
        val endring = TestData.lagEndreDeltakelsesmengde(50, fraDato.plusDays(15), fraDato.plusDays(10).atStartOfDay())
        val historikk = TestData.lagDeltakerHistorikk(listOf(vedtak), endringer = listOf(endring))

        val deltakelsesmengder = historikk.toDeltakelsesmengder().periode(fraDato, tilDato)

        deltakelsesmengder.size shouldBe 2
        deltakelsesmengder shouldBe listOf(vedtak.toDeltakelsesmengde(), endring.toDeltakelsesmengde())
    }

    @Test
    fun `DeltakelsesmengderPeriode - vedtak og endring samme dato - returnerer riktig deltakelsesmengder`() {
        val fraDato = "2024-01-01".toDate()
        val tilDato = "2024-01-31".toDate()

        val vedtak = TestData.lagVedtak(fattet = fraDato.atStartOfDay())
        val endring = TestData.lagEndreDeltakelsesmengde(50, fraDato, fraDato.plusDays(10).atStartOfDay())
        val historikk = TestData.lagDeltakerHistorikk(listOf(vedtak), endringer = listOf(endring))

        val deltakelsesmengder = historikk.toDeltakelsesmengder().periode(fraDato, tilDato)

        deltakelsesmengder.size shouldBe 1
        deltakelsesmengder shouldBe listOf(endring.toDeltakelsesmengde())
    }

    @Test
    fun `DeltakelsesmengderPeriode - flere gyldige og ugyldige endringer - returnerer riktig deltakelsesmengder`() {
        val fraDato = "2024-01-15".toDate()
        val tilDato = "2024-01-31".toDate()

        val ugyldigeDeltakelsesmengder =
            listOf(
                TestData.lagEndreDeltakelsesmengde(
                    deltakelsesprosent = 90,
                    gyldigFra = "2024-01-10".toDate(),
                    opprettet = "2024-01-05".toDateTime(),
                ),
                TestData.lagEndreDeltakelsesmengde(
                    deltakelsesprosent = 80,
                    gyldigFra = "2024-01-10".toDate(),
                    opprettet = "2024-01-06".toDateTime(),
                ),
                TestData.lagEndreDeltakelsesmengde(
                    deltakelsesprosent = 70,
                    gyldigFra = "2024-01-05".toDate(),
                    opprettet = "2024-01-10".toDateTime(),
                ),
                TestData.lagEndreDeltakelsesmengde(
                    deltakelsesprosent = 60,
                    gyldigFra = "2024-01-15".toDate(),
                    opprettet = "2024-01-11".toDateTime(),
                ),
            )

        val gyldigeDeltakelsesmengder =
            listOf(
                TestData.lagEndreDeltakelsesmengde(
                    deltakelsesprosent = 69,
                    gyldigFra = "2024-01-01".toDate(),
                    opprettet = "2024-01-14".toDateTime(),
                ),
                TestData.lagEndreDeltakelsesmengde(
                    deltakelsesprosent = 100,
                    gyldigFra = "2024-01-15".toDate(),
                    opprettet = "2024-01-15".toDateTime(),
                ),
                TestData.lagEndreDeltakelsesmengde(
                    deltakelsesprosent = 90,
                    gyldigFra = "2024-01-30".toDate(),
                    opprettet = "2024-01-25".toDateTime(),
                ),
            )
        val historikk =
            TestData.lagDeltakerHistorikk(
                endringer = gyldigeDeltakelsesmengder + ugyldigeDeltakelsesmengder,
            )

        val deltakelsesmengder = historikk.toDeltakelsesmengder().periode(fraDato, tilDato)

        deltakelsesmengder.size shouldBe 2
        deltakelsesmengder shouldBe listOf(gyldigeDeltakelsesmengder[1], gyldigeDeltakelsesmengder[2]).map { it.toDeltakelsesmengde() }
    }

    @Test
    fun `DeltakelsesmengderPeriode - tilDato er null - returnerer riktig deltakelsesmengder`() {
        val fraDato = "2024-01-01".toDate()
        val tilDato = null

        val ugyldigeDeltakelsesmengder =
            listOf(
                TestData.lagEndreDeltakelsesmengde(
                    deltakelsesprosent = 90,
                    gyldigFra = "2024-01-10".toDate(),
                    opprettet = "2024-01-05".toDateTime(),
                ),
                TestData.lagEndreDeltakelsesmengde(
                    deltakelsesprosent = 80,
                    gyldigFra = "2024-01-10".toDate(),
                    opprettet = "2024-01-06".toDateTime(),
                ),
                TestData.lagEndreDeltakelsesmengde(
                    deltakelsesprosent = 70,
                    gyldigFra = "2024-01-05".toDate(),
                    opprettet = "2024-01-10".toDateTime(),
                ),
                TestData.lagEndreDeltakelsesmengde(
                    deltakelsesprosent = 60,
                    gyldigFra = "2024-01-15".toDate(),
                    opprettet = "2024-01-11".toDateTime(),
                ),
            )

        val gyldigeDeltakelsesmengder =
            listOf(
                TestData.lagEndreDeltakelsesmengde(
                    deltakelsesprosent = 69,
                    gyldigFra = "2024-01-01".toDate(),
                    opprettet = "2024-01-14".toDateTime(),
                ),
                TestData.lagEndreDeltakelsesmengde(
                    deltakelsesprosent = 100,
                    gyldigFra = "2024-01-15".toDate(),
                    opprettet = "2024-01-15".toDateTime(),
                ),
                TestData.lagEndreDeltakelsesmengde(
                    deltakelsesprosent = 90,
                    gyldigFra = "2024-01-30".toDate(),
                    opprettet = "2024-01-25".toDateTime(),
                ),
            )
        val historikk =
            TestData.lagDeltakerHistorikk(
                endringer = gyldigeDeltakelsesmengder + ugyldigeDeltakelsesmengder,
            )

        val deltakelsesmengder = historikk.toDeltakelsesmengder().periode(fraDato, tilDato)

        deltakelsesmengder.size shouldBe 3
        deltakelsesmengder shouldBe gyldigeDeltakelsesmengder.map { it.toDeltakelsesmengde() }
    }

    @Test
    fun `DeltakelsesmengderPeriode - initialDeltakelsesmengde gyldigFra tidligere enn startdato - justeres til startdato`() {
        // Reproduserer produksjonsproblemet: mengde opprettet uten startdato (gyldigFra = opprettelsesdato),
        // startdato settes til en FREMTIDIG dato men uten tilhørende EndreStartdato i historikk.
        // periode(startdato, sluttdato) skal aldri returnere mengde med gyldigFra < startdato.
        val vedtakGyldigFra = "2024-01-01".toDate()
        val startdato = "2024-01-10".toDate()

        val vedtak = TestData.lagVedtak(fattet = vedtakGyldigFra.atStartOfDay())

        // Ingen EndreStartdato/LeggTilOppstartsdato i historikk – toDeltakelsesmengder justerer ikke gyldigFra
        val historikk = TestData.lagDeltakerHistorikk(listOf(vedtak))
        val deltakelsesmengder = historikk.toDeltakelsesmengder().periode(startdato, null)

        // gyldigFra må ALDRI være før startdato
        deltakelsesmengder.all { it.gyldigFra >= startdato } shouldBe true
        deltakelsesmengder.first().gyldigFra shouldBe startdato
    }

    @Test
    fun `DeltakelsesmengderPeriode - sluttdato endres, fremtidig mengde etter ny sluttdato ekskluderes`() {
        // Docs-scenario 03.01.2025: Sluttdato endres til 15.01
        // Fremtidig mengde gyldigFra 01.02 skal ikke lenger vises
        val startdato = "2024-12-10".toDate()
        val nySluttdato = "2025-01-15".toDate()

        val vedtak = TestData.lagVedtak(fattet = startdato.atStartOfDay())
        val fremtidigMengde = TestData.lagEndreDeltakelsesmengde(
            deltakelsesprosent = 100,
            dagerPerUke = null,
            gyldigFra = "2025-02-01".toDate(),
            opprettet = "2025-01-02".toDateTime(),
        )
        val historikk = TestData.lagDeltakerHistorikk(
            vedtak = listOf(vedtak),
            endringerFraArrangor = listOf(TestData.lagLeggTilOppstartsdato(startdato, opprettet = startdato.atStartOfDay())),
            endringer = listOf(fremtidigMengde),
        )

        val deltakelsesmengder = historikk.toDeltakelsesmengder().periode(startdato, nySluttdato)

        // Fremtidig mengde (01.02) er etter ny sluttdato (15.01) – skal ikke inkluderes
        deltakelsesmengder.none { it.gyldigFra > nySluttdato } shouldBe true
        deltakelsesmengder.size shouldBe 1
        deltakelsesmengder.first().gyldigFra shouldBe startdato
    }

    @Test
    fun `DeltakelsesmengderPeriode - sluttdato forlenges, fremtidig mengde innenfor ny sluttdato inkluderes igjen`() {
        // Docs-scenario 05.01.2025: Sluttdato endres til 31.03 – mengde 01.02 blir gyldig igjen
        val startdato = "2024-12-10".toDate()
        val nySluttdato = "2025-03-31".toDate()

        val vedtak = TestData.lagVedtak(fattet = startdato.atStartOfDay())
        val fremtidigMengde = TestData.lagEndreDeltakelsesmengde(
            deltakelsesprosent = 100,
            dagerPerUke = null,
            gyldigFra = "2025-02-01".toDate(),
            opprettet = "2025-01-02".toDateTime(),
        )
        val historikk = TestData.lagDeltakerHistorikk(
            vedtak = listOf(vedtak),
            endringerFraArrangor = listOf(TestData.lagLeggTilOppstartsdato(startdato, opprettet = startdato.atStartOfDay())),
            endringer = listOf(fremtidigMengde),
        )

        val deltakelsesmengder = historikk.toDeltakelsesmengder().periode(startdato, nySluttdato)

        // Fremtidig mengde (01.02) er etter startdato og FØR ny sluttdato (31.03) – skal inkluderes
        deltakelsesmengder.size shouldBe 2
        deltakelsesmengder.last().gyldigFra shouldBe "2025-02-01".toDate()
    }

    @Test
    fun `DeltakelsesmengderPeriode - flere endringer med samme gyldigFra - returnerer riktig deltakelsesmengde`() {
        val fraDato = "2024-01-01".toDate()
        val tilDato = "2024-01-31".toDate()

        val ugyldigDeltakelsesmengde =
            TestData.lagEndreDeltakelsesmengde(
                deltakelsesprosent = 90,
                gyldigFra = "2024-01-01".toDate(),
                opprettet = "2024-01-05".toDateTime(),
            )
        val gyldigDeltakelsesmengde =
            TestData.lagEndreDeltakelsesmengde(
                deltakelsesprosent = 69,
                gyldigFra = "2024-01-01".toDate(),
                opprettet = "2024-01-14".toDateTime(),
            )

        val historikk =
            TestData.lagDeltakerHistorikk(
                endringer = listOf(gyldigDeltakelsesmengde, ugyldigDeltakelsesmengde),
            )

        val deltakelsesmengder = historikk.toDeltakelsesmengder().periode(fraDato, tilDato)

        deltakelsesmengder.size shouldBe 1
        deltakelsesmengder[0] shouldBe gyldigDeltakelsesmengde.toDeltakelsesmengde()
    }
}
