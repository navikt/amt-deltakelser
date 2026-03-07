package no.nav.amt.deltaker.bff.deltaker

import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.result.shouldBeFailure
import io.kotest.matchers.result.shouldBeSuccess
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import no.nav.amt.deltaker.bff.deltaker.db.DeltakerRepository
import no.nav.amt.deltaker.bff.deltaker.forslag.ForslagRepository
import no.nav.amt.deltaker.bff.deltaker.model.Deltakeroppdatering
import no.nav.amt.deltaker.bff.navenhet.NavEnhetRepository
import no.nav.amt.deltaker.bff.navenhet.NavEnhetService
import no.nav.amt.deltaker.bff.utils.DeltakerTestUtils.sammenlignDeltakere
import no.nav.amt.deltaker.bff.utils.MockResponseHandler
import no.nav.amt.deltaker.bff.utils.data.TestData
import no.nav.amt.deltaker.bff.utils.data.TestData.lagDeltaker
import no.nav.amt.deltaker.bff.utils.data.TestData.lagDeltakerEndring
import no.nav.amt.deltaker.bff.utils.data.TestData.lagDeltakerKladd
import no.nav.amt.deltaker.bff.utils.data.TestData.lagDeltakerStatus
import no.nav.amt.deltaker.bff.utils.data.TestRepository
import no.nav.amt.deltaker.bff.utils.data.endre
import no.nav.amt.deltaker.bff.utils.mockAmtDeltakerClient
import no.nav.amt.deltaker.bff.utils.mockAmtPersonServiceClient
import no.nav.amt.deltaker.bff.utils.toDeltakeroppdatering
import no.nav.amt.lib.models.arrangor.melding.Forslag
import no.nav.amt.lib.models.deltaker.Deltakelsesinnhold
import no.nav.amt.lib.models.deltaker.DeltakerEndring
import no.nav.amt.lib.models.deltaker.DeltakerHistorikk
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltaker.Innhold
import no.nav.amt.lib.models.deltaker.internalapis.deltaker.request.AvbrytDeltakelseRequest
import no.nav.amt.lib.models.deltaker.internalapis.deltaker.request.AvsluttDeltakelseRequest
import no.nav.amt.lib.models.deltaker.internalapis.deltaker.request.BakgrunnsinformasjonRequest
import no.nav.amt.lib.models.deltaker.internalapis.deltaker.request.DeltakelsesmengdeRequest
import no.nav.amt.lib.models.deltaker.internalapis.deltaker.request.EndreAvslutningRequest
import no.nav.amt.lib.models.deltaker.internalapis.deltaker.request.FjernOppstartsdatoRequest
import no.nav.amt.lib.models.deltaker.internalapis.deltaker.request.ForlengDeltakelseRequest
import no.nav.amt.lib.models.deltaker.internalapis.deltaker.request.IkkeAktuellRequest
import no.nav.amt.lib.models.deltaker.internalapis.deltaker.request.InnholdRequest
import no.nav.amt.lib.models.deltaker.internalapis.deltaker.request.ReaktiverDeltakelseRequest
import no.nav.amt.lib.models.deltaker.internalapis.deltaker.request.SluttarsakRequest
import no.nav.amt.lib.models.deltaker.internalapis.deltaker.request.SluttdatoRequest
import no.nav.amt.lib.models.deltaker.internalapis.deltaker.request.StartdatoRequest
import no.nav.amt.lib.testing.DatabaseTestExtension
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

class DeltakerServiceTest {
    private val navEnhetService = NavEnhetService(NavEnhetRepository(), mockAmtPersonServiceClient())
    private val forslagRepository = mockk<ForslagRepository>(relaxed = true)
    private val deltakerRepository = DeltakerRepository()

    private val deltakerService = DeltakerService(
        deltakerRepository,
        mockAmtDeltakerClient(),
        navEnhetService,
        forslagRepository,
    )

    companion object {
        @RegisterExtension
        val dbExtension = DatabaseTestExtension()
    }

