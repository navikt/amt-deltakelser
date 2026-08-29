package no.nav.amt.deltaker.service

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.result.shouldBeFailure
import io.kotest.matchers.result.shouldBeSuccess
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import kotlinx.coroutines.test.runTest
import no.nav.amt.deltaker.Environment
import no.nav.amt.deltaker.enkeltplass.kafka.GjennomforingRequestPayload
import no.nav.amt.deltaker.kafka.payload.DeltakerEksternV1Dto
import no.nav.amt.deltaker.kafka.payload.DeltakerV1Dto
import no.nav.amt.deltaker.repository.DeltakerRepositoryTest
import no.nav.amt.deltaker.repository.DeltakerStatusRepository
import no.nav.amt.deltaker.repository.OpplaringKategoriseringRepoAdapter
import no.nav.amt.deltaker.repository.PrisinfoRepoAdapter
import no.nav.amt.deltaker.repository.dbo.PrisinfoDbo
import no.nav.amt.deltaker.utils.IntegrationTestWithDbBase
import no.nav.amt.deltaker.utils.assertNotProduced
import no.nav.amt.deltaker.utils.assertProduced
import no.nav.amt.deltaker.utils.assertProducedHendelse
import no.nav.amt.deltaker.utils.data.TestData.lagDeltaker
import no.nav.amt.deltaker.utils.data.TestData.lagDeltakerStatus
import no.nav.amt.deltaker.utils.data.TestData.lagDeltakerliste
import no.nav.amt.deltaker.utils.data.TestData.lagDeltakerlisteMedDirekteVedtak
import no.nav.amt.deltaker.utils.data.TestData.lagForslag
import no.nav.amt.deltaker.utils.data.TestData.lagTiltakstype
import no.nav.amt.deltaker.utils.data.TestData.lagVedtak
import no.nav.amt.deltaker.utils.data.TestRepository
import no.nav.amt.internapi.deltaker.request.AvsluttDeltakelseRequest
import no.nav.amt.internapi.deltaker.request.BakgrunnsinformasjonRequest
import no.nav.amt.internapi.deltaker.request.DeltakelsesmengdeRequest
import no.nav.amt.internapi.deltaker.request.EndretOpplaringKategoriseringRequest
import no.nav.amt.internapi.deltaker.request.EndretPrisinfoRequest
import no.nav.amt.internapi.deltaker.request.ForlengDeltakelseRequest
import no.nav.amt.internapi.deltaker.request.OpplaringKategoriseringValgRequest
import no.nav.amt.internapi.deltaker.request.ReaktiverDeltakelseRequest
import no.nav.amt.internapi.deltaker.request.StartdatoRequest
import no.nav.amt.internapi.enkeltplass.OpplaringKategoriseringResponse
import no.nav.amt.internapi.hendelse.HendelseType
import no.nav.amt.lib.models.arrangor.melding.Forslag
import no.nav.amt.lib.models.deltaker.Deltakelsesinnhold
import no.nav.amt.lib.models.deltaker.DeltakerEndring
import no.nav.amt.lib.models.deltaker.DeltakerKafkaPayload
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltaker.Innhold
import no.nav.amt.lib.models.deltaker.OpplaringKategoriseringType
import no.nav.amt.lib.models.deltaker.OpplaringKategoriseringValg
import no.nav.amt.lib.models.deltaker.PrisinformasjonDto
import no.nav.amt.lib.models.deltakerliste.SertifiseringValg
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import no.nav.amt.lib.testing.shouldBeCloseTo
import no.nav.amt.lib.testing.utils.TestData.lagNavAnsatt
import no.nav.amt.lib.testing.utils.TestData.lagNavBruker
import no.nav.amt.lib.testing.utils.TestData.lagNavEnhet
import no.nav.amt.lib.testing.utils.TestData.lagOppfolgingsperiode
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

class VeilederEndringServiceTest : IntegrationTestWithDbBase() {
    private val navEnhetInTest = lagNavEnhet(enhetsnummer = "0326")
    private val navAnsattInTest = lagNavAnsatt(navEnhetId = navEnhetInTest.id)

    @BeforeEach
    fun setup() {
        navEnhetRepository.upsert(navEnhetInTest)
        navAnsattRepository.upsert(navAnsattInTest)
    }

    @Nested
    inner class IngenEndring {
        @Test
        fun `godkjenner forslag når deltaker er uendret`() = runTest {
            // Arrange
            val deltaker = lagDeltaker(
                sluttdato = LocalDate.now().minusDays(2),
                sistEndret = LocalDateTime.now().minusDays(2),
            )
            TestRepository.insert(deltaker)
            val forslag = lagForslag(deltakerId = deltaker.id)
            forslagRepository.upsert(forslag)
            val request = AvsluttDeltakelseRequest(
                endretAv = navAnsattInTest.navIdent,
                endretAvEnhet = navEnhetInTest.enhetsnummer,
                sluttdato = deltaker.sluttdato.shouldNotBeNull(),
                aarsak = null,
                begrunnelse = null,
                forslagId = forslag.id,
                harFullfort = null,
            )

            // Act
            veilederEndringService.upsertEndretDeltaker(deltaker.id, request)

            // Assert
            deltakerRepository.get(deltaker.id).shouldBeSuccess().sistEndret shouldBeCloseTo deltaker.sistEndret
            deltakerEndringRepository.getForDeltaker(deltaker.id).shouldBeEmpty()
            assertSoftly(forslagRepository.get(forslag.id).shouldBeSuccess()) {
                it.status.shouldBeInstanceOf<Forslag.Status.Godkjent>()
            }
        }

        @Test
        fun `uendret deltaker uten forslag returnerer eksisterende deltaker`() = runTest {
            // Arrange
            val deltaker = lagDeltaker(
                sluttdato = LocalDate.now().minusDays(2),
                sistEndret = LocalDateTime.now().minusDays(2),
            )
            TestRepository.insert(deltaker)
            val request = AvsluttDeltakelseRequest(
                endretAv = navAnsattInTest.navIdent,
                endretAvEnhet = navEnhetInTest.enhetsnummer,
                sluttdato = deltaker.sluttdato.shouldNotBeNull(),
                aarsak = null,
                begrunnelse = null,
                forslagId = null,
                harFullfort = null,
            )

            // Act
            val resultat = veilederEndringService.upsertEndretDeltaker(deltaker.id, request)

            // Assert
            resultat.sistEndret shouldBeCloseTo deltaker.sistEndret
            deltakerEndringRepository.getForDeltaker(deltaker.id).shouldBeEmpty()
        }
    }

