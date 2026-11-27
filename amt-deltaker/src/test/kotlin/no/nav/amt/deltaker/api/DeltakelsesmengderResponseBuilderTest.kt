package no.nav.amt.deltaker.api

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import no.nav.amt.deltaker.api.response.DeltakerResponseBuilder
import no.nav.amt.deltaker.utils.data.TestData.lagDeltaker
import no.nav.amt.deltaker.utils.data.TestData.lagDeltakerEndring
import no.nav.amt.deltaker.utils.data.TestData.lagVedtak
import no.nav.amt.internapi.deltaker.response.DeltakelsesmengdeResponse
import no.nav.amt.lib.models.deltaker.DeltakerEndring
import no.nav.amt.lib.models.deltaker.DeltakerHistorikk
import no.nav.amt.lib.models.deltaker.deltakelsesmengde.Deltakelsesmengde.Companion.FALLBACK_DELTAKELSESPROSENT
import no.nav.amt.lib.models.person.NavAnsatt
import no.nav.amt.lib.models.person.NavEnhet
import no.nav.amt.lib.testing.utils.TestData
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

class DeltakelsesmengderResponseBuilderTest {
    private val deltakerResponseBuilder = DeltakerResponseBuilder(
        navAnsattService = mockk(),
        navEnhetService = mockk(),
        digitalBrukerService = mockk(),
        deltakerHistorikkService = mockk(),
        forslagRepository = mockk(),
        deltakerLaaseService = mockk(),
        vurderingRepository = mockk(),
        deltakerRepository = mockk(),
    )

    @Test
    fun `tom historikk gir null for deltakelsesmengder`() {
        // Arrange
        val deltaker = lagDeltaker(
            startdato = LocalDate.now(),
            sluttdato = LocalDate.now().plusMonths(3),
        )

        // Act
        val response = deltakerResponseBuilder.buildDeltakelsesmengderResponse(deltaker, emptyList())

        // Assert
        response shouldBe null
    }

    @Test
    fun `vedtak med prosent eller dager bevarer perioden i responsen`() {
        // Arrange
        val navAnsatt = TestData.lagNavAnsatt()
        val navEnhet = TestData.lagNavEnhet()
        val startdato = LocalDate.now().minusMonths(1)

        listOf(null to 3F, 80F to null).forEach { (prosent, dager) ->
            val deltaker = lagDeltaker(
                navBruker = TestData.lagNavBruker(navVeilederId = navAnsatt.id, navEnhetId = navEnhet.id),
                startdato = startdato,
                sluttdato = startdato.plusMonths(3),
                deltakelsesprosent = prosent,
                dagerPerUke = dager,
            )
            val vedtak = lagVedtak(
                deltakerVedVedtak = deltaker,
                fattet = startdato.atStartOfDay(),
                opprettetAv = navAnsatt,
                opprettetAvEnhet = navEnhet,
            )
            // Act
            val response = deltakerResponseBuilder.buildDeltakelsesmengderResponse(
                deltaker,
                listOf(DeltakerHistorikk.Vedtak(vedtak)),
            )

            // Assert
            assertSoftly(response.shouldNotBeNull()) {
                nesteDeltakelsesmengde shouldBe null
                sisteDeltakelsesmengde shouldBe DeltakelsesmengdeResponse(prosent, dager, startdato)
            }
        }
    }

    @Test
    fun `vedtak uten prosent og dager gir null for deltakelsesmengder`() {
        // Arrange
        val navAnsatt = TestData.lagNavAnsatt()
        val navEnhet = TestData.lagNavEnhet()
        val startdato = LocalDate.now().minusMonths(1)
        val deltaker = lagDeltaker(
            navBruker = TestData.lagNavBruker(navVeilederId = navAnsatt.id, navEnhetId = navEnhet.id),
            startdato = startdato,
            sluttdato = startdato.plusMonths(3),
            deltakelsesprosent = null,
            dagerPerUke = null,
        )
        val vedtak = lagVedtak(
            deltakerVedVedtak = deltaker,
            fattet = startdato.atStartOfDay(),
            opprettetAv = navAnsatt,
            opprettetAvEnhet = navEnhet,
        )
        // Act
        val response = deltakerResponseBuilder.buildDeltakelsesmengderResponse(
            deltaker,
            listOf(DeltakerHistorikk.Vedtak(vedtak)),
        )

        // Assert
        response shouldBe null
    }

