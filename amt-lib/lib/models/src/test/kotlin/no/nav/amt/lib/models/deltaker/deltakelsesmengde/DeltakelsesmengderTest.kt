package no.nav.amt.lib.models.deltaker.deltakelsesmengde

import io.kotest.matchers.shouldBe
import no.nav.amt.lib.models.deltaker.deltakelsesmengde.utils.TestData
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime

class DeltakelsesmengderTest {
    @Test
    fun `Deltakelsesmengder - kun vedtak - returnerer riktig deltakelsesmengder`() {
        val vedtak = TestData.lagVedtak(fattet = LocalDateTime.now())
        val historikk = TestData.lagDeltakerHistorikk(listOf(vedtak))

        val deltakelsesmengder = historikk.toDeltakelsesmengder()

        deltakelsesmengder.size shouldBe 1
        deltakelsesmengder[0].deltakelsesprosent shouldBe vedtak.deltakerVedVedtak.deltakelsesprosent
        deltakelsesmengder[0].dagerPerUke shouldBe vedtak.deltakerVedVedtak.dagerPerUke
        deltakelsesmengder[0].gyldigFra shouldBe vedtak.fattet!!.toLocalDate()
        deltakelsesmengder[0].opprettet shouldBe vedtak.fattet
    }

    @Test
    fun `Deltakelsesmengder - kun importert fra arena - returnerer riktig deltakelsesmengder`() {
        val importertFraArena = TestData.lagImportertFraArena()
        val historikk = TestData.lagDeltakerHistorikk(importertFraArena = listOf(importertFraArena))

        val deltakelsesmengder = historikk.toDeltakelsesmengder()

        deltakelsesmengder.size shouldBe 1
        deltakelsesmengder[0].deltakelsesprosent shouldBe importertFraArena.deltakerVedImport.deltakelsesprosent
        deltakelsesmengder[0].dagerPerUke shouldBe importertFraArena.deltakerVedImport.dagerPerUke
        deltakelsesmengder[0].gyldigFra shouldBe importertFraArena.deltakerVedImport.innsoktDato
        deltakelsesmengder[0].opprettet shouldBe importertFraArena.deltakerVedImport.innsoktDato.atStartOfDay()
    }

    @Test
    fun `Deltakelsesmengder - tidligere gjeldende og fremtidig - returnerer riktig deltakelsesmengder`() {
        val gjeldende = TestData.lagEndreDeltakelsesmengde(
            deltakelsesprosent = 100,
            gyldigFra = LocalDate.now(),
            opprettet = LocalDate.now().atStartOfDay(),
        )

        val tidligere = TestData.lagEndreDeltakelsesmengde(
            deltakelsesprosent = 80,
            gyldigFra = LocalDate.now().minusDays(1),
            opprettet = LocalDate.now().minusDays(1).atStartOfDay(),
        )

        val fremtidig = TestData.lagEndreDeltakelsesmengde(
            deltakelsesprosent = 90,
            gyldigFra = LocalDate.now().plusDays(1),
            opprettet = LocalDate.now().atStartOfDay(),
        )

        val endringer = listOf(tidligere, gjeldende, fremtidig)

        val historikk = TestData.lagDeltakerHistorikk(
            endringer = endringer,
        )

        val deltakelsesmengder = historikk.toDeltakelsesmengder()

        deltakelsesmengder shouldBe endringer.map { it.toDeltakelsesmengde() }
    }

    @Test
    fun `Deltakelsesmengder - ingen overlappende deltakelsesmengder`() {
        val endringer = listOf(
            TestData.lagEndreDeltakelsesmengde(
                deltakelsesprosent = 50,
                gyldigFra = "2024-01-01".toDate(),
                opprettet = "2024-01-01".toDateTime(),
            ),
            TestData.lagEndreDeltakelsesmengde(
                deltakelsesprosent = 75,
                gyldigFra = "2024-01-10".toDate(),
                opprettet = "2024-01-09".toDateTime(),
            ),
            TestData.lagEndreDeltakelsesmengde(
                deltakelsesprosent = 90,
                gyldigFra = "2024-01-20".toDate(),
                opprettet = "2024-01-15".toDateTime(),
            ),
        )

        val historikk = TestData.lagDeltakerHistorikk(endringer = endringer)
        val deltakelsesmengder = historikk.toDeltakelsesmengder()

        deltakelsesmengder.size shouldBe 3
        deltakelsesmengder shouldBe endringer.map { it.toDeltakelsesmengde() }
    }