    @Test
    fun `updateBatch - flere deltakere - oppdaterer deltakere og statuser riktig`() = runTest {
        val deltaker1 = lagDeltaker()
        val deltaker2 = lagDeltaker()

        TestRepository.insert(deltaker1)
        TestRepository.insert(deltaker2)

        val oppdatertDeltaker1 = deltaker1.copy(
            sluttdato = LocalDate.now().plusWeeks(2),
            status = lagDeltakerStatus(DeltakerStatus.Type.DELTAR),
            erManueltDeltMedArrangor = true,
        )

        val oppdatertDeltaker2 = deltaker2.copy(
            sluttdato = LocalDate.now().plusWeeks(2),
            status = lagDeltakerStatus(DeltakerStatus.Type.DELTAR),
            erManueltDeltMedArrangor = true,
        )

        val deltakerOppdateringer = listOf(
            oppdatertDeltaker1,
            oppdatertDeltaker2,
        ).map { it.toDeltakeroppdatering() }

        deltakerService.oppdaterDeltakere(deltakerOppdateringer)

        val deltaker1FraDB = deltakerRepository.get(deltaker1.id).getOrThrow()
        sammenlignDeltakere(deltaker1FraDB, oppdatertDeltaker1)

        val deltaker2FraDB = deltakerRepository.get(deltaker2.id).getOrThrow()
        sammenlignDeltakere(deltaker2FraDB, oppdatertDeltaker2)
    }

    @Test
    fun `delete - ingen endring eller vedtak - sletter deltaker`() = runTest {
        val deltaker = lagDeltaker(status = lagDeltakerStatus(DeltakerStatus.Type.KLADD))
        TestRepository.insert(deltaker)

        deltakerService.deleteDeltaker(deltaker.id)

        deltakerRepository.get(deltaker.id).shouldBeFailure()
    }