    @Nested
    inner class Prisinfo {
        @Test
        fun `identisk prisinfo gir ingen endring`() = runTest {
            // Arrange
            val deltaker = lagDeltaker(
                status = lagDeltakerStatus(DeltakerStatus.Type.DELTAR),
            )
            val vedtak = lagVedtak(
                deltakerId = deltaker.id,
                opprettetAv = navAnsattInTest,
                opprettetAvEnhet = navEnhetInTest,
            )
            TestRepository.insertAll(deltaker, vedtak)
            val prisinfo = PrisinformasjonDto.Anskaffelse(pris = 5000)
            PrisinfoRepoAdapter.lagrePrisinfoEndring(
                gjennomforingId = deltaker.deltakerliste.id,
                prisinformasjon = prisinfo,
            )
            val request = EndretPrisinfoRequest(
                endretAv = navAnsattInTest.navIdent,
                endretAvEnhet = navEnhetInTest.enhetsnummer,
                prisinfo = prisinfo,
                begrunnelse = "Oppdatert prisgrunnlag",
            )
            val lagretDeltaker = deltakerRepository.get(deltaker.id).shouldBeSuccess()

            // Act
            val resultat = veilederEndringService.upsertEndretDeltaker(
                deltakerId = deltaker.id,
                endringRequest = request,
            )

            // Assert
            DeltakerRepositoryTest.assertDeltakereAreEqual(resultat, lagretDeltaker)
            deltakerEndringRepository.getForDeltaker(deltaker.id).shouldBeEmpty()
            PrisinfoRepoAdapter.hentPrisinfo(
                gjennomforingId = deltaker.deltakerliste.id,
                rolle = PrisinfoDbo.Rolle.ENDRING,
            ) shouldBe prisinfo
            outboxService.assertNotProduced<GjennomforingRequestPayload.EnkeltplassEndrePrisinformasjon>(
                expectedKey = deltaker.deltakerliste.id,
                expectedTopic = Environment.GJENNOMFORING_REQUEST_TOPIC,
            )
        }

        @Test
        fun `endret prisinfo lagrer ny pris og produserer hendelse`() = runTest {
            // Arrange
            val deltaker = lagDeltaker(
                status = lagDeltakerStatus(DeltakerStatus.Type.DELTAR),
            )
            val vedtak = lagVedtak(
                deltakerId = deltaker.id,
                deltakerVedVedtak = deltaker,
                opprettetAv = navAnsattInTest,
                opprettetAvEnhet = navEnhetInTest,
                fattet = LocalDateTime.now(),
            )
            TestRepository.insertAll(deltaker, vedtak)
            val gammelPrisinfo = PrisinformasjonDto.Anskaffelse(pris = 5000)
            PrisinfoRepoAdapter.lagrePrisinfoEndring(
                gjennomforingId = deltaker.deltakerliste.id,
                prisinformasjon = gammelPrisinfo,
            )
            val nyPrisinfo = PrisinformasjonDto.Anskaffelse(pris = 7500)
            val request = EndretPrisinfoRequest(
                endretAv = navAnsattInTest.navIdent,
                endretAvEnhet = navEnhetInTest.enhetsnummer,
                prisinfo = nyPrisinfo,
                begrunnelse = "Ny pris fra leverandør",
            )

            // Act
            veilederEndringService.upsertEndretDeltaker(
                deltakerId = deltaker.id,
                endringRequest = request,
            )

            // Assert
            PrisinfoRepoAdapter.hentPrisinfo(
                gjennomforingId = deltaker.deltakerliste.id,
                rolle = PrisinfoDbo.Rolle.ENDRING,
            ) shouldBe nyPrisinfo
            deltakerEndringRepository.getForDeltaker(deltaker.id) shouldHaveSize 1
            outboxService.assertProducedHendelse<HendelseType.EnkeltplassEndrePrisinfo>(deltaker.id)
        }
    }

