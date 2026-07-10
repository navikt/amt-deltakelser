package no.nav.amt.deltaker.repository

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import no.nav.amt.deltaker.digitalbruker.DigitalBrukerCacheRepository
import no.nav.amt.deltaker.navtiltakskoordinator.ulestdeltakerhendelse.UlestHendelseRepository
import no.nav.amt.deltaker.navtiltakskoordinator.ulestdeltakerhendelse.model.UlestHendelse
import no.nav.amt.deltaker.navtiltakskoordinator.ulestdeltakerhendelse.model.UlestHendelseType
import no.nav.amt.deltaker.tiltaksarrangor.forslag.ForslagRepository
import no.nav.amt.deltaker.tiltaksarrangor.vurdering.VurderingRepository
import no.nav.amt.deltaker.utils.data.TestData.lagDeltaker
import no.nav.amt.deltaker.utils.data.TestData.lagDeltakerStatus
import no.nav.amt.deltaker.utils.data.TestData.lagDeltakerliste
import no.nav.amt.deltaker.utils.data.TestData.lagForslag
import no.nav.amt.deltaker.utils.data.TestData.lagVedtak
import no.nav.amt.deltaker.utils.data.TestData.lagVurdering
import no.nav.amt.deltaker.utils.data.TestRepository
import no.nav.amt.internapi.tiltakskoordinator.HandlingFilterValg
import no.nav.amt.internapi.tiltakskoordinator.request.TiltaksKoordinatorDeltakerlisteRequest
import no.nav.amt.lib.models.arrangor.melding.Forslag
import no.nav.amt.lib.models.arrangor.melding.Vurderingstype
import no.nav.amt.lib.models.deltaker.DeltakerEndring
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.testing.DatabaseTestExtension
import no.nav.amt.lib.testing.utils.TestData.lagDeltakerVedImport
import no.nav.amt.lib.testing.utils.TestData.lagImportertFraArena
import no.nav.amt.lib.testing.utils.TestData.lagNavAnsatt
import no.nav.amt.lib.testing.utils.TestData.lagNavEnhet
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

class TiltakskoordinatorViewRepositoryTest {
    private val viewRepository = TiltakskoordinatorViewRepository()
    private val forslagRepository = ForslagRepository()
    private val vurderingRepository = VurderingRepository()
    private val ulestHendelseRepository = UlestHendelseRepository()

    private fun getDeltakereMedBerikelse(gjennomforingId: UUID) = viewRepository.getDeltakere(
        TiltaksKoordinatorDeltakerlisteRequest(
            gjennomforingId = gjennomforingId,
        ),
    )

    companion object {
        @RegisterExtension
        val dbExtension = DatabaseTestExtension()
    }

