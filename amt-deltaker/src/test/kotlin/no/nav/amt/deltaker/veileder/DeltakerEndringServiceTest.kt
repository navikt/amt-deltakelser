package no.nav.amt.deltaker.veileder

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.result.shouldBeFailure
import io.kotest.matchers.result.shouldBeSuccess
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import no.nav.amt.deltaker.service.DeltakerTestUtils
import no.nav.amt.deltaker.utils.IntegrationTestWithDbBase
import no.nav.amt.deltaker.utils.assertProducedForslag
import no.nav.amt.deltaker.utils.assertProducedHendelse
import no.nav.amt.deltaker.utils.data.TestRepository
import no.nav.amt.deltaker.veileder.endring.VellykketEndring
import no.nav.amt.internapi.deltaker.request.BakgrunnsinformasjonRequest
import no.nav.amt.internapi.deltaker.request.EndretInnholdRequest
import no.nav.amt.internapi.deltaker.request.FjernOppstartsdatoRequest
import no.nav.amt.internapi.deltaker.request.ForlengDeltakelseRequest
import no.nav.amt.internapi.deltaker.request.IkkeAktuellRequest
import no.nav.amt.internapi.deltaker.request.InnholdsElementRequest
import no.nav.amt.internapi.hendelse.HendelseType
import no.nav.amt.lib.models.arrangor.melding.EndringAarsak
import no.nav.amt.lib.models.arrangor.melding.Forslag
import no.nav.amt.lib.models.deltaker.DeltakerEndring
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltaker.Innhold
import no.nav.amt.lib.models.deltaker.deltakelsesmengde.toDeltakelsesmengde
import no.nav.amt.lib.models.deltaker.deltakelsesmengde.toDeltakelsesmengder
import no.nav.amt.lib.testing.utils.TestData
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

class DeltakerEndringServiceTest : IntegrationTestWithDbBase() {
    private val navEnhetInTest = TestData.lagNavEnhet(enhetsnummer = "0326")
    private val navAnsattInTest = TestData.lagNavAnsatt(navEnhetId = navEnhetInTest.id)

    @BeforeEach
    fun setup() {
        navEnhetRepository.upsert(navEnhetInTest)
        navAnsattRepository.upsert(navAnsattInTest)
    }

    @Test
    fun `upsertEndring - endret bakgrunnsinformasjon - upserter endring og returnerer deltaker`() {
        // Arrange
        val deltaker = no.nav.amt.deltaker.utils.data.TestData
            .lagDeltaker()
        val endringResultat = VellykketEndring(deltaker = deltaker)
        TestRepository.insert(deltaker)

        val endringsrequest = BakgrunnsinformasjonRequest(
            endretAv = navAnsattInTest.navIdent,
            endretAvEnhet = navEnhetInTest.enhetsnummer,
            bakgrunnsinformasjon = "Nye opplysninger",
        )

        // Act
        deltakerEndringService.upsertEndring(
            endringResultat = endringResultat,
            endringRequest = endringsrequest,
            endretAvNavAnsatt = navAnsattInTest,
        )

        // Assert
        assertSoftly(deltakerEndringRepository.getForDeltaker(deltaker.id).first()) {
            endretAv shouldBe navAnsattInTest.id
            endretAvEnhet shouldBe navEnhetInTest.id

            assertSoftly(endring.shouldBeInstanceOf<DeltakerEndring.Endring.EndreBakgrunnsinformasjon>()) {
                bakgrunnsinformasjon shouldBe endringsrequest.bakgrunnsinformasjon
            }
        }

        outboxService.assertProducedHendelse<HendelseType.EndreBakgrunnsinformasjon>(deltaker.id)
    }

    @Test
    fun `upsertEndring - endret innhold - upserter og returnerer endring`() {
        // Arrange
        val deltaker = no.nav.amt.deltaker.utils.data.TestData
            .lagDeltaker()
        val tiltaksinnhold = deltaker.deltakerliste.tiltakstype.innhold!!
            .innholdselementer[0]
        val nyttInnhold = InnholdsElementRequest(innholdskode = tiltaksinnhold.innholdskode, beskrivelse = null)
        val expectedInnhold = Innhold(
            innholdskode = nyttInnhold.innholdskode,
            beskrivelse = nyttInnhold.beskrivelse,
            valgt = true,
            tekst = tiltaksinnhold.tekst,
        )
        TestRepository.insert(deltaker)

        val endringsrequest = EndretInnholdRequest(
            endretAv = navAnsattInTest.navIdent,
            endretAvEnhet = navEnhetInTest.enhetsnummer,
            innholdselementer = listOf(nyttInnhold),
        )

        // Act
        val resultat = deltakerEndringService
            .upsertEndring(
                endringResultat = VellykketEndring(deltaker),
                endringRequest = endringsrequest,
                endretAvNavAnsatt = navAnsattInTest,
            ).endring

        // Assert
        assertSoftly(resultat.shouldBeInstanceOf<DeltakerEndring.Endring.EndreInnhold>()) {
            innhold shouldBe listOf(expectedInnhold)
            ledetekst shouldBe deltaker.deltakerliste.tiltakstype.innhold!!
                .ledetekst
        }

        assertSoftly(deltakerEndringRepository.getForDeltaker(deltaker.id).first()) {
            endretAv shouldBe navAnsattInTest.id
            endretAvEnhet shouldBe navEnhetInTest.id

            assertSoftly(endring.shouldBeInstanceOf<DeltakerEndring.Endring.EndreInnhold>()) {
                innhold shouldBe listOf(expectedInnhold)
                ledetekst shouldBe deltaker.deltakerliste.tiltakstype.innhold!!
                    .ledetekst
            }
        }

        outboxService.assertProducedHendelse<HendelseType.EndreInnhold>(deltaker.id)
    }