    @Test
    fun `fremtidig periode uten prosent beholdes`() {
        // Arrange
        val navAnsatt = TestData.lagNavAnsatt()
        val navEnhet = TestData.lagNavEnhet()
        val startdato = LocalDate.now().minusMonths(1)
        val gyldigFra = LocalDate.now().plusMonths(1)
        val deltaker = lagDeltaker(
            navBruker = TestData.lagNavBruker(navVeilederId = navAnsatt.id, navEnhetId = navEnhet.id),
            startdato = startdato,
            sluttdato = startdato.plusMonths(3),
            deltakelsesprosent = 80F,
            dagerPerUke = 4F,
        )
        val vedtak = lagVedtak(
            deltakerVedVedtak = deltaker,
            fattet = startdato.atStartOfDay(),
            opprettetAv = navAnsatt,
            opprettetAvEnhet = navEnhet,
        )

        listOf(3F, null).forEach { dager ->
            val endring = endreDeltakelsesmengde(
                deltakerId = deltaker.id,
                navAnsatt = navAnsatt,
                navEnhet = navEnhet,
                deltakelsesprosent = null,
                dagerPerUke = dager,
                gyldigFra = gyldigFra,
                endret = LocalDateTime.now(),
            )
            // Act
            val response = deltakerResponseBuilder.buildDeltakelsesmengderResponse(
                deltaker,
                listOf(DeltakerHistorikk.Vedtak(vedtak), endring),
            )

            // Assert
            assertSoftly(response.shouldNotBeNull()) {
                nesteDeltakelsesmengde shouldBe DeltakelsesmengdeResponse(null, dager, gyldigFra)
                sisteDeltakelsesmengde shouldBe nesteDeltakelsesmengde
            }
        }
    }

    @Test
    fun `kun gjeldende vedtak - sisteDeltakelsesmengde settes, nesteDeltakelsesmengde er null`() {
        // Arrange
        val navAnsatt = TestData.lagNavAnsatt()
        val navEnhet = TestData.lagNavEnhet()
        val startdato = LocalDate.now().minusMonths(1)
        val deltaker = lagDeltaker(
            navBruker = TestData.lagNavBruker(navVeilederId = navAnsatt.id, navEnhetId = navEnhet.id),
            startdato = startdato,
            sluttdato = startdato.plusMonths(3),
            deltakelsesprosent = 80F,
            dagerPerUke = 4F,
        )
        val vedtak = lagVedtak(
            deltakerVedVedtak = deltaker,
            fattet = startdato.atStartOfDay(),
            opprettetAv = navAnsatt,
            opprettetAvEnhet = navEnhet,
        )

        // Act
        val response = deltakerResponseBuilder.buildDeltakelsesmengderResponse(
            deltaker,
            listOf(DeltakerHistorikk.Vedtak(vedtak)),
        )

        // Assert
        assertSoftly(response.shouldNotBeNull()) {
            nesteDeltakelsesmengde shouldBe null
            sisteDeltakelsesmengde shouldBe DeltakelsesmengdeResponse(
                deltakelsesprosent = 80F,
                dagerPerUke = 4F,
                gyldigFra = startdato,
            )
        }
    }