    @Nested
    inner class OpplaringKategorisering {
        private val kodeverkId = UUID.randomUUID()
        private val sertifiseringer = setOf(SertifiseringValg(id = 1, navn = "Truckfører T1"))

        private fun mockKategoriseringKlient(tiltakskode: Tiltakskode) {
            coEvery {
                opplaringKategoriseringClient.hentOpplaringKategorisering(tiltakskode)
            } returns OpplaringKategoriseringResponse(
                tiltakskode = tiltakskode,
                alternativer = listOf(
                    OpplaringKategoriseringResponse.Alternativ.Verdigruppe(
                        id = UUID.randomUUID(),
                        visningsnavn = "Bransje",
                        representerer = OpplaringKategoriseringType.BRANSJE_ID,
                        pakrevd = true,
                        seleksjonstype = OpplaringKategoriseringResponse.Seleksjonstype.ENKELTVALG,
                        alternativer = listOf(
                            OpplaringKategoriseringResponse.Alternativ.Verdi(
                                id = kodeverkId,
                                visningsnavn = "Bygg og anlegg",
                            ),
                        ),
                    ),
                ),
                sertifiseringValg = sertifiseringer,
            )
        }

        @Test
        fun `endret kategorisering lagrer valg, historikk og hendelse`() = runTest {
            // Arrange
            val deltaker = lagDeltaker(
                status = lagDeltakerStatus(DeltakerStatus.Type.SOKT_INN),
            )
            val vedtak = lagVedtak(
                deltakerId = deltaker.id,
                deltakerVedVedtak = deltaker,
                opprettetAv = navAnsattInTest,
                opprettetAvEnhet = navEnhetInTest,
                fattet = LocalDateTime.now(),
            )
            TestRepository.insertAll(deltaker, vedtak)
            mockKategoriseringKlient(deltaker.deltakerliste.tiltakstype.tiltakskode)
            val request = EndretOpplaringKategoriseringRequest(
                endretAv = navAnsattInTest.navIdent,
                endretAvEnhet = navEnhetInTest.enhetsnummer,
                beskrivelse = "Oppdaterer opplæringskategorisering",
                opplaringKategoriseringValg = setOf(
                    OpplaringKategoriseringValgRequest(
                        representerer = OpplaringKategoriseringType.BRANSJE_ID,
                        valgteIder = setOf(kodeverkId),
                    ),
                ),
                sertifiseringValg = sertifiseringer,
                pavirkerPris = false,
            )

            // Act
            val resultat = veilederEndringService.upsertEndretDeltaker(
                deltakerId = deltaker.id,
                endringRequest = request,
            )

            // Assert
            resultat.status.type shouldBe deltaker.status.type
            resultat.deltakelsesinnhold?.getAnnetFritekstBeskrivelse() shouldBe request.beskrivelse

            val lagretKategorisering = OpplaringKategoriseringRepoAdapter.hentOpplaringKategoriseringValg(deltaker.deltakerliste.id)
            lagretKategorisering shouldBe OpplaringKategoriseringValg(
                valgteKategoriseringer = setOf(
                    OpplaringKategoriseringValg.ValgteFelt(
                        representerer = OpplaringKategoriseringType.BRANSJE_ID,
                        valg = mapOf(kodeverkId to "Bygg og anlegg"),
                    ),
                ),
                valgteSertifiseringer = sertifiseringer,
            )
            assertSoftly(deltakerEndringRepository.getForDeltaker(deltaker.id).first()) {
                endretAv shouldBe navAnsattInTest.id
                endretAvEnhet shouldBe navEnhetInTest.id
                assertSoftly(endring.shouldBeInstanceOf<DeltakerEndring.Endring.EndreOpplaringKategorisering>()) {
                    opplaringKategoriseringValg shouldBe lagretKategorisering
                    beskrivelse shouldBe request.beskrivelse
                }
            }
            outboxService.assertProducedHendelse<HendelseType.EnkeltplassEndreOpplaringKategorisering>(deltaker.id)
            outboxService.assertProduced<DeltakerKafkaPayload>(deltaker.id, Environment.DELTAKER_V2_TOPIC)
            outboxService.assertProduced<DeltakerV1Dto>(deltaker.id, Environment.DELTAKER_V1_TOPIC)
            outboxService.assertProduced<DeltakerEksternV1Dto>(deltaker.id, Environment.DELTAKER_EKSTERN_V1_TOPIC)
        }

        @Test
        fun `identisk kategorisering gir ingen endring`() = runTest {
            // Arrange
            val beskrivelse = "Eksisterende beskrivelse"
            val deltaker = lagDeltaker(
                status = lagDeltakerStatus(DeltakerStatus.Type.SOKT_INN),
                innhold = Deltakelsesinnhold(
                    ledetekst = "ledetekst",
                    innhold = listOf(
                        Innhold(
                            tekst = "Annet",
                            innholdskode = "annet",
                            valgt = true,
                            beskrivelse = beskrivelse,
                        ),
                    ),
                ),
            )
            val vedtak = lagVedtak(
                deltakerId = deltaker.id,
                deltakerVedVedtak = deltaker,
                opprettetAv = navAnsattInTest,
                opprettetAvEnhet = navEnhetInTest,
                fattet = LocalDateTime.now(),
            )
            TestRepository.insertAll(deltaker, vedtak)
            OpplaringKategoriseringRepoAdapter.lagreOpplaringKategoriseringValg(
                gjennomforingId = deltaker.deltakerliste.id,
                valgteVerdier = setOf(
                    OpplaringKategoriseringValg.ValgteFelt(
                        representerer = OpplaringKategoriseringType.BRANSJE_ID,
                        valg = mapOf(kodeverkId to "Bygg og anlegg"),
                    ),
                ),
                valgteSertifiseringer = sertifiseringer,
            )
            val request = EndretOpplaringKategoriseringRequest(
                endretAv = navAnsattInTest.navIdent,
                endretAvEnhet = navEnhetInTest.enhetsnummer,
                beskrivelse = beskrivelse,
                opplaringKategoriseringValg = setOf(
                    OpplaringKategoriseringValgRequest(
                        representerer = OpplaringKategoriseringType.BRANSJE_ID,
                        valgteIder = setOf(kodeverkId),
                    ),
                ),
                sertifiseringValg = sertifiseringer,
                pavirkerPris = false,
            )
            val lagretDeltaker = deltakerRepository.get(deltaker.id).shouldBeSuccess()

            // Act
            val resultat = veilederEndringService.upsertEndretDeltaker(
                deltakerId = deltaker.id,
                endringRequest = request,
            )

            // Assert
            DeltakerRepositoryTest.assertDeltakereAreEqual(resultat, lagretDeltaker)
            deltakerEndringRepository.getForDeltaker(deltaker.id).shouldBeEmpty()
        }

        @Test
        fun `kun valg endret lagrer valg uten å endre deltaker`() = runTest {
            // Arrange — beskrivelse uendret, men kodeverk-valg er forskjellige
            val beskrivelse = "Eksisterende beskrivelse"
            val gammelKodeverkId = UUID.randomUUID()
            val deltaker = lagDeltaker(
                status = lagDeltakerStatus(DeltakerStatus.Type.SOKT_INN),
                innhold = Deltakelsesinnhold(
                    ledetekst = "ledetekst",
                    innhold = listOf(
                        Innhold(
                            tekst = "Annet",
                            innholdskode = "annet",
                            valgt = true,
                            beskrivelse = beskrivelse,
                        ),
                    ),
                ),
            )
            val vedtak = lagVedtak(
                deltakerId = deltaker.id,
                deltakerVedVedtak = deltaker,
                opprettetAv = navAnsattInTest,
                opprettetAvEnhet = navEnhetInTest,
                fattet = LocalDateTime.now(),
            )
            TestRepository.insertAll(deltaker, vedtak)
            OpplaringKategoriseringRepoAdapter.lagreOpplaringKategoriseringValg(
                gjennomforingId = deltaker.deltakerliste.id,
                valgteVerdier = setOf(
                    OpplaringKategoriseringValg.ValgteFelt(
                        representerer = OpplaringKategoriseringType.BRANSJE_ID,
                        valg = mapOf(gammelKodeverkId to "Gammel bransje"),
                    ),
                ),
                valgteSertifiseringer = sertifiseringer,
            )
            mockKategoriseringKlient(deltaker.deltakerliste.tiltakstype.tiltakskode)
            val request = EndretOpplaringKategoriseringRequest(
                endretAv = navAnsattInTest.navIdent,
                endretAvEnhet = navEnhetInTest.enhetsnummer,
                beskrivelse = beskrivelse,
                opplaringKategoriseringValg = setOf(
                    OpplaringKategoriseringValgRequest(
                        representerer = OpplaringKategoriseringType.BRANSJE_ID,
                        valgteIder = setOf(kodeverkId),
                    ),
                ),
                sertifiseringValg = sertifiseringer,
                pavirkerPris = false,
            )

            // Act
            val resultat = veilederEndringService.upsertEndretDeltaker(
                deltakerId = deltaker.id,
                endringRequest = request,
            )

            // Assert — deltaker-objektet er uendret, men valg er lagret
            resultat.deltakelsesinnhold?.getAnnetFritekstBeskrivelse() shouldBe beskrivelse
            deltakerEndringRepository.getForDeltaker(deltaker.id) shouldHaveSize 1
        }

        @Test
        fun `kun beskrivelse endret oppdaterer deltaker`() = runTest {
            // Arrange — kodeverk-valg identisk, men beskrivelse er annerledes
            val deltaker = lagDeltaker(
                status = lagDeltakerStatus(DeltakerStatus.Type.SOKT_INN),
                innhold = Deltakelsesinnhold(
                    ledetekst = "ledetekst",
                    innhold = listOf(
                        Innhold(
                            tekst = "Annet",
                            innholdskode = "annet",
                            valgt = true,
                            beskrivelse = "Gammel beskrivelse",
                        ),
                    ),
                ),
            )
            val vedtak = lagVedtak(
                deltakerId = deltaker.id,
                deltakerVedVedtak = deltaker,
                opprettetAv = navAnsattInTest,
                opprettetAvEnhet = navEnhetInTest,
                fattet = LocalDateTime.now(),
            )
            TestRepository.insertAll(deltaker, vedtak)
            OpplaringKategoriseringRepoAdapter.lagreOpplaringKategoriseringValg(
                gjennomforingId = deltaker.deltakerliste.id,
                valgteVerdier = setOf(
                    OpplaringKategoriseringValg.ValgteFelt(
                        representerer = OpplaringKategoriseringType.BRANSJE_ID,
                        valg = mapOf(kodeverkId to "Bygg og anlegg"),
                    ),
                ),
                valgteSertifiseringer = sertifiseringer,
            )
            mockKategoriseringKlient(deltaker.deltakerliste.tiltakstype.tiltakskode)
            val request = EndretOpplaringKategoriseringRequest(
                endretAv = navAnsattInTest.navIdent,
                endretAvEnhet = navEnhetInTest.enhetsnummer,
                beskrivelse = "Ny beskrivelse",
                opplaringKategoriseringValg = setOf(
                    OpplaringKategoriseringValgRequest(
                        representerer = OpplaringKategoriseringType.BRANSJE_ID,
                        valgteIder = setOf(kodeverkId),
                    ),
                ),
                sertifiseringValg = sertifiseringer,
                pavirkerPris = false,
            )

            // Act
            val resultat = veilederEndringService.upsertEndretDeltaker(
                deltakerId = deltaker.id,
                endringRequest = request,
            )

            // Assert
            resultat.deltakelsesinnhold?.getAnnetFritekstBeskrivelse() shouldBe "Ny beskrivelse"
            deltakerEndringRepository.getForDeltaker(deltaker.id) shouldHaveSize 1
        }
    }