    @Test
    fun `upsertEndring - forleng deltakelse - upserter endring og returnerer deltaker`() {
        // Arrange
        val deltaker = no.nav.amt.deltaker.utils.data.TestData
            .lagDeltaker()
        val forslag = no.nav.amt.deltaker.utils.data.TestData
            .lagForslag(deltakerId = deltaker.id)
        TestRepository.insertAll(deltaker, forslag)

        val endringsrequest = ForlengDeltakelseRequest(
            endretAv = navAnsattInTest.navIdent,
            endretAvEnhet = navEnhetInTest.enhetsnummer,
            sluttdato = LocalDate.now().plusMonths(1),
            begrunnelse = "begrunnelse",
            forslagId = forslag.id,
        )

        // Act
        deltakerEndringService.upsertEndring(
            endringResultat = VellykketEndring(deltaker),
            endringRequest = endringsrequest,
            endretAvNavAnsatt = navAnsattInTest,
        )

        // Assert
        assertSoftly(deltakerEndringRepository.getForDeltaker(deltaker.id).first()) {
            endretAv shouldBe navAnsattInTest.id
            endretAvEnhet shouldBe navEnhetInTest.id

            assertSoftly(endring.shouldBeInstanceOf<DeltakerEndring.Endring.ForlengDeltakelse>()) {
                sluttdato shouldBe endringsrequest.sluttdato
                begrunnelse shouldBe endringsrequest.begrunnelse
            }
        }

        val forslagFraDb = forslagRepository.get(forslag.id).shouldBeSuccess()
        assertSoftly(forslagFraDb.status.shouldBeInstanceOf<Forslag.Status.Godkjent>()) {
            godkjentAv shouldBe Forslag.NavAnsatt(
                id = navAnsattInTest.id,
                enhetId = navEnhetInTest.id,
            )
        }

        outboxService.assertProducedHendelse<HendelseType.ForlengDeltakelse>(deltaker.id)
        outboxService.assertProducedForslag<Forslag.Status.Godkjent>(forslag.id)
    }

    @Test
    fun `upsertEndring - ikke aktuell - upserter endring og returnerer deltaker`() {
        // Arrange
        val deltaker = no.nav.amt.deltaker.utils.data.TestData.lagDeltaker(
            status = no.nav.amt.deltaker.utils.data.TestData
                .lagDeltakerStatus(DeltakerStatus.Type.VENTER_PA_OPPSTART),
        )
        val forslag = no.nav.amt.deltaker.utils.data.TestData.lagForslag(
            deltakerId = deltaker.id,
            endring = Forslag.IkkeAktuell(EndringAarsak.FattJobb),
        )
        TestRepository.insertAll(deltaker, forslag)

        val endringsrequest = IkkeAktuellRequest(
            endretAv = navAnsattInTest.navIdent,
            endretAvEnhet = navEnhetInTest.enhetsnummer,
            aarsak = DeltakerEndring.Aarsak(DeltakerEndring.Aarsak.Type.FATT_JOBB, null),
            begrunnelse = "begrunnelse",
            forslagId = forslag.id,
        )

        // Act
        deltakerEndringService.upsertEndring(
            endringResultat = VellykketEndring(deltaker),
            endringRequest = endringsrequest,
            endretAvNavAnsatt = navAnsattInTest,
        )

        // Assert
        assertSoftly(deltakerEndringRepository.getForDeltaker(deltaker.id).first()) {
            endretAv shouldBe navAnsattInTest.id
            endretAvEnhet shouldBe navEnhetInTest.id

            assertSoftly(endring.shouldBeInstanceOf<DeltakerEndring.Endring.IkkeAktuell>()) {
                aarsak shouldBe endringsrequest.aarsak
                begrunnelse shouldBe endringsrequest.begrunnelse
            }
        }

        val forslagFraDb = forslagRepository.get(forslag.id).shouldBeSuccess()
        assertSoftly(forslagFraDb.status.shouldBeInstanceOf<Forslag.Status.Godkjent>()) {
            godkjentAv shouldBe Forslag.NavAnsatt(
                id = navAnsattInTest.id,
                enhetId = navEnhetInTest.id,
            )
        }

        outboxService.assertProducedHendelse<HendelseType.IkkeAktuell>(deltaker.id)
        outboxService.assertProducedForslag<Forslag.Status.Godkjent>(forslag.id)
    }

