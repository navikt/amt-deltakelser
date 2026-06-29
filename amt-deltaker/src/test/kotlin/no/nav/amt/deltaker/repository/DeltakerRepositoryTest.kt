package no.nav.amt.deltaker.repository

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.maps.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.result.shouldBeFailure
import io.kotest.matchers.result.shouldBeSuccess
import io.kotest.matchers.shouldBe
import no.nav.amt.deltaker.enkeltplass.EnkeltplassDeltakerUpdateDbo
import no.nav.amt.deltaker.model.Deltaker
import no.nav.amt.deltaker.utils.data.TestData
import no.nav.amt.deltaker.utils.data.TestData.lagDeltaker
import no.nav.amt.deltaker.utils.data.TestData.lagDeltakerStatus
import no.nav.amt.deltaker.utils.data.TestData.lagDeltakerliste
import no.nav.amt.deltaker.utils.data.TestData.lagForslag
import no.nav.amt.deltaker.utils.data.TestData.lagTiltakstype
import no.nav.amt.deltaker.utils.data.TestData.lagVedtak
import no.nav.amt.deltaker.utils.data.TestRepository
import no.nav.amt.deltaker.veileder.KladdService.Companion.lagKladdUpsertDbo
import no.nav.amt.lib.models.deltaker.Deltakelsesinnhold
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltaker.Innhold
import no.nav.amt.lib.models.deltakerliste.GjennomforingStatusType
import no.nav.amt.lib.models.deltakerliste.GjennomforingType
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import no.nav.amt.lib.testing.DatabaseTestExtension
import no.nav.amt.lib.testing.shouldBeCloseTo
import no.nav.amt.lib.testing.utils.TestData.lagDeltakerVedImport
import no.nav.amt.lib.testing.utils.TestData.lagImportertFraArena
import no.nav.amt.lib.testing.utils.TestData.lagNavAnsatt
import no.nav.amt.lib.testing.utils.TestData.lagNavBruker
import no.nav.amt.lib.testing.utils.TestData.lagNavEnhet
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

class DeltakerRepositoryTest {
    private val deltakerRepository = DeltakerRepository()

    @Nested
    inner class GetUtdaterteKladderTests {
        @Test
        fun `getUtdaterteKladder - finnes en utdatert kladd - returnerer utdatert kladd`() {
            // Arrange
            val aktivKladd = lagDeltaker(
                status = lagDeltakerStatus(DeltakerStatus.Type.KLADD),
            )

            val utdatertKladd = lagDeltaker(
                status = lagDeltakerStatus(DeltakerStatus.Type.KLADD),
                sistEndret = LocalDateTime.now().minusWeeks(3),
            )

            TestRepository.insertAll(aktivKladd, utdatertKladd)

            // Act
            val utdaterteKladder = deltakerRepository.getUtdaterteKladder(LocalDateTime.now().minusWeeks(2))

            // Assert
            utdaterteKladder.size shouldBe 1
            utdaterteKladder.first() shouldBe utdatertKladd.id
        }

        @Test
        fun `getUtdaterteKladder - deltaker med historisk KLADD status (gyldig_til set) men annen aktiv status - returneres ikke`() {
            // Arrange - Test for regresjonstesting av gyldig_til filter
            // Deltaker som hadde KLADD status men har nå progrediert til SOKT_INN
            val utdatertTidspunkt = LocalDateTime.now().minusWeeks(3)
            val nySoktInnStatusTidspunkt = LocalDateTime.now().minusWeeks(1)

            val deltaker = lagDeltaker(
                sistEndret = utdatertTidspunkt,
            )

            TestRepository.insert(deltaker)

            // Avslutt gammel KLADD status
            DeltakerStatusRepository.lagreStatus(
                deltakerId = deltaker.id,
                deltakerStatus = lagDeltakerStatus(
                    statusType = DeltakerStatus.Type.KLADD,
                    gyldigFra = utdatertTidspunkt,
                    gyldigTil = nySoktInnStatusTidspunkt,
                ),
            )

            // Legg til ny aktiv SOKT_INN status
            DeltakerStatusRepository.lagreStatus(
                deltakerId = deltaker.id,
                deltakerStatus = lagDeltakerStatus(
                    statusType = DeltakerStatus.Type.SOKT_INN,
                    gyldigFra = nySoktInnStatusTidspunkt,
                    gyldigTil = null,
                ),
            )

            // Act
            val utdaterteKladder = deltakerRepository.getUtdaterteKladder(LocalDateTime.now().minusWeeks(2))

            // Assert - deltaker skal IKKE returneres fordi aktiv status (gyldig_til IS NULL) er SOKT_INN, ikke KLADD
            utdaterteKladder.shouldBeEmpty()
        }
    }

