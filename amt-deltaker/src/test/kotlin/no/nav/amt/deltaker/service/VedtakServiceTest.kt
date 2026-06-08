package no.nav.amt.deltaker.service

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.test.runTest
import no.nav.amt.deltaker.model.Deltakerliste
import no.nav.amt.deltaker.navansatt.NavAnsattRepository
import no.nav.amt.deltaker.navenhet.NavEnhetRepository
import no.nav.amt.deltaker.repository.VedtakRepository
import no.nav.amt.deltaker.utils.data.TestData
import no.nav.amt.deltaker.utils.data.TestRepository
import no.nav.amt.lib.models.deltaker.Deltakelsesinnhold
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltaker.Vedtak
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import no.nav.amt.lib.models.person.NavBruker
import no.nav.amt.lib.testing.DatabaseTestExtension
import no.nav.amt.lib.testing.shouldBeCloseTo
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.LocalDateTime
import java.util.UUID

class VedtakServiceTest {
    private val vedtakRepository = VedtakRepository()
    private val vedtakService = VedtakService(vedtakRepository)
    private val navEnhetRepository = NavEnhetRepository()
    private val navAnsattRepository = NavAnsattRepository()

    companion object {
        @RegisterExtension
        val dbExtension = DatabaseTestExtension()
    }

    @Nested
    inner class InnbyggerFattVedtakTests {
        val deltaker = TestData.lagDeltaker()

        @Test
        fun `innbyggerFattVedtak - ikke-fattet vedtak finnes -  fattes`() {
            val vedtakInTest = TestData.lagVedtak(deltakerVedVedtak = deltaker)
            insertVedtak(vedtakInTest)

            runTest {
                vedtakService.innbyggerFattVedtak(deltaker.id)
                val fattetVedtak = vedtakRepository.getForDeltaker(deltaker.id).shouldNotBeNull()

                assertSoftly(fattetVedtak.shouldNotBeNull()) {
                    id shouldBe vedtakInTest.id
                    fattet shouldNotBe null
                    fattetAvNav shouldBe false
                }
            }
        }

        @Test
        fun `innbyggerFattVedtak - vedtaket er fattet - kaster feil`() {
            val vedtakInTest = TestData.lagVedtak(fattet = LocalDateTime.now(), deltakerVedVedtak = deltaker)
            insertVedtak(vedtakInTest)

            val thrown = shouldThrow<IllegalArgumentException> {
                vedtakService.innbyggerFattVedtak(deltaker.id)
            }

            thrown.message shouldBe "Deltaker-id ${deltaker.id} har allerede et fattet vedtak"
        }

        @Test
        fun `innbyggerFattVedtak - vedtak er allerede avbrutt - kaster feil`() {
            val vedtakInTest = TestData.lagVedtak(gyldigTil = LocalDateTime.now(), deltakerVedVedtak = deltaker)
            insertVedtak(vedtakInTest)

            val thrown = shouldThrow<IllegalStateException> {
                vedtakService.innbyggerFattVedtak(deltaker.id)
            }

            thrown.message shouldBe "Deltaker-id ${deltaker.id} har et vedtak som er avbrutt"
        }

        @Test
        fun `innbyggerFattVedtak - deltaker har ingen vedtak - kaster feil`() {
            val thrown = shouldThrow<IllegalStateException> {
                vedtakService.innbyggerFattVedtak(deltaker.id)
            }

            thrown.message shouldBe "Deltaker-id ${deltaker.id} har ingen vedtak"
        }
    }