    @Test
    fun `Deltakelsesmengder - med flere overlappende deltakelsesmengder`() {
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
                opprettet = "2024-01-01".toDateTime(),
            ),
            TestData.lagEndreDeltakelsesmengde(
                deltakelsesprosent = 70,
                gyldigFra = "2024-01-02".toDate(),
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

        val deltakelsesmengder = historikk.toDeltakelsesmengder()

        deltakelsesmengder.size shouldBe 4
        deltakelsesmengder shouldBe gyldigeDeltakelsesmengder.map { it.toDeltakelsesmengde() }
    }

    @Test
    fun `Deltakelsesmengder - en overlappende endring`() {
        val gyldigeEndringer = listOf(
            TestData.lagEndreDeltakelsesmengde(
                deltakelsesprosent = 60,
                gyldigFra = "2024-01-01".toDate(),
                opprettet = "2024-01-01".toDateTime(),
            ),
            TestData.lagEndreDeltakelsesmengde(
                deltakelsesprosent = 100,
                gyldigFra = "2024-01-15".toDate(),
                opprettet = "2024-01-15".toDateTime(),
            ),
        )

        val ugyldigeEndringer = listOf(
            TestData.lagEndreDeltakelsesmengde(
                deltakelsesprosent = 50,
                gyldigFra = "2024-01-15".toDate(),
                opprettet = "2024-01-12".toDateTime(),
            ),
        )

        val historikk = TestData.lagDeltakerHistorikk(endringer = gyldigeEndringer + ugyldigeEndringer)
        val deltakelsesmengder = historikk.toDeltakelsesmengder()

        deltakelsesmengder shouldBe gyldigeEndringer.map { it.toDeltakelsesmengde() }
    }

    @Test
    fun `Deltakelsesmengder - nyeste overlappende deltakelsesmengde benyttes`() {
        val endringer = listOf(
            TestData.lagEndreDeltakelsesmengde(
                deltakelsesprosent = 80,
                gyldigFra = "2024-01-10".toDate(),
                opprettet = "2024-01-05".toDateTime(),
            ),
            TestData.lagEndreDeltakelsesmengde(
                deltakelsesprosent = 100,
                gyldigFra = "2024-01-05".toDate(),
                opprettet = "2024-01-14".toDateTime(),
            ),
        )

        val historikk = TestData.lagDeltakerHistorikk(endringer = endringer)
        val deltakelsesmengder = historikk.toDeltakelsesmengder()

        deltakelsesmengder.size shouldBe 1
        deltakelsesmengder.first().deltakelsesprosent shouldBe 100
    }

    @Test
    fun `Deltakelsesmengder - flere deltakelsesmengder på samme dato, siste benyttes`() {
        val endringer = listOf(
            TestData.lagEndreDeltakelsesmengde(
                deltakelsesprosent = 70,
                gyldigFra = "2024-01-10".toDate(),
                opprettet = "2024-01-08".toDateTime(),
            ),
            TestData.lagEndreDeltakelsesmengde(
                deltakelsesprosent = 80,
                gyldigFra = "2024-01-10".toDate(),
                opprettet = "2024-01-09".toDateTime(),
            ),
            TestData.lagEndreDeltakelsesmengde(
                deltakelsesprosent = 90,
                gyldigFra = "2024-01-10".toDate(),
                opprettet = "2024-01-10".toDateTime(),
            ),
        )

        val historikk = TestData.lagDeltakerHistorikk(endringer = endringer)
        val deltakelsesmengder = historikk.toDeltakelsesmengder()

        deltakelsesmengder.size shouldBe 1
        deltakelsesmengder.first().deltakelsesprosent shouldBe 90
    }

    @Test
    fun `Deltakelsesmengder- endring overskriver siste mengde med samme mengde som forrige - returnerer en deltakelsmengde for perioden`() {
        val forsteEndring = TestData.lagEndreDeltakelsesmengde(
            deltakelsesprosent = 100,
            gyldigFra = "2024-01-15".toDate(),
            opprettet = "2024-01-15".toDateTime(),
        )

        val andreEndring = TestData.lagEndreDeltakelsesmengde(
            deltakelsesprosent = 90,
            gyldigFra = "2024-01-30".toDate(),
            opprettet = "2024-01-25".toDateTime(),
        )

        val tredjeEndring = TestData.lagEndreDeltakelsesmengde(
            deltakelsesprosent = 100,
            gyldigFra = "2024-01-20".toDate(),
            opprettet = "2024-01-30".toDateTime(),
        )

        val historikk = TestData.lagDeltakerHistorikk(
            endringer = listOf(forsteEndring, andreEndring, tredjeEndring),
        )

        val deltakelsesmengder = historikk.toDeltakelsesmengder()

        deltakelsesmengder.size shouldBe 1
        forsteEndring.toDeltakelsesmengde() shouldBe deltakelsesmengder[0]
    }