    @Nested
    inner class GetEnkeltplassdeltakerTests {
        @Test
        fun `skal returnere failure hvis ingen deltaker`() {
            // Act
            val deltaker = deltakerRepository.getEnkeltplassdeltaker(
                deltakerlisteId = UUID.randomUUID(),
            )

            // Assert
            deltaker.shouldBeFailure()
        }

        @Test
        fun `skal returnere deltaker`() {
            // Arrange
            val deltakerInTest = lagDeltaker(
                deltakerliste = lagDeltakerliste(gjennomforingstype = GjennomforingType.Enkeltplass),
                status = lagDeltakerStatus(DeltakerStatus.Type.KLADD),
            )
            TestRepository.insert(deltakerInTest)

            // Act
            val deltaker = deltakerRepository.getEnkeltplassdeltaker(
                deltakerlisteId = deltakerInTest.deltakerliste.id,
            )

            // Assert
            deltaker.shouldBeSuccess()
        }

        @Test
        fun `skal returnere failure for deltaker som ikke er enkeltplass`() {
            // Arrange
            val deltakerInTest = lagDeltaker(
                deltakerliste = lagDeltakerliste(gjennomforingstype = GjennomforingType.Gruppe),
                status = lagDeltakerStatus(DeltakerStatus.Type.KLADD),
            )
            TestRepository.insert(deltakerInTest)

            // Act
            val deltaker = deltakerRepository.getEnkeltplassdeltaker(
                deltakerlisteId = deltakerInTest.deltakerliste.id,
            )

            // Assert
            deltaker.shouldBeFailure()
        }
    }

    @Nested
    inner class KladdTests {
        val deltakerliste = lagDeltakerliste()

        @Test
        fun `getKladdForDeltakerliste - skal returnere failure hvis ingen kladd`() {
            TestRepository.insert(deltakerliste)

            val kladdResult = deltakerRepository.getKladdForDeltakerliste(
                deltakerlisteId = deltakerliste.id,
                personident = "~personident~",
            )

            kladdResult.shouldBeFailure()
        }

        @Test
        fun `getKladdForDeltakerliste - skal returnere success hvis kladd finnes`() {
            val deltaker = lagDeltaker(
                status = lagDeltakerStatus(DeltakerStatus.Type.KLADD),
                deltakerliste = deltakerliste,
            )
            TestRepository.insert(deltaker)

            val kladdResult = deltakerRepository.getKladdForDeltakerliste(
                deltakerlisteId = deltakerliste.id,
                personident = deltaker.navBruker.personident,
            )

            kladdResult.shouldBeSuccess()
        }

        @Test
        fun `upsertKladd - skal oppdatere eksisterende kladd`() {
            val deltaker = lagDeltaker(
                status = lagDeltakerStatus(DeltakerStatus.Type.KLADD),
                deltakerliste = deltakerliste,
            )
            TestRepository.insert(deltaker)

            val oppdatertDeltaker = lagKladdUpsertDbo(
                deltaker = deltaker,
                innhold = listOf(Innhold("", "", true, "")),
                bakgrunnsinformasjon = "Tralala",
                deltakelsesprosent = 5,
                dagerPerUke = 3,
            )

            deltakerRepository.upsertKladd(oppdatertDeltaker)

            val kladdResult = deltakerRepository
                .get(deltaker.id)
                .getOrThrow()

            assertSoftly(kladdResult) {
                id shouldBe deltaker.id
                bakgrunnsinformasjon shouldBe oppdatertDeltaker.bakgrunnsinformasjon
                deltakelsesprosent shouldBe oppdatertDeltaker.deltakelsesprosent
                dagerPerUke shouldBe oppdatertDeltaker.dagerPerUke
                deltakelsesinnhold shouldBe oppdatertDeltaker.deltakelsesinnhold
            }
        }

        @Test
        fun `updateEnkeltplassKladd - skal opprette kladd`() {
            val deltaker = lagDeltaker(
                status = lagDeltakerStatus(DeltakerStatus.Type.KLADD),
                deltakerliste = deltakerliste,
            )

            TestRepository.insert(deltaker)
            val oppdatertDeltaker = EnkeltplassDeltakerUpdateDbo(
                id = deltaker.id,
                startdato = deltaker.startdato,
                sluttdato = deltaker.sluttdato,
                deltakelsesinnhold = Deltakelsesinnhold(
                    ledetekst = deltaker.deltakerliste.tiltakstype.innhold
                        ?.ledetekst,
                    innhold = listOf(Innhold.createFritekstInnhold("Dette er beskrivelsen")),
                ),
            )

            deltakerRepository.updateEnkeltplass(oppdatertDeltaker)
            val kladdResult = deltakerRepository
                .get(deltaker.id)
                .getOrThrow()

            assertSoftly(kladdResult) {
                id shouldBe deltaker.id
                startdato shouldBe oppdatertDeltaker.startdato
                sluttdato shouldBe oppdatertDeltaker.sluttdato
                deltakelsesinnhold shouldBe oppdatertDeltaker.deltakelsesinnhold
            }
        }
    }