    @Nested
    inner class OpprettEllerOppdaterVedtakTests {
        val endretAvAnsatt = no.nav.amt.lib.testing.utils.TestData
            .lagNavAnsatt()
        val endretAvEnhet = no.nav.amt.lib.testing.utils.TestData
            .lagNavEnhet()

        @BeforeEach
        fun setup() = TestRepository.insertAll(endretAvAnsatt, endretAvEnhet)

        @Test
        fun `oppdaterEllerOpprettVedtak - nytt vedtak - opprettes`() {
            val deltaker = lagDeltakerKladd()
            TestRepository.insert(deltaker)

            runTest {
                val vedtak = vedtakService.opprettEllerOppdaterVedtak(
                    deltaker = deltaker.toDeltakerVedVedtak(),
                    endretAv = endretAvAnsatt,
                    endretAvEnhet = endretAvEnhet,
                    fattetAvNav = false,
                    fattetDato = null,
                )

                assertSoftly(vedtak.shouldNotBeNull()) {
                    deltakerVedVedtak shouldBe deltaker.toDeltakerVedVedtak()
                    opprettetAv shouldBe endretAvAnsatt.id
                    opprettetAvEnhet shouldBe endretAvEnhet.id
                    fattet shouldBe null
                    fattetAvNav shouldBe false
                    deltakerId shouldBe deltaker.id
                }
            }
        }

        @Test
        fun `oppdaterEllerOpprettVedtak - vedtak finnes, endres - oppdateres`() {
            val vedtak = TestData.lagVedtak()
            insertVedtak(vedtak)

            val oppdatertDeltaker = lagDeltakerKladd(id = vedtak.deltakerId)
                .copy(bakgrunnsinformasjon = "Endret bakgrunn")

            runTest {
                val oppdatertVedtak = vedtakService.opprettEllerOppdaterVedtak(
                    deltaker = oppdatertDeltaker.toDeltakerVedVedtak(),
                    endretAv = endretAvAnsatt,
                    endretAvEnhet = endretAvEnhet,
                    fattetAvNav = false,
                    fattetDato = null,
                )

                assertSoftly(oppdatertVedtak) {
                    deltakerVedVedtak shouldBe oppdatertDeltaker.toDeltakerVedVedtak()
                    sistEndretAv shouldBe endretAvAnsatt.id
                    sistEndretAvEnhet shouldBe endretAvEnhet.id
                    deltakerId shouldBe vedtak.deltakerId
                }
            }
        }
    }

    @Nested
    inner class AvbrytVedtakTests {
        val deltaker = TestData.lagDeltaker()
        val avbruttAvAnsatt = no.nav.amt.lib.testing.utils.TestData
            .lagNavAnsatt()
        val avbryttAvEnhet = no.nav.amt.lib.testing.utils.TestData
            .lagNavEnhet()

        @BeforeEach
        fun setup() = TestRepository.insertAll(avbruttAvAnsatt, avbryttAvEnhet)

        @Test
        fun `avbrytVedtak - vedtak kan avbrytes - avbrytes`() {
            val vedtakInTest = TestData.lagVedtak(deltakerVedVedtak = deltaker)
            insertVedtak(vedtakInTest)

            runTest {
                val avbruttVedtak = vedtakService.avbrytVedtak(
                    deltakerId = deltaker.id,
                    avbruttAv = avbruttAvAnsatt,
                    avbruttAvNavEnhet = avbryttAvEnhet,
                )

                assertSoftly(avbruttVedtak.shouldNotBeNull()) {
                    id shouldBe vedtakInTest.id
                    gyldigTil shouldBeCloseTo LocalDateTime.now()
                    fattet shouldBe null
                    sistEndretAv shouldBe avbruttAvAnsatt.id
                    sistEndretAvEnhet shouldBe avbryttAvEnhet.id
                }
            }
        }

        @Test
        fun `avbrytVedtak - vedtak er fattet og kan ikke avbrytes - feiler`() {
            val vedtakInTest = TestData.lagVedtak(fattet = LocalDateTime.now(), deltakerVedVedtak = deltaker)
            insertVedtak(vedtakInTest)

            val thrown = shouldThrow<IllegalArgumentException> {
                vedtakService.avbrytVedtak(
                    deltakerId = deltaker.id,
                    avbruttAv = avbruttAvAnsatt,
                    avbruttAvNavEnhet = avbryttAvEnhet,
                )
            }

            thrown.message shouldBe "Deltaker-id ${deltaker.id} har allerede et fattet vedtak"
        }

        @Test
        fun `avbrytVedtak - vedtak er allerede avbrutt - feiler`() {
            val vedtakInTest = TestData.lagVedtak(gyldigTil = LocalDateTime.now(), deltakerVedVedtak = deltaker)
            insertVedtak(vedtakInTest)

            val thrown = shouldThrow<IllegalStateException> {
                vedtakService.avbrytVedtak(
                    deltakerId = deltaker.id,
                    avbruttAv = avbruttAvAnsatt,
                    avbruttAvNavEnhet = avbryttAvEnhet,
                )
            }

            thrown.message shouldBe "Deltaker-id ${deltaker.id} har et vedtak som er avbrutt"
        }

        @Test
        fun `avbrytVedtak - deltaker har ingen vedtak - feiler`() {
            val thrown = shouldThrow<IllegalStateException> {
                vedtakService.avbrytVedtak(
                    deltakerId = deltaker.id,
                    avbruttAv = avbruttAvAnsatt,
                    avbruttAvNavEnhet = avbryttAvEnhet,
                )
            }

            thrown.message shouldBe "Deltaker-id ${deltaker.id} har ingen vedtak"
        }
    }