    @Nested
    inner class GetDeltakereCountPerStatusTests {
        @Test
        fun `skal kaste IllegalArgumentException nar statuser er tomme`() {
            val exception = shouldThrow<IllegalArgumentException> {
                viewRepository.getDeltakereCountPerStatus(
                    TiltaksKoordinatorDeltakerlisteRequest(
                        gjennomforingId = UUID.randomUUID(),
                        statuser = emptySet(),
                    ),
                )
            }

            exception.message shouldBe "Statuser må spesifiseres for å hente deltakerantall per status"
        }

        @Test
        fun `skal kun telle aktiv status nar deltaker har statushistorikk`() {
            val deltakerliste = lagDeltakerliste()
            val deltaker = lagDeltaker(
                deltakerliste = deltakerliste,
                status = lagDeltakerStatus(
                    statusType = DeltakerStatus.Type.DELTAR,
                    gyldigFra = LocalDateTime.now().minusHours(1),
                    gyldigTil = null,
                ),
            )
            TestRepository.insert(deltaker)

            DeltakerStatusRepository.lagreStatus(
                deltakerId = deltaker.id,
                deltakerStatus = lagDeltakerStatus(
                    statusType = DeltakerStatus.Type.HAR_SLUTTET,
                    gyldigFra = LocalDateTime.now().minusDays(7),
                    gyldigTil = LocalDateTime.now().minusDays(1),
                ),
            )

            val request = TiltaksKoordinatorDeltakerlisteRequest(
                gjennomforingId = deltakerliste.id,
                statuser = setOf(DeltakerStatus.Type.DELTAR, DeltakerStatus.Type.HAR_SLUTTET),
            )

            val result = viewRepository.getDeltakereCountPerStatus(request)

            result.statusCounts[DeltakerStatus.Type.DELTAR] shouldBe 1
            result.statusCounts.containsKey(DeltakerStatus.Type.HAR_SLUTTET) shouldBe false
            result.handlingCounts[HandlingFilterValg.NyeDeltakere] shouldBe 0
            result.handlingCounts[HandlingFilterValg.OppdateringFraNav] shouldBe 0
            result.handlingCounts[HandlingFilterValg.AktiveForslag] shouldBe 0
        }

        @Test
        fun `skal telle NyeDeltakere basert på uleste godkjenn-utkast-hendelser`() {
            val deltakerliste = lagDeltakerliste()
            val deltaker = lagDeltaker(
                deltakerliste = deltakerliste,
                status = lagDeltakerStatus(DeltakerStatus.Type.DELTAR),
            )
            TestRepository.insert(deltaker)

            ulestHendelseRepository.upsert(
                UlestHendelse(
                    id = UUID.randomUUID(),
                    opprettet = LocalDateTime.now(),
                    deltakerId = deltaker.id,
                    ansvarlig = null,
                    hendelse = UlestHendelseType.InnbyggerGodkjennUtkast,
                ),
            )
            ulestHendelseRepository.upsert(
                UlestHendelse(
                    id = UUID.randomUUID(),
                    opprettet = LocalDateTime.now(),
                    deltakerId = deltaker.id,
                    ansvarlig = null,
                    hendelse = UlestHendelseType.NavGodkjennUtkast,
                ),
            )

            val result = viewRepository.getDeltakereCountPerStatus(
                TiltaksKoordinatorDeltakerlisteRequest(
                    gjennomforingId = deltakerliste.id,
                    statuser = setOf(DeltakerStatus.Type.DELTAR),
                ),
            )

            result.handlingCounts[HandlingFilterValg.NyeDeltakere] shouldBe 1
        }

        @Test
        fun `skal telle OppdateringFraNav basert på uleste nav-hendelser`() {
            val deltakerliste = lagDeltakerliste()
            val deltaker = lagDeltaker(
                deltakerliste = deltakerliste,
                status = lagDeltakerStatus(DeltakerStatus.Type.IKKE_AKTUELL),
            )
            TestRepository.insert(deltaker)

            ulestHendelseRepository.upsert(
                UlestHendelse(
                    id = UUID.randomUUID(),
                    opprettet = LocalDateTime.now(),
                    deltakerId = deltaker.id,
                    ansvarlig = null,
                    hendelse = UlestHendelseType.IkkeAktuell(
                        aarsak = DeltakerEndring.Aarsak(
                            type = DeltakerEndring.Aarsak.Type.ANNET,
                            beskrivelse = null,
                        ),
                        begrunnelseFraNav = null,
                        begrunnelseFraArrangor = null,
                        endringFraForslag = null,
                    ),
                ),
            )

            val result = viewRepository.getDeltakereCountPerStatus(
                TiltaksKoordinatorDeltakerlisteRequest(
                    gjennomforingId = deltakerliste.id,
                    statuser = setOf(DeltakerStatus.Type.IKKE_AKTUELL),
                ),
            )

            result.handlingCounts[HandlingFilterValg.OppdateringFraNav] shouldBe 1
        }

        @Test
        fun `skal telle AktiveForslag og ikke dobbeltelle samme deltaker`() {
            val deltakerliste = lagDeltakerliste()
            val deltaker = lagDeltaker(
                deltakerliste = deltakerliste,
                status = lagDeltakerStatus(DeltakerStatus.Type.DELTAR),
            )
            TestRepository.insert(deltaker)

            forslagRepository.upsert(lagForslag(deltakerId = deltaker.id, status = Forslag.Status.VenterPaSvar))
            forslagRepository.upsert(lagForslag(deltakerId = deltaker.id, status = Forslag.Status.VenterPaSvar))

            val result = viewRepository.getDeltakereCountPerStatus(
                TiltaksKoordinatorDeltakerlisteRequest(
                    gjennomforingId = deltakerliste.id,
                    statuser = setOf(DeltakerStatus.Type.DELTAR),
                ),
            )

            result.handlingCounts[HandlingFilterValg.AktiveForslag] shouldBe 1
        }

        @Test
        fun `skal ikke telle handlingCounts fra annen gjennomforing`() {
            val deltakerliste1 = lagDeltakerliste()
            val deltakerliste2 = lagDeltakerliste()
            val deltaker1 = lagDeltaker(
                deltakerliste = deltakerliste1,
                status = lagDeltakerStatus(DeltakerStatus.Type.DELTAR),
            )
            val deltaker2 = lagDeltaker(
                deltakerliste = deltakerliste2,
                status = lagDeltakerStatus(DeltakerStatus.Type.DELTAR),
            )
            TestRepository.insert(deltaker1)
            TestRepository.insert(deltaker2)

            ulestHendelseRepository.upsert(
                UlestHendelse(
                    id = UUID.randomUUID(),
                    opprettet = LocalDateTime.now(),
                    deltakerId = deltaker2.id,
                    ansvarlig = null,
                    hendelse = UlestHendelseType.InnbyggerGodkjennUtkast,
                ),
            )
            forslagRepository.upsert(lagForslag(deltakerId = deltaker2.id, status = Forslag.Status.VenterPaSvar))

            val result = viewRepository.getDeltakereCountPerStatus(
                TiltaksKoordinatorDeltakerlisteRequest(
                    gjennomforingId = deltakerliste1.id,
                    statuser = setOf(DeltakerStatus.Type.DELTAR),
                ),
            )

            result.handlingCounts[HandlingFilterValg.NyeDeltakere] shouldBe 0
            result.handlingCounts[HandlingFilterValg.AktiveForslag] shouldBe 0
        }
    }