    @Nested
    inner class GetDeltakereForDeltakerlisteTests {
        val deltakerliste = lagDeltakerliste()

        @Test
        fun `skal returnere failure hvis ingen deltakere`() {
            TestRepository.insert(deltakerliste)

            val kladdResult = deltakerRepository.getKladdForDeltakerliste(
                deltakerlisteId = deltakerliste.id,
                personident = "~personident~",
            )

            kladdResult.shouldBeFailure()
        }

        @Test
        fun `skal returnere success hvis kladd finnes`() {
            val deltaker = lagDeltaker(
                status = lagDeltakerStatus(DeltakerStatus.Type.KLADD),
                deltakerliste = deltakerliste,
            )
            TestRepository.insert(deltaker)

            val kladdResult = deltakerRepository.getKladdForDeltakerliste(
                deltakerlisteId = deltakerliste.id,
                personident = deltaker.navBruker.personident,
            )

            kladdResult.shouldBeSuccess()
        }
    }

    @Nested
    inner class GetDeltakereForAvsluttetDeltakerlisteTests {
        val deltakerlisteInTest = lagDeltakerliste()

        @BeforeEach
        fun setup() = TestRepository.insert(deltakerlisteInTest)

        @Test
        fun `skal returnere tom liste hvis ingen deltakere`() {
            val deltakere = deltakerRepository.getDeltakereForAvsluttetDeltakerliste(deltakerlisteInTest.id)

            deltakere.shouldBeEmpty()
        }

        @Test
        fun `skal filtrere bort deltakere med status KLADD`() {
            TestRepository.insert(
                lagDeltaker(
                    status = lagDeltakerStatus(DeltakerStatus.Type.KLADD),
                    deltakerliste = deltakerlisteInTest,
                ),
            )

            val deltakere = deltakerRepository.getDeltakereForAvsluttetDeltakerliste(deltakerlisteInTest.id)

            deltakere.shouldBeEmpty()
        }

        @Test
        fun `skal returnere deltakere med status DELTAR`() {
            TestRepository.insert(
                lagDeltaker(
                    status = lagDeltakerStatus(DeltakerStatus.Type.DELTAR),
                    deltakerliste = deltakerlisteInTest,
                ),
            )

            val deltakere = deltakerRepository.getDeltakereForAvsluttetDeltakerliste(deltakerlisteInTest.id)

            deltakere.size shouldBe 1
        }
    }

    @Nested
    inner class GetDeltakerHvorSluttdatoSkalEndresTests {
        val deltakerlisteInTest = lagDeltakerliste(sluttDato = LocalDate.now().minusDays(2))

        @BeforeEach
        fun setup() = TestRepository.insert(deltakerlisteInTest)

        @Test
        fun `skal returnere tom liste hvis ingen deltakere`() {
            val deltakere = deltakerRepository.getDeltakerHvorSluttdatoSkalEndres(deltakerlisteInTest.id)

            deltakere.shouldBeEmpty()
        }

        @ParameterizedTest
        @EnumSource(
            value = DeltakerStatus.Type::class,
            names = ["AVBRUTT", "AVBRUTT_UTKAST", "FULLFORT", "HAR_SLUTTET", "IKKE_AKTUELL", "FEILREGISTRERT"],
        )
        fun `skal filtrere bort deltakere med avsluttende status`(status: DeltakerStatus.Type) {
            TestRepository.insert(
                lagDeltaker(
                    status = lagDeltakerStatus(status),
                    deltakerliste = deltakerlisteInTest,
                ),
            )

            val deltakere = deltakerRepository.getDeltakerHvorSluttdatoSkalEndres(deltakerlisteInTest.id)

            deltakere.shouldBeEmpty()
        }

        @Test
        fun `skal returnere deltakere med sluttdato storre enn deltakerliste sluttdato`() {
            setOf(
                LocalDate.now().plusDays(1), // skal returneres
                LocalDate.now().minusDays(2),
                null,
            ).forEach { sluttdato ->
                TestRepository.insert(
                    lagDeltaker(
                        status = lagDeltakerStatus(DeltakerStatus.Type.DELTAR),
                        deltakerliste = deltakerlisteInTest,
                        sluttdato = sluttdato,
                    ),
                )
            }

            val deltakere = deltakerRepository.getDeltakerHvorSluttdatoSkalEndres(deltakerlisteInTest.id)

            deltakere.size shouldBe 1
        }
    }