    @Nested
    inner class NavFattVedtakTests {
        @Test
        fun `navFattVedtak - vedtak finnes, fattes av Nav - oppdateres`() {
            val vedtak = TestData.lagVedtak()
            insertVedtak(vedtak)

            val oppdatertDeltaker = lagDeltakerKladd(id = vedtak.deltakerId).copy(bakgrunnsinformasjon = "Endret bakgrunn")
            val endretAvAnsatt = no.nav.amt.lib.testing.utils.TestData
                .lagNavAnsatt()
            val endretAvEnhet = no.nav.amt.lib.testing.utils.TestData
                .lagNavEnhet()
            TestRepository.insertAll(endretAvAnsatt, endretAvEnhet)

            runTest {
                val oppdatertVedtak = vedtakService.navFattVedtak(
                    deltaker = oppdatertDeltaker,
                    endretAv = endretAvAnsatt,
                    endretAvEnhet = endretAvEnhet,
                )

                assertSoftly(oppdatertVedtak.shouldNotBeNull()) {
                    deltakerVedVedtak shouldBe oppdatertDeltaker.toDeltakerVedVedtak()
                    sistEndretAv shouldBe endretAvAnsatt.id
                    sistEndretAvEnhet shouldBe endretAvEnhet.id
                    fattet shouldBeCloseTo LocalDateTime.now()
                    fattetAvNav shouldBe true
                    deltakerId shouldBe vedtak.deltakerId
                }
            }
        }

        @Test
        fun `fatter vedtak`() {
            with(DeltakerContext()) {
                medVedtak(fattet = false)
                withTiltakstype(Tiltakskode.GRUPPE_ARBEIDSMARKEDSOPPLAERING)

                val fattetVedtak = vedtakService.navFattVedtak(
                    deltaker = deltaker,
                    endretAv = veileder,
                    endretAvEnhet = navEnhet,
                )

                fattetVedtak.shouldNotBeNull()

                val deltakersVedtak = vedtakRepository.getForDeltaker(deltaker.id).shouldNotBeNull()
                deltakersVedtak shouldBe fattetVedtak
            }
        }

        @Test
        fun `mangler vedtak - kaster feil`() {
            with(DeltakerContext()) {
                withTiltakstype(Tiltakskode.GRUPPE_ARBEIDSMARKEDSOPPLAERING)
                val thrown = shouldThrow<IllegalStateException> {
                    vedtakService.navFattVedtak(
                        deltaker = deltaker,
                        endretAv = veileder,
                        endretAvEnhet = navEnhet,
                    )
                }

                thrown.message shouldBe "Deltaker ${deltaker.id} mangler et vedtak som kan fattes"
            }
        }

        @Test
        fun `navFattVedtak - vedtak allerede fattet - fatter ikke nytt vedtak`() {
            with(DeltakerContext()) {
                medVedtak(fattet = true)
                withTiltakstype(Tiltakskode.GRUPPE_ARBEIDSMARKEDSOPPLAERING)

                shouldNotThrowAny {
                    vedtakService.navFattVedtak(
                        deltaker = deltaker,
                        endretAv = veileder,
                        endretAvEnhet = navEnhet,
                    )
                }

                val deltakersVedtak = vedtakRepository.getForDeltaker(deltaker.id).shouldNotBeNull()
                DeltakerTestUtils.sammenlignVedtak(deltakersVedtak, vedtak)
            }
        }
    }