    @Nested
    inner class FremtidigAvslutning {
        @Test
        fun `avslutt deltakelse i fremtiden setter fremtidig status`() = runTest {
            // Arrange
            val deltaker = lagDeltaker(
                status = lagDeltakerStatus(DeltakerStatus.Type.HAR_SLUTTET),
            )
            val vedtak = lagVedtak(
                deltakerId = deltaker.id,
                deltakerVedVedtak = deltaker,
                opprettetAv = navAnsattInTest,
                opprettetAvEnhet = navEnhetInTest,
            )
            TestRepository.insertAll(deltaker, vedtak)
            val request = AvsluttDeltakelseRequest(
                endretAv = navAnsattInTest.navIdent,
                endretAvEnhet = navEnhetInTest.enhetsnummer,
                sluttdato = LocalDate.now().plusWeeks(1),
                aarsak = DeltakerEndring.Aarsak(DeltakerEndring.Aarsak.Type.FATT_JOBB, null),
                begrunnelse = "Avslutter fremtidig",
                forslagId = null,
                harFullfort = null,
            )

            // Act
            val resultat = veilederEndringService.upsertEndretDeltaker(deltaker.id, request)

            // Assert
            resultat.status.type shouldBe DeltakerStatus.Type.DELTAR
            assertSoftly(DeltakerStatusRepository.getGjeldendeDeltakerStatus(deltaker.id).shouldNotBeNull()) {
                type shouldBe DeltakerStatus.Type.DELTAR
            }
            val fremtidigeStatuser = TestRepository.getFremtidigeDeltakerStatuser(deltaker.id)
            fremtidigeStatuser shouldHaveSize 1
            assertSoftly(fremtidigeStatuser.first()) {
                type shouldBe DeltakerStatus.Type.HAR_SLUTTET
                aarsak?.type shouldBe DeltakerStatus.Aarsak.Type.FATT_JOBB
                gyldigFra.toLocalDate() shouldBe request.sluttdato.plusDays(1)
                gyldigTil shouldBe null
            }
        }

        @Test
        fun `avslutt kursdeltaker i fremtiden setter fremtidig FULLFORT`() = runTest {
            // Arrange
            val deltaker = lagDeltaker(
                status = lagDeltakerStatus(DeltakerStatus.Type.DELTAR),
                sluttdato = LocalDate.now().plusMonths(1),
                deltakerliste = lagDeltakerliste(
                    tiltakstype = lagTiltakstype(tiltakskode = Tiltakskode.GRUPPE_ARBEIDSMARKEDSOPPLAERING),
                ),
            )
            val vedtak = lagVedtak(
                deltakerId = deltaker.id,
                deltakerVedVedtak = deltaker,
                opprettetAv = navAnsattInTest,
                opprettetAvEnhet = navEnhetInTest,
                fattet = LocalDateTime.now(),
            )
            TestRepository.insertAll(deltaker, vedtak)
            val request = AvsluttDeltakelseRequest(
                endretAv = navAnsattInTest.navIdent,
                endretAvEnhet = navEnhetInTest.enhetsnummer,
                sluttdato = LocalDate.now().plusWeeks(1),
                aarsak = DeltakerEndring.Aarsak(DeltakerEndring.Aarsak.Type.FATT_JOBB, null),
                begrunnelse = null,
                forslagId = null,
                harFullfort = null,
            )

            // Act
            val resultat = veilederEndringService.upsertEndretDeltaker(deltaker.id, request)

            // Assert
            resultat.status.type shouldBe DeltakerStatus.Type.DELTAR
            resultat.sluttdato shouldBe request.sluttdato

            assertSoftly(TestRepository.getFremtidigeDeltakerStatuser(deltaker.id).first()) {
                gyldigFra.toLocalDate() shouldBe request.sluttdato.plusDays(1)
                type shouldBe DeltakerStatus.Type.FULLFORT
                aarsak.shouldNotBeNull().type shouldBe DeltakerStatus.Aarsak.Type.FATT_JOBB
            }
        }

        @Test
        fun `forleng deltaker deaktiverer fremtidig status`() = runTest {
            // Arrange
            val deltaker = lagDeltaker(
                status = lagDeltakerStatus(DeltakerStatus.Type.DELTAR),
                sluttdato = LocalDate.now().plusDays(2),
            )
            val vedtak = lagVedtak(
                deltakerId = deltaker.id,
                deltakerVedVedtak = deltaker,
                opprettetAv = navAnsattInTest,
                opprettetAvEnhet = navEnhetInTest,
                fattet = LocalDateTime.now(),
            )
            TestRepository.insertAll(deltaker, vedtak)

            val fremtidigHarSluttetStatus = lagDeltakerStatus(
                statusType = DeltakerStatus.Type.HAR_SLUTTET,
                gyldigFra = LocalDateTime.now().plusDays(2),
            )
            DeltakerStatusRepository.lagreStatus(deltakerId = deltaker.id, deltakerStatus = fremtidigHarSluttetStatus)

            val request = ForlengDeltakelseRequest(
                endretAv = navAnsattInTest.navIdent,
                endretAvEnhet = navEnhetInTest.enhetsnummer,
                sluttdato = LocalDate.now().plusMonths(1),
                begrunnelse = "~begrunnelse~",
                forslagId = null,
            )

            // Act
            val resultat = veilederEndringService.upsertEndretDeltaker(deltaker.id, request)

            // Assert
            resultat.status.type shouldBe DeltakerStatus.Type.DELTAR
            resultat.sluttdato shouldBe request.sluttdato

            assertSoftly(TestRepository.getDeltakerStatus(deltaker.status.id)) {
                gyldigTil.shouldBeNull()
                type shouldBe DeltakerStatus.Type.DELTAR
            }

            assertSoftly(TestRepository.getDeltakerStatus(fremtidigHarSluttetStatus.id)) {
                gyldigTil.shouldNotBeNull()
                type shouldBe DeltakerStatus.Type.HAR_SLUTTET
            }
        }

        @Test
        fun `har sluttet og avsluttes i fremtiden blir DELTAR med fremtidig HAR_SLUTTET`() = runTest {
            // Arrange
            val deltaker = lagDeltaker(
                status = lagDeltakerStatus(DeltakerStatus.Type.HAR_SLUTTET),
                sluttdato = LocalDate.now().minusWeeks(1),
            )
            val vedtak = lagVedtak(
                deltakerId = deltaker.id,
                deltakerVedVedtak = deltaker,
                opprettetAv = navAnsattInTest,
                opprettetAvEnhet = navEnhetInTest,
                fattet = LocalDateTime.now(),
            )
            TestRepository.insertAll(deltaker, vedtak)
            val request = AvsluttDeltakelseRequest(
                endretAv = navAnsattInTest.navIdent,
                endretAvEnhet = navEnhetInTest.enhetsnummer,
                sluttdato = LocalDate.now().plusWeeks(1),
                aarsak = DeltakerEndring.Aarsak(DeltakerEndring.Aarsak.Type.FATT_JOBB, null),
                begrunnelse = null,
                forslagId = null,
                harFullfort = null,
            )

            // Act
            val resultat = veilederEndringService.upsertEndretDeltaker(deltaker.id, request)

            // Assert
            resultat.status.type shouldBe DeltakerStatus.Type.DELTAR
            resultat.sluttdato shouldBe request.sluttdato

            assertSoftly(TestRepository.getDeltakerStatus(deltaker.status.id)) {
                gyldigTil shouldBeCloseTo LocalDateTime.now()
                type shouldBe DeltakerStatus.Type.HAR_SLUTTET
            }

            val oppdatertDeltaker = deltakerRepository.get(deltaker.id).shouldBeSuccess()
            assertSoftly(TestRepository.getDeltakerStatus(oppdatertDeltaker.status.id)) {
                gyldigTil.shouldBeNull()
                type shouldBe DeltakerStatus.Type.DELTAR
            }

            assertSoftly(TestRepository.getFremtidigeDeltakerStatuser(oppdatertDeltaker.id).first()) {
                gyldigTil shouldBe null
                gyldigFra.toLocalDate() shouldBe request.sluttdato.plusDays(1)
                type shouldBe DeltakerStatus.Type.HAR_SLUTTET
                aarsak.shouldNotBeNull().type shouldBe DeltakerStatus.Aarsak.Type.FATT_JOBB
            }
        }
    }