    @Nested
    inner class GetDeltakereTests {
        val deltakerliste = lagDeltakerliste()

        val requestInTest = TiltaksKoordinatorDeltakerlisteRequest(
            gjennomforingId = deltakerliste.id,
            statuser = setOf(
                DeltakerStatus.Type.DELTAR,
                DeltakerStatus.Type.VENTER_PA_OPPSTART,
                DeltakerStatus.Type.HAR_SLUTTET,
            ),
        )

        @Test
        fun `skal returnere tom liste når ingen deltakere finnes for gjennomføring`() {
            val result = viewRepository.getDeltakere(request = requestInTest)

            result shouldBe emptyList()
        }

        @Test
        fun `skal returnere deltakere for gjennomføring`() {
            val deltaker1 = lagDeltaker(
                deltakerliste = deltakerliste,
                status = lagDeltakerStatus(DeltakerStatus.Type.DELTAR),
            )
            TestRepository.insert(deltaker1)

            val deltaker2 = lagDeltaker(
                deltakerliste = deltakerliste,
                status = lagDeltakerStatus(DeltakerStatus.Type.VENTER_PA_OPPSTART),
            )
            TestRepository.insert(deltaker2)

            val result = viewRepository.getDeltakere(request = requestInTest)

            result.map { it.id }.toSet() shouldBe setOf(deltaker1.id, deltaker2.id)
        }
    }