    @Nested
    inner class OppdaterDeltakerTests {
        @Test
        fun `deltaker er endret - oppdaterer`() = runTest {
            val sistEndret = LocalDateTime.now().minusDays(3)
            val deltaker = lagDeltaker(sistEndret = sistEndret)
            TestRepository.insert(deltaker)

            val endring = DeltakerEndring.Endring.EndreBakgrunnsinformasjon("ny bakgrunn for innsøk")
            val oppdatertDeltaker = deltaker.endre(lagDeltakerEndring(endring = endring))

            deltakerService.oppdaterDeltaker(oppdatertDeltaker.toDeltakeroppdatering())

            val deltakerFromDb = deltakerRepository.get(deltaker.id).shouldBeSuccess()
            sammenlignDeltakere(deltakerFromDb, oppdatertDeltaker)
        }

        @Test
        fun `deltaker endringshistorikk mangler men er utkast - oppdaterer`() = runTest {
            val deltaker = lagDeltaker(
                status = lagDeltakerStatus(DeltakerStatus.Type.UTKAST_TIL_PAMELDING),
            )
            TestRepository.insert(deltaker)
            val oppdatertDeltaker = deltaker.copy(bakgrunnsinformasjon = "Endringshistorikk mangler")

            deltakerService.oppdaterDeltaker(oppdatertDeltaker.toDeltakeroppdatering())

            sammenlignDeltakere(deltakerRepository.get(deltaker.id).getOrThrow(), oppdatertDeltaker)
        }

        @Test
        fun `deltakerstatus er endret - oppdaterer`() = runTest {
            val deltaker = lagDeltakerKladd()
            TestRepository.insert(deltaker)

            val oppdatertDeltaker = deltaker.copy(
                status = lagDeltakerStatus(DeltakerStatus.Type.UTKAST_TIL_PAMELDING),
            )

            deltakerService.oppdaterDeltaker(oppdatertDeltaker.toDeltakeroppdatering())

            sammenlignDeltakere(deltakerRepository.get(deltaker.id).getOrThrow(), oppdatertDeltaker)
        }

        @Test
        fun `deltaker kan ikke endres - oppdaterer deltaker men beholder lasing`() = runTest {
            val sistEndret = LocalDateTime.now().minusDays(3)
            val deltaker = lagDeltaker(sistEndret = sistEndret, kanEndres = false)
            TestRepository.insert(deltaker)

            val endring = DeltakerEndring.Endring.EndreBakgrunnsinformasjon("ny bakgrunn for innsøk")
            val oppdatertDeltaker = deltaker.endre(lagDeltakerEndring(endring = endring))

            deltakerService.oppdaterDeltaker(oppdatertDeltaker.toDeltakeroppdatering())

            val deltakerFromDb = deltakerRepository.get(deltaker.id).shouldBeSuccess()
            sammenlignDeltakere(deltakerFromDb, oppdatertDeltaker)
            deltakerFromDb.kanEndres shouldBe false
        }

        @Test
        fun `deltaker kan ikke endres, kun oppdatert historikk - oppdaterer historikk`() = runTest {
            val sistEndret = LocalDateTime.now().minusDays(3)
            val deltaker = lagDeltaker(sistEndret = sistEndret, kanEndres = false)
            TestRepository.insert(deltaker)
            val avvistForslag = TestData.lagForslag(
                deltakerId = deltaker.id,
                status = Forslag.Status.Avvist(
                    avvistAv = Forslag.NavAnsatt(id = UUID.randomUUID(), enhetId = UUID.randomUUID()),
                    avvist = LocalDateTime.now(),
                    begrunnelseFraNav = "begrunnelse",
                ),
            )

            val historikk = deltaker.historikk + listOf(DeltakerHistorikk.Forslag(avvistForslag))
            val oppdatertDeltaker = deltaker.copy(historikk = historikk, sistEndret = LocalDateTime.now())

            deltakerService.oppdaterDeltaker(oppdatertDeltaker.toDeltakeroppdatering())

            sammenlignDeltakere(deltakerRepository.get(deltaker.id).getOrThrow(), oppdatertDeltaker)
        }

        @Test
        fun `oppdaterDeltaker(endring) - kaller client og returnerer deltaker`() = runTest {
            val deltaker = lagDeltaker()
            TestRepository.insert(deltaker)

            val navEnhet = navEnhetService
                .hentEnhet(
                    deltaker.navBruker.navEnhetId.shouldNotBeNull(),
                ).shouldNotBeNull()

            val endringRequests = listOf(
                BakgrunnsinformasjonRequest(
                    endretAv = "~endretAv~",
                    endretAvEnhet = navEnhet.enhetsnummer,
                    bakgrunnsinformasjon = "foo",
                ),
                InnholdRequest(
                    endretAv = "~endretAv~",
                    endretAvEnhet = navEnhet.enhetsnummer,
                    deltakelsesinnhold = Deltakelsesinnhold(
                        ledetekst = "~ledetekst~",
                        innhold = listOf(Innhold("tekst,", "innholdskode,", true, "beskrivelse")),
                    ),
                ),
                DeltakelsesmengdeRequest(
                    endretAv = "~endretAv~",
                    endretAvEnhet = navEnhet.enhetsnummer,
                    forslagId = null,
                    deltakelsesprosent = 50,
                    dagerPerUke = 2,
                    gyldigFra = LocalDate.now(),
                    begrunnelse = null,
                ),
                StartdatoRequest(
                    endretAv = "~endretAv~",
                    endretAvEnhet = navEnhet.enhetsnummer,
                    forslagId = null,
                    startdato = LocalDate.now(),
                    sluttdato = LocalDate.now().plusWeeks(2),
                    begrunnelse = null,
                ),
                SluttdatoRequest(
                    endretAv = "~endretAv~",
                    endretAvEnhet = navEnhet.enhetsnummer,
                    forslagId = null,
                    sluttdato = LocalDate.now(),
                    begrunnelse = null,
                ),
                SluttarsakRequest(
                    endretAv = "~endretAv~",
                    endretAvEnhet = navEnhet.enhetsnummer,
                    forslagId = null,
                    aarsak = DeltakerEndring.Aarsak(
                        type = DeltakerEndring.Aarsak.Type.ANNET,
                        beskrivelse = "beskrivelse",
                    ),
                    begrunnelse = null,
                ),
                ForlengDeltakelseRequest(
                    endretAv = "~endretAv~",
                    endretAvEnhet = navEnhet.enhetsnummer,
                    forslagId = null,
                    sluttdato = LocalDate.now(),
                    begrunnelse = "begrunnelse",
                ),
                IkkeAktuellRequest(
                    endretAv = "~endretAv~",
                    endretAvEnhet = navEnhet.enhetsnummer,
                    forslagId = null,
                    DeltakerEndring.Aarsak(
                        type = DeltakerEndring.Aarsak.Type.ANNET,
                        beskrivelse = "beskrivelse",
                    ),
                    begrunnelse = "begrunnelse",
                ),
                AvsluttDeltakelseRequest(
                    endretAv = "~endretAv~",
                    endretAvEnhet = navEnhet.enhetsnummer,
                    forslagId = null,
                    sluttdato = LocalDate.now(),
                    aarsak = DeltakerEndring.Aarsak(
                        type = DeltakerEndring.Aarsak.Type.ANNET,
                        beskrivelse = "beskrivelse",
                    ),
                    begrunnelse = "begrunnelse",
                    harFullfort = true,
                ),
                AvbrytDeltakelseRequest(
                    endretAv = "~endretAv~",
                    endretAvEnhet = navEnhet.enhetsnummer,
                    forslagId = null,
                    sluttdato = LocalDate.now(),
                    aarsak = DeltakerEndring.Aarsak(
                        type = DeltakerEndring.Aarsak.Type.ANNET,
                        beskrivelse = "beskrivelse",
                    ),
                    begrunnelse = "begrunnelse",
                ),
                ReaktiverDeltakelseRequest(
                    endretAv = "~endretAv~",
                    endretAvEnhet = navEnhet.enhetsnummer,
                    begrunnelse = "begrunnelse",
                ),
                FjernOppstartsdatoRequest(
                    endretAv = "~endretAv~",
                    endretAvEnhet = navEnhet.enhetsnummer,
                    forslagId = null,
                    begrunnelse = "begrunnelse",
                ),
            )

            endringRequests.forEach { endringRequest ->
                MockResponseHandler.addEndringsresponse(
                    deltaker.endre(
                        lagDeltakerEndring(
                            deltakerId = deltaker.id,
                            endring = endringRequest.toEndring(),
                        ),
                    ),
                )

                val oppdatertDeltaker = deltakerService.oppdaterDeltaker(
                    deltaker = deltaker,
                    endringRequest = endringRequest,
                )

                when (endringRequest) {
                    is BakgrunnsinformasjonRequest -> {
                        oppdatertDeltaker.bakgrunnsinformasjon shouldBe endringRequest.bakgrunnsinformasjon
                    }

                    is InnholdRequest -> {
                        oppdatertDeltaker.deltakelsesinnhold.shouldNotBeNull().innhold shouldBe endringRequest.deltakelsesinnhold.innhold
                        oppdatertDeltaker.deltakelsesinnhold.ledetekst shouldBe endringRequest.deltakelsesinnhold.ledetekst
                    }

                    is DeltakelsesmengdeRequest -> {
                        oppdatertDeltaker.deltakelsesprosent shouldBe endringRequest.deltakelsesprosent
                        oppdatertDeltaker.dagerPerUke shouldBe endringRequest.dagerPerUke
                    }

                    is StartdatoRequest -> {
                        oppdatertDeltaker.startdato shouldBe endringRequest.startdato
                        oppdatertDeltaker.sluttdato shouldBe endringRequest.sluttdato
                    }

                    is SluttdatoRequest -> {
                        oppdatertDeltaker.sluttdato shouldBe endringRequest.sluttdato
                    }

                    is SluttarsakRequest -> {
                        oppdatertDeltaker.status.aarsak shouldBe endringRequest.aarsak.toDeltakerStatusAarsak()
                    }

                    is ForlengDeltakelseRequest -> {
                        oppdatertDeltaker.sluttdato shouldBe endringRequest.sluttdato
                    }

                    is IkkeAktuellRequest -> {
                        oppdatertDeltaker.status.type shouldBe DeltakerStatus.Type.IKKE_AKTUELL
                        oppdatertDeltaker.status.aarsak shouldBe endringRequest.aarsak.toDeltakerStatusAarsak()
                    }

                    is AvsluttDeltakelseRequest -> {
                        oppdatertDeltaker.status.type shouldBe DeltakerStatus.Type.HAR_SLUTTET
                        oppdatertDeltaker.status.aarsak shouldBe endringRequest.aarsak?.toDeltakerStatusAarsak()
                        oppdatertDeltaker.sluttdato shouldBe endringRequest.sluttdato
                    }

                    is EndreAvslutningRequest -> {
                        if (endringRequest.harFullfort == true) {
                            oppdatertDeltaker.status.type shouldBe DeltakerStatus.Type.FULLFORT
                        } else {
                            oppdatertDeltaker.status.type shouldBe DeltakerStatus.Type.AVBRUTT
                        }
                        oppdatertDeltaker.status.aarsak shouldBe endringRequest.aarsak?.toDeltakerStatusAarsak()
                    }

                    is AvbrytDeltakelseRequest -> {
                        oppdatertDeltaker.status.type shouldBe DeltakerStatus.Type.AVBRUTT
                        oppdatertDeltaker.status.aarsak shouldBe endringRequest.aarsak.toDeltakerStatusAarsak()
                        oppdatertDeltaker.sluttdato shouldBe endringRequest.sluttdato
                    }

                    is ReaktiverDeltakelseRequest -> {
                        oppdatertDeltaker.status.type shouldBe DeltakerStatus.Type.VENTER_PA_OPPSTART
                        oppdatertDeltaker.startdato shouldBe null
                        oppdatertDeltaker.sluttdato shouldBe null
                    }

                    is FjernOppstartsdatoRequest -> {
                        oppdatertDeltaker.startdato shouldBe null
                        oppdatertDeltaker.sluttdato shouldBe null
                    }
                }
            }
        }

        @Test
        fun `oppdaterDeltaker(deltakerOppdatering) - reaktivering med kladd - sletter kladd`() = runTest {
            val deltakerKladd = lagDeltakerKladd()
            val deltaker = lagDeltaker(deltakerliste = deltakerKladd.deltakerliste, navBruker = deltakerKladd.navBruker)
            TestRepository.insert(deltaker)
            TestRepository.insert(deltakerKladd)

            val navEnhet = navEnhetService
                .hentEnhet(
                    deltaker.navBruker.navEnhetId.shouldNotBeNull(),
                ).shouldNotBeNull()

            val endringRequest = ReaktiverDeltakelseRequest(
                endretAv = "~endretAv~",
                endretAvEnhet = navEnhet.enhetsnummer,
                begrunnelse = "begrunnelse",
            )

            MockResponseHandler.addEndringsresponse(
                deltaker.endre(lagDeltakerEndring(deltakerId = deltaker.id, endring = endringRequest.toEndring())),
            )

            MockResponseHandler.addSlettKladdResponse(deltakerKladd.id)
            every { forslagRepository.deleteForDeltaker(deltakerKladd.id) } returns Unit

            deltakerRepository.get(deltakerKladd.id).shouldBeSuccess()

            deltakerService.oppdaterDeltaker(
                deltaker = deltaker,
                endringRequest = endringRequest,
            )

            val deltakerFraDb = deltakerRepository.get(deltaker.id).getOrThrow()
            deltakerFraDb.status.type shouldBe DeltakerStatus.Type.VENTER_PA_OPPSTART
            deltakerRepository.get(deltakerKladd.id).shouldBeFailure()
        }

        @Test
        fun `oppdaterDeltaker(deltakerOppdatering) - har ikke andre deltakelser - oppdaterer deltaker`() = runTest {
            val deltaker = lagDeltakerKladd()
            TestRepository.insert(deltaker)

            val deltakeroppdatering = Deltakeroppdatering(
                id = deltaker.id,
                startdato = null,
                sluttdato = null,
                dagerPerUke = null,
                deltakelsesprosent = 100F,
                bakgrunnsinformasjon = "Tekst",
                deltakelsesinnhold = null,
                status = lagDeltakerStatus(DeltakerStatus.Type.UTKAST_TIL_PAMELDING),
                erManueltDeltMedArrangor = false,
                historikk = emptyList(),
            )

            deltakerService.oppdaterDeltaker(deltakeroppdatering)

            val deltakerFraDb = deltakerRepository.get(deltaker.id).getOrThrow()
            deltakerFraDb.status.type shouldBe DeltakerStatus.Type.UTKAST_TIL_PAMELDING
        }

        @Test
        fun `oppdaterDeltaker(deltakerOppdatering) - har ikke andre deltakelser, har sluttet - oppdaterer deltaker`() = runTest {
            val deltaker = lagDeltaker(
                status = lagDeltakerStatus(DeltakerStatus.Type.HAR_SLUTTET),
            )
            TestRepository.insert(deltaker)
            val deltakeroppdatering = Deltakeroppdatering(
                id = deltaker.id,
                startdato = LocalDate.now().minusMonths(2),
                sluttdato = LocalDate.now().minusDays(2),
                dagerPerUke = null,
                deltakelsesprosent = 100F,
                bakgrunnsinformasjon = "Tekst",
                deltakelsesinnhold = Deltakelsesinnhold("ny ledetekst", listOf(Innhold("", "", true, ""))),
                status = lagDeltakerStatus(
                    statusType = DeltakerStatus.Type.HAR_SLUTTET,
                    aarsakType = DeltakerStatus.Aarsak.Type.ANNET,
                    aarsakBeskrivelse = "Oppdatert",
                ),
                erManueltDeltMedArrangor = false,
                historikk = emptyList(),
            )

            deltakerService.oppdaterDeltaker(deltakeroppdatering)

            val deltakerFraDb = deltakerRepository.get(deltaker.id).getOrThrow()
            deltakerFraDb.status.type shouldBe DeltakerStatus.Type.HAR_SLUTTET
            deltakerFraDb.status.aarsak?.type shouldBe DeltakerStatus.Aarsak.Type.ANNET
            deltakerFraDb.status.aarsak?.beskrivelse shouldBe "Oppdatert"
            deltakerFraDb.deltakelsesinnhold!!.innhold shouldBe deltakeroppdatering.deltakelsesinnhold!!.innhold
            deltakerFraDb.deltakelsesinnhold.ledetekst shouldBe deltakeroppdatering.deltakelsesinnhold.ledetekst
            deltakerFraDb.kanEndres shouldBe true
        }

        @Test
        fun `oppdaterDeltaker(deltakerOppdatering) - har tidligere deltakelse, statusoppdatering - setter kan ikke endres`() = runTest {
            val deltaker = lagDeltakerKladd()
            TestRepository.insert(deltaker)
            val gammelDeltaker = lagDeltaker(
                deltakerliste = deltaker.deltakerliste,
                navBruker = deltaker.navBruker,
                status = lagDeltakerStatus(DeltakerStatus.Type.IKKE_AKTUELL),
                kanEndres = true,
            )
            TestRepository.insert(gammelDeltaker)
            val deltakeroppdatering = Deltakeroppdatering(
                id = deltaker.id,
                startdato = null,
                sluttdato = null,
                dagerPerUke = null,
                deltakelsesprosent = 100F,
                bakgrunnsinformasjon = "Tekst",
                deltakelsesinnhold = Deltakelsesinnhold("ny ledetekst", listOf(Innhold("", "", true, ""))),
                status = lagDeltakerStatus(DeltakerStatus.Type.UTKAST_TIL_PAMELDING),
                erManueltDeltMedArrangor = false,
                historikk = emptyList(),
            )

            deltakerService.oppdaterDeltaker(deltakeroppdatering)

            val deltakerFraDb = deltakerRepository.get(deltaker.id).getOrThrow()
            deltakerFraDb.status.type shouldBe DeltakerStatus.Type.UTKAST_TIL_PAMELDING

            val gammelDeltakerFraDb = deltakerRepository.get(gammelDeltaker.id).getOrThrow()
            gammelDeltakerFraDb.kanEndres shouldBe false
        }

        @Test
        fun `oppdaterDeltaker(deltakerOppdatering) - har tidligere deltakelse, ikke statusoppdatering - oppdaterer deltaker`() = runTest {
            val deltaker = lagDeltaker(
                status = lagDeltakerStatus(DeltakerStatus.Type.DELTAR),
                startdato = LocalDate.now().minusDays(10),
                sluttdato = null,
            )
            TestRepository.insert(deltaker)
            val gammelDeltaker = lagDeltaker(
                deltakerliste = deltaker.deltakerliste,
                navBruker = deltaker.navBruker,
                status = lagDeltakerStatus(DeltakerStatus.Type.IKKE_AKTUELL),
                kanEndres = true,
            )
            TestRepository.insert(gammelDeltaker)
            val deltakeroppdatering = Deltakeroppdatering(
                id = deltaker.id,
                startdato = LocalDate.now().minusDays(10),
                sluttdato = LocalDate.now().plusDays(10),
                dagerPerUke = null,
                deltakelsesprosent = 100F,
                bakgrunnsinformasjon = "Tekst",
                deltakelsesinnhold = null,
                status = lagDeltakerStatus(DeltakerStatus.Type.DELTAR),
                erManueltDeltMedArrangor = false,
                historikk = emptyList(),
            )

            deltakerService.oppdaterDeltaker(deltakeroppdatering)

            val deltakerFraDb = deltakerRepository.get(deltaker.id).getOrThrow()
            deltakerFraDb.status.type shouldBe DeltakerStatus.Type.DELTAR

            val gammelDeltakerFraDb = deltakerRepository.get(gammelDeltaker.id).getOrThrow()
            gammelDeltakerFraDb.kanEndres shouldBe true
        }

        @Test
        fun `oppdaterDeltaker(deltakerOppdatering) - feilregistrert - setter kan ikke endres`() = runTest {
            val deltaker = lagDeltaker(status = lagDeltakerStatus(DeltakerStatus.Type.VENTER_PA_OPPSTART))
            TestRepository.insert(deltaker)
            val deltakeroppdatering = Deltakeroppdatering(
                id = deltaker.id,
                startdato = null,
                sluttdato = null,
                dagerPerUke = null,
                deltakelsesprosent = null,
                bakgrunnsinformasjon = null,
                deltakelsesinnhold = null,
                status = lagDeltakerStatus(DeltakerStatus.Type.FEILREGISTRERT),
                erManueltDeltMedArrangor = false,
                historikk = emptyList(),
            )

            deltakerService.oppdaterDeltaker(deltakeroppdatering)

            val deltakerFraDb = deltakerRepository.get(deltaker.id).getOrThrow()
            deltakerFraDb.status.type shouldBe DeltakerStatus.Type.FEILREGISTRERT
            deltakerFraDb.kanEndres shouldBe false
        }

        @Test
        fun `oppdaterDeltaker(deltakerOppdatering) - avlyst gjennomforing - setter kan ikke endres`() = runTest {
            val navBruker = TestData.lagNavBruker()
            val deltaker = lagDeltaker(
                status = lagDeltakerStatus(DeltakerStatus.Type.VENTER_PA_OPPSTART),
                navBruker = navBruker,
            )
            TestRepository.insert(navBruker)
            TestRepository.insert(deltaker)
            val deltakeroppdatering = Deltakeroppdatering(
                id = deltaker.id,
                startdato = null,
                sluttdato = null,
                dagerPerUke = null,
                deltakelsesprosent = null,
                bakgrunnsinformasjon = null,
                deltakelsesinnhold = null,
                status = lagDeltakerStatus(
                    statusType = DeltakerStatus.Type.IKKE_AKTUELL,
                    aarsakType = DeltakerStatus.Aarsak.Type.SAMARBEIDET_MED_ARRANGOREN_ER_AVBRUTT,
                ),
                erManueltDeltMedArrangor = false,
                historikk = emptyList(),
            )

            deltakerService.oppdaterDeltaker(deltakeroppdatering)

            val deltakerFraDb = deltakerRepository.get(deltaker.id).getOrThrow()
            deltakerFraDb.status.type shouldBe DeltakerStatus.Type.IKKE_AKTUELL
            deltakerFraDb.status.aarsak?.type shouldBe DeltakerStatus.Aarsak.Type.SAMARBEIDET_MED_ARRANGOREN_ER_AVBRUTT
            deltakerFraDb.kanEndres shouldBe false
        }
    }

