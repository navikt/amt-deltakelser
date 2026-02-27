package no.nav.amt.lib.models.deltaker.deltakelsesmengde

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
        deltakelsesmengder[0].deltakelsesprosent shouldBe vedtak.deltakerVedVedtak.deltakelsesprosent
        deltakelsesmengder[0].dagerPerUke shouldBe vedtak.deltakerVedVedtak.dagerPerUke
        deltakelsesmengder[0].gyldigFra shouldBe vedtak.fattet!!.toLocalDate()
        deltakelsesmengder[0].opprettet shouldBe vedtak.fattet
    }

    @Test
    fun `DeltakelsesmengderPeriode - kun importert fra arena - returnerer riktig deltakelsesmengder`() {
        val importertFraArena = TestData.lagImportertFraArena()
        val historikk = TestData.lagDeltakerHistorikk(importertFraArena = listOf(importertFraArena))

        val fraDato = importertFraArena.deltakerVedImport.innsoktDato
        val tilDato = fraDato.plusMonths(1)

        val deltakelsesmengder = historikk.toDeltakelsesmengder().periode(fraDato, tilDato)

        deltakelsesmengder.size shouldBe 1
        deltakelsesmengder[0].deltakelsesprosent shouldBe importertFraArena.deltakerVedImport.deltakelsesprosent
        deltakelsesmengder[0].dagerPerUke shouldBe importertFraArena.deltakerVedImport.dagerPerUke
        deltakelsesmengder[0].gyldigFra shouldBe importertFraArena.deltakerVedImport.innsoktDato
        deltakelsesmengder[0].opprettet shouldBe importertFraArena.deltakerVedImport.innsoktDato.atStartOfDay()
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

        val ugyldigeDeltakelsesmengder = listOf(
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

        val gyldigeDeltakelsesmengder = listOf(
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
        val historikk = TestData.lagDeltakerHistorikk(
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

        val ugyldigeDeltakelsesmengder = listOf(
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

        val gyldigeDeltakelsesmengder = listOf(
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
        val historikk = TestData.lagDeltakerHistorikk(
            endringer = gyldigeDeltakelsesmengder + ugyldigeDeltakelsesmengder,
        )

        val deltakelsesmengder = historikk.toDeltakelsesmengder().periode(fraDato, tilDato)

        deltakelsesmengder.size shouldBe 3
        deltakelsesmengder shouldBe gyldigeDeltakelsesmengder.map { it.toDeltakelsesmengde() }
    }

    @Test
    fun `DeltakelsesmengderPeriode - flere endringer med samme gyldigFra - returnerer riktig deltakelsesmengde`() {
        val fraDato = "2024-01-01".toDate()
        val tilDato = "2024-01-31".toDate()

        val ugyldigDeltakelsesmengde = TestData.lagEndreDeltakelsesmengde(
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

        val historikk = TestData.lagDeltakerHistorikk(
            endringer = listOf(gyldigDeltakelsesmengde, ugyldigDeltakelsesmengde),
        )

        val deltakelsesmengder = historikk.toDeltakelsesmengder().periode(fraDato, tilDato)

        deltakelsesmengder.size shouldBe 1
        deltakelsesmengder[0] shouldBe gyldigDeltakelsesmengde.toDeltakelsesmengde()
    }
}
