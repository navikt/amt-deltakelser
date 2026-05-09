package no.nav.amt.deltaker.bff.deltaker

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.result.shouldBeFailure
import io.kotest.matchers.result.shouldBeSuccess
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.amt.deltaker.bff.model.Deltaker
import no.nav.amt.deltaker.bff.tiltaksarrangor.ArrangorRepository
import no.nav.amt.deltaker.bff.utils.DeltakerTestUtils
import no.nav.amt.deltaker.bff.utils.TestData
import no.nav.amt.deltaker.bff.utils.TestRepository
import no.nav.amt.lib.models.deltaker.DeltakerHistorikk
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.testing.DatabaseTestExtension
import no.nav.amt.lib.testing.shouldBeCloseTo
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.LocalDateTime
import java.time.ZonedDateTime
import java.util.UUID

class DeltakerRepositoryTest {
    val deltakerRepository = DeltakerRepository()
    val arrangorRepository = ArrangorRepository()

    @Nested
    inner class SettKanEndresTests {
        @Test
        fun `deltaker finnes ikke - kaster ikke feil`() {
            shouldNotThrowAny {
                deltakerRepository.settKanEndres(UUID.randomUUID(), true)
            }
        }

        @Test
        fun `deltaker finnes - oppdaterer deltaker`() {
            val deltaker = TestData.lagDeltaker()
            deltaker.kanEndres.shouldBeTrue()
            TestRepository.insert(deltaker)

            deltakerRepository.settKanEndres(deltaker.id, false)

            deltakerRepository
                .get(deltaker.id)
                .shouldBeSuccess()
                .kanEndres
                .shouldBeFalse()
        }
    }

    @Nested
    inner class DisableKanEndresManyTests {
        @Test
        fun `tom liste med ider - kaster ikke feil`() {
            shouldNotThrowAny {
                deltakerRepository.disableKanEndresMany(emptyList())
            }
        }

        @Test
        fun `tom database - kaster ikke feil`() {
            shouldNotThrowAny {
                deltakerRepository.disableKanEndresMany(listOf(UUID.randomUUID()))
            }
        }

        @Test
        fun `deltakere finnes - oppdaterer deltakere`() {
            val firstDeltaker = TestData.lagDeltaker()
            firstDeltaker.kanEndres.shouldBeTrue()
            TestRepository.insert(firstDeltaker)

            val scondDeltaker = TestData.lagDeltaker()
            scondDeltaker.kanEndres.shouldBeTrue()
            TestRepository.insert(scondDeltaker)

            deltakerRepository.disableKanEndresMany(listOf(firstDeltaker.id, scondDeltaker.id))

            deltakerRepository
                .get(firstDeltaker.id)
                .shouldBeSuccess()
                .kanEndres
                .shouldBeFalse()

            deltakerRepository
                .get(scondDeltaker.id)
                .shouldBeSuccess()
                .kanEndres
                .shouldBeFalse()
        }
    }

    @Nested
    inner class GetAntallDeltakereForDeltakerlisteTests {
        val deltakerlisteInTest = TestData.lagDeltakerliste()

        @Test
        fun `skal returnere 0 hvis ingen deltakere`() {
            val antallDeltakere = deltakerRepository.getAntallDeltakereForDeltakerliste(deltakerlisteInTest.id)

            antallDeltakere shouldBe 0
        }

        @Test
        fun `skal returnere antall deltaker hvis deltakerliste inneholder deltakere`() {
            TestRepository.insert(TestData.lagDeltaker(deltakerliste = deltakerlisteInTest))

            val antallDeltakere = deltakerRepository.getAntallDeltakereForDeltakerliste(deltakerlisteInTest.id)

            antallDeltakere shouldBe 1
        }
    }

    @Test
    fun `get - deltaker har flere vedtak, et aktivt - returnerer deltaker med aktivt vedtak`() {
        val baseDeltaker = TestData.lagDeltaker()
        val deltakerInTest = TestData.leggTilHistorikk(baseDeltaker, 2)

        TestRepository.insert(deltakerInTest)

        val deltakerInDb = deltakerRepository.get(baseDeltaker.id).getOrNull()

        assertSoftly(deltakerInDb.shouldNotBeNull().fattetVedtak.shouldNotBeNull()) {
            id shouldBe deltakerInTest.getAlleVedtak().first { it.gyldigTil == null }.id
            fattet shouldNotBe null
            fattetAvNav shouldBe true
        }
    }