    @Test
    fun `gjeldende og fremtidig endring - nesteDeltakelsesmengde mapper fremtidig, siste mapper fremtidig (lastOrNull)`() {
        // Arrange
        val navAnsatt = TestData.lagNavAnsatt()
        val navEnhet = TestData.lagNavEnhet()
        val startdato = LocalDate.now().minusMonths(1)
        val deltaker = lagDeltaker(
            navBruker = TestData.lagNavBruker(navVeilederId = navAnsatt.id, navEnhetId = navEnhet.id),
            startdato = startdato,
            sluttdato = startdato.plusMonths(6),
            deltakelsesprosent = FALLBACK_DELTAKELSESPROSENT,
            dagerPerUke = 5F,
        )
        val vedtak = lagVedtak(
            deltakerVedVedtak = deltaker,
            fattet = startdato.atStartOfDay(),
            opprettetAv = navAnsatt,
            opprettetAvEnhet = navEnhet,
        )
        val fremtidigGyldigFra = LocalDate.now().plusDays(7)
        val fremtidig = endreDeltakelsesmengde(
            deltakerId = deltaker.id,
            navAnsatt = navAnsatt,
            navEnhet = navEnhet,
            deltakelsesprosent = 60F,
            dagerPerUke = 3F,
            gyldigFra = fremtidigGyldigFra,
            endret = LocalDateTime.now(),
        )

        // Act
        val response = deltakerResponseBuilder.buildDeltakelsesmengderResponse(
            deltaker,
            listOf(DeltakerHistorikk.Vedtak(vedtak), fremtidig),
        )

        // Assert
        val fremtidigResponse = DeltakelsesmengdeResponse(
            deltakelsesprosent = 60F,
            dagerPerUke = 3F,
            gyldigFra = fremtidigGyldigFra,
        )
        assertSoftly(response.shouldNotBeNull()) {
            nesteDeltakelsesmengde shouldBe fremtidigResponse
            sisteDeltakelsesmengde shouldBe fremtidigResponse
        }
    }

    @Test
    fun `mengden fra ny oppstart vises ikke også som neste periode`() {
        val navAnsatt = TestData.lagNavAnsatt()
        val navEnhet = TestData.lagNavEnhet()
        val opprinneligStartdato = LocalDate.now().minusMonths(1)

        listOf(LocalDate.now(), LocalDate.now().plusDays(10)).forEach { nyStartdato ->
            listOf(100F to 5F, null to 3F, 80F to null).forEach { (prosent, dager) ->
                val deltaker = lagDeltaker(
                    navBruker = TestData.lagNavBruker(navVeilederId = navAnsatt.id, navEnhetId = navEnhet.id),
                    startdato = nyStartdato,
                    sluttdato = nyStartdato.plusMonths(3),
                    deltakelsesprosent = prosent,
                    dagerPerUke = dager,
                )
                val vedtak = lagVedtak(
                    deltakerVedVedtak = deltaker.copy(startdato = opprinneligStartdato),
                    fattet = opprinneligStartdato.atStartOfDay(),
                    opprettetAv = navAnsatt,
                    opprettetAvEnhet = navEnhet,
                )
                val endretStartdato = DeltakerHistorikk.Endring(
                    lagDeltakerEndring(
                        deltakerId = deltaker.id,
                        endring = DeltakerEndring.Endring.EndreStartdato(
                            startdato = nyStartdato,
                            sluttdato = null,
                            begrunnelse = null,
                        ),
                        endretAv = navAnsatt.id,
                        endretAvEnhet = navEnhet.id,
                        endret = LocalDateTime.now(),
                    ),
                )
                val response = deltakerResponseBuilder.buildDeltakelsesmengderResponse(
                    deltaker,
                    listOf(DeltakerHistorikk.Vedtak(vedtak), endretStartdato),
                )

                assertSoftly(response.shouldNotBeNull()) {
                    nesteDeltakelsesmengde shouldBe null
                    sisteDeltakelsesmengde shouldBe DeltakelsesmengdeResponse(prosent, dager, nyStartdato)
                }
            }
        }
    }