    @Test
    fun `oppdaterDeltakerLaas - ny deltaker - beholder låsing`() {
        val deltaker = lagDeltaker()
        TestRepository.insert(deltaker)
        deltakerRepository.get(deltaker.id).getOrThrow().kanEndres shouldBe true
        deltakerService.oppdaterDeltakerLaas(deltaker.id, deltaker.navBruker.personident, deltaker.deltakerliste.id)
        deltakerRepository.get(deltaker.id).getOrThrow().kanEndres shouldBe true
    }

    @Test
    fun `oppdaterDeltakerLaas - importerte deltakere med samme innsøktDato, endring på nyeste deltaker - beholder låsing`() {
        val deltaker = lagDeltaker(
            status = lagDeltakerStatus(
                statusType = DeltakerStatus.Type.HAR_SLUTTET,
                gyldigFra = LocalDateTime.now().minusDays(1),
            ),
            historikk = true,
            kanEndres = true,
            innsoktDatoFraArena = LocalDate.parse("2015-02-18"),
        )
        val historisertDeltaker = lagDeltaker(
            navBruker = deltaker.navBruker,
            deltakerliste = deltaker.deltakerliste,
            status = lagDeltakerStatus(
                statusType = DeltakerStatus.Type.HAR_SLUTTET,
                gyldigFra = LocalDateTime.now().minusDays(2),
            ),
            historikk = true,
            kanEndres = false,
            innsoktDatoFraArena = LocalDate.parse("2015-02-18"),
        )

        TestRepository.insert(historisertDeltaker)
        TestRepository.insert(deltaker)

        deltakerService.oppdaterDeltakerLaas(
            deltaker.id,
            deltaker.navBruker.personident,
            deltaker.deltakerliste.id,
        )

        deltakerRepository.get(deltaker.id).getOrThrow().kanEndres shouldBe true
        deltakerRepository.get(historisertDeltaker.id).getOrThrow().kanEndres shouldBe false
    }