    @Nested
    inner class GetTidligereAvsluttedeDeltakelserTests {
        @Test
        fun `getTidligereAvsluttedeDeltakelser - har ingen tidligere deltakelse - returnerer tom liste`() {
            val deltaker = TestData.lagDeltaker(
                status = TestData.lagDeltakerStatus(DeltakerStatus.Type.UTKAST_TIL_PAMELDING),
            )
            TestRepository.insert(deltaker)

            deltakerRepository.getTidligereAvsluttedeDeltakelser(deltaker.id) shouldBe emptyList()
        }

        @Test
        fun `getTidligereAvsluttedeDeltakelser - har aktiv tidligere deltakelse - returnerer tom liste`() {
            val avsluttetDeltaker = TestData.lagDeltaker(
                status = TestData.lagDeltakerStatus(DeltakerStatus.Type.DELTAR),
            )
            TestRepository.insert(avsluttetDeltaker)
            val deltaker = TestData.lagDeltaker(
                deltakerliste = avsluttetDeltaker.deltakerliste,
                navBruker = avsluttetDeltaker.navBruker,
                status = TestData.lagDeltakerStatus(DeltakerStatus.Type.UTKAST_TIL_PAMELDING),
            )
            TestRepository.insert(deltaker)

            deltakerRepository.getTidligereAvsluttedeDeltakelser(deltaker.id) shouldBe emptyList()
        }

        @Test
        fun `getTidligereAvsluttedeDeltakelser - har tidligere avsluttet deltakelse - returnerer id`() {
            val avsluttetDeltaker = TestData.lagDeltaker(
                status = TestData.lagDeltakerStatus(DeltakerStatus.Type.HAR_SLUTTET),
            )
            TestRepository.insert(avsluttetDeltaker)
            val deltaker = TestData.lagDeltaker(
                deltakerliste = avsluttetDeltaker.deltakerliste,
                navBruker = avsluttetDeltaker.navBruker,
                status = TestData.lagDeltakerStatus(DeltakerStatus.Type.UTKAST_TIL_PAMELDING),
            )
            TestRepository.insert(deltaker)

            deltakerRepository.getTidligereAvsluttedeDeltakelser(deltaker.id) shouldBe listOf(avsluttetDeltaker.id)
        }

        @Test
        fun `getTidligereAvsluttedeDeltakelser - har tidligere avsluttet deltakelse, er avsluttet deltakelse - returnerer id`() {
            val avsluttetDeltaker = TestData.lagDeltaker(
                status = TestData.lagDeltakerStatus(DeltakerStatus.Type.HAR_SLUTTET),
            )
            TestRepository.insert(avsluttetDeltaker)
            val deltaker = TestData.lagDeltaker(
                deltakerliste = avsluttetDeltaker.deltakerliste,
                navBruker = avsluttetDeltaker.navBruker,
                status = TestData.lagDeltakerStatus(DeltakerStatus.Type.HAR_SLUTTET),
            )
            TestRepository.insert(deltaker)

            deltakerRepository.getTidligereAvsluttedeDeltakelser(deltaker.id) shouldBe listOf(avsluttetDeltaker.id)
        }
    }

    @Test
    fun `getUtdaterteKladder - finnes en utdatert kladd - returnerer utdatert kladd`() {
        val kladd = TestData.lagDeltakerKladd(sistEndret = LocalDateTime.now().minusDays(2))
        TestRepository.insert(kladd)
        val utdatertKladd = TestData.lagDeltakerKladd(sistEndret = LocalDateTime.now().minusDays(20))
        TestRepository.insert(utdatertKladd)

        val utdaterteKladder = deltakerRepository.getUtdaterteKladder(LocalDateTime.now().minusWeeks(2))

        utdaterteKladder.size shouldBe 1
        utdaterteKladder.first().id shouldBe utdatertKladd.id
    }

    @Test
    fun `oppdaterSistBesokt - oppdaterer sistBesokt - skal ikke feile`() {
        val deltaker = TestData.lagDeltaker()
        TestRepository.insert(deltaker)

        val sistBesokt = ZonedDateTime.now()

        deltakerRepository.oppdaterSistBesokt(deltaker.id, sistBesokt)

        val lagretSistBesokt = TestRepository.getDeltakerSistBesokt(deltaker.id)
        lagretSistBesokt.shouldNotBeNull()
        lagretSistBesokt shouldBeCloseTo sistBesokt
    }

    @Nested
    inner class GetForDeltakerlisteTests {
        @Test
        fun `getForDeltakerliste - flere deltakere pa samme liste - returnerer liste med deltakere`() {
            val deltakerliste = TestData.lagDeltakerliste()
            val deltakere = (0..10).map {
                TestData.lagDeltaker(
                    deltakerliste = deltakerliste,
                    status = TestData.lagDeltakerStatus(DeltakerStatus.Type.DELTAR),
                )
            }
            deltakere.forEach { TestRepository.insert(it) }

            val deltakereFraDb = deltakerRepository.getForDeltakerliste(deltakerliste.id)

            deltakereFraDb.size shouldBe deltakere.size
            deltakere
                .sortedBy { it.id }
                .zip(deltakereFraDb.sortedBy { it.id })
                .forEach { DeltakerTestUtils.sammenlignDeltakere(it.first, it.second) }
        }

        @Test
        fun `getForDeltakerliste - deltakerliste finnes ikke - returnerer tom liste`() {
            val deltakereFraDb = deltakerRepository.getForDeltakerliste(UUID.randomUUID())

            deltakereFraDb shouldBe emptyList()
        }
    }