    @Test
    fun `mengder før startdato trimmes bort av periode-funksjonen`() {
        // Arrange
        val navAnsatt = TestData.lagNavAnsatt()
        val navEnhet = TestData.lagNavEnhet()
        // Startdato i framtiden gjør at "tidligere" endringer havner før perioden
        val startdato = LocalDate.now().plusDays(10)
        val deltaker = lagDeltaker(
            navBruker = TestData.lagNavBruker(navVeilederId = navAnsatt.id, navEnhetId = navEnhet.id),
            startdato = startdato,
            sluttdato = startdato.plusMonths(3),
            deltakelsesprosent = FALLBACK_DELTAKELSESPROSENT,
            dagerPerUke = 5F,
        )
        val vedtak = lagVedtak(
            deltakerVedVedtak = deltaker,
            fattet = LocalDate.now().minusMonths(2).atStartOfDay(),
            opprettetAv = navAnsatt,
            opprettetAvEnhet = navEnhet,
        )
        // Endring som gjelder fra startdato (innenfor perioden)
        val gyldigFraInnenforPeriode = startdato.plusDays(1)
        val innenforPeriode = endreDeltakelsesmengde(
            deltakerId = deltaker.id,
            navAnsatt = navAnsatt,
            navEnhet = navEnhet,
            deltakelsesprosent = 50F,
            dagerPerUke = 2F,
            gyldigFra = gyldigFraInnenforPeriode,
            endret = LocalDateTime.now(),
        )

        // Act
        val response = deltakerResponseBuilder.buildDeltakelsesmengderResponse(
            deltaker,
            listOf(DeltakerHistorikk.Vedtak(vedtak), innenforPeriode),
        )

        // Assert
        // Vedtaket før startdato avgrenses til startdato, og endringen innenfor blir siste/neste
        assertSoftly(response.shouldNotBeNull()) {
            sisteDeltakelsesmengde shouldBe DeltakelsesmengdeResponse(
                deltakelsesprosent = 50F,
                dagerPerUke = 2F,
                gyldigFra = gyldigFraInnenforPeriode,
            )
            nesteDeltakelsesmengde shouldBe DeltakelsesmengdeResponse(
                deltakelsesprosent = 50F,
                dagerPerUke = 2F,
                gyldigFra = gyldigFraInnenforPeriode,
            )
        }
    }

    @Test
    fun `uten startdato - bruker hele tidslinjen uten periode-trim`() {
        // Arrange
        val navAnsatt = TestData.lagNavAnsatt()
        val navEnhet = TestData.lagNavEnhet()
        val deltaker = lagDeltaker(
            navBruker = TestData.lagNavBruker(navVeilederId = navAnsatt.id, navEnhetId = navEnhet.id),
            startdato = null,
            sluttdato = null,
            deltakelsesprosent = FALLBACK_DELTAKELSESPROSENT,
            dagerPerUke = 5F,
        )
        val fattetDato = LocalDate.now().minusMonths(1)
        val vedtak = lagVedtak(
            deltakerVedVedtak = deltaker,
            fattet = fattetDato.atStartOfDay(),
            opprettetAv = navAnsatt,
            opprettetAvEnhet = navEnhet,
        )

        // Act
        val response = deltakerResponseBuilder.buildDeltakelsesmengderResponse(
            deltaker,
            listOf(DeltakerHistorikk.Vedtak(vedtak)),
        )

        // Assert
        assertSoftly(response.shouldNotBeNull()) {
            sisteDeltakelsesmengde shouldBe DeltakelsesmengdeResponse(
                deltakelsesprosent = FALLBACK_DELTAKELSESPROSENT,
                dagerPerUke = 5F,
                gyldigFra = fattetDato,
            )
            nesteDeltakelsesmengde shouldBe null
        }
    }

    @Test
    fun `ImportertFraArena - brukes som basis-deltakelsesmengde`() {
        // Arrange
        val navAnsatt = TestData.lagNavAnsatt()
        val navEnhet = TestData.lagNavEnhet()
        val startdato = LocalDate.now().minusMonths(1)
        val deltaker = lagDeltaker(
            navBruker = TestData.lagNavBruker(navVeilederId = navAnsatt.id, navEnhetId = navEnhet.id),
            startdato = startdato,
            sluttdato = startdato.plusMonths(3),
            deltakelsesprosent = 75F,
            dagerPerUke = 3F,
        )
        val importertFraArena = DeltakerHistorikk.ImportertFraArena(
            TestData.lagImportertFraArena(
                deltakerId = deltaker.id,
                importertDato = startdato.atStartOfDay(),
                deltakerVedImport = TestData.lagDeltakerVedImport(
                    startdato = startdato,
                    sluttdato = startdato.plusMonths(3),
                    deltakelsesprosent = 75F,
                    dagerPerUke = 3F,
                ),
            ),
        )

        // Act
        val response = deltakerResponseBuilder.buildDeltakelsesmengderResponse(deltaker, listOf(importertFraArena))

        // Assert
        assertSoftly(response.shouldNotBeNull()) {
            nesteDeltakelsesmengde shouldBe null
            sisteDeltakelsesmengde shouldBe DeltakelsesmengdeResponse(
                deltakelsesprosent = 75F,
                dagerPerUke = 3F,
                gyldigFra = startdato,
            )
        }
    }

