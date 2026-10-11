package no.nav.amt.lib.models.deltaker.deltakelsesmengde

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import no.nav.amt.lib.models.deltaker.deltakelsesmengde.utils.TestData
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime

class DeltakelsesmengderTest {
    @Nested
    inner class ToDeltakelsesmengder {
        @Test
        fun `kun vedtak - returnerer riktig deltakelsesmengder`() {
            // Arrange
            val vedtak = TestData.lagVedtak(fattet = LocalDateTime.now())
            val historikk = TestData.lagDeltakerHistorikk(listOf(vedtak))

            // Act
            val deltakelsesmengder = historikk.toDeltakelsesmengder()

            // Assert
            deltakelsesmengder.perioder.size shouldBe 1

            assertSoftly(deltakelsesmengder.perioder.first()) {
                deltakelsesprosent shouldBe vedtak.deltakerVedVedtak.deltakelsesprosent
                dagerPerUke shouldBe vedtak.deltakerVedVedtak.dagerPerUke
                gyldigFra shouldBe vedtak.fattet.shouldNotBeNull().toLocalDate()
                opprettet shouldBe vedtak.fattet
            }
        }

        @Test
        fun `vedtak med kun dager per uke - returnerer deltakelsesmengde`() {
            // Arrange
            val vedtak = TestData.lagVedtak(
                deltakelsesprosent = null,
                dagerPerUke = 3F,
                fattet = LocalDateTime.now(),
            )

            // Act
            val deltakelsesmengder = TestData
                .lagDeltakerHistorikk(vedtak = listOf(vedtak))
                .toDeltakelsesmengder()

            // Assert
            deltakelsesmengder.perioder.size shouldBe 1
            assertSoftly(deltakelsesmengder.perioder.first()) {
                deltakelsesprosent shouldBe null
                dagerPerUke shouldBe 3F
                gyldigFra shouldBe vedtak.fattet!!.toLocalDate()
            }
        }

        @Test
        fun `kun importert fra arena - returnerer riktig deltakelsesmengder`() {
            // Arrange
            val importertFraArena = TestData.lagImportertFraArena()
            val historikk = TestData.lagDeltakerHistorikk(importertFraArena = listOf(importertFraArena))

            // Act
            val deltakelsesmengder = historikk.toDeltakelsesmengder()

            // Assert
            deltakelsesmengder.perioder.size shouldBe 1

            assertSoftly(deltakelsesmengder.perioder.first()) {
                deltakelsesprosent shouldBe importertFraArena.deltakerVedImport.deltakelsesprosent
                dagerPerUke shouldBe importertFraArena.deltakerVedImport.dagerPerUke
                gyldigFra shouldBe importertFraArena.deltakerVedImport.innsoktDato
                opprettet shouldBe importertFraArena.deltakerVedImport.innsoktDato.atStartOfDay()
            }
        }

        @Test
        fun `importert fra arena med kun dager per uke - returnerer deltakelsesmengde`() {
            // Arrange
            val importertFraArena = TestData.lagImportertFraArena(
                deltakelsesprosent = null,
                dagerPerUke = 3F,
            )

            // Act
            val deltakelsesmengder = TestData
                .lagDeltakerHistorikk(importertFraArena = listOf(importertFraArena))
                .toDeltakelsesmengder()

            // Assert
            deltakelsesmengder.perioder.size shouldBe 1
            assertSoftly(deltakelsesmengder.perioder.first()) {
                deltakelsesprosent shouldBe null
                dagerPerUke shouldBe 3F
                gyldigFra shouldBe importertFraArena.deltakerVedImport.innsoktDato
            }
        }

        @Test
        fun `tidligere gjeldende og fremtidig - returnerer riktig deltakelsesmengder`() {
            // Arrange
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

            // Act
            val deltakelsesmengder = historikk.toDeltakelsesmengder()

            // Assert
            deltakelsesmengder.perioder shouldBe endringer.map { it.toDeltakelsesmengde() }
        }

        @Test
        fun `ingen overlappende deltakelsesmengder`() {
            // Arrange
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
            // Act
            val deltakelsesmengder = historikk.toDeltakelsesmengder()

            // Assert
            deltakelsesmengder.perioder.size shouldBe 3
            deltakelsesmengder.perioder shouldBe endringer.map { it.toDeltakelsesmengde() }
        }

        @Test
        fun `ny tidligere mengde beholder senere mengder`() {
            // Arrange
            val eksisterende = listOf(
                TestData.lagEndreDeltakelsesmengde(
                    deltakelsesprosent = 40,
                    gyldigFra = "2024-01-01".toDate(),
                    opprettet = "2024-01-01".toDateTime(),
                ),
                TestData.lagEndreDeltakelsesmengde(
                    deltakelsesprosent = 60,
                    gyldigFra = "2024-01-10".toDate(),
                    opprettet = "2024-01-09".toDateTime(),
                ),
                TestData.lagEndreDeltakelsesmengde(
                    deltakelsesprosent = 80,
                    gyldigFra = "2024-01-20".toDate(),
                    opprettet = "2024-01-19".toDateTime(),
                ),
            )
            val ny = TestData.lagEndreDeltakelsesmengde(
                deltakelsesprosent = 50,
                gyldigFra = "2024-01-05".toDate(),
                opprettet = "2024-01-30".toDateTime(),
            )

            // Act
            val deltakelsesmengder = TestData
                .lagDeltakerHistorikk(endringer = eksisterende + ny)
                .toDeltakelsesmengder()

            // Assert
            deltakelsesmengder.perioder.map { it.gyldigFra } shouldBe listOf(
                "2024-01-01".toDate(),
                "2024-01-05".toDate(),
                "2024-01-10".toDate(),
                "2024-01-20".toDate(),
            )
            deltakelsesmengder.perioder.map { it.deltakelsesprosent } shouldBe listOf(40F, 50F, 60F, 80F)
        }

        @Test
        fun `med flere overlappende deltakelsesmengder`() {
            // Arrange
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

            // Act
            val deltakelsesmengder = historikk.toDeltakelsesmengder()

            // Assert
            deltakelsesmengder.perioder.size shouldBe 5
            deltakelsesmengder.perioder.map { it.deltakelsesprosent } shouldBe listOf(69F, 70F, 80F, 100F, 90F)
        }

        @Test
        fun `en overlappende endring`() {
            // Arrange
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

            // Act
            val deltakelsesmengder = historikk.toDeltakelsesmengder()

            // Assert
            deltakelsesmengder.perioder shouldBe gyldigeEndringer.map { it.toDeltakelsesmengde() }
        }

        @Test
        fun `nyeste overlappende deltakelsesmengde benyttes`() {
            // Arrange
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
            // Act
            val deltakelsesmengder = historikk.toDeltakelsesmengder()

            // Assert
            deltakelsesmengder.perioder.size shouldBe 2
            deltakelsesmengder.perioder.map { it.deltakelsesprosent } shouldBe listOf(100F, 80F)
        }

        @Test
        fun `flere deltakelsesmengder på samme dato, siste benyttes`() {
            // Arrange
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
            // Act
            val deltakelsesmengder = historikk.toDeltakelsesmengder()

            // Assert
            deltakelsesmengder.perioder.size shouldBe 1
            deltakelsesmengder.perioder.first().deltakelsesprosent shouldBe 90
        }

        @Test
        fun `endring overskriver siste mengde med samme mengde som forrige - returnerer en deltakelsesmengde for perioden`() {
            // Arrange
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

            // Act
            val deltakelsesmengder = historikk.toDeltakelsesmengder()

            // Assert
            deltakelsesmengder.perioder shouldBe listOf(
                forsteEndring.toDeltakelsesmengde(),
                andreEndring.toDeltakelsesmengde(),
            )
        }
    }

