package no.nav.amt.lib.models.deltaker.deltakelsesmengde

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import no.nav.amt.lib.models.deltaker.deltakelsesmengde.utils.TestData
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime

class DeltakelsesmengderTest {
    @Test
    fun `endring uten prosent bevarer dager og manglende prosent`() {
        // Arrange
        val endring = TestData.lagEndreDeltakelsesmengde(
            deltakelsesprosent = null,
            dagerPerUke = 3,
            gyldigFra = LocalDate.now(),
            opprettet = LocalDateTime.now(),
        )

        // Act
        val deltakelsesmengde = endring.toDeltakelsesmengde()

        // Assert
        assertSoftly(deltakelsesmengde.shouldNotBeNull()) {
            deltakelsesprosent shouldBe null
            dagerPerUke shouldBe 3F
        }
    }

    @Test
    fun `vedtak med prosent eller dager bevarer perioden`() {
        listOf(null to 3F, 80F to null).forEach { (prosent, dager) ->
            // Arrange
            val vedtak = TestData.lagVedtak(deltakelsesprosent = prosent, dagerPerUke = dager)
            val historikk = TestData.lagDeltakerHistorikk(vedtak = listOf(vedtak))

            // Act
            val mengder = historikk.toDeltakelsesmengder()

            // Assert
            mengder.size shouldBe 1
            assertSoftly(mengder.single()) {
                deltakelsesprosent shouldBe prosent
                dagerPerUke shouldBe dager
                gyldigFra shouldBe vedtak.fattet.shouldNotBeNull().toLocalDate()
            }
        }
    }

    @Test
    fun `vedtak uten prosent og dager gir ingen periode`() {
        // Arrange
        val vedtak = TestData.lagVedtak(deltakelsesprosent = null, dagerPerUke = null)
        val historikk = TestData.lagDeltakerHistorikk(vedtak = listOf(vedtak))

        // Act
        val mengder = historikk.toDeltakelsesmengder()

        // Assert
        mengder.toList() shouldBe emptyList()
        mengder.gjeldende shouldBe null
        mengder.neste shouldBe null
    }

    @Test
    fun `vedtak uten prosent og dager erstatter ikke tidligere registrert mengde`() {
        // Arrange
        val tidligere = TestData.lagVedtak(
            deltakelsesprosent = 80F,
            dagerPerUke = 4F,
            fattet = LocalDate.now().minusMonths(1).atStartOfDay(),
        )
        val tomtVedtak = TestData.lagVedtak(
            deltakelsesprosent = null,
            dagerPerUke = null,
            fattet = LocalDate.now().atStartOfDay(),
        )
        val historikk = TestData.lagDeltakerHistorikk(vedtak = listOf(tidligere, tomtVedtak))

        // Act
        val mengder = historikk.toDeltakelsesmengder()

        // Assert
        mengder.toList() shouldBe listOf(tidligere.toDeltakelsesmengde())
        mengder.gjeldende shouldBe tidligere.toDeltakelsesmengde()
    }

    @Test
    fun `Arena-import med prosent eller dager bevarer perioden`() {
        listOf(null to 3F, 80F to null).forEach { (prosent, dager) ->
            // Arrange
            val import = TestData.lagImportertFraArena(deltakelsesprosent = prosent, dagerPerUke = dager)
            val historikk = TestData.lagDeltakerHistorikk(importertFraArena = listOf(import))

            // Act
            val mengder = historikk.toDeltakelsesmengder()

            // Assert
            mengder.size shouldBe 1
            assertSoftly(mengder.single()) {
                deltakelsesprosent shouldBe prosent
                dagerPerUke shouldBe dager
                gyldigFra shouldBe import.deltakerVedImport.innsoktDato
            }
        }
    }

    @Test
    fun `Arena-import uten prosent og dager gir ingen periode`() {
        // Arrange
        val import = TestData.lagImportertFraArena(deltakelsesprosent = null, dagerPerUke = null)
        val historikk = TestData.lagDeltakerHistorikk(importertFraArena = listOf(import))

        // Act
        val mengder = historikk.toDeltakelsesmengder()

        // Assert
        mengder.toList() shouldBe emptyList()
        mengder.gjeldende shouldBe null
        mengder.neste shouldBe null
    }

    @Test
    fun `Arena-import uten prosent og dager erstatter ikke tidligere registrert mengde`() {
        // Arrange
        val tidligere = TestData.lagVedtak(
            deltakelsesprosent = 80F,
            dagerPerUke = 4F,
            fattet = LocalDate.now().minusMonths(1).atStartOfDay(),
        )
        val import = TestData.lagImportertFraArena(
            deltakelsesprosent = null,
            dagerPerUke = null,
            innsoktDato = LocalDate.now(),
        )
        val historikk = TestData.lagDeltakerHistorikk(
            vedtak = listOf(tidligere),
            importertFraArena = listOf(import),
        )

        // Act
        val mengder = historikk.toDeltakelsesmengder()

        // Assert
        mengder.toList() shouldBe listOf(tidligere.toDeltakelsesmengde())
        mengder.gjeldende shouldBe tidligere.toDeltakelsesmengde()
    }