    @Nested
    inner class GetAntallDeltakereForDeltakerlisteTests {
        val deltakerlisteInTest = lagDeltakerliste()

        @BeforeEach
        fun setup() = TestRepository.insert(deltakerlisteInTest)

        @Test
        fun `skal returnere 0 hvis ingen deltakere`() {
            val antallDeltakere = deltakerRepository.getAntallDeltakereForDeltakerliste(deltakerlisteInTest.id)

            antallDeltakere shouldBe 0
        }

        @Test
        fun `skal returnere antall deltaker hvis deltakerliste inneholder deltakere`() {
            TestRepository.insert(lagDeltaker(deltakerliste = deltakerlisteInTest))

            val antallDeltakere = deltakerRepository.getAntallDeltakereForDeltakerliste(deltakerlisteInTest.id)

            antallDeltakere shouldBe 1
        }
    }

    @Nested
    inner class UpsertTests {
        @Test
        fun `ny deltaker - insertes`() {
            val expectedDeltaker = lagDeltaker()
            TestRepository.insertAll(expectedDeltaker.deltakerliste, expectedDeltaker.navBruker)

            deltakerRepository.upsert(expectedDeltaker)
            DeltakerStatusRepository.lagreStatus(expectedDeltaker.id, expectedDeltaker.status)

            val deltakerFromDb = deltakerRepository.get(expectedDeltaker.id).shouldBeSuccess()
            assertDeltakereAreEqual(deltakerFromDb, expectedDeltaker)
        }

        @Test
        fun `oppdatert deltaker - oppdaterer`() {
            val deltaker = lagDeltaker()
            TestRepository.insert(deltaker)

            val oppdatertDeltaker = deltaker.copy(
                startdato = LocalDate.now().plusWeeks(1),
                sluttdato = LocalDate.now().plusWeeks(5),
                dagerPerUke = 1F,
                deltakelsesprosent = 20F,
            )

            deltakerRepository.upsert(oppdatertDeltaker)

            assertDeltakereAreEqual(
                deltakerRepository.get(deltaker.id).shouldBeSuccess(),
                oppdatertDeltaker,
            )
        }
    }

    @Nested
    inner class SkalHaAvsluttendeStatusTests {
        @Test
        fun `deltar, sluttdato passert - returnerer deltaker`() {
            val deltaker = lagDeltaker(
                status = lagDeltakerStatus(DeltakerStatus.Type.VENTER_PA_OPPSTART),
                startdato = LocalDate.now().minusDays(10),
                sluttdato = LocalDate.now().plusWeeks(2),
            )
            TestRepository.insert(deltaker)

            val oppdatertDeltaker = deltaker.copy(
                status = lagDeltakerStatus(DeltakerStatus.Type.DELTAR),
                startdato = LocalDate.now().minusDays(10),
                sluttdato = LocalDate.now().minusDays(1),
            )
            deltakerRepository.upsert(oppdatertDeltaker)

            val deltakereSomSkalHaAvsluttendeStatus = deltakerRepository.getDeltakereHvorSluttdatoHarPassert()

            deltakereSomSkalHaAvsluttendeStatus.size shouldBe 1
            deltakereSomSkalHaAvsluttendeStatus.first().id shouldBe deltaker.id
        }

        @Test
        fun `venter pa oppstart, sluttdato mangler - returnerer ikke deltaker`() {
            val deltaker = lagDeltaker(
                status = lagDeltakerStatus(DeltakerStatus.Type.VENTER_PA_OPPSTART),
                startdato = LocalDate.now().minusDays(10),
                sluttdato = null,
            )
            TestRepository.insert(deltaker)

            val deltakereSomSkalHaAvsluttendeStatus = deltakerRepository.getDeltakereHvorSluttdatoHarPassert()

            deltakereSomSkalHaAvsluttendeStatus.shouldBeEmpty()
        }
    }