    @Nested
    inner class ValiderNyDeltakelsesmengde {
        @Test
        fun `ny deltakelsesmengde - uendret mengde med gyldig fra lik aktiv mengde - avvises`() {
            // Arrange
            val eksisterende = listOf(
                TestData.lagEndreDeltakelsesmengde(
                    deltakelsesprosent = 40,
                    gyldigFra = "2024-01-01".toDate(),
                    opprettet = "2024-01-01".toDateTime(),
                ),
                TestData.lagEndreDeltakelsesmengde(
                    deltakelsesprosent = 60,
                    gyldigFra = "2024-01-10".toDate(),
                    opprettet = "2024-01-09".toDateTime(),
                ),
                TestData.lagEndreDeltakelsesmengde(
                    deltakelsesprosent = 80,
                    gyldigFra = "2024-01-20".toDate(),
                    opprettet = "2024-01-19".toDateTime(),
                ),
            )
            val ny = TestData.lagEndreDeltakelsesmengde(
                deltakelsesprosent = 80,
                gyldigFra = "2024-01-20".toDate(),
                opprettet = "2024-01-30".toDateTime(),
            )
            // Act
            val deltakelsesmengder = TestData
                .lagDeltakerHistorikk(endringer = eksisterende)
                .toDeltakelsesmengder()

            val resultat = deltakelsesmengder.validerNyDeltakelsesmengde(
                ny.toDeltakelsesmengde().shouldNotBeNull(),
            )

            // Assert
            resultat shouldBe false
        }

        @Test
        fun `ny deltakelsesmengde - ny deltakelsesmengde fører ikke til endring - avvises`() {
            // Arrange
            val aktivGyldigFra = LocalDate.now().minusDays(5)
            val forsteEndring = TestData.lagEndreDeltakelsesmengde(
                deltakelsesprosent = 100,
                gyldigFra = aktivGyldigFra,
                opprettet = aktivGyldigFra.atStartOfDay(),
            )

            val andreEndring = TestData.lagEndreDeltakelsesmengde(
                deltakelsesprosent = 100,
                gyldigFra = aktivGyldigFra.plusDays(5),
                opprettet = LocalDateTime.now(),
            )

            val historikk = TestData.lagDeltakerHistorikk(
                endringer = listOf(forsteEndring),
            )

            val deltakelsesmengder = historikk.toDeltakelsesmengder()

            // Act
            val resultat = deltakelsesmengder.validerNyDeltakelsesmengde(
                andreEndring.toDeltakelsesmengde().shouldNotBeNull(),
            )

            // Assert
            resultat shouldBe false
        }

        @Test
        fun `ny deltakelsesmengde - uendret mengde med gyldig fra før aktiv mengde - godtas`() {
            // Arrange
            val aktivGyldigFra = LocalDate.now().minusDays(5)
            val aktiv = TestData.lagEndreDeltakelsesmengde(
                deltakelsesprosent = 100,
                gyldigFra = aktivGyldigFra,
                opprettet = aktivGyldigFra.atStartOfDay(),
            )
            val ny = TestData.lagEndreDeltakelsesmengde(
                deltakelsesprosent = 100,
                gyldigFra = aktivGyldigFra.minusDays(5),
                opprettet = LocalDateTime.now(),
            )
            val deltakelsesmengder = TestData
                .lagDeltakerHistorikk(endringer = listOf(aktiv))
                .toDeltakelsesmengder()

            // Act
            val resultat = deltakelsesmengder.validerNyDeltakelsesmengde(
                ny.toDeltakelsesmengde().shouldNotBeNull(),
            )

            // Assert
            resultat shouldBe true
        }

        @Test
        fun `ny deltakelsesmengde - tilbakedatert mengde er lik mengden på datoen - godtas`() {
            // Arrange
            val iDag = LocalDate.now()
            val tidligere = TestData.lagEndreDeltakelsesmengde(
                deltakelsesprosent = 100,
                gyldigFra = iDag.minusDays(10),
                opprettet = iDag.minusDays(10).atStartOfDay(),
            )
            val aktiv = TestData.lagEndreDeltakelsesmengde(
                deltakelsesprosent = 50,
                gyldigFra = iDag.minusDays(5),
                opprettet = iDag.minusDays(5).atStartOfDay(),
            )
            val ny = TestData.lagEndreDeltakelsesmengde(
                deltakelsesprosent = 100,
                gyldigFra = iDag.minusDays(7),
                opprettet = iDag.atStartOfDay(),
            )
            val deltakelsesmengder = TestData
                .lagDeltakerHistorikk(endringer = listOf(tidligere, aktiv))
                .toDeltakelsesmengder()

            // Act
            val resultat = deltakelsesmengder.validerNyDeltakelsesmengde(
                ny.toDeltakelsesmengde().shouldNotBeNull(),
            )

            // Assert
            resultat shouldBe true
        }

        @Test
        fun `ny deltakelsesmengde - bare fremtidige mengder finnes - godtas`() {
            // Arrange
            val framtidig = TestData.lagEndreDeltakelsesmengde(
                deltakelsesprosent = 100,
                gyldigFra = LocalDate.now().plusDays(10),
                opprettet = LocalDateTime.now(),
            )
            val ny = TestData.lagEndreDeltakelsesmengde(
                deltakelsesprosent = 100,
                gyldigFra = LocalDate.now().plusDays(20),
                opprettet = LocalDateTime.now().plusSeconds(1),
            )
            val deltakelsesmengder = TestData
                .lagDeltakerHistorikk(endringer = listOf(framtidig))
                .toDeltakelsesmengder()

            // Act
            val resultat = deltakelsesmengder.validerNyDeltakelsesmengde(
                ny.toDeltakelsesmengde().shouldNotBeNull(),
            )

            // Assert
            resultat shouldBe true
        }

        @Test
        fun `ny deltakelsesmengde - tidligere gyldig fra fører til endring - godtas`() {
            // Arrange
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

            // Act
            val resultat = deltakelsesmengder.validerNyDeltakelsesmengde(
                andreEndring.toDeltakelsesmengde().shouldNotBeNull(),
            )

            // Assert
            resultat shouldBe true
        }

        @Test
        fun `ny deltakelsesmengde - ny deltakelsesmengde fører til endring - godtas`() {
            // Arrange
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

            // Act
            val resultat = deltakelsesmengder.validerNyDeltakelsesmengde(
                andreEndring.toDeltakelsesmengde().shouldNotBeNull(),
            )

            // Assert
            resultat shouldBe true
        }

        @Test
        fun `ny deltakelsesmengde - planlegger retur til dagens mengde etter fremtidig endring`() {
            // Arrange
            val iDag = LocalDate.now()
            val aktiv = TestData.lagEndreDeltakelsesmengde(
                deltakelsesprosent = 100,
                gyldigFra = iDag.minusDays(5),
                opprettet = iDag.minusDays(5).atStartOfDay(),
            )
            val planlagt = TestData.lagEndreDeltakelsesmengde(
                deltakelsesprosent = 50,
                gyldigFra = iDag.plusDays(5),
                opprettet = iDag.minusDays(1).atStartOfDay(),
            )
            val returTilAktivMengde = TestData.lagEndreDeltakelsesmengde(
                deltakelsesprosent = 100,
                gyldigFra = iDag.plusDays(10),
                opprettet = iDag.atStartOfDay(),
            )
            val deltakelsesmengder = TestData
                .lagDeltakerHistorikk(endringer = listOf(aktiv, planlagt))
                .toDeltakelsesmengder()

            // Act
            val resultat = deltakelsesmengder.validerNyDeltakelsesmengde(
                returTilAktivMengde.toDeltakelsesmengde().shouldNotBeNull(),
            )

            // Assert
            resultat shouldBe true
        }

        @Test
        fun `ny deltakelsesmengde - endring overskriver siste mengde med samme mengde som forrige - godtas`() {
            // Arrange
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

            // Act
            val resultat = deltakelsesmengder.validerNyDeltakelsesmengde(
                tredjeEndring.toDeltakelsesmengde().shouldNotBeNull(),
            )

            // Assert
            resultat shouldBe true
        }
    }