    @Nested
    inner class GetManyByIdList {
        @Test
        fun `getMany - ingen deltakere - returnerer tom liste`() {
            deltakerRepository.getMany(emptyList()).shouldBeEmpty()
        }

        @Test
        fun `getMany - henter flere deltakere`() {
            val deltaker1 = TestData.lagDeltaker()
            val deltaker2 = TestData.lagDeltaker()

            TestRepository.insert(deltaker1)
            TestRepository.insert(deltaker2)

            deltakerRepository.getMany(listOf(deltaker1.id, deltaker2.id)) shouldHaveSize 2
        }
    }

    @Nested
    inner class GetManyByPersonIdentAndDeltakerlisteIdTests {
        @Test
        fun `getMany - ingen deltakere - returnerer tom liste`() {
            deltakerRepository.getMany("~personident~", UUID.randomUUID()).shouldBeEmpty()
        }

        @Test
        fun `getMany - henter flere deltakere`() {
            val arrangor = no.nav.amt.lib.testing.utils.TestData
                .lagArrangor()
            arrangorRepository.upsert(arrangor)

            val deltakerliste = TestData.lagDeltakerliste(arrangor = arrangor)

            val deltakerInTest = TestData.lagDeltaker(deltakerliste = deltakerliste)
            TestRepository.insert(deltakerInTest)

            deltakerRepository
                .getMany(
                    personident = deltakerInTest.navBruker.personident,
                    deltakerlisteId = deltakerliste.id,
                ).shouldNotBeEmpty()
        }
    }

    @Nested
    inner class GetManyByPersonIdentTests {
        @Test
        fun `getMany - ingen deltakere - returnerer tom liste`() {
            deltakerRepository.getMany("~personident~").shouldBeEmpty()
        }

        @Test
        fun `getMany - henter flere deltakere`() {
            val deltakerInTest = TestData.lagDeltaker()
            TestRepository.insert(deltakerInTest)

            deltakerRepository
                .getMany(personident = deltakerInTest.navBruker.personident)
                .shouldNotBeEmpty()
        }
    }

    @Nested
    inner class GetKladdForDeltakerlisteTests {
        @Test
        fun `getKladderForDeltakerliste - ingen deltakere - returnerer tom liste`() {
            deltakerRepository
                .getKladderForDeltakerliste(UUID.randomUUID())
                .shouldBeEmpty()
        }

        @Test
        fun `getKladderForDeltakerliste - henter flere deltakere`() {
            val arrangor = no.nav.amt.lib.testing.utils.TestData
                .lagArrangor()
            arrangorRepository.upsert(arrangor)

            val deltakerliste = TestData.lagDeltakerliste(arrangor = arrangor)

            val deltakerInTest = TestData.lagDeltaker(
                status = TestData.lagDeltakerStatus(DeltakerStatus.Type.KLADD),
                deltakerliste = deltakerliste,
            )
            TestRepository.insert(deltakerInTest)

            deltakerRepository
                .getKladderForDeltakerliste(deltakerliste.id)
                .shouldNotBeEmpty()
        }

        @Test
        fun `getKladdForDeltakerliste - ingen deltakere - returnerer null`() {
            deltakerRepository
                .getKladdForDeltakerliste(
                    deltakerlisteId = UUID.randomUUID(),
                    personident = "~personindent~",
                ).shouldBeFailure()
        }

        @Test
        fun `getKladdForDeltakerliste - henter deltaker`() {
            val arrangor = no.nav.amt.lib.testing.utils.TestData
                .lagArrangor()
            arrangorRepository.upsert(arrangor)

            val deltakerliste = TestData.lagDeltakerliste(arrangor = arrangor)

            val deltakerInTest = TestData.lagDeltaker(
                status = TestData.lagDeltakerStatus(DeltakerStatus.Type.KLADD),
                deltakerliste = deltakerliste,
            )
            TestRepository.insert(deltakerInTest)

            deltakerRepository
                .getKladdForDeltakerliste(
                    deltakerlisteId = deltakerliste.id,
                    personident = deltakerInTest.navBruker.personident,
                ).shouldBeSuccess()
        }
    }

    companion object {
        @RegisterExtension
        val dbExtension = DatabaseTestExtension()

        private fun Deltaker.getAlleVedtak() = historikk
            .filterIsInstance<DeltakerHistorikk.Vedtak>()
            .map { it.vedtak }
    }
}