    @Test
    fun `upsertEndring - fjern oppstartsdato - upserter endring og returnerer deltaker`() {
        // Arrange
        val deltaker = no.nav.amt.deltaker.utils.data.TestData.lagDeltaker(
            status = no.nav.amt.deltaker.utils.data.TestData
                .lagDeltakerStatus(DeltakerStatus.Type.VENTER_PA_OPPSTART),
            startdato = LocalDate.now().plusDays(3),
            sluttdato = LocalDate.now().plusWeeks(4),
        )
        TestRepository.insert(deltaker)

        val endringsrequest = FjernOppstartsdatoRequest(
            endretAv = navAnsattInTest.navIdent,
            endretAvEnhet = navEnhetInTest.enhetsnummer,
            forslagId = null,
            begrunnelse = "begrunnelse",
        )

        // Act
        deltakerEndringService.upsertEndring(
            endringResultat = VellykketEndring(deltaker),
            endringRequest = endringsrequest,
            endretAvNavAnsatt = navAnsattInTest,
        )

        // Assert
        assertSoftly(deltakerEndringRepository.getForDeltaker(deltaker.id).first()) {
            endretAv shouldBe navAnsattInTest.id
            endretAvEnhet shouldBe navEnhetInTest.id

            assertSoftly(endring.shouldBeInstanceOf<DeltakerEndring.Endring.FjernOppstartsdato>()) {
                begrunnelse shouldBe endringsrequest.begrunnelse
            }
        }

        outboxService.assertProducedHendelse<HendelseType.FjernOppstartsdato>(deltaker.id)
    }

    @Test
    fun `behandleLagretEndring - ubehandlet gyldig endring - oppdaterer deltaker og upserter endring med behandlet`() {
        // Arrange
        val deltaker = no.nav.amt.deltaker.utils.data.TestData.lagDeltaker(
            status = no.nav.amt.deltaker.utils.data.TestData
                .lagDeltakerStatus(DeltakerStatus.Type.DELTAR),
        )
        TestRepository.insert(deltaker)

        val deltakelsesprosent = 50F
        val dagerPerUke = 3F
        val id = UUID.randomUUID()

        val ubehandletEndring = upsertEndring(
            no.nav.amt.deltaker.utils.data.TestData.lagDeltakerEndring(
                id = id,
                deltakerId = deltaker.id,
                endretAv = navAnsattInTest.id,
                endretAvEnhet = navEnhetInTest.id,
                endring = DeltakerEndring.Endring.EndreDeltakelsesmengde(
                    deltakelsesprosent = deltakelsesprosent,
                    dagerPerUke = dagerPerUke,
                    gyldigFra = LocalDate.now(),
                    begrunnelse = "begrunnelse",
                ),
                endret = LocalDateTime.now().minusDays(1),
            ),
        )

        // Act
        val resultat = deltakerEndringService
            .behandleLagretDeltakelsesmengde(
                deltakerEndring = ubehandletEndring,
                deltaker = deltaker,
            ).shouldBeSuccess()

        // Assert
        assertSoftly(resultat.deltaker) {
            deltakelsesprosent shouldBe deltakelsesprosent
            dagerPerUke shouldBe dagerPerUke
        }

        val ubehandlete = deltakerEndringRepository.getUbehandletDeltakelsesmengder()
        ubehandlete.size shouldBe 0
    }