    @Nested
    inner class Reaktivering {
        @Test
        fun `reaktiverer deltaker og sletter kladd`() = runTest {
            // Arrange
            val deltakerliste = lagDeltakerlisteMedDirekteVedtak()
            val deltaker = lagDeltaker(
                deltakerliste = deltakerliste,
                status = lagDeltakerStatus(DeltakerStatus.Type.IKKE_AKTUELL),
            )
            val vedtak = lagVedtak(
                deltakerId = deltaker.id,
                deltakerVedVedtak = deltaker,
                opprettetAv = navAnsattInTest,
                opprettetAvEnhet = navEnhetInTest,
                fattet = LocalDateTime.now(),
            )
            TestRepository.insertAll(deltaker, vedtak)
            val kladd = lagDeltaker(
                deltakerliste = deltakerliste,
                navBruker = deltaker.navBruker,
                status = lagDeltakerStatus(DeltakerStatus.Type.KLADD),
            )
            TestRepository.insert(kladd)
            val request = ReaktiverDeltakelseRequest(
                endretAv = navAnsattInTest.navIdent,
                endretAvEnhet = navEnhetInTest.enhetsnummer,
                begrunnelse = "Reaktiverer deltaker",
            )

            // Act
            val resultat = veilederEndringService.upsertEndretDeltaker(deltaker.id, request)

            // Assert
            resultat.status.type shouldBe DeltakerStatus.Type.VENTER_PA_OPPSTART
            resultat.startdato shouldBe null
            resultat.sluttdato shouldBe null
            deltakerRepository.get(kladd.id).shouldBeFailure()
        }

        @Test
        fun `reaktiverer deltaker uten eksisterende kladd`() = runTest {
            // Arrange — ingen kladd finnes for denne deltakerlisten
            val deltakerliste = lagDeltakerlisteMedDirekteVedtak()
            val deltaker = lagDeltaker(
                deltakerliste = deltakerliste,
                status = lagDeltakerStatus(DeltakerStatus.Type.IKKE_AKTUELL),
            )
            val vedtak = lagVedtak(
                deltakerId = deltaker.id,
                deltakerVedVedtak = deltaker,
                opprettetAv = navAnsattInTest,
                opprettetAvEnhet = navEnhetInTest,
                fattet = LocalDateTime.now(),
            )
            TestRepository.insertAll(deltaker, vedtak)
            val request = ReaktiverDeltakelseRequest(
                endretAv = navAnsattInTest.navIdent,
                endretAvEnhet = navEnhetInTest.enhetsnummer,
                begrunnelse = "Reaktiverer deltaker",
            )

            // Act
            val resultat = veilederEndringService.upsertEndretDeltaker(deltaker.id, request)

            // Assert
            resultat.status.type shouldBe DeltakerStatus.Type.VENTER_PA_OPPSTART
        }

        @Test
        fun `uten aktiv oppfolging kaster feil`() = runTest {
            // Arrange
            val deltaker = lagDeltaker(
                status = lagDeltakerStatus(DeltakerStatus.Type.HAR_SLUTTET),
            ).copy(
                navBruker = lagNavBruker(oppfolgingsperioder = emptyList()),
            )
            TestRepository.insert(deltaker)
            val request = ReaktiverDeltakelseRequest(
                endretAv = navAnsattInTest.navIdent,
                endretAvEnhet = navEnhetInTest.enhetsnummer,
                begrunnelse = "Reaktiverer deltaker",
            )

            // Act & Assert
            shouldThrow<IllegalArgumentException> {
                veilederEndringService.upsertEndretDeltaker(deltaker.id, request)
            }.message shouldBe "Kan ikke utføre endring ReaktiverDeltakelseRequest på deltaker ${deltaker.id} uten aktiv oppfølgingsperiode"
        }
    }