    @Nested
    inner class DeltarPaAvsluttetDeltakerlisteTests {
        @Test
        fun `deltar, dl-sluttdato passert - returnerer deltaker`() {
            val deltaker = lagDeltaker(
                status = lagDeltakerStatus(DeltakerStatus.Type.DELTAR),
                startdato = LocalDate.now().minusDays(10),
                sluttdato = LocalDate.now().plusDays(2),
                deltakerliste = lagDeltakerliste(status = GjennomforingStatusType.AVSLUTTET),
            )
            TestRepository.insert(deltaker)

            val deltakerePaAvsluttetDeltakerliste = deltakerRepository.getDeltakereSomDeltarPaAvsluttetDeltakerliste()

            deltakerePaAvsluttetDeltakerliste.size shouldBe 1
            deltakerePaAvsluttetDeltakerliste.first().id shouldBe deltaker.id
        }

        @Test
        fun `har sluttet, dl-sluttdato passert - returnerer ikke deltaker`() {
            val deltaker = lagDeltaker(
                status = lagDeltakerStatus(DeltakerStatus.Type.HAR_SLUTTET),
                startdato = LocalDate.now().minusDays(10),
                sluttdato = LocalDate.now(),
                deltakerliste = lagDeltakerliste(status = GjennomforingStatusType.AVSLUTTET),
            )
            TestRepository.insert(deltaker)

            val deltakerePaAvsluttetDeltakerliste = deltakerRepository.getDeltakereSomDeltarPaAvsluttetDeltakerliste()

            deltakerePaAvsluttetDeltakerliste.shouldBeEmpty()
        }

        @Test
        fun `deltar, enkeltplass-dl avsluttet - returnerer ikke deltaker`() {
            val deltaker = lagDeltaker(
                status = lagDeltakerStatus(DeltakerStatus.Type.DELTAR),
                startdato = LocalDate.now().minusDays(10),
                sluttdato = LocalDate.now().plusDays(2),
                deltakerliste = lagDeltakerliste(
                    gjennomforingstype = GjennomforingType.Enkeltplass,
                    tiltakstype = lagTiltakstype(tiltakskode = Tiltakskode.ENKELTPLASS_ARBEIDSMARKEDSOPPLAERING),
                    status = GjennomforingStatusType.AVSLUTTET,
                ),
            )
            TestRepository.insert(deltaker)

            val deltakerePaAvsluttetDeltakerliste = deltakerRepository.getDeltakereSomDeltarPaAvsluttetDeltakerliste()

            deltakerePaAvsluttetDeltakerliste.shouldBeEmpty()
        }

        @Test
        fun `deltar, enkeltplass-dl avbrutt - returnerer ikke deltaker`() {
            val deltaker = lagDeltaker(
                status = lagDeltakerStatus(DeltakerStatus.Type.DELTAR),
                startdato = LocalDate.now().minusDays(10),
                sluttdato = LocalDate.now().plusDays(2),
                deltakerliste = lagDeltakerliste(
                    gjennomforingstype = GjennomforingType.Enkeltplass,
                    tiltakstype = lagTiltakstype(tiltakskode = Tiltakskode.ENKELTPLASS_FAG_OG_YRKESOPPLAERING),
                    status = GjennomforingStatusType.AVBRUTT,
                ),
            )
            TestRepository.insert(deltaker)

            val deltakerePaAvsluttetDeltakerliste = deltakerRepository.getDeltakereSomDeltarPaAvsluttetDeltakerliste()

            deltakerePaAvsluttetDeltakerliste.shouldBeEmpty()
        }

        @Test
        fun `deltar, enkeltplass-dl avlyst - returnerer ikke deltaker`() {
            val deltaker = lagDeltaker(
                status = lagDeltakerStatus(DeltakerStatus.Type.DELTAR),
                startdato = LocalDate.now().minusDays(10),
                sluttdato = LocalDate.now().plusDays(2),
                deltakerliste = lagDeltakerliste(
                    gjennomforingstype = GjennomforingType.Enkeltplass,
                    tiltakstype = lagTiltakstype(tiltakskode = Tiltakskode.HOYERE_UTDANNING),
                    status = GjennomforingStatusType.AVLYST,
                ),
            )
            TestRepository.insert(deltaker)

            val deltakerePaAvsluttetDeltakerliste = deltakerRepository.getDeltakereSomDeltarPaAvsluttetDeltakerliste()

            deltakerePaAvsluttetDeltakerliste.shouldBeEmpty()
        }
    }

    @Nested
    inner class GetTests {
        @Test
        fun `skal returnere eksisterende deltaker`() {
            val deltaker = lagDeltaker()
            TestRepository.insert(deltaker)

            val deltakerFraDb = deltakerRepository.get(deltaker.id).shouldBeSuccess()

            deltakerFraDb.shouldNotBeNull()
            deltakerFraDb.id shouldBe deltaker.id
        }

        @Test
        fun `deltaker er feilregistrert - fjerner informasjon`() {
            val deltaker = lagDeltaker(status = lagDeltakerStatus(DeltakerStatus.Type.FEILREGISTRERT))
            TestRepository.insert(deltaker)

            val deltakerFraDb = deltakerRepository.get(deltaker.id).shouldBeSuccess()
            assertSoftly(deltakerFraDb) {
                startdato shouldBe null
                sluttdato shouldBe null
                dagerPerUke shouldBe null
                deltakelsesprosent shouldBe null
                bakgrunnsinformasjon shouldBe null
                deltakelsesinnhold shouldBe null
            }
        }
    }