    @Test
    fun `validerNyDeltakelsesmengde - ny deltakelsesmengde fører ikke til endring - returnerer false`() {
        val forsteEndring = TestData.lagEndreDeltakelsesmengde(
            deltakelsesprosent = 100,
            gyldigFra = "2024-01-15".toDate(),
            opprettet = "2024-01-15".toDateTime(),
        )

        val andreEndring = TestData.lagEndreDeltakelsesmengde(
            deltakelsesprosent = 100,
            gyldigFra = "2024-01-20".toDate(),
            opprettet = "2024-01-30".toDateTime(),
        )

        val historikk = TestData.lagDeltakerHistorikk(
            endringer = listOf(forsteEndring),
        )

        val deltakelsesmengder = historikk.toDeltakelsesmengder()

        deltakelsesmengder.validerNyDeltakelsesmengde(andreEndring.toDeltakelsesmengde()!!) shouldBe false
    }

    @Test
    fun `validerNyDeltakelsesmengde - tidligere gyldigFra fører til endring - returnerer true`() {
        val forsteEndring = TestData.lagEndreDeltakelsesmengde(
            deltakelsesprosent = 100,
            gyldigFra = "2024-01-15".toDate(),
            opprettet = "2024-01-15".toDateTime(),
        )

        val andreEndring = TestData.lagEndreDeltakelsesmengde(
            deltakelsesprosent = 100,
            gyldigFra = "2024-01-01".toDate(),
            opprettet = "2024-01-30".toDateTime(),
        )

        val historikk = TestData.lagDeltakerHistorikk(
            endringer = listOf(forsteEndring),
        )

        val deltakelsesmengder = historikk.toDeltakelsesmengder()

        deltakelsesmengder.validerNyDeltakelsesmengde(andreEndring.toDeltakelsesmengde()!!) shouldBe true
    }

    @Test
    fun `validerNyDeltakelsesmengde - ny deltakelsesmengde fører til endring - returnerer true`() {
        val forsteEndring = TestData.lagEndreDeltakelsesmengde(
            deltakelsesprosent = 100,
            gyldigFra = "2024-01-15".toDate(),
            opprettet = "2024-01-15".toDateTime(),
        )

        val andreEndring = TestData.lagEndreDeltakelsesmengde(
            deltakelsesprosent = 42,
            gyldigFra = "2024-01-15".toDate(),
            opprettet = "2024-01-30".toDateTime(),
        )

        val historikk = TestData.lagDeltakerHistorikk(
            endringer = listOf(forsteEndring),
        )

        val deltakelsesmengder = historikk.toDeltakelsesmengder()

        deltakelsesmengder.validerNyDeltakelsesmengde(andreEndring.toDeltakelsesmengde()!!) shouldBe true
    }

    @Test
    fun `validerNyDeltakelsesmengde- endring overskriver siste mengde med samme mengde som forrige - returnerer true`() {
        val forsteEndring = TestData.lagEndreDeltakelsesmengde(
            deltakelsesprosent = 100,
            gyldigFra = "2024-01-15".toDate(),
            opprettet = "2024-01-15".toDateTime(),
        )

        val andreEndring = TestData.lagEndreDeltakelsesmengde(
            deltakelsesprosent = 90,
            gyldigFra = "2024-01-30".toDate(),
            opprettet = "2024-01-25".toDateTime(),
        )

        val tredjeEndring = TestData.lagEndreDeltakelsesmengde(
            deltakelsesprosent = 100,
            gyldigFra = "2024-01-20".toDate(),
            opprettet = "2024-01-30".toDateTime(),
        )

        val historikk = TestData.lagDeltakerHistorikk(
            endringer = listOf(forsteEndring, andreEndring),
        )

        val deltakelsesmengder = historikk.toDeltakelsesmengder()

        deltakelsesmengder.validerNyDeltakelsesmengde(tredjeEndring.toDeltakelsesmengde()!!) shouldBe true
    }