    @Nested
    inner class Deltakelsesmengde {
        @Test
        fun `endret deltakelsesmengde upserter endring`() = runTest {
            // Arrange
            val deltaker = lagDeltaker(
                startdato = LocalDate.now().minusMonths(3),
                sluttdato = LocalDate.now().plusMonths(3),
                status = lagDeltakerStatus(DeltakerStatus.Type.DELTAR),
            )
            val vedtak = lagVedtak(
                deltakerId = deltaker.id,
                opprettetAv = navAnsattInTest,
                opprettetAvEnhet = navEnhetInTest,
            )
            TestRepository.insertAll(deltaker, vedtak)
            val request = DeltakelsesmengdeRequest(
                endretAv = navAnsattInTest.navIdent,
                endretAvEnhet = navEnhetInTest.enhetsnummer,
                deltakelsesprosent = 50,
                dagerPerUke = null,
                forslagId = null,
                begrunnelse = "begrunnelse",
                gyldigFra = LocalDate.now(),
            )

            // Act
            val resultat = veilederEndringService.upsertEndretDeltaker(deltaker.id, request)

            // Assert
            resultat.deltakelsesprosent shouldBe request.deltakelsesprosent?.toFloat()
            resultat.dagerPerUke shouldBe null

            assertSoftly(deltakerRepository.get(deltaker.id).shouldBeSuccess()) {
                deltakelsesprosent shouldBe request.deltakelsesprosent?.toFloat()
                dagerPerUke shouldBe null
            }

            val endring = deltakerEndringRepository.getForDeltaker(deltaker.id).first()
            assertSoftly(endring.endring.shouldBeInstanceOf<DeltakerEndring.Endring.EndreDeltakelsesmengde>()) {
                deltakelsesprosent shouldBe request.deltakelsesprosent
                dagerPerUke shouldBe request.dagerPerUke
            }

            outboxService.assertProducedHendelse<HendelseType.EndreDeltakelsesmengde>(deltaker.id)
            outboxService.assertProduced<DeltakerKafkaPayload>(deltaker.id, Environment.DELTAKER_V2_TOPIC)
            outboxService.assertProduced<DeltakerV1Dto>(deltaker.id, Environment.DELTAKER_V1_TOPIC)
            outboxService.assertProduced<DeltakerEksternV1Dto>(deltaker.id, Environment.DELTAKER_EKSTERN_V1_TOPIC)
        }

        @Test
        fun `fremtidig deltakelsesmengde endrer ikke deltaker`() = runTest {
            // Arrange
            val deltaker = lagDeltaker(
                startdato = LocalDate.now().minusMonths(3),
                sluttdato = LocalDate.now().plusMonths(3),
                status = lagDeltakerStatus(DeltakerStatus.Type.DELTAR),
            )
            val vedtak = lagVedtak(
                deltakerId = deltaker.id,
                opprettetAv = navAnsattInTest,
                opprettetAvEnhet = navEnhetInTest,
            )
            TestRepository.insertAll(deltaker, vedtak)
            val request = DeltakelsesmengdeRequest(
                endretAv = navAnsattInTest.navIdent,
                endretAvEnhet = navEnhetInTest.enhetsnummer,
                deltakelsesprosent = 50,
                dagerPerUke = null,
                forslagId = null,
                begrunnelse = "begrunnelse",
                gyldigFra = LocalDate.now().plusDays(1),
            )

            // Act
            val resultat = veilederEndringService.upsertEndretDeltaker(deltaker.id, request)

            // Assert
            resultat.deltakelsesprosent shouldBe deltaker.deltakelsesprosent
            resultat.dagerPerUke shouldBe deltaker.dagerPerUke
        }

        @Test
        fun `gyldigFra etter sluttdato kaster feil`() = runTest {
            // Arrange
            val sluttdato = LocalDate.now().plusMonths(1)
            val deltaker = lagDeltaker(
                startdato = LocalDate.now().minusMonths(1),
                sluttdato = sluttdato,
                status = lagDeltakerStatus(DeltakerStatus.Type.DELTAR),
            )
            val vedtak = lagVedtak(
                deltakerId = deltaker.id,
                opprettetAv = navAnsattInTest,
                opprettetAvEnhet = navEnhetInTest,
            )
            TestRepository.insertAll(deltaker, vedtak)
            val request = DeltakelsesmengdeRequest(
                endretAv = navAnsattInTest.navIdent,
                endretAvEnhet = navEnhetInTest.enhetsnummer,
                deltakelsesprosent = 50,
                dagerPerUke = null,
                forslagId = null,
                begrunnelse = "begrunnelse",
                gyldigFra = sluttdato.plusDays(1),
            )

            // Act & Assert
            shouldThrow<IllegalArgumentException> {
                veilederEndringService.upsertEndretDeltaker(deltaker.id, request)
            }
        }
    }