    @Nested
    inner class Deltakelsesmengder {
        @Test
        fun `lik opprettet og gyldig fra - siste i historikken benyttes`() {
            // Arrange
            val opprettet = "2024-01-10".toDateTime()
            val gyldigFra = "2024-01-15".toDate()
            val forste = Deltakelsesmengde(50F, null, gyldigFra, opprettet)
            val siste = Deltakelsesmengde(80F, null, gyldigFra, opprettet)

            // Act
            val resultat = Deltakelsesmengder(listOf(forste, siste)).perioder
            // Assert
            resultat shouldBe listOf(siste)
        }
    }

    @Nested
    inner class AvgrensPeriodeTilStartdato {
        @Test
        fun `fjernet startdato gjenoppretter råperioder og beregner gjeldende mengde på nytt`() {
            // Arrange
            val idag = LocalDate.now()
            val startdato = idag.plusDays(2)
            val mengder = Deltakelsesmengder(
                mengder = listOf(
                    Deltakelsesmengde(100F, 5F, idag.minusDays(1), idag.minusDays(1).atStartOfDay()),
                    Deltakelsesmengde(50F, 3F, startdato, startdato.atStartOfDay()),
                ),
                startdato = startdato,
            )

            // Act
            val utenStartdato = mengder.avgrensPeriodeTilStartdato(null)

            // Assert
            utenStartdato.perioder.map { it.deltakelsesprosent } shouldBe listOf(100F, 50F)
            assertSoftly(utenStartdato.gjeldende.shouldNotBeNull()) {
                deltakelsesprosent shouldBe 100F
                dagerPerUke shouldBe 5F
            }
        }
    }