    @Test
    fun `getMany(list) - henter mange deltakere`() {
        val deltaker1 = lagDeltaker()
        val deltaker2 = lagDeltaker()

        TestRepository.insertAll(deltaker1, deltaker2)

        val deltakere = deltakerRepository.getMany(setOf(deltaker1.id, deltaker2.id))
        deltakere shouldHaveSize 2
        deltakere.any { it.id == deltaker1.id } shouldBe true
        deltakere.any { it.id == deltaker2.id } shouldBe true
    }

    @Test
    fun `getPersonidentForDeltaker - returnerer personident`() {
        val deltaker = lagDeltaker()
        TestRepository.insertAll(deltaker)
        deltakerRepository.getPersonidentForDeltaker(deltaker.id) shouldBe deltaker.navBruker.personident
    }

    @Test
    fun `getPersonidentForForslag - returnerer personident`() {
        val deltaker = lagDeltaker()
        val forslag = lagForslag(deltakerId = deltaker.id)

        TestRepository.insertAll(deltaker, forslag)
        deltakerRepository.getPersonidentForForslag(forslag.id) shouldBe deltaker.navBruker.personident
    }

    @Nested
    inner class GetDeltakelserForLaaseSjekkTests {
        @Test
        fun `ingen treff i database - returnerer tomt map`() {
            // Act
            val resultat = deltakerRepository.getDeltakelserForLaaseSjekk(
                personIdenter = setOf("12345678901"),
                gjennomforingId = UUID.randomUUID(),
            )

            // Assert
            resultat shouldBe emptyMap()
        }

        @Test
        fun `en deltakelse - mapper alle felter korrekt`() {
            // Arrange
            val deltakerliste = lagDeltakerliste()
            val gyldigFra = LocalDateTime.now().minusDays(2)
            val deltaker = lagDeltaker(
                deltakerliste = deltakerliste,
                status = lagDeltakerStatus(
                    statusType = DeltakerStatus.Type.DELTAR,
                    gyldigFra = gyldigFra,
                ),
            )
            TestRepository.insertAll(deltakerliste, deltaker)

            // Act
            val resultat = deltakerRepository.getDeltakelserForLaaseSjekk(
                setOf(deltaker.navBruker.personident),
                gjennomforingId = deltakerliste.id,
            )

            // Assert
            resultat shouldHaveSize 1
            resultat.values.first() shouldHaveSize 1

            assertSoftly(resultat.values.first().first()) {
                id shouldBe deltaker.id
                statusType shouldBe DeltakerStatus.Type.DELTAR
                statusGyldigFra shouldBeCloseTo gyldigFra
                vedtakFattet shouldBe null
                innsoektDatoFraArena shouldBe null
            }
        }

        @Test
        fun `inkluderer vedtak_fattet naar gyldig vedtak finnes`() {
            // Arrange
            val deltakerliste = lagDeltakerliste()
            val deltaker = lagDeltaker(
                deltakerliste = deltakerliste,
                status = lagDeltakerStatus(statusType = DeltakerStatus.Type.DELTAR),
            )
            val fattet = LocalDateTime.now().minusWeeks(1)
            val navAnsatt = lagNavAnsatt()
            val navEnhet = lagNavEnhet()
            val vedtak = lagVedtak(
                deltakerVedVedtak = deltaker,
                fattet = fattet,
                opprettetAv = navAnsatt,
                opprettetAvEnhet = navEnhet,
                sistEndretAv = navAnsatt,
                sistEndretAvEnhet = navEnhet,
            )
            TestRepository.insertAll(navEnhet, navAnsatt, deltakerliste, deltaker, vedtak)

            // Act
            val resultat = deltakerRepository.getDeltakelserForLaaseSjekk(
                personIdenter = setOf(deltaker.navBruker.personident),
                gjennomforingId = deltakerliste.id,
            )

            // Assert
            resultat[deltaker.navBruker.personident].shouldNotBeNull()

            resultat[deltaker.navBruker.personident]?.single()?.vedtakFattet shouldBeCloseTo fattet
        }

        @Test
        fun `inkluderer innsoektDatoFraArena fra JSONB-kolonnen`() {
            // Arrange
            val deltakerliste = lagDeltakerliste()
            val deltaker = lagDeltaker(
                deltakerliste = deltakerliste,
                status = lagDeltakerStatus(statusType = DeltakerStatus.Type.DELTAR),
            )
            val innsoktDato = LocalDate.now().minusMonths(2)
            val arenaImport = lagImportertFraArena(
                deltakerId = deltaker.id,
                deltakerVedImport = deltaker.toDeltakerVedImport(innsoktDato),
            )
            TestRepository.insertAll(deltakerliste, deltaker, arenaImport)

            // Act
            val resultat = deltakerRepository.getDeltakelserForLaaseSjekk(
                personIdenter = setOf(deltaker.navBruker.personident),
                gjennomforingId = deltakerliste.id,
            )

            // Assert
            resultat[deltaker.navBruker.personident]?.first()?.innsoektDatoFraArena shouldBe innsoktDato
        }

        @Test
        fun `filtrerer paa deltakerlisteId - deltakelser i andre lister ignoreres`() {
            // Arrange — samme person med deltakelse i to ulike lister
            val bruker = lagNavBruker()
            val annenListe = lagDeltakerliste()
            val maalListe = lagDeltakerliste()
            val annenDeltakelse = lagDeltaker(navBruker = bruker, deltakerliste = annenListe)
            val maalDeltakelse = lagDeltaker(navBruker = bruker, deltakerliste = maalListe)
            TestRepository.insertAll(annenListe, maalListe, annenDeltakelse, maalDeltakelse)

            // Act
            val resultat = deltakerRepository.getDeltakelserForLaaseSjekk(
                personIdenter = setOf(bruker.personident),
                gjennomforingId = maalListe.id,
            )

            // Assert — kun deltakelsen i målliste returneres
            resultat[bruker.personident]?.first()?.id shouldBe maalDeltakelse.id
        }

        @Test
        fun `flere deltakelser paa samme person i samme liste - alle returneres`() {
            // Arrange — én bruker har to deltakelser i samme deltakerliste (tidligere + aktiv)
            val bruker = lagNavBruker()
            val deltakerliste = lagDeltakerliste()
            val tidligere = lagDeltaker(
                navBruker = bruker,
                deltakerliste = deltakerliste,
                status = lagDeltakerStatus(
                    statusType = DeltakerStatus.Type.HAR_SLUTTET,
                    gyldigFra = LocalDateTime.now().minusMonths(6),
                ),
            )
            val aktiv = lagDeltaker(
                navBruker = bruker,
                deltakerliste = deltakerliste,
                status = lagDeltakerStatus(
                    statusType = DeltakerStatus.Type.DELTAR,
                    gyldigFra = LocalDateTime.now().minusDays(1),
                ),
            )
            TestRepository.insertAll(deltakerliste, tidligere, aktiv)

            // Act
            val resultat = deltakerRepository.getDeltakelserForLaaseSjekk(
                personIdenter = setOf(bruker.personident),
                gjennomforingId = deltakerliste.id,
            )

            // Assert
            resultat.values
                .flatMap { laaseInfoList -> laaseInfoList.map { laaseInfo -> laaseInfo.id } }
                .toSet() shouldBe setOf(tidligere.id, aktiv.id)
        }
    }

