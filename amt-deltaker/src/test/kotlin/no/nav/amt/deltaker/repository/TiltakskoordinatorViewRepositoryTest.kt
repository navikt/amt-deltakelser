package no.nav.amt.deltaker.repository

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import no.nav.amt.deltaker.digitalbruker.DigitalBrukerCacheRepository
import no.nav.amt.deltaker.tiltaksarrangor.forslag.ForslagRepository
import no.nav.amt.deltaker.tiltaksarrangor.vurdering.VurderingRepository
import no.nav.amt.deltaker.utils.data.TestData.lagDeltaker
import no.nav.amt.deltaker.utils.data.TestData.lagDeltakerStatus
import no.nav.amt.deltaker.utils.data.TestData.lagDeltakerliste
import no.nav.amt.deltaker.utils.data.TestData.lagForslag
import no.nav.amt.deltaker.utils.data.TestData.lagVedtak
import no.nav.amt.deltaker.utils.data.TestData.lagVurdering
import no.nav.amt.deltaker.utils.data.TestRepository
import no.nav.amt.internapi.deltaker.request.PageRequest
import no.nav.amt.internapi.deltaker.request.TiltaksKoordinatorDeltakerlisteRequest
import no.nav.amt.lib.models.arrangor.melding.Forslag
import no.nav.amt.lib.models.arrangor.melding.Vurderingstype
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.testing.DatabaseTestExtension
import no.nav.amt.lib.testing.utils.TestData.lagDeltakerVedImport
import no.nav.amt.lib.testing.utils.TestData.lagImportertFraArena
import no.nav.amt.lib.testing.utils.TestData.lagNavAnsatt
import no.nav.amt.lib.testing.utils.TestData.lagNavBruker
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

    private fun getDeltakerePaged(gjennomforingId: UUID) = viewRepository.getDeltakere(
        TiltaksKoordinatorDeltakerlisteRequest(
            gjennomforingId = gjennomforingId,
            pageRequest = PageRequest(pageSize = 1000),
        ),
        paginationEnabled = true,
    )

    companion object {
        @RegisterExtension
        val dbExtension = DatabaseTestExtension()
    }

    @Nested
    inner class GetDeltakereCountTests {
        val deltakerliste = lagDeltakerliste()

        val deltakerlisteRequestInTest = TiltaksKoordinatorDeltakerlisteRequest(
            gjennomforingId = deltakerliste.id,
            harForslagFraArrangor = true,
            statuser = setOf(
                DeltakerStatus.Type.DELTAR,
                DeltakerStatus.Type.VENTER_PA_OPPSTART,
                DeltakerStatus.Type.HAR_SLUTTET,
            ),
        )

        @Test
        fun `skal returnere 0 når ingen deltakere finnes for gjennomføring`() {
            val result = viewRepository.getDeltakereTotalCount(deltakerlisteRequestInTest)

            result shouldBe 0
        }

        @Test
        fun `skal returnere antall deltakere for gjennomføring`() {
            val deltaker1 = lagDeltaker(deltakerliste = deltakerliste, status = lagDeltakerStatus(DeltakerStatus.Type.DELTAR))
            TestRepository.insert(deltaker1)

            val deltaker2 = lagDeltaker(deltakerliste = deltakerliste, status = lagDeltakerStatus(DeltakerStatus.Type.VENTER_PA_OPPSTART))
            TestRepository.insert(deltaker2)

            val result = viewRepository.getDeltakereTotalCount(deltakerlisteRequestInTest.copy(harForslagFraArrangor = false))

            result shouldBe 2
        }
    }

    @Nested
    inner class GetDeltakerePagedTests {
        val deltakerliste = lagDeltakerliste()

        val pageRequestInTest = PageRequest<TiltaksKoordinatorDeltakerlisteRequest.SortColumn>(
            sort = TiltaksKoordinatorDeltakerlisteRequest.SortColumn.SOKT_INN_DATO,
            order = PageRequest.SortDirection.ASC,
            page = 1,
            pageSize = 10,
        )

        val requestInTest = TiltaksKoordinatorDeltakerlisteRequest(
            gjennomforingId = deltakerliste.id,
            harForslagFraArrangor = false,
            statuser = setOf(
                DeltakerStatus.Type.DELTAR,
                DeltakerStatus.Type.VENTER_PA_OPPSTART,
                DeltakerStatus.Type.HAR_SLUTTET,
            ),
            pageRequest = pageRequestInTest,
        )

        @Test
        fun `skal returnere tom liste når ingen deltakere finnes for gjennomføring`() {
            val result = viewRepository.getDeltakere(
                request = requestInTest,
                paginationEnabled = true,
            )

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

            val result = viewRepository.getDeltakere(
                request = requestInTest,
                paginationEnabled = true,
            )

            result.map { it.id }.toSet() shouldBe setOf(deltaker1.id, deltaker2.id)
        }

        @Test
        fun `skal returnere sorterte deltakere for gjennomføring`() {
            val deltaker1 = lagDeltaker(deltakerliste = deltakerliste, status = lagDeltakerStatus(DeltakerStatus.Type.DELTAR))
            TestRepository.insert(deltaker1)

            val deltaker2 = lagDeltaker(deltakerliste = deltakerliste, status = lagDeltakerStatus(DeltakerStatus.Type.VENTER_PA_OPPSTART))
            TestRepository.insert(deltaker2)

            TiltaksKoordinatorDeltakerlisteRequest.SortColumn.entries.forEach { sortColumn ->
                val result = viewRepository.getDeltakere(
                    request = requestInTest.copy(pageRequest = pageRequestInTest.copy(sort = sortColumn)),
                    paginationEnabled = true,
                )

                result.map { it.id }.toSet() shouldBe setOf(deltaker1.id, deltaker2.id)
            }
        }

        @Test
        fun `skal returnere rekkefolge`() {
            val deltaker1 = lagDeltaker(
                navBruker = lagNavBruker(fornavn = "A", mellomnavn = "B", etternavn = "C"),
                deltakerliste = deltakerliste,
                status = lagDeltakerStatus(DeltakerStatus.Type.DELTAR),
            )
            TestRepository.insert(deltaker1)

            val deltaker2 = lagDeltaker(
                navBruker = lagNavBruker(fornavn = "B", mellomnavn = "C", etternavn = "D"),
                deltakerliste = deltakerliste,
                status = lagDeltakerStatus(DeltakerStatus.Type.VENTER_PA_OPPSTART),
            )
            TestRepository.insert(deltaker2)

            val ascending = viewRepository.getDeltakere(
                request = requestInTest.copy(
                    pageRequest = pageRequestInTest.copy(
                        sort = TiltaksKoordinatorDeltakerlisteRequest.SortColumn.NAVN,
                        order = PageRequest.SortDirection.ASC,
                    ),
                ),
                paginationEnabled = true,
            )
            val descending = viewRepository.getDeltakere(
                request = requestInTest.copy(
                    pageRequest = pageRequestInTest.copy(
                        sort = TiltaksKoordinatorDeltakerlisteRequest.SortColumn.NAVN,
                        order = PageRequest.SortDirection.DESC,
                    ),
                ),
                paginationEnabled = true,
            )

            ascending.map { it.id } shouldBe listOf(deltaker1.id, deltaker2.id)
            descending.map { it.id } shouldBe listOf(deltaker2.id, deltaker1.id)
        }

        @Test
        fun `skal sortere paa sokt inn dato synkende som default`() {
            val eldste = lagDeltaker(deltakerliste = deltakerliste, status = lagDeltakerStatus(DeltakerStatus.Type.DELTAR))
            val nyeste = lagDeltaker(deltakerliste = deltakerliste, status = lagDeltakerStatus(DeltakerStatus.Type.DELTAR))
            val midterste = lagDeltaker(deltakerliste = deltakerliste, status = lagDeltakerStatus(DeltakerStatus.Type.DELTAR))
            TestRepository.insert(eldste)
            TestRepository.insert(nyeste)
            TestRepository.insert(midterste)
            TestRepository.insertAll(
                lagImportertFraArena(
                    deltakerId = eldste.id,
                    deltakerVedImport = lagDeltakerVedImport(innsoktDato = LocalDate.of(2024, 1, 1)),
                ),
                lagImportertFraArena(
                    deltakerId = nyeste.id,
                    deltakerVedImport = lagDeltakerVedImport(innsoktDato = LocalDate.of(2024, 3, 1)),
                ),
                lagImportertFraArena(
                    deltakerId = midterste.id,
                    deltakerVedImport = lagDeltakerVedImport(innsoktDato = LocalDate.of(2024, 2, 1)),
                ),
            )

            val defaultSort = viewRepository.getDeltakere(
                request = requestInTest.copy(pageRequest = PageRequest(pageSize = 10)),
                paginationEnabled = true,
            )
            val explicitAscending = viewRepository.getDeltakere(
                request = requestInTest.copy(
                    pageRequest = pageRequestInTest.copy(
                        sort = TiltaksKoordinatorDeltakerlisteRequest.SortColumn.SOKT_INN_DATO,
                        order = PageRequest.SortDirection.ASC,
                    ),
                ),
                paginationEnabled = true,
            )

            defaultSort.map { it.id } shouldBe listOf(nyeste.id, midterste.id, eldste.id)
            explicitAscending.map { it.id } shouldBe listOf(eldste.id, midterste.id, nyeste.id)
        }

        @Test
        fun `skal ignorere pageSize naar pagination er skrudd av`() {
            val deltakerliste = lagDeltakerliste()
            val request = TiltaksKoordinatorDeltakerlisteRequest(
                gjennomforingId = deltakerliste.id,
                pageRequest = pageRequestInTest.copy(pageSize = 1),
            )
            val deltaker1 = lagDeltaker(deltakerliste = deltakerliste, status = lagDeltakerStatus(DeltakerStatus.Type.DELTAR))
            val deltaker2 = lagDeltaker(deltakerliste = deltakerliste, status = lagDeltakerStatus(DeltakerStatus.Type.DELTAR))
            TestRepository.insert(deltaker1)
            TestRepository.insert(deltaker2)

            val result = viewRepository.getDeltakere(
                request = request,
                paginationEnabled = false,
            )

            result.map { it.id }.toSet() shouldBe setOf(deltaker1.id, deltaker2.id)
        }
    }

    @Nested
    inner class GetDeltakerePagedMedBerikelseTests {
        @Test
        fun `skal returnere tom liste når ingen deltakere finnes for gjennomføring`() {
            val result = getDeltakerePaged(UUID.randomUUID())

            result.shouldBeEmpty()
        }

        @Test
        fun `skal returnere deltakere for gjennomføring`() {
            val deltakerliste = lagDeltakerliste()

            val deltaker1 = lagDeltaker(deltakerliste = deltakerliste, status = lagDeltakerStatus(DeltakerStatus.Type.DELTAR))
            TestRepository.insert(deltaker1)

            val deltaker2 = lagDeltaker(deltakerliste = deltakerliste, status = lagDeltakerStatus(DeltakerStatus.Type.VENTER_PA_OPPSTART))
            TestRepository.insert(deltaker2)

            val result = getDeltakerePaged(deltakerliste.id)

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

            val result = getDeltakerePaged(deltakerliste1.id)

            result shouldHaveSize 1
            result.first().id shouldBe deltaker1.id
        }

        @Test
        fun `skal mappe nav-bruker-felt korrekt`() {
            val deltakerliste = lagDeltakerliste()
            val deltaker = lagDeltaker(deltakerliste = deltakerliste, status = lagDeltakerStatus(DeltakerStatus.Type.DELTAR))
            TestRepository.insert(deltaker)

            val result = getDeltakerePaged(deltakerliste.id).single()

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

            val result = getDeltakerePaged(deltakerliste.id).single()

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

            val result = getDeltakerePaged(deltakerliste.id)

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

                val result = getDeltakerePaged(deltakerliste.id).single()

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

                val result = getDeltakerePaged(deltakerliste.id).single()

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

                val result = getDeltakerePaged(deltakerliste.id).single()

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

                val result = getDeltakerePaged(deltakerliste.id).single()

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

                val result = getDeltakerePaged(deltakerliste.id).single()

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

                val result = getDeltakerePaged(deltakerliste.id).single()

                result.harAktivtForslag shouldBe false
            }

            @Test
            fun `skal være true når VenterPaSvar-forslag finnes`() {
                val deltakerliste = lagDeltakerliste()
                val deltaker = lagDeltaker(deltakerliste = deltakerliste, status = lagDeltakerStatus(DeltakerStatus.Type.DELTAR))
                TestRepository.insert(deltaker)
                forslagRepository.upsert(lagForslag(deltakerId = deltaker.id, status = Forslag.Status.VenterPaSvar))

                val result = getDeltakerePaged(deltakerliste.id).single()

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

                val result = getDeltakerePaged(deltakerliste.id).single()

                result.harAktivtForslag shouldBe false
            }

            @Test
            fun `skal kun returnere deltakere med aktivt forslag når harForslagFraArrangor er true`() {
                val deltakerliste = lagDeltakerliste()
                val deltakerMedAktivtForslag = lagDeltaker(
                    deltakerliste = deltakerliste,
                    status = lagDeltakerStatus(DeltakerStatus.Type.DELTAR),
                )
                val deltakerUtenForslag = lagDeltaker(
                    deltakerliste = deltakerliste,
                    status = lagDeltakerStatus(DeltakerStatus.Type.DELTAR),
                )
                val deltakerMedIkkeAktivtForslag = lagDeltaker(
                    deltakerliste = deltakerliste,
                    status = lagDeltakerStatus(DeltakerStatus.Type.DELTAR),
                )
                TestRepository.insert(deltakerMedAktivtForslag)
                TestRepository.insert(deltakerUtenForslag)
                TestRepository.insert(deltakerMedIkkeAktivtForslag)
                forslagRepository.upsert(lagForslag(deltakerId = deltakerMedAktivtForslag.id, status = Forslag.Status.VenterPaSvar))
                forslagRepository.upsert(
                    lagForslag(
                        deltakerId = deltakerMedIkkeAktivtForslag.id,
                        status = Forslag.Status.Godkjent(
                            godkjentAv = Forslag.NavAnsatt(UUID.randomUUID(), UUID.randomUUID()),
                            godkjent = LocalDateTime.now(),
                        ),
                    ),
                )

                val result = viewRepository.getDeltakere(
                    request = TiltaksKoordinatorDeltakerlisteRequest(
                        gjennomforingId = deltakerliste.id,
                        harForslagFraArrangor = true,
                        pageRequest = PageRequest(pageSize = 1000),
                    ),
                    paginationEnabled = true,
                )

                result shouldHaveSize 1
                result.single().id shouldBe deltakerMedAktivtForslag.id
                result.single().harAktivtForslag shouldBe true
            }
        }

        @Nested
        inner class SisteVurderingstypeTests {
            @Test
            fun `skal være null når ingen vurdering finnes`() {
                val deltakerliste = lagDeltakerliste()
                val deltaker = lagDeltaker(deltakerliste = deltakerliste, status = lagDeltakerStatus(DeltakerStatus.Type.DELTAR))
                TestRepository.insert(deltaker)

                val result = getDeltakerePaged(deltakerliste.id).single()

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

                val result = getDeltakerePaged(deltakerliste.id).single()

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

                val result = getDeltakerePaged(deltakerliste.id).single()

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

                val result = getDeltakerePaged(deltakerliste.id).single()

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

                val result = getDeltakerePaged(deltakerliste.id).single()

                // v_active filtrerer på gyldig_til IS NULL, så expired vedtak gir null
                result.vedtakFattet.shouldBeNull()
            }
        }

        @Nested
        inner class DigitalBrukerCacheTests {
            @Test
            fun `skal være null når ingen cache-entry finnes`() {
                val deltakerliste = lagDeltakerliste()
                val deltaker = lagDeltaker(deltakerliste = deltakerliste, status = lagDeltakerStatus(DeltakerStatus.Type.DELTAR))
                TestRepository.insert(deltaker)

                val result = getDeltakerePaged(deltakerliste.id).single()

                result.erDigitalCached.shouldBeNull()
            }

            @Test
            fun `skal returnere cachet digital-status for fersk cache-entry`() {
                val deltakerliste = lagDeltakerliste()
                val deltaker = lagDeltaker(deltakerliste = deltakerliste, status = lagDeltakerStatus(DeltakerStatus.Type.DELTAR))
                TestRepository.insert(deltaker)

                DigitalBrukerCacheRepository.upsertBatch(listOf(deltaker.navBruker.personident to true))

                val result = getDeltakerePaged(deltakerliste.id).single()

                result.erDigitalCached shouldBe true
            }
        }
    }
}