    @Test
    fun `oppdaterDeltakerLaas - importerte deltakere med samme innsøktDato, endring på historisert deltaker - beholder låsing`() {
        val deltaker = lagDeltaker(
            status = lagDeltakerStatus(
                statusType = DeltakerStatus.Type.HAR_SLUTTET,
                gyldigFra = LocalDateTime.now().minusDays(1),
            ),
            historikk = true,
            innsoktDatoFraArena = LocalDate.parse("2015-02-18"),
        )
        val historisertDeltaker = lagDeltaker(
            navBruker = deltaker.navBruker,
            deltakerliste = deltaker.deltakerliste,
            status = lagDeltakerStatus(
                statusType = DeltakerStatus.Type.HAR_SLUTTET,
                gyldigFra = LocalDateTime.now().minusDays(2),
            ),
            historikk = true,
            kanEndres = false,
            innsoktDatoFraArena = LocalDate.parse("2015-02-18"),
        )

        TestRepository.insert(historisertDeltaker)
        TestRepository.insert(deltaker)

        deltakerService.oppdaterDeltakerLaas(
            historisertDeltaker.id,
            historisertDeltaker.navBruker.personident,
            historisertDeltaker.deltakerliste.id,
        )

        deltakerRepository.get(deltaker.id).getOrThrow().kanEndres shouldBe true
        deltakerRepository.get(historisertDeltaker.id).getOrThrow().kanEndres shouldBe false
    }