    @Nested
    inner class Datoer {
        @Test
        fun `endret datoer upserter endring`() = runTest {
            // Arrange
            val deltaker = lagDeltaker(
                status = lagDeltakerStatus(DeltakerStatus.Type.DELTAR),
                sluttdato = LocalDate.now().plusDays(1),
            )
            val vedtak = lagVedtak(
                deltakerId = deltaker.id,
                opprettetAv = navAnsattInTest,
                opprettetAvEnhet = navEnhetInTest,
            )
            TestRepository.insertAll(deltaker, vedtak)
            val request = StartdatoRequest(
                endretAv = navAnsattInTest.navIdent,
                endretAvEnhet = navEnhetInTest.enhetsnummer,
                startdato = LocalDate.now().minusWeeks(1),
                sluttdato = LocalDate.now().plusWeeks(4),
                begrunnelse = null,
                forslagId = null,
            )

            // Act
            val resultat = veilederEndringService.upsertEndretDeltaker(deltaker.id, request)

            // Assert
            assertSoftly(resultat) {
                startdato shouldBe request.startdato
                sluttdato shouldBe request.sluttdato
                status.type shouldBe DeltakerStatus.Type.DELTAR
            }

            assertSoftly(deltakerRepository.get(deltaker.id).shouldBeSuccess()) {
                startdato shouldBe request.startdato
                sluttdato shouldBe request.sluttdato
            }

            assertSoftly(deltakerEndringRepository.getForDeltaker(deltaker.id).first()) {
                endretAv shouldBe navAnsattInTest.id
                endretAvEnhet shouldBe navEnhetInTest.id
                assertSoftly(it.endring.shouldBeInstanceOf<DeltakerEndring.Endring.EndreStartdato>()) {
                    startdato shouldBe request.startdato
                    sluttdato shouldBe request.sluttdato
                }
            }

            outboxService.assertProducedHendelse<HendelseType.EndreStartdato>(deltaker.id)
            outboxService.assertProduced<DeltakerKafkaPayload>(deltaker.id, Environment.DELTAKER_V2_TOPIC)
            outboxService.assertProduced<DeltakerV1Dto>(deltaker.id, Environment.DELTAKER_V1_TOPIC)
        }

        @Test
        fun `endret startdato oppdaterer dato og status`() = runTest {
            // Arrange
            val deltakersSluttdato = LocalDate.now().plusWeeks(3)
            val deltaker = lagDeltaker(
                status = lagDeltakerStatus(DeltakerStatus.Type.VENTER_PA_OPPSTART),
                startdato = LocalDate.now().plusDays(3),
                sluttdato = deltakersSluttdato,
            )
            val vedtak = lagVedtak(
                deltakerId = deltaker.id,
                opprettetAv = navAnsattInTest,
                opprettetAvEnhet = navEnhetInTest,
            )
            TestRepository.insertAll(deltaker, vedtak)
            val request = StartdatoRequest(
                endretAv = navAnsattInTest.navIdent,
                endretAvEnhet = navEnhetInTest.enhetsnummer,
                startdato = LocalDate.now().minusWeeks(2),
                sluttdato = deltakersSluttdato,
                begrunnelse = null,
                forslagId = null,
            )

            // Act
            val resultat = veilederEndringService.upsertEndretDeltaker(deltaker.id, request)

            // Assert
            assertSoftly(resultat) {
                startdato shouldBe request.startdato
                sluttdato shouldBe deltakersSluttdato
                status.type shouldBe DeltakerStatus.Type.DELTAR
            }

            assertSoftly(deltakerRepository.get(deltaker.id).shouldBeSuccess()) {
                status.type shouldBe DeltakerStatus.Type.DELTAR
                startdato shouldBe request.startdato
                sluttdato shouldBe deltakersSluttdato
            }

            outboxService.assertProducedHendelse<HendelseType.EndreStartdato>(deltaker.id)
            outboxService.assertProduced<DeltakerKafkaPayload>(deltaker.id, Environment.DELTAKER_V2_TOPIC)
            outboxService.assertProduced<DeltakerV1Dto>(deltaker.id, Environment.DELTAKER_V1_TOPIC)
            outboxService.assertProduced<DeltakerEksternV1Dto>(deltaker.id, Environment.DELTAKER_EKSTERN_V1_TOPIC)
        }
    }