    @Nested
    inner class GetDeltakereByIdsTests {
        @Test
        fun `skal returnere tom liste for tom input`() {
            val result = viewRepository.getDeltakere(emptyList())

            result.shouldBeEmpty()
        }

        @Test
        fun `skal returnere tom liste når ingen deltakere matcher`() {
            val result = viewRepository.getDeltakere(listOf(UUID.randomUUID()))

            result.shouldBeEmpty()
        }

        @Test
        fun `skal returnere deltakere som matcher gitte IDer`() {
            val deltakerliste = lagDeltakerliste()
            val deltaker1 = lagDeltaker(deltakerliste = deltakerliste, status = lagDeltakerStatus(DeltakerStatus.Type.DELTAR))
            val deltaker2 = lagDeltaker(deltakerliste = deltakerliste, status = lagDeltakerStatus(DeltakerStatus.Type.VENTER_PA_OPPSTART))
            val deltaker3 = lagDeltaker(deltakerliste = deltakerliste, status = lagDeltakerStatus(DeltakerStatus.Type.HAR_SLUTTET))
            TestRepository.insert(deltaker1)
            TestRepository.insert(deltaker2)
            TestRepository.insert(deltaker3)

            val result = viewRepository.getDeltakere(listOf(deltaker1.id, deltaker3.id))

            result shouldHaveSize 2
            result.map { it.id }.toSet() shouldBe setOf(deltaker1.id, deltaker3.id)
        }

        @Test
        fun `skal returnere deltakere fra ulike deltakerlister`() {
            val deltakerliste1 = lagDeltakerliste()
            val deltakerliste2 = lagDeltakerliste()
            val deltaker1 = lagDeltaker(deltakerliste = deltakerliste1, status = lagDeltakerStatus(DeltakerStatus.Type.DELTAR))
            val deltaker2 = lagDeltaker(deltakerliste = deltakerliste2, status = lagDeltakerStatus(DeltakerStatus.Type.DELTAR))
            TestRepository.insert(deltaker1)
            TestRepository.insert(deltaker2)

            val result = viewRepository.getDeltakere(listOf(deltaker1.id, deltaker2.id))

            result shouldHaveSize 2
            result.map { it.id }.toSet() shouldBe setOf(deltaker1.id, deltaker2.id)
        }

        @Test
        fun `skal ikke filtrere bort skjulte statuser`() {
            val deltakerliste = lagDeltakerliste()
            val deltaker = lagDeltaker(deltakerliste = deltakerliste, status = lagDeltakerStatus(DeltakerStatus.Type.SOKT_INN))
            TestRepository.insert(deltaker)

            val result = viewRepository.getDeltakere(listOf(deltaker.id))

            result shouldHaveSize 1
            result.single().id shouldBe deltaker.id
            result.single().status.type shouldBe DeltakerStatus.Type.SOKT_INN
        }

        @Test
        fun `skal mappe alle felter korrekt`() {
            val deltakerliste = lagDeltakerliste()
            val deltaker = lagDeltaker(
                deltakerliste = deltakerliste,
                status = lagDeltakerStatus(DeltakerStatus.Type.DELTAR),
                startdato = LocalDate.now().minusMonths(1),
                sluttdato = LocalDate.now().plusMonths(2),
            )
            TestRepository.insert(deltaker)

            val result = viewRepository.getDeltakere(listOf(deltaker.id)).single()

            result.id shouldBe deltaker.id
            result.personident shouldBe deltaker.navBruker.personident
            result.fornavn shouldBe deltaker.navBruker.fornavn
            result.mellomnavn shouldBe deltaker.navBruker.mellomnavn
            result.etternavn shouldBe deltaker.navBruker.etternavn
            result.erSkjermet shouldBe deltaker.navBruker.erSkjermet
            result.startdato shouldBe deltaker.startdato
            result.sluttdato shouldBe deltaker.sluttdato
            result.status.type shouldBe DeltakerStatus.Type.DELTAR
            result.erManueltDeltMedArrangor shouldBe deltaker.erManueltDeltMedArrangor
        }

        @Test
        fun `skal inkludere berikede felter - forslag og vurdering`() {
            val deltakerliste = lagDeltakerliste()
            val deltaker = lagDeltaker(deltakerliste = deltakerliste, status = lagDeltakerStatus(DeltakerStatus.Type.DELTAR))
            TestRepository.insert(deltaker)

            forslagRepository.upsert(lagForslag(deltakerId = deltaker.id, status = Forslag.Status.VenterPaSvar))
            vurderingRepository.upsert(
                lagVurdering(
                    deltakerId = deltaker.id,
                    vurderingstype = Vurderingstype.OPPFYLLER_KRAVENE,
                    gyldigFra = LocalDateTime.now(),
                ),
            )

            val result = viewRepository.getDeltakere(listOf(deltaker.id)).single()

            result.harAktivtForslag shouldBe true
            result.sisteVurderingstype shouldBe Vurderingstype.OPPFYLLER_KRAVENE
        }

        @Test
        fun `skal inkludere soktInnDato fra vedtak`() {
            val deltakerliste = lagDeltakerliste()
            val deltaker = lagDeltaker(deltakerliste = deltakerliste, status = lagDeltakerStatus(DeltakerStatus.Type.DELTAR))
            val ansatt = lagNavAnsatt()
            val enhet = lagNavEnhet()
            TestRepository.insert(deltaker)
            TestRepository.insertAll(ansatt, enhet)

            val vedtakOpprettet = LocalDateTime.of(2024, 6, 10, 9, 0)
            TestRepository.insert(
                lagVedtak(
                    deltakerVedVedtak = deltaker,
                    opprettet = vedtakOpprettet,
                    opprettetAv = ansatt,
                    opprettetAvEnhet = enhet,
                ),
            )

            val result = viewRepository.getDeltakere(listOf(deltaker.id)).single()

            result.soktInnDato shouldBe vedtakOpprettet.toLocalDate()
        }
    }