    @Nested
    inner class GodkjentOkonomiFattVedtakTests {
        @Test
        fun `godkjentOkonomiFattVedtak - ikke-fattet vedtak finnes - godkjennes og fattes`() {
            val deltaker = lagDeltakerKladd()
            val vedtakInTest = TestData.lagVedtak(
                deltakerVedVedtak = deltaker,
                fattet = null,
                fattetAvNav = false,
            )
            insertVedtak(vedtakInTest)

            val deltakerSomGodkjennes = deltaker.copy(bakgrunnsinformasjon = "Godkjent økonomi")

            runTest {
                vedtakService.godkjentOkonomiFattVedtak(deltakerSomGodkjennes)

                val fattetVedtak = vedtakRepository.getForDeltaker(deltaker.id).shouldNotBeNull()

                assertSoftly(fattetVedtak) {
                    id shouldBe vedtakInTest.id
                    deltakerId shouldBe deltaker.id
                    fattet shouldNotBe null
                    fattetAvNav shouldBe true
                    deltakerVedVedtak shouldBe deltakerSomGodkjennes.toDeltakerVedVedtak()
                }
            }
        }

        @Test
        fun `godkjentOkonomiFattVedtak - deltaker har ingen vedtak - kaster feil`() {
            val deltaker = lagDeltakerKladd()

            val thrown = shouldThrow<IllegalStateException> {
                vedtakService.godkjentOkonomiFattVedtak(deltaker)
            }

            thrown.message shouldBe "Deltaker-id ${deltaker.id} har ingen vedtak"
        }

        @Test
        fun `godkjentOkonomiFattVedtak - vedtak er allerede fattet - kaster feil`() {
            val deltaker = lagDeltakerKladd()
            val vedtakInTest = TestData.lagVedtak(fattet = LocalDateTime.now(), deltakerVedVedtak = deltaker)
            insertVedtak(vedtakInTest)

            val thrown = shouldThrow<IllegalArgumentException> {
                vedtakService.godkjentOkonomiFattVedtak(deltaker)
            }

            thrown.message shouldBe "Deltaker-id ${deltaker.id} har allerede et fattet vedtak"
        }

        @Test
        fun `godkjentOkonomiFattVedtak - vedtak er avbrutt - kaster feil`() {
            val deltaker = lagDeltakerKladd()
            val vedtakInTest = TestData.lagVedtak(gyldigTil = LocalDateTime.now(), deltakerVedVedtak = deltaker)
            insertVedtak(vedtakInTest)

            val thrown = shouldThrow<IllegalStateException> {
                vedtakService.godkjentOkonomiFattVedtak(deltaker)
            }

            thrown.message shouldBe "Deltaker-id ${deltaker.id} har et vedtak som er avbrutt"
        }
    }

    private fun insertVedtak(vedtak: Vedtak) {
        val navEnhet = no.nav.amt.lib.testing.utils.TestData
            .lagNavEnhet(vedtak.opprettetAvEnhet)
        navEnhetRepository.upsert(navEnhet)

        navAnsattRepository.upsert(
            no.nav.amt.lib.testing.utils.TestData.lagNavAnsatt(
                id = vedtak.opprettetAv,
                navEnhetId = vedtak.opprettetAvEnhet,
            ),
        )
        TestRepository.insert(lagDeltakerKladd(id = vedtak.deltakerId))
        vedtakRepository.upsert(vedtak)
    }

    private fun lagDeltakerKladd(
        id: UUID = UUID.randomUUID(),
        navBruker: NavBruker = no.nav.amt.lib.testing.utils.TestData
            .lagNavBruker(),
        deltakerliste: Deltakerliste = TestData.lagDeltakerliste(),
    ) = TestData.lagDeltaker(
        id = id,
        navBruker = navBruker,
        deltakerliste = deltakerliste,
        startdato = null,
        sluttdato = null,
        dagerPerUke = null,
        deltakelsesprosent = null,
        bakgrunnsinformasjon = null,
        innhold = Deltakelsesinnhold(deltakerliste.tiltakstype.innhold?.ledetekst, emptyList()),
        status = TestData.lagDeltakerStatus(statusType = DeltakerStatus.Type.KLADD),
    )
}