    @Test
    fun `behandleLagretEndring - ubehandlet ugyldig endring - oppdaterer ikke deltaker og upserter endring med behandlet`() {
        // Arrange
        val deltaker = no.nav.amt.deltaker.utils.data.TestData.lagDeltaker(
            status = no.nav.amt.deltaker.utils.data.TestData
                .lagDeltakerStatus(DeltakerStatus.Type.DELTAR),
        )
        val vedtak = no.nav.amt.deltaker.utils.data.TestData.lagVedtak(
            deltakerVedVedtak = deltaker,
            opprettetAv = navAnsattInTest,
            opprettetAvEnhet = navEnhetInTest,
            fattet = LocalDateTime.now().minusHours(1),
        )
        TestRepository.insertAll(deltaker, vedtak)

        val ugyldigEndring = upsertEndring(
            no.nav.amt.deltaker.utils.data.TestData.lagDeltakerEndring(
                deltakerId = deltaker.id,
                endretAv = navAnsattInTest.id,
                endretAvEnhet = navEnhetInTest.id,
                endring = DeltakerEndring.Endring.EndreDeltakelsesmengde(
                    deltakelsesprosent = 90F,
                    dagerPerUke = null,
                    gyldigFra = LocalDate.now(),
                    begrunnelse = "begrunnelse",
                ),
                endret = LocalDateTime.now().minusSeconds(2),
            ),
        )

        val gyldigEndring = upsertEndring(
            no.nav.amt.deltaker.utils.data.TestData.lagDeltakerEndring(
                deltakerId = deltaker.id,
                endretAv = navAnsattInTest.id,
                endretAvEnhet = navEnhetInTest.id,
                endring = DeltakerEndring.Endring.EndreDeltakelsesmengde(
                    deltakelsesprosent = 80F,
                    dagerPerUke = null,
                    gyldigFra = LocalDate.now(),
                    begrunnelse = "begrunnelse",
                ),
                endret = LocalDateTime.now().minusSeconds(1),
            ),
        )

        // Act
        deltakerEndringService
            .behandleLagretDeltakelsesmengde(
                deltakerEndring = ugyldigEndring,
                deltaker = deltaker,
            ).shouldBeFailure()

        // Assert
        val ubehandlete = deltakerEndringRepository.getUbehandletDeltakelsesmengder()

        ubehandlete.size shouldBe 1
        DeltakerTestUtils.sammenlignDeltakerEndring(ubehandlete.first(), gyldigEndring)
    }

    @Test
    fun `behandleLagretEndring - endringen er utfort pga endret startdato - oppdaterer ikke deltaker og upserter endring med behandlet`() {
        // Arrange
        val deltaker = no.nav.amt.deltaker.utils.data.TestData.lagDeltaker(
            status = no.nav.amt.deltaker.utils.data.TestData
                .lagDeltakerStatus(DeltakerStatus.Type.DELTAR),
        )
        val vedtak = no.nav.amt.deltaker.utils.data.TestData.lagVedtak(
            deltakerVedVedtak = deltaker,
            opprettetAv = navAnsattInTest,
            opprettetAvEnhet = navEnhetInTest,
            fattet = LocalDateTime.now().minusWeeks(2),
        )

        val startdato = LocalDate.now().plusWeeks(1)

        val startdatoEndring = no.nav.amt.deltaker.utils.data.TestData.lagDeltakerEndring(
            deltakerId = deltaker.id,
            endretAv = navAnsattInTest.id,
            endretAvEnhet = navEnhetInTest.id,
            endring = DeltakerEndring.Endring.EndreStartdato(
                startdato = startdato,
                sluttdato = null,
                begrunnelse = null,
            ),
            endret = LocalDateTime.now().minusMinutes(2),
        )

        TestRepository.insertAll(deltaker, vedtak, startdatoEndring)

        val fremtidigDeltakelsesprosent = 90F
        val fremtidigDagerPerUke = null

        val fremtidigEndring = upsertEndring(
            no.nav.amt.deltaker.utils.data.TestData.lagDeltakerEndring(
                deltakerId = deltaker.id,
                endretAv = navAnsattInTest.id,
                endretAvEnhet = navEnhetInTest.id,
                endring = DeltakerEndring.Endring.EndreDeltakelsesmengde(
                    deltakelsesprosent = fremtidigDeltakelsesprosent,
                    dagerPerUke = fremtidigDagerPerUke,
                    gyldigFra = startdato,
                    begrunnelse = "begrunnelse",
                ),
                endret = LocalDateTime.now().minusDays(2),
            ),
        )

        // Act
        val resultat = deltakerEndringService.behandleLagretDeltakelsesmengde(
            deltakerEndring = fremtidigEndring,
            deltaker = deltaker.copy(
                deltakelsesprosent = fremtidigDeltakelsesprosent,
                dagerPerUke = fremtidigDagerPerUke,
            ), // deltaker skal være oppdatert pga startdatoendringen...
        )

        // Assert
        resultat.shouldBeFailure()

        val ubehandlete = deltakerEndringRepository.getUbehandletDeltakelsesmengder()
        ubehandlete.size shouldBe 0

        assertSoftly(deltakerHistorikkService.getForDeltaker(deltaker.id).toDeltakelsesmengder()) {
            size shouldBe 1
            gjeldende shouldBe fremtidigEndring.toDeltakelsesmengde()
            nesteGjeldende shouldBe null
        }
    }

    private fun upsertEndring(endring: DeltakerEndring): DeltakerEndring {
        deltakerEndringRepository.upsert(
            deltakerEndring = endring,
            behandletTidspunkt = null,
        )
        return deltakerEndringRepository.get(endring.id).shouldBeSuccess()
    }
}