    @Nested
    inner class GetDeltakereMedBerikelseTests {
        @Test
        fun `skal returnere tom liste når ingen deltakere finnes for gjennomføring`() {
            val result = getDeltakereMedBerikelse(UUID.randomUUID())

            result.shouldBeEmpty()
        }

        @Test
        fun `skal returnere deltakere for gjennomføring`() {
            val deltakerliste = lagDeltakerliste()

            val deltaker1 = lagDeltaker(deltakerliste = deltakerliste, status = lagDeltakerStatus(DeltakerStatus.Type.DELTAR))
            TestRepository.insert(deltaker1)

            val deltaker2 = lagDeltaker(deltakerliste = deltakerliste, status = lagDeltakerStatus(DeltakerStatus.Type.VENTER_PA_OPPSTART))
            TestRepository.insert(deltaker2)

            val result = getDeltakereMedBerikelse(deltakerliste.id)

            result shouldHaveSize 2
            result.map { it.id }.toSet() shouldBe setOf(deltaker1.id, deltaker2.id)
        }

        @Test
        fun `skal ikke returnere deltakere fra andre gjennomføringer`() {
            val deltakerliste1 = lagDeltakerliste()
            val deltakerliste2 = lagDeltakerliste()
            val deltaker1 = lagDeltaker(deltakerliste = deltakerliste1, status = lagDeltakerStatus(DeltakerStatus.Type.DELTAR))
            val deltaker2 = lagDeltaker(deltakerliste = deltakerliste2, status = lagDeltakerStatus(DeltakerStatus.Type.DELTAR))
            TestRepository.insert(deltaker1)
            TestRepository.insert(deltaker2)

            val result = getDeltakereMedBerikelse(deltakerliste1.id)

            result shouldHaveSize 1
            result.first().id shouldBe deltaker1.id
        }

        @Test
        fun `skal mappe nav-bruker-felt korrekt`() {
            val deltakerliste = lagDeltakerliste()
            val deltaker = lagDeltaker(deltakerliste = deltakerliste, status = lagDeltakerStatus(DeltakerStatus.Type.DELTAR))
            TestRepository.insert(deltaker)

            val result = getDeltakereMedBerikelse(deltakerliste.id).single()

            result.personident shouldBe deltaker.navBruker.personident
            result.fornavn shouldBe deltaker.navBruker.fornavn
            result.mellomnavn shouldBe deltaker.navBruker.mellomnavn
            result.etternavn shouldBe deltaker.navBruker.etternavn
            result.erSkjermet shouldBe deltaker.navBruker.erSkjermet
        }

        @Test
        fun `skal mappe deltakerstatus korrekt`() {
            val deltakerliste = lagDeltakerliste()
            val deltaker = lagDeltaker(deltakerliste = deltakerliste, status = lagDeltakerStatus(DeltakerStatus.Type.DELTAR))
            TestRepository.insert(deltaker)

            val result = getDeltakereMedBerikelse(deltakerliste.id).single()

            result.status.type shouldBe DeltakerStatus.Type.DELTAR
        }

        @Test
        fun `skal filtrere bort skjulte statuser i SQL`() {
            val deltakerliste = lagDeltakerliste()
            val synlig = lagDeltaker(deltakerliste = deltakerliste, status = lagDeltakerStatus(DeltakerStatus.Type.DELTAR))
            val kladd = lagDeltaker(deltakerliste = deltakerliste, status = lagDeltakerStatus(DeltakerStatus.Type.KLADD))
            val feilregistrert = lagDeltaker(deltakerliste = deltakerliste, status = lagDeltakerStatus(DeltakerStatus.Type.FEILREGISTRERT))
            val utkast = lagDeltaker(deltakerliste = deltakerliste, status = lagDeltakerStatus(DeltakerStatus.Type.UTKAST_TIL_PAMELDING))
            TestRepository.insert(synlig)
            TestRepository.insert(kladd)
            TestRepository.insert(feilregistrert)
            TestRepository.insert(utkast)

            val result = getDeltakereMedBerikelse(deltakerliste.id)

            result shouldHaveSize 1
            result.single().id shouldBe synlig.id
        }

        @Nested
        inner class SoktInnDatoTests {
            @Test
            fun `skal returnere null når ingen søkt-inn-kilder finnes`() {
                val deltakerliste = lagDeltakerliste()
                val deltaker = lagDeltaker(deltakerliste = deltakerliste, status = lagDeltakerStatus(DeltakerStatus.Type.DELTAR))
                TestRepository.insert(deltaker)

                val result = getDeltakereMedBerikelse(deltakerliste.id).single()

                result.soktInnDato.shouldBeNull()
            }

            @Test
            fun `skal bruke arena innsoktDato når den finnes`() {
                val deltakerliste = lagDeltakerliste()
                val deltaker = lagDeltaker(deltakerliste = deltakerliste, status = lagDeltakerStatus(DeltakerStatus.Type.DELTAR))
                TestRepository.insert(deltaker)

                val arenaDato = LocalDate.of(2024, 3, 15)
                TestRepository.insertAll(
                    lagImportertFraArena(deltakerId = deltaker.id, deltakerVedImport = lagDeltakerVedImport(innsoktDato = arenaDato)),
                )

                val result = getDeltakereMedBerikelse(deltakerliste.id).single()

                result.soktInnDato shouldBe arenaDato
            }

            @Test
            fun `skal bruke vedtak created_at som fallback for soktInnDato`() {
                val deltakerliste = lagDeltakerliste()
                val deltaker = lagDeltaker(deltakerliste = deltakerliste, status = lagDeltakerStatus(DeltakerStatus.Type.DELTAR))
                val ansatt = lagNavAnsatt()
                val enhet = lagNavEnhet()
                TestRepository.insert(deltaker)
                TestRepository.insertAll(ansatt, enhet)

                val vedtakOpprettet = LocalDateTime.of(2024, 6, 10, 9, 0)
                TestRepository.insert(
                    lagVedtak(
                        deltakerVedVedtak = deltaker,
                        opprettet = vedtakOpprettet,
                        opprettetAv = ansatt,
                        opprettetAvEnhet = enhet,
                    ),
                )

                val result = getDeltakereMedBerikelse(deltakerliste.id).single()

                result.soktInnDato shouldBe vedtakOpprettet.toLocalDate()
            }

            @Test
            fun `skal bruke vedtak created_at selv når vedtak er utløpt (gyldig_til satt)`() {
                val deltakerliste = lagDeltakerliste()
                val deltaker = lagDeltaker(deltakerliste = deltakerliste, status = lagDeltakerStatus(DeltakerStatus.Type.DELTAR))
                val ansatt = lagNavAnsatt()
                val enhet = lagNavEnhet()
                TestRepository.insert(deltaker)
                TestRepository.insertAll(ansatt, enhet)

                val vedtakOpprettet = LocalDateTime.of(2024, 6, 10, 9, 0)
                TestRepository.insert(
                    lagVedtak(
                        deltakerVedVedtak = deltaker,
                        opprettet = vedtakOpprettet,
                        gyldigTil = LocalDateTime.now().minusDays(1),
                        opprettetAv = ansatt,
                        opprettetAvEnhet = enhet,
                    ),
                )

                val result = getDeltakereMedBerikelse(deltakerliste.id).single()

                // v_all (uten gyldig_til-filter) skal brukes for soktInnDato
                result.soktInnDato shouldBe vedtakOpprettet.toLocalDate()
            }

            @Test
            fun `skal prioritere arena-import over vedtak`() {
                val deltakerliste = lagDeltakerliste()
                val deltaker = lagDeltaker(deltakerliste = deltakerliste, status = lagDeltakerStatus(DeltakerStatus.Type.DELTAR))
                val ansatt = lagNavAnsatt()
                val enhet = lagNavEnhet()
                TestRepository.insert(deltaker)
                TestRepository.insertAll(ansatt, enhet)

                val arenaDato = LocalDate.of(2024, 1, 1)
                TestRepository.insertAll(
                    lagImportertFraArena(deltakerId = deltaker.id, deltakerVedImport = lagDeltakerVedImport(innsoktDato = arenaDato)),
                )
                TestRepository.insert(
                    lagVedtak(
                        deltakerVedVedtak = deltaker,
                        opprettet = LocalDateTime.of(2024, 7, 1, 12, 0),
                        opprettetAv = ansatt,
                        opprettetAvEnhet = enhet,
                    ),
                )

                val result = getDeltakereMedBerikelse(deltakerliste.id).single()

                result.soktInnDato shouldBe arenaDato
            }
        }

        @Nested
        inner class HarAktivtForslagTests {
            @Test
            fun `skal være false når ingen forslag finnes`() {
                val deltakerliste = lagDeltakerliste()
                val deltaker = lagDeltaker(deltakerliste = deltakerliste, status = lagDeltakerStatus(DeltakerStatus.Type.DELTAR))
                TestRepository.insert(deltaker)

                val result = getDeltakereMedBerikelse(deltakerliste.id).single()

                result.harAktivtForslag shouldBe false
            }

            @Test
            fun `skal være true når VenterPaSvar-forslag finnes`() {
                val deltakerliste = lagDeltakerliste()
                val deltaker = lagDeltaker(deltakerliste = deltakerliste, status = lagDeltakerStatus(DeltakerStatus.Type.DELTAR))
                TestRepository.insert(deltaker)
                forslagRepository.upsert(lagForslag(deltakerId = deltaker.id, status = Forslag.Status.VenterPaSvar))

                val result = getDeltakereMedBerikelse(deltakerliste.id).single()

                result.harAktivtForslag shouldBe true
            }

            @Test
            fun `skal være false når kun ikke-aktive forslag finnes`() {
                val deltakerliste = lagDeltakerliste()
                val deltaker = lagDeltaker(deltakerliste = deltakerliste, status = lagDeltakerStatus(DeltakerStatus.Type.DELTAR))
                TestRepository.insert(deltaker)
                forslagRepository.upsert(
                    lagForslag(
                        deltakerId = deltaker.id,
                        status = Forslag.Status.Godkjent(
                            godkjentAv = Forslag.NavAnsatt(UUID.randomUUID(), UUID.randomUUID()),
                            godkjent = LocalDateTime.now(),
                        ),
                    ),
                )

                val result = getDeltakereMedBerikelse(deltakerliste.id).single()

                result.harAktivtForslag shouldBe false
            }
        }

        @Nested
        inner class SisteVurderingstypeTests {
            @Test
            fun `skal være null når ingen vurdering finnes`() {
                val deltakerliste = lagDeltakerliste()
                val deltaker = lagDeltaker(deltakerliste = deltakerliste, status = lagDeltakerStatus(DeltakerStatus.Type.DELTAR))
                TestRepository.insert(deltaker)

                val result = getDeltakereMedBerikelse(deltakerliste.id).single()

                result.sisteVurderingstype.shouldBeNull()
            }

            @Test
            fun `skal returnere nyeste vurderingstype`() {
                val deltakerliste = lagDeltakerliste()
                val deltaker = lagDeltaker(deltakerliste = deltakerliste, status = lagDeltakerStatus(DeltakerStatus.Type.DELTAR))
                TestRepository.insert(deltaker)

                vurderingRepository.upsert(
                    lagVurdering(
                        deltakerId = deltaker.id,
                        vurderingstype = Vurderingstype.OPPFYLLER_KRAVENE,
                        gyldigFra = LocalDateTime.now().minusDays(5),
                    ),
                )
                vurderingRepository.upsert(
                    lagVurdering(
                        deltakerId = deltaker.id,
                        vurderingstype = Vurderingstype.OPPFYLLER_IKKE_KRAVENE,
                        gyldigFra = LocalDateTime.now(),
                    ),
                )

                val result = getDeltakereMedBerikelse(deltakerliste.id).single()

                result.sisteVurderingstype shouldBe Vurderingstype.OPPFYLLER_IKKE_KRAVENE
            }
        }

        @Nested
        inner class VedtakFattetTests {
            @Test
            fun `skal være null når ingen vedtak finnes`() {
                val deltakerliste = lagDeltakerliste()
                val deltaker = lagDeltaker(deltakerliste = deltakerliste, status = lagDeltakerStatus(DeltakerStatus.Type.DELTAR))
                TestRepository.insert(deltaker)

                val result = getDeltakereMedBerikelse(deltakerliste.id).single()

                result.vedtakFattet.shouldBeNull()
            }

            @Test
            fun `skal returnere fattet kun fra aktivt vedtak`() {
                val deltakerliste = lagDeltakerliste()
                val deltaker = lagDeltaker(deltakerliste = deltakerliste, status = lagDeltakerStatus(DeltakerStatus.Type.DELTAR))
                val ansatt = lagNavAnsatt()
                val enhet = lagNavEnhet()
                TestRepository.insert(deltaker)
                TestRepository.insertAll(ansatt, enhet)

                val fattetDato = LocalDateTime.of(2024, 5, 1, 10, 0)
                TestRepository.insert(
                    lagVedtak(
                        deltakerVedVedtak = deltaker,
                        fattet = fattetDato,
                        gyldigTil = null, // active
                        opprettetAv = ansatt,
                        opprettetAvEnhet = enhet,
                    ),
                )

                val result = getDeltakereMedBerikelse(deltakerliste.id).single()

                result.vedtakFattet.shouldNotBeNull()
            }

            @Test
            fun `skal være null for utløpt vedtak`() {
                val deltakerliste = lagDeltakerliste()
                val deltaker = lagDeltaker(deltakerliste = deltakerliste, status = lagDeltakerStatus(DeltakerStatus.Type.DELTAR))
                val ansatt = lagNavAnsatt()
                val enhet = lagNavEnhet()
                TestRepository.insert(deltaker)
                TestRepository.insertAll(ansatt, enhet)

                TestRepository.insert(
                    lagVedtak(
                        deltakerVedVedtak = deltaker,
                        fattet = LocalDateTime.of(2024, 5, 1, 10, 0),
                        gyldigTil = LocalDateTime.now().minusDays(1), // expired
                        opprettetAv = ansatt,
                        opprettetAvEnhet = enhet,
                    ),
                )

                val result = getDeltakereMedBerikelse(deltakerliste.id).single()

                // v_active filtrerer på gyldig_til IS NULL, så expired vedtak gir null
                result.vedtakFattet.shouldBeNull()
            }
        }

        // Regresjonstest for gotcha: amt-deltaker lagrer full statushistorikk per deltaker.
        // Uten filteret "gyldig_til IS NULL AND gyldig_fra <= CURRENT_TIMESTAMP" i JOINen mot
        // deltaker_status ville spørringen returnere én rad per historisk status, ikke én per deltaker.
        @Test
        fun `historiske statuser (gyldig_til satt) ekskluderes - kun aktiv status returneres`() {
            // Arrange — én deltaker med én historisk KLADD-status og én aktiv DELTAR-status
            val deltakerliste = lagDeltakerliste()
            val historiskStatusTidspunkt = LocalDateTime.now().minusWeeks(2)
            val aktivStatusTidspunkt = LocalDateTime.now().minusWeeks(1)

            val deltaker = lagDeltaker(
                deltakerliste = deltakerliste,
                status = lagDeltakerStatus(
                    statusType = DeltakerStatus.Type.DELTAR,
                    gyldigFra = aktivStatusTidspunkt,
                ),
            )
            TestRepository.insert(deltaker)

            // Legg til en historisk status (gyldig_til er satt — den er ikke lenger aktiv)
            DeltakerStatusRepository.lagreStatus(
                deltakerId = deltaker.id,
                deltakerStatus = lagDeltakerStatus(
                    statusType = DeltakerStatus.Type.KLADD,
                    gyldigFra = historiskStatusTidspunkt,
                    gyldigTil = aktivStatusTidspunkt,
                ),
            )

            // Act — uten status-filter (henter alle synlige statuser)
            val result = getDeltakereMedBerikelse(deltakerliste.id)

            // Assert — kun én rad returneres (den aktive), ikke to (historisk + aktiv)
            result shouldHaveSize 1
            result.single().status.type shouldBe DeltakerStatus.Type.DELTAR
        }

        @Nested
        inner class DigitalBrukerCacheTests {
            @Test
            fun `skal være null når ingen cache-entry finnes`() {
                val deltakerliste = lagDeltakerliste()
                val deltaker = lagDeltaker(deltakerliste = deltakerliste, status = lagDeltakerStatus(DeltakerStatus.Type.DELTAR))
                TestRepository.insert(deltaker)

                val result = getDeltakereMedBerikelse(deltakerliste.id).single()

                result.erDigitalCached.shouldBeNull()
            }

            @Test
            fun `skal returnere cachet digital-status for fersk cache-entry`() {
                val deltakerliste = lagDeltakerliste()
                val deltaker = lagDeltaker(deltakerliste = deltakerliste, status = lagDeltakerStatus(DeltakerStatus.Type.DELTAR))
                TestRepository.insert(deltaker)

                DigitalBrukerCacheRepository.upsertBatch(listOf(deltaker.navBruker.personident to true))

                val result = getDeltakereMedBerikelse(deltakerliste.id).single()

                result.erDigitalCached shouldBe true
            }
        }
    }
}