    @Nested
    inner class GetSoktInnDatoTests {
        @Test
        fun `ukjent deltaker - returnerer null`() {
            // Arrange / Act
            val resultat = deltakerRepository.getSoktInnDato(UUID.randomUUID())

            // Assert
            resultat shouldBe null
        }

        @Test
        fun `deltaker uten arena-import, innsok eller vedtak - returnerer null`() {
            // Arrange
            val deltaker = lagDeltaker()
            TestRepository.insert(deltaker)

            // Act
            val resultat = deltakerRepository.getSoktInnDato(deltaker.id)

            // Assert
            resultat shouldBe null
        }

        @Test
        fun `deltaker med arena-import - bruker innsoktDato fra arena`() {
            // Arrange
            val deltaker = lagDeltaker()
            TestRepository.insert(deltaker)
            val arenaDato = LocalDate.of(2024, 3, 15)
            val arenaImport = lagImportertFraArena(
                deltakerId = deltaker.id,
                deltakerVedImport = lagDeltakerVedImport(innsoktDato = arenaDato),
            )
            TestRepository.insertAll(arenaImport)

            // Act
            val resultat = deltakerRepository.getSoktInnDato(deltaker.id)

            // Assert
            resultat shouldBe arenaDato
        }

        @Test
        fun `deltaker med innsok paa felles oppstart - bruker innsokt-dato`() {
            // Arrange
            val ansatt = lagNavAnsatt()
            val enhet = lagNavEnhet()
            val deltaker = lagDeltaker()
            TestRepository.insert(deltaker)
            TestRepository.insertAll(ansatt, enhet)
            val innsoktTidspunkt = LocalDateTime.of(2024, 5, 20, 14, 30)
            val innsok = TestData.lagInnsoktPaaKurs(
                deltakerId = deltaker.id,
                innsokt = innsoktTidspunkt,
                innsoktAv = ansatt.id,
                innsoktAvEnhet = enhet.id,
            )
            TestRepository.insertAll(innsok)

            // Act
            val resultat = deltakerRepository.getSoktInnDato(deltaker.id)

            // Assert
            resultat shouldBe innsoktTidspunkt.toLocalDate()
        }

        @Test
        fun `deltaker med vedtak - bruker vedtak created_at som fallback`() {
            // Arrange
            val deltaker = lagDeltaker()
            val ansatt = lagNavAnsatt()
            val enhet = lagNavEnhet()
            TestRepository.insert(deltaker)
            TestRepository.insertAll(ansatt, enhet)
            val vedtakOpprettet = LocalDateTime.of(2024, 6, 10, 9, 0)
            val vedtak = lagVedtak(
                deltakerVedVedtak = deltaker,
                opprettet = vedtakOpprettet,
                opprettetAv = ansatt,
                opprettetAvEnhet = enhet,
            )
            TestRepository.insertAll(vedtak)

            // Act
            val resultat = deltakerRepository.getSoktInnDato(deltaker.id)

            // Assert
            resultat shouldBe vedtakOpprettet.toLocalDate()
        }

        @Test
        fun `arena-import prioriteres over innsok og vedtak`() {
            // Arrange
            val deltaker = lagDeltaker()
            val ansatt = lagNavAnsatt()
            val enhet = lagNavEnhet()
            TestRepository.insert(deltaker)
            TestRepository.insertAll(ansatt, enhet)

            val arenaDato = LocalDate.of(2024, 1, 1)
            val arenaImport = lagImportertFraArena(
                deltakerId = deltaker.id,
                deltakerVedImport = lagDeltakerVedImport(innsoktDato = arenaDato),
            )
            val innsok = TestData.lagInnsoktPaaKurs(
                deltakerId = deltaker.id,
                innsokt = LocalDateTime.of(2024, 6, 1, 12, 0),
                innsoktAv = ansatt.id,
                innsoktAvEnhet = enhet.id,
            )
            val vedtak = lagVedtak(
                deltakerVedVedtak = deltaker,
                opprettet = LocalDateTime.of(2024, 7, 1, 12, 0),
                opprettetAv = ansatt,
                opprettetAvEnhet = enhet,
            )
            TestRepository.insertAll(arenaImport, innsok, vedtak)

            // Act
            val resultat = deltakerRepository.getSoktInnDato(deltaker.id)

            // Assert — COALESCE prioriterer arena-import først
            resultat shouldBe arenaDato
        }

        @Test
        fun `innsok prioriteres over vedtak naar arena-import mangler`() {
            // Arrange
            val deltaker = lagDeltaker()
            val ansatt = lagNavAnsatt()
            val enhet = lagNavEnhet()
            TestRepository.insert(deltaker)
            TestRepository.insertAll(ansatt, enhet)

            val innsoktTidspunkt = LocalDateTime.of(2024, 5, 15, 10, 0)
            val innsok = TestData.lagInnsoktPaaKurs(
                deltakerId = deltaker.id,
                innsokt = innsoktTidspunkt,
                innsoktAv = ansatt.id,
                innsoktAvEnhet = enhet.id,
            )
            val vedtak = lagVedtak(
                deltakerVedVedtak = deltaker,
                opprettet = LocalDateTime.of(2024, 7, 1, 12, 0),
                opprettetAv = ansatt,
                opprettetAvEnhet = enhet,
            )
            TestRepository.insertAll(innsok, vedtak)

            // Act
            val resultat = deltakerRepository.getSoktInnDato(deltaker.id)

            // Assert — COALESCE prioriterer innsøk over vedtak
            resultat shouldBe innsoktTidspunkt.toLocalDate()
        }
    }

    companion object {
        @RegisterExtension
        val dbExtension = DatabaseTestExtension()

        fun assertDeltakereAreEqual(
            first: Deltaker,
            second: Deltaker,
        ) {
            assertSoftly(first) {
                id shouldBe second.id
                navBruker shouldBe second.navBruker
                startdato shouldBe second.startdato
                sluttdato shouldBe second.sluttdato
                dagerPerUke shouldBe second.dagerPerUke
                deltakelsesprosent shouldBe second.deltakelsesprosent
                bakgrunnsinformasjon shouldBe second.bakgrunnsinformasjon
                deltakelsesinnhold shouldBe second.deltakelsesinnhold
                status.id shouldBe second.status.id
                status.type shouldBe second.status.type
                status.aarsak shouldBe second.status.aarsak
                status.gyldigFra shouldBeCloseTo second.status.gyldigFra
                status.gyldigTil shouldBeCloseTo second.status.gyldigTil
                status.opprettet shouldBeCloseTo second.status.opprettet
                sistEndret shouldBeCloseTo second.sistEndret
            }
        }
    }
}