    @Test
    fun `tom gjeldende periode erstatter tidligere registrert mengde`() {
        // Arrange
        val tidligere = TestData.lagVedtak(
            deltakelsesprosent = 80F,
            dagerPerUke = 4F,
            fattet = LocalDate.now().minusMonths(1).atStartOfDay(),
        )
        val tomPeriode = TestData.lagEndreDeltakelsesmengde(
            deltakelsesprosent = null,
            dagerPerUke = null,
            gyldigFra = LocalDate.now(),
            opprettet = LocalDateTime.now(),
        )

        val historikk = TestData.lagDeltakerHistorikk(
            vedtak = listOf(tidligere),
            endringer = listOf(tomPeriode),
        )

        // Act
        val mengder = historikk.toDeltakelsesmengder()

        // Assert
        mengder.size shouldBe 2
        assertSoftly(mengder.gjeldende.shouldNotBeNull()) {
            deltakelsesprosent shouldBe null
            dagerPerUke shouldBe null
            gyldigFra shouldBe LocalDate.now()
        }
    }

    @Test
    fun `tom fremtidig periode beholdes som neste og siste mengde`() {
        // Arrange
        val tidligere = TestData.lagVedtak(fattet = LocalDate.now().minusMonths(1).atStartOfDay())
        val gyldigFra = LocalDate.now().plusMonths(1)
        val tomPeriode = TestData.lagEndreDeltakelsesmengde(
            deltakelsesprosent = null,
            dagerPerUke = null,
            gyldigFra = gyldigFra,
            opprettet = LocalDateTime.now(),
        )

        val historikk = TestData.lagDeltakerHistorikk(
            vedtak = listOf(tidligere),
            endringer = listOf(tomPeriode),
        )

        // Act
        val mengder = historikk.toDeltakelsesmengder()

        // Assert
        mengder.gjeldende?.deltakelsesprosent shouldBe tidligere.deltakerVedVedtak.deltakelsesprosent
        mengder.neste shouldBe mengder.last()
        assertSoftly(mengder.neste.shouldNotBeNull()) {
            deltakelsesprosent shouldBe null
            dagerPerUke shouldBe null
            this.gyldigFra shouldBe gyldigFra
        }
    }

    @Test
    fun `manglende prosent slås ikke sammen med hundre prosent`() {
        // Arrange
        val vedtak = TestData.lagVedtak(
            deltakelsesprosent = 100F,
            dagerPerUke = 3F,
            fattet = LocalDate.now().minusMonths(1).atStartOfDay(),
        )
        val endring = TestData.lagEndreDeltakelsesmengde(
            deltakelsesprosent = null,
            dagerPerUke = 3,
            gyldigFra = LocalDate.now().plusMonths(1),
            opprettet = LocalDateTime.now(),
        )

        val historikk = TestData.lagDeltakerHistorikk(
            vedtak = listOf(vedtak),
            endringer = listOf(endring),
        )

        // Act
        val mengder = historikk.toDeltakelsesmengder()

        // Assert
        mengder.map { it.deltakelsesprosent } shouldBe listOf(100F, null)
    }

    @Test
    fun `Deltakelsesmengder - kun vedtak - returnerer riktig deltakelsesmengder`() {
        val vedtak = TestData.lagVedtak(fattet = LocalDateTime.now())
        val historikk = TestData.lagDeltakerHistorikk(listOf(vedtak))

        val deltakelsesmengder = historikk.toDeltakelsesmengder()

        deltakelsesmengder.size shouldBe 1

        assertSoftly(deltakelsesmengder.first()) {
            deltakelsesprosent shouldBe vedtak.deltakerVedVedtak.deltakelsesprosent
            dagerPerUke shouldBe vedtak.deltakerVedVedtak.dagerPerUke
            gyldigFra shouldBe vedtak.fattet.shouldNotBeNull().toLocalDate()
            opprettet shouldBe vedtak.fattet
        }
    }