    @Test
    fun `gjeldende - siste deltakelsesmengde er ikke gjeldende enda - returnerer riktig deltakelsesmengde`() {
        val gjeldende = TestData.lagEndreDeltakelsesmengde(
            deltakelsesprosent = 100,
            gyldigFra = LocalDate.now(),
            opprettet = LocalDate.now().atStartOfDay(),
        )

        val tidligere = TestData.lagEndreDeltakelsesmengde(
            deltakelsesprosent = 80,
            gyldigFra = LocalDate.now().minusDays(1),
            opprettet = LocalDate.now().minusDays(1).atStartOfDay(),
        )

        val fremtidig = TestData.lagEndreDeltakelsesmengde(
            deltakelsesprosent = 90,
            gyldigFra = LocalDate.now().plusDays(1),
            opprettet = LocalDate.now().atStartOfDay(),
        )

        val historikk = TestData.lagDeltakerHistorikk(
            endringer = listOf(tidligere, gjeldende, fremtidig),
        )

        val deltakelsesmengder = historikk.toDeltakelsesmengder()

        deltakelsesmengder.gjeldende shouldBe gjeldende.toDeltakelsesmengde()
    }

    @Test
    fun `gjeldende - bare fremtidige - returnerer første`() {
        val fremtidig = TestData.lagEndreDeltakelsesmengde(
            deltakelsesprosent = 90,
            gyldigFra = LocalDate.now().plusDays(1),
            opprettet = LocalDate.now().atStartOfDay(),
        )

        val historikk = TestData.lagDeltakerHistorikk(
            endringer = listOf(fremtidig),
        )

        val deltakelsesmengder = historikk.toDeltakelsesmengder()

        deltakelsesmengder.gjeldende shouldBe fremtidig.toDeltakelsesmengde()
        deltakelsesmengder.nesteGjeldende shouldBe null
    }

    @Test
    fun `gjeldende - ikke fattet vedtak - returnerer deltakelsesmengde`() {
        val fremtidig = TestData.lagVedtak(
            deltakelsesprosent = 90F,
            fattet = null,
            opprettet = LocalDate.now().atStartOfDay(),
        )

        val historikk = TestData.lagDeltakerHistorikk(
            vedtak = listOf(fremtidig),
        )

        val deltakelsesmengder = historikk.toDeltakelsesmengder()

        deltakelsesmengder.gjeldende shouldBe fremtidig.toDeltakelsesmengde()
    }

    @Test
    fun `nesteGjeldende - bare 1 mengde - returnerer null`() {
        val endring = TestData.lagEndreDeltakelsesmengde(
            deltakelsesprosent = 90,
            gyldigFra = LocalDate.now(),
            opprettet = LocalDate.now().atStartOfDay(),
        )

        val historikk = TestData.lagDeltakerHistorikk(
            endringer = listOf(endring),
        )

        val deltakelsesmengder = historikk.toDeltakelsesmengder()

        deltakelsesmengder.nesteGjeldende shouldBe null
    }

    @Test
    fun `nesteGjeldende - fremtidig mengde - returnerer riktig deltakelsesmengde`() {
        val gjeldende = TestData.lagEndreDeltakelsesmengde(
            deltakelsesprosent = 100,
            gyldigFra = LocalDate.now(),
            opprettet = LocalDate.now().atStartOfDay(),
        )

        val tidligere = TestData.lagEndreDeltakelsesmengde(
            deltakelsesprosent = 80,
            gyldigFra = LocalDate.now().minusDays(1),
            opprettet = LocalDate.now().minusDays(1).atStartOfDay(),
        )

        val fremtidig = TestData.lagEndreDeltakelsesmengde(
            deltakelsesprosent = 90,
            gyldigFra = LocalDate.now().plusDays(1),
            opprettet = LocalDate.now().atStartOfDay(),
        )

        val historikk = TestData.lagDeltakerHistorikk(
            endringer = listOf(tidligere, gjeldende, fremtidig),
        )

        val deltakelsesmengder = historikk.toDeltakelsesmengder()

        deltakelsesmengder.nesteGjeldende shouldBe fremtidig.toDeltakelsesmengde()
    }
}

fun String.toDate(): LocalDate = LocalDate.parse(this)

fun String.toDateTime(): LocalDateTime = this.toDate().atStartOfDay()