    @Test
    fun `oppdaterDeltakerLaas - låst, unik deltaker på deltakerliste - låser opp`() {
        val deltaker = lagDeltaker(
            kanEndres = false,
            status = lagDeltakerStatus(DeltakerStatus.Type.HAR_SLUTTET),
        )
        TestRepository.insert(deltaker)

        deltakerService.oppdaterDeltakerLaas(
            deltakerId = deltaker.id,
            personident = deltaker.navBruker.personident,
            deltakerlisteId = deltaker.deltakerliste.id,
        )

        deltakerRepository.get(deltaker.id).shouldBeSuccess().kanEndres shouldBe true
    }

    @Test
    fun `oppdaterDeltakerLaas - flere deltakelser på samme deltakerliste - låser den eldste`() {
        val deltaker = lagDeltaker(status = lagDeltakerStatus(DeltakerStatus.Type.HAR_SLUTTET))
        val deltaker2 = lagDeltaker(navBruker = deltaker.navBruker, deltakerliste = deltaker.deltakerliste)
        TestRepository.insert(deltaker)
        TestRepository.insert(deltaker2)

        deltakerService.oppdaterDeltakerLaas(deltaker.id, deltaker.navBruker.personident, deltaker.deltakerliste.id)

        deltakerRepository.get(deltaker.id).getOrThrow().kanEndres shouldBe false
        deltakerRepository.get(deltaker2.id).getOrThrow().kanEndres shouldBe true
    }