    @Test
    fun `Deltakelsesmengder - kun importert fra arena - returnerer riktig deltakelsesmengder`() {
        val importertFraArena = TestData.lagImportertFraArena()
        val historikk = TestData.lagDeltakerHistorikk(importertFraArena = listOf(importertFraArena))

        val deltakelsesmengder = historikk.toDeltakelsesmengder()

        deltakelsesmengder.size shouldBe 1

        assertSoftly(deltakelsesmengder.first()) {
            deltakelsesprosent shouldBe importertFraArena.deltakerVedImport.deltakelsesprosent
            dagerPerUke shouldBe importertFraArena.deltakerVedImport.dagerPerUke
            gyldigFra shouldBe importertFraArena.deltakerVedImport.innsoktDato
            opprettet shouldBe importertFraArena.deltakerVedImport.innsoktDato.atStartOfDay()
        }
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
    fun `legacy-konvertering slår sammen perioder etter fallback`() {
        // Arrange
        val vedtak = TestData.lagVedtak(
            deltakelsesprosent = 100F,
            dagerPerUke = 3F,
            fattet = LocalDate.now().minusMonths(1).atStartOfDay(),
        )
        val endring = TestData.lagEndreDeltakelsesmengde(
            deltakelsesprosent = null,
            dagerPerUke = 3,
            gyldigFra = LocalDate.now().plusMonths(1),
            opprettet = LocalDateTime.now(),
        )
        val historikk = TestData.lagDeltakerHistorikk(vedtak = listOf(vedtak), endringer = listOf(endring))

        // Act
        val mengderMedFallback = historikk.toDeltakelsesmengderMedFallback()
        val mengder = historikk.toDeltakelsesmengder()

        // Assert
        mengderMedFallback.toList() shouldBe listOf(vedtak.toDeltakelsesmengde())
        mengder.map { it.deltakelsesprosent } shouldBe listOf(100F, null)
    }

    @Test
    fun `legacy-konvertering utelater fortsatt vedtak og import uten prosent`() {
        listOf(3F, null).forEach { dager ->
            // Arrange
            val historikk = TestData.lagDeltakerHistorikk(
                vedtak = listOf(TestData.lagVedtak(deltakelsesprosent = null, dagerPerUke = dager)),
                importertFraArena = listOf(TestData.lagImportertFraArena(deltakelsesprosent = null, dagerPerUke = dager)),
            )

            // Act
            val mengder = historikk.toDeltakelsesmengderMedFallback()

            // Assert
            mengder.isEmpty() shouldBe true
        }
    }

    @Test
    fun `legacy-konvertering bruker fortsatt hundre prosent for endringer uten mengde`() {
        // Arrange
        val gyldigFra = LocalDate.now().plusMonths(1)
        val endring = TestData.lagEndreDeltakelsesmengde(
            deltakelsesprosent = null,
            dagerPerUke = null,
            gyldigFra = gyldigFra,
            opprettet = LocalDateTime.now(),
        )
        val historikk = TestData.lagDeltakerHistorikk(endringer = listOf(endring))

        // Act
        val mengder = historikk.toDeltakelsesmengderMedFallback()

        // Assert
        assertSoftly(mengder.single()) {
            deltakelsesprosent shouldBe 100F
            dagerPerUke shouldBe null
            this.gyldigFra shouldBe gyldigFra
        }
    }

    @Test
    fun `tom mengde brukes som sammenligningsgrunnlag i validering`() {
        // Arrange
        val tomMengde = Deltakelsesmengde(null, null, LocalDate.now(), LocalDateTime.now())
        val mengder = Deltakelsesmengder(listOf(tomMengde))

        // Act
        val uendretMengdeErGyldig = mengder.validerNyDeltakelsesmengde(tomMengde)
        val endretProsentErGyldig = mengder.validerNyDeltakelsesmengde(tomMengde.copy(deltakelsesprosent = 60F))
        val endretDagerPerUkeErGyldig = mengder.validerNyDeltakelsesmengde(tomMengde.copy(dagerPerUke = 3F))

        // Assert
        uendretMengdeErGyldig shouldBe false
        endretProsentErGyldig shouldBe true
        endretDagerPerUkeErGyldig shouldBe true
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

        deltakelsesmengder.validerNyDeltakelsesmengde(
            andreEndring.toDeltakelsesmengde().shouldNotBeNull(),
        ) shouldBe false
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

        deltakelsesmengder.validerNyDeltakelsesmengde(
            andreEndring.toDeltakelsesmengde().shouldNotBeNull(),
        ) shouldBe true
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

        deltakelsesmengder.validerNyDeltakelsesmengde(
            andreEndring.toDeltakelsesmengde().shouldNotBeNull(),
        ) shouldBe true
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

        deltakelsesmengder.validerNyDeltakelsesmengde(
            tredjeEndring.toDeltakelsesmengde().shouldNotBeNull(),
        ) shouldBe true
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
    fun `gjeldende - bare fremtidige - returnerer null`() {
        val fremtidig = TestData.lagEndreDeltakelsesmengde(
            deltakelsesprosent = 90,
            gyldigFra = LocalDate.now().plusDays(1),
            opprettet = LocalDate.now().atStartOfDay(),
        )

        val historikk = TestData.lagDeltakerHistorikk(
            endringer = listOf(fremtidig),
        )

        val deltakelsesmengder = historikk.toDeltakelsesmengder()

        deltakelsesmengder.gjeldende shouldBe null
        deltakelsesmengder.neste shouldBe fremtidig.toDeltakelsesmengde()
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

        deltakelsesmengder.neste shouldBe null
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

        deltakelsesmengder.neste shouldBe fremtidig.toDeltakelsesmengde()
    }
}

fun String.toDate(): LocalDate = LocalDate.parse(this)

fun String.toDateTime(): LocalDateTime = this.toDate().atStartOfDay()
