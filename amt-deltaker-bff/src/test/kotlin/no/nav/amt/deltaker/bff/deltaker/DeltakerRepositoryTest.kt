package no.nav.amt.deltaker.bff.deltaker

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.result.shouldBeSuccess
import io.kotest.matchers.shouldBe
import no.nav.amt.deltaker.bff.tiltaksarrangor.ArrangorRepository
import no.nav.amt.deltaker.bff.utils.TestData
import no.nav.amt.deltaker.bff.utils.TestRepository
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.testing.DatabaseTestExtension
import no.nav.amt.lib.testing.shouldBeCloseTo
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.ZonedDateTime
import java.util.UUID

class DeltakerRepositoryTest {
    val deltakerRepository = DeltakerRepository()
    val arrangorRepository = ArrangorRepository()

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
            val firstDeltaker = TestData.lagDeltakerOld()
            firstDeltaker.kanEndres.shouldBeTrue()
            TestRepository.insert(firstDeltaker)

            val scondDeltaker = TestData.lagDeltakerOld()
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
            TestRepository.insert(TestData.lagDeltakerOld(deltakerliste = deltakerlisteInTest))

            val antallDeltakere = deltakerRepository.getAntallDeltakereForDeltakerliste(deltakerlisteInTest.id)

            antallDeltakere shouldBe 1
        }
    }

    @Nested
    inner class GetTidligereAvsluttedeDeltakelserTests {
        @Test
        fun `getTidligereAvsluttedeDeltakelser - har ingen tidligere deltakelse - returnerer tom liste`() {
            val deltaker = TestData.lagDeltakerOld(
                status = TestData.lagDeltakerStatus(DeltakerStatus.Type.UTKAST_TIL_PAMELDING),
            )
            TestRepository.insert(deltaker)

            deltakerRepository.getTidligereAvsluttedeDeltakelser(deltaker.id) shouldBe emptyList()
        }

        @Test
        fun `getTidligereAvsluttedeDeltakelser - har aktiv tidligere deltakelse - returnerer tom liste`() {
            val avsluttetDeltaker = TestData.lagDeltakerOld(
                status = TestData.lagDeltakerStatus(DeltakerStatus.Type.DELTAR),
            )
            TestRepository.insert(avsluttetDeltaker)
            val deltaker = TestData.lagDeltakerOld(
                deltakerliste = avsluttetDeltaker.deltakerliste,
                navBruker = avsluttetDeltaker.navBruker,
                status = TestData.lagDeltakerStatus(DeltakerStatus.Type.UTKAST_TIL_PAMELDING),
            )
            TestRepository.insert(deltaker)

            deltakerRepository.getTidligereAvsluttedeDeltakelser(deltaker.id) shouldBe emptyList()
        }

        @Test
        fun `getTidligereAvsluttedeDeltakelser - har tidligere avsluttet deltakelse - returnerer id`() {
            val avsluttetDeltaker = TestData.lagDeltakerOld(
                status = TestData.lagDeltakerStatus(DeltakerStatus.Type.HAR_SLUTTET),
            )
            TestRepository.insert(avsluttetDeltaker)
            val deltaker = TestData.lagDeltakerOld(
                deltakerliste = avsluttetDeltaker.deltakerliste,
                navBruker = avsluttetDeltaker.navBruker,
                status = TestData.lagDeltakerStatus(DeltakerStatus.Type.UTKAST_TIL_PAMELDING),
            )
            TestRepository.insert(deltaker)

            deltakerRepository.getTidligereAvsluttedeDeltakelser(deltaker.id) shouldBe listOf(avsluttetDeltaker.id)
        }

        @Test
        fun `getTidligereAvsluttedeDeltakelser - har tidligere avsluttet deltakelse, er avsluttet deltakelse - returnerer id`() {
            val avsluttetDeltaker = TestData.lagDeltakerOld(
                status = TestData.lagDeltakerStatus(DeltakerStatus.Type.HAR_SLUTTET),
            )
            TestRepository.insert(avsluttetDeltaker)
            val deltaker = TestData.lagDeltakerOld(
                deltakerliste = avsluttetDeltaker.deltakerliste,
                navBruker = avsluttetDeltaker.navBruker,
                status = TestData.lagDeltakerStatus(DeltakerStatus.Type.HAR_SLUTTET),
            )
            TestRepository.insert(deltaker)

            deltakerRepository.getTidligereAvsluttedeDeltakelser(deltaker.id) shouldBe listOf(avsluttetDeltaker.id)
        }
    }

    @Test
    fun `oppdaterSistBesokt - oppdaterer sistBesokt - skal ikke feile`() {
        val deltaker = TestData.lagDeltakerOld()
        TestRepository.insert(deltaker)

        val sistBesokt = ZonedDateTime.now()

        deltakerRepository.oppdaterSistBesokt(deltaker.id, sistBesokt)

        val lagretSistBesokt = TestRepository.getDeltakerSistBesokt(deltaker.id)
        lagretSistBesokt.shouldNotBeNull()
        lagretSistBesokt shouldBeCloseTo sistBesokt
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

            val deltakerInTest = TestData.lagDeltakerOld(deltakerliste = deltakerliste)
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
            val deltakerInTest = TestData.lagDeltakerOld()
            TestRepository.insert(deltakerInTest)

            deltakerRepository
                .getMany(personident = deltakerInTest.navBruker.personident)
                .shouldNotBeEmpty()
        }
    }

    companion object {
        @RegisterExtension
        val dbExtension = DatabaseTestExtension()
    }
}