    @Test
    fun `oppdaterDeltakerLaas - flere deltakelser på samme deltakerliste, nyeste er feilregistrert - låser begge`() {
        val deltaker = lagDeltaker(status = lagDeltakerStatus(DeltakerStatus.Type.HAR_SLUTTET))
        val deltaker2 = lagDeltaker(
            status = lagDeltakerStatus(DeltakerStatus.Type.FEILREGISTRERT),
            navBruker = deltaker.navBruker,
            deltakerliste = deltaker.deltakerliste,
        )
        TestRepository.insert(deltaker)
        TestRepository.insert(deltaker2)

        deltakerService.oppdaterDeltakerLaas(deltaker.id, deltaker.navBruker.personident, deltaker.deltakerliste.id)

        deltakerRepository.get(deltaker.id).getOrThrow().kanEndres shouldBe false
        deltakerRepository.get(deltaker2.id).getOrThrow().kanEndres shouldBe false
    }

    @Test
    fun `oppdaterDeltakerLaas - flere deltakelser på samme deltakerliste med samme reg dato - låser den med avsluttende status`() {
        val deltaker = lagDeltaker(
            status = lagDeltakerStatus(DeltakerStatus.Type.HAR_SLUTTET),
            historikk = true,
        )
        val deltaker2 = lagDeltaker(
            status = lagDeltakerStatus(DeltakerStatus.Type.DELTAR),
            navBruker = deltaker.navBruker,
            deltakerliste = deltaker.deltakerliste,
            historikk = true,
        ).copy(historikk = deltaker.historikk)

        TestRepository.insert(deltaker)
        TestRepository.insert(deltaker2)

        deltakerService.oppdaterDeltakerLaas(deltaker.id, deltaker.navBruker.personident, deltaker.deltakerliste.id)

        deltakerRepository.get(deltaker.id).getOrThrow().kanEndres shouldBe false
        deltakerRepository.get(deltaker2.id).getOrThrow().kanEndres shouldBe true
    }

    @Test
    fun `oppdaterDeltakerLaas - flere deltakelser på samme deltakerliste med samme reg dato - kaster exception`() {
        val deltaker = lagDeltaker(
            status = lagDeltakerStatus(DeltakerStatus.Type.DELTAR),
            historikk = true,
        )
        val deltaker2 = lagDeltaker(
            status = lagDeltakerStatus(DeltakerStatus.Type.DELTAR),
            navBruker = deltaker.navBruker,
            deltakerliste = deltaker.deltakerliste,
            historikk = true,
        ).copy(historikk = deltaker.historikk)

        TestRepository.insert(deltaker)
        TestRepository.insert(deltaker2)

        assertThrows<IllegalStateException> {
            deltakerService.oppdaterDeltakerLaas(
                deltaker.id,
                deltaker.navBruker.personident,
                deltaker.deltakerliste.id,
            )
        }
    }
}

fun DeltakerEndring.Aarsak.toDeltakerStatusAarsak() = DeltakerStatus.Aarsak(
    DeltakerStatus.Aarsak.Type
        .valueOf(type.name),
    beskrivelse,
)