    @Test
    fun `to fremtidige endringer - nesteGjeldende er nærmeste, sisteDeltakelsesmengde er sist i tid`() {
        // Arrange
        val navAnsatt = TestData.lagNavAnsatt()
        val navEnhet = TestData.lagNavEnhet()
        val startdato = LocalDate.now().minusMonths(1)
        val deltaker = lagDeltaker(
            navBruker = TestData.lagNavBruker(navVeilederId = navAnsatt.id, navEnhetId = navEnhet.id),
            startdato = startdato,
            sluttdato = startdato.plusMonths(6),
            deltakelsesprosent = FALLBACK_DELTAKELSESPROSENT,
            dagerPerUke = 5F,
        )
        val vedtak = lagVedtak(
            deltakerVedVedtak = deltaker,
            fattet = startdato.atStartOfDay(),
            opprettetAv = navAnsatt,
            opprettetAvEnhet = navEnhet,
        )
        val naermesteFremtidigGyldigFra = LocalDate.now().plusDays(7)
        val naermesteFremtidig = endreDeltakelsesmengde(
            deltakerId = deltaker.id,
            navAnsatt = navAnsatt,
            navEnhet = navEnhet,
            deltakelsesprosent = 75F,
            dagerPerUke = 4F,
            gyldigFra = naermesteFremtidigGyldigFra,
            endret = LocalDateTime.now().minusDays(2),
        )
        val fjernesteFremtidigGyldigFra = LocalDate.now().plusMonths(2)
        val fjernesteFremtidig = endreDeltakelsesmengde(
            deltakerId = deltaker.id,
            navAnsatt = navAnsatt,
            navEnhet = navEnhet,
            deltakelsesprosent = 50F,
            dagerPerUke = 2F,
            gyldigFra = fjernesteFremtidigGyldigFra,
            endret = LocalDateTime.now().minusDays(1),
        )

        // Act
        val response = deltakerResponseBuilder.buildDeltakelsesmengderResponse(
            deltaker,
            listOf(DeltakerHistorikk.Vedtak(vedtak), naermesteFremtidig, fjernesteFremtidig),
        )

        // Assert
        assertSoftly(response.shouldNotBeNull()) {
            nesteDeltakelsesmengde shouldBe DeltakelsesmengdeResponse(
                deltakelsesprosent = 75F,
                dagerPerUke = 4F,
                gyldigFra = naermesteFremtidigGyldigFra,
            )
            sisteDeltakelsesmengde shouldBe DeltakelsesmengdeResponse(
                deltakelsesprosent = 50F,
                dagerPerUke = 2F,
                gyldigFra = fjernesteFremtidigGyldigFra,
            )
        }
    }

    private fun endreDeltakelsesmengde(
        deltakerId: UUID,
        navAnsatt: NavAnsatt,
        navEnhet: NavEnhet,
        deltakelsesprosent: Float?,
        dagerPerUke: Float?,
        gyldigFra: LocalDate,
        endret: LocalDateTime,
    ) = DeltakerHistorikk.Endring(
        lagDeltakerEndring(
            deltakerId = deltakerId,
            endring = DeltakerEndring.Endring.EndreDeltakelsesmengde(
                deltakelsesprosent = deltakelsesprosent,
                dagerPerUke = dagerPerUke,
                gyldigFra = gyldigFra,
                begrunnelse = null,
            ),
            endretAv = navAnsatt.id,
            endretAvEnhet = navEnhet.id,
            endret = endret,
        ),
    )
}