    @Nested
    inner class Oppfolgingsperiode {
        @Test
        fun `aktiv oppfolging med endring som krever oppfolging utforer endring`() = runTest {
            // Arrange
            val navBruker = lagNavBruker(
                oppfolgingsperioder = listOf(
                    lagOppfolgingsperiode(
                        startdato = LocalDateTime.now().minusMonths(2),
                        sluttdato = null,
                    ),
                ),
            )
            val deltaker = lagDeltaker(
                navBruker = navBruker,
                status = lagDeltakerStatus(DeltakerStatus.Type.DELTAR),
                bakgrunnsinformasjon = "Gammel informasjon",
            )
            val vedtak = lagVedtak(
                deltakerId = deltaker.id,
                deltakerVedVedtak = deltaker,
                opprettetAv = navAnsattInTest,
                opprettetAvEnhet = navEnhetInTest,
                fattet = LocalDateTime.now(),
            )
            TestRepository.insertAll(deltaker, vedtak)
            val request = BakgrunnsinformasjonRequest(
                endretAv = navAnsattInTest.navIdent,
                endretAvEnhet = navEnhetInTest.enhetsnummer,
                bakgrunnsinformasjon = "Ny informasjon",
            )

            // Act
            val resultat = veilederEndringService.upsertEndretDeltaker(deltaker.id, request)

            // Assert
            resultat.bakgrunnsinformasjon shouldBe "Ny informasjon"
            deltakerRepository.get(deltaker.id).shouldBeSuccess().bakgrunnsinformasjon shouldBe "Ny informasjon"
            deltakerEndringRepository
                .getForDeltaker(deltaker.id)
                .first()
                .endring
                .shouldBeInstanceOf<DeltakerEndring.Endring.EndreBakgrunnsinformasjon>()
        }

        @Test
        fun `ingen aktiv oppfolging med endring som krever oppfolging kaster feil`() = runTest {
            // Arrange
            val navBruker = lagNavBruker(
                oppfolgingsperioder = listOf(
                    lagOppfolgingsperiode(
                        startdato = LocalDateTime.now().minusMonths(6),
                        sluttdato = LocalDateTime.now().minusDays(2),
                    ),
                ),
            )
            val deltaker = lagDeltaker(
                navBruker = navBruker,
                status = lagDeltakerStatus(DeltakerStatus.Type.DELTAR),
                bakgrunnsinformasjon = "Gammel informasjon",
            )
            val vedtak = lagVedtak(
                deltakerId = deltaker.id,
                deltakerVedVedtak = deltaker,
                opprettetAv = navAnsattInTest,
                opprettetAvEnhet = navEnhetInTest,
                fattet = LocalDateTime.now(),
            )
            TestRepository.insertAll(deltaker, vedtak)
            val request = BakgrunnsinformasjonRequest(
                endretAv = navAnsattInTest.navIdent,
                endretAvEnhet = navEnhetInTest.enhetsnummer,
                bakgrunnsinformasjon = "Ny informasjon",
            )

            // Act & Assert
            shouldThrow<IllegalArgumentException> {
                veilederEndringService.upsertEndretDeltaker(deltaker.id, request)
            }.message shouldBe
                "Kan ikke utføre endring BakgrunnsinformasjonRequest på deltaker ${deltaker.id} uten aktiv oppfølgingsperiode"

            deltakerRepository.get(deltaker.id).shouldBeSuccess().bakgrunnsinformasjon shouldBe "Gammel informasjon"
            deltakerEndringRepository.getForDeltaker(deltaker.id).shouldBeEmpty()
        }

        @Test
        fun `ingen aktiv oppfolging med endring som kan iverksettes uten utforer endring`() = runTest {
            // Arrange
            val navBruker = lagNavBruker(
                oppfolgingsperioder = listOf(
                    lagOppfolgingsperiode(
                        startdato = LocalDateTime.now().minusMonths(6),
                        sluttdato = LocalDateTime.now().minusDays(2),
                    ),
                ),
            )
            val deltaker = lagDeltaker(
                navBruker = navBruker,
                status = lagDeltakerStatus(DeltakerStatus.Type.DELTAR),
                sluttdato = LocalDate.now().plusMonths(1),
            )
            val vedtak = lagVedtak(
                deltakerId = deltaker.id,
                deltakerVedVedtak = deltaker,
                opprettetAv = navAnsattInTest,
                opprettetAvEnhet = navEnhetInTest,
                fattet = LocalDateTime.now(),
            )
            TestRepository.insertAll(deltaker, vedtak)
            val request = AvsluttDeltakelseRequest(
                endretAv = navAnsattInTest.navIdent,
                endretAvEnhet = navEnhetInTest.enhetsnummer,
                sluttdato = LocalDate.now().plusWeeks(1),
                aarsak = DeltakerEndring.Aarsak(DeltakerEndring.Aarsak.Type.FATT_JOBB, null),
                begrunnelse = null,
                forslagId = null,
                harFullfort = null,
            )

            // Act
            val resultat = veilederEndringService.upsertEndretDeltaker(deltaker.id, request)

            // Assert
            resultat.sluttdato shouldBe request.sluttdato
            deltakerRepository.get(deltaker.id).shouldBeSuccess().sluttdato shouldBe request.sluttdato
            deltakerEndringRepository
                .getForDeltaker(deltaker.id)
                .first()
                .endring
                .shouldBeInstanceOf<DeltakerEndring.Endring.AvsluttDeltakelse>()
        }
    }

    @Nested
    inner class Validering {
        @Test
        fun `gyldigFra for startdato kaster feil`() = runTest {
            // Arrange
            val deltaker = lagDeltaker(
                status = lagDeltakerStatus(DeltakerStatus.Type.DELTAR),
                startdato = LocalDate.now(),
                sluttdato = LocalDate.now().plusWeeks(2),
            )
            TestRepository.insert(deltaker)
            val request = DeltakelsesmengdeRequest(
                endretAv = navAnsattInTest.navIdent,
                endretAvEnhet = navEnhetInTest.enhetsnummer,
                forslagId = null,
                deltakelsesprosent = 50,
                dagerPerUke = 3,
                begrunnelse = "Oppdaterer deltakelsesmengde",
                gyldigFra = LocalDate.now().minusDays(1),
            )

            // Act & Assert
            shouldThrow<IllegalArgumentException> {
                veilederEndringService.upsertEndretDeltaker(deltaker.id, request)
            }.message shouldBe "gyldigFra (${LocalDate.now().minusDays(1)}) kan ikke være før startdato (${deltaker.startdato})"
        }

        @Test
        fun `feilregistrert deltaker kaster feil`() = runTest {
            // Arrange
            val deltaker = lagDeltaker(
                status = lagDeltakerStatus(DeltakerStatus.Type.FEILREGISTRERT),
            )
            TestRepository.insert(deltaker)
            val request = AvsluttDeltakelseRequest(
                endretAv = navAnsattInTest.navIdent,
                endretAvEnhet = navEnhetInTest.enhetsnummer,
                sluttdato = LocalDate.now(),
                aarsak = null,
                begrunnelse = null,
                forslagId = null,
                harFullfort = null,
            )

            // Act & Assert
            shouldThrow<IllegalArgumentException> {
                veilederEndringService.upsertEndretDeltaker(deltaker.id, request)
            }
        }
    }
}