    @Nested
    inner class Gjeldende {
        @Test
        fun `gjeldende - siste deltakelsesmengde er ikke gjeldende enda - returnerer riktig deltakelsesmengde`() {
            // Arrange
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

            // Act
            val deltakelsesmengder = historikk.toDeltakelsesmengder()

            // Assert
            deltakelsesmengder.gjeldende shouldBe gjeldende.toDeltakelsesmengde()
        }

        @Test
        fun `gjeldende - bare fremtidige - returnerer første`() {
            // Arrange
            val fremtidig = TestData.lagEndreDeltakelsesmengde(
                deltakelsesprosent = 90,
                gyldigFra = LocalDate.now().plusDays(1),
                opprettet = LocalDate.now().atStartOfDay(),
            )

            val historikk = TestData.lagDeltakerHistorikk(
                endringer = listOf(fremtidig),
            )

            // Act
            val deltakelsesmengder = historikk.toDeltakelsesmengder()

            // Assert
            deltakelsesmengder.gjeldende shouldBe fremtidig.toDeltakelsesmengde()
            deltakelsesmengder.nesteGjeldende shouldBe null
        }

        @Test
        fun `gjeldende - ikke fattet vedtak - returnerer deltakelsesmengde`() {
            // Arrange
            val fremtidig = TestData.lagVedtak(
                deltakelsesprosent = 90F,
                fattet = null,
                opprettet = LocalDate.now().atStartOfDay(),
            )

            val historikk = TestData.lagDeltakerHistorikk(
                vedtak = listOf(fremtidig),
            )

            // Act
            val deltakelsesmengder = historikk.toDeltakelsesmengder()

            // Assert
            deltakelsesmengder.gjeldende shouldBe fremtidig.toDeltakelsesmengde()
        }
    }

    @Nested
    inner class NesteGjeldende {
        @Test
        fun `neste gjeldende - bare én mengde - returnerer ingen`() {
            // Arrange
            val endring = TestData.lagEndreDeltakelsesmengde(
                deltakelsesprosent = 90,
                gyldigFra = LocalDate.now(),
                opprettet = LocalDate.now().atStartOfDay(),
            )

            val historikk = TestData.lagDeltakerHistorikk(
                endringer = listOf(endring),
            )

            // Act
            val deltakelsesmengder = historikk.toDeltakelsesmengder()

            // Assert
            deltakelsesmengder.nesteGjeldende shouldBe null
        }

        @Test
        fun `neste gjeldende - fremtidig mengde - returnerer riktig deltakelsesmengde`() {
            // Arrange
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

            // Act
            val deltakelsesmengder = historikk.toDeltakelsesmengder()

            // Assert
            deltakelsesmengder.nesteGjeldende shouldBe fremtidig.toDeltakelsesmengde()
        }
    }
}

fun String.toDate(): LocalDate = LocalDate.parse(this)

fun String.toDateTime(): LocalDateTime = this.toDate().atStartOfDay()
