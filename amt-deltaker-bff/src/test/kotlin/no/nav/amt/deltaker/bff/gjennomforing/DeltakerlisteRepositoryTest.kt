package no.nav.amt.deltaker.bff.gjennomforing

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.result.shouldBeFailure
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotliquery.queryOf
import no.nav.amt.deltaker.bff.navtiltakskoordinator.ulestdeltakerhendelse.UlestHendelseRepository
import no.nav.amt.deltaker.bff.navtiltakskoordinator.ulestdeltakerhendelse.model.AnsvarligNavnOgEnhet
import no.nav.amt.deltaker.bff.navtiltakskoordinator.ulestdeltakerhendelse.model.UlestHendelse
import no.nav.amt.deltaker.bff.navtiltakskoordinator.ulestdeltakerhendelse.model.UlestHendelseType
import no.nav.amt.deltaker.bff.tiltak.TiltakRepository
import no.nav.amt.deltaker.bff.tiltaksarrangor.ArrangorRepository
import no.nav.amt.deltaker.bff.utils.TestData.lagDeltakerOld
import no.nav.amt.deltaker.bff.utils.TestData.lagDeltakerStatus
import no.nav.amt.deltaker.bff.utils.TestData.lagDeltakerliste
import no.nav.amt.deltaker.bff.utils.TestRepository
import no.nav.amt.internapi.tiltakskoordinator.HandlingFilterValg
import no.nav.amt.internapi.tiltakskoordinator.request.TiltaksKoordinatorDeltakerlisteRequest
import no.nav.amt.lib.models.deltaker.DeltakerEndring
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.testing.DatabaseTestExtension
import no.nav.amt.lib.testing.utils.TestData.lagArrangor
import no.nav.amt.lib.utils.database.Database
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

class DeltakerlisteRepositoryTest {
    private val deltakerlisteRepository = DeltakerlisteRepository()
    private val arrangorRepository = ArrangorRepository()
    private val tiltakRepository = TiltakRepository()
    private val ulestHendelseRepository = UlestHendelseRepository()

    companion object {
        @RegisterExtension
        val dbExtension = DatabaseTestExtension()
    }

    @Nested
    inner class GetDeltakereCountPerStatusTests {
        @Test
        fun `skal kaste IllegalArgumentException nar statuser er tomme`() {
            val deltakerliste = lagDeltakerliste()
            val request = TiltaksKoordinatorDeltakerlisteRequest(
                gjennomforingId = deltakerliste.id,
                statuser = emptySet(),
            )

            val exception = shouldThrow<IllegalArgumentException> {
                deltakerlisteRepository.getDeltakereCountPerStatus(request)
            }
            exception.message shouldBe "Statuser må spesifiseres for å hente deltakerantall per status"
        }

        @Test
        fun `skal returnere empty map nar ingen deltakere finnes`() {
            val deltakerliste = lagDeltakerliste()
            val request = TiltaksKoordinatorDeltakerlisteRequest(
                gjennomforingId = deltakerliste.id,
                statuser = setOf(DeltakerStatus.Type.DELTAR),
            )

            val result = deltakerlisteRepository.getDeltakereCountPerStatus(request)

            result.statusCounts.isEmpty() shouldBe true
            result.handlingCounts[HandlingFilterValg.NyeDeltakere] shouldBe 0
            result.handlingCounts[HandlingFilterValg.OppdateringFraNav] shouldBe 0
            result.handlingCounts[HandlingFilterValg.AktiveForslag] shouldBe 0
        }

        @Test
        fun `skal telle deltakere per status`() {
            val deltakerliste = lagDeltakerliste()
            val deltaker1 = lagDeltakerOld(deltakerliste = deltakerliste, status = lagDeltakerStatus(DeltakerStatus.Type.DELTAR))
            val deltaker2 = lagDeltakerOld(deltakerliste = deltakerliste, status = lagDeltakerStatus(DeltakerStatus.Type.DELTAR))
            val deltaker3 =
                lagDeltakerOld(deltakerliste = deltakerliste, status = lagDeltakerStatus(DeltakerStatus.Type.VENTER_PA_OPPSTART))
            val deltaker4 = lagDeltakerOld(deltakerliste = deltakerliste, status = lagDeltakerStatus(DeltakerStatus.Type.HAR_SLUTTET))
            TestRepository.insert(deltaker1)
            TestRepository.insert(deltaker2)
            TestRepository.insert(deltaker3)
            TestRepository.insert(deltaker4)

            val request = TiltaksKoordinatorDeltakerlisteRequest(
                gjennomforingId = deltakerliste.id,
                statuser = setOf(
                    DeltakerStatus.Type.DELTAR,
                    DeltakerStatus.Type.VENTER_PA_OPPSTART,
                    DeltakerStatus.Type.HAR_SLUTTET,
                ),
            )

            val result = deltakerlisteRepository.getDeltakereCountPerStatus(request)

            result.statusCounts.size shouldBe 3
            result.statusCounts[DeltakerStatus.Type.DELTAR] shouldBe 2
            result.statusCounts[DeltakerStatus.Type.VENTER_PA_OPPSTART] shouldBe 1
            result.statusCounts[DeltakerStatus.Type.HAR_SLUTTET] shouldBe 1
        }

        @Test
        fun `skal filtrere pa spesifiserte statuser`() {
            val deltakerliste = lagDeltakerliste()
            val deltaker1 = lagDeltakerOld(deltakerliste = deltakerliste, status = lagDeltakerStatus(DeltakerStatus.Type.DELTAR))
            val deltaker2 =
                lagDeltakerOld(deltakerliste = deltakerliste, status = lagDeltakerStatus(DeltakerStatus.Type.VENTER_PA_OPPSTART))
            val deltaker3 = lagDeltakerOld(deltakerliste = deltakerliste, status = lagDeltakerStatus(DeltakerStatus.Type.HAR_SLUTTET))
            TestRepository.insert(deltaker1)
            TestRepository.insert(deltaker2)
            TestRepository.insert(deltaker3)

            val request = TiltaksKoordinatorDeltakerlisteRequest(
                gjennomforingId = deltakerliste.id,
                statuser = setOf(DeltakerStatus.Type.DELTAR, DeltakerStatus.Type.VENTER_PA_OPPSTART),
            )

            val result = deltakerlisteRepository.getDeltakereCountPerStatus(request)

            result.statusCounts.size shouldBe 2
            result.statusCounts[DeltakerStatus.Type.DELTAR] shouldBe 1
            result.statusCounts[DeltakerStatus.Type.VENTER_PA_OPPSTART] shouldBe 1
            result.statusCounts.containsKey(DeltakerStatus.Type.HAR_SLUTTET) shouldBe false
        }

        @Test
        fun `skal telle er_ny_deltaker basert pa InnbyggerGodkjennUtkast-hendelse`() {
            val deltakerliste = lagDeltakerliste()
            val deltaker = lagDeltakerOld(deltakerliste = deltakerliste, status = lagDeltakerStatus(DeltakerStatus.Type.DELTAR))
            TestRepository.insert(deltaker)

            ulestHendelseRepository.upsert(
                UlestHendelse(
                    id = UUID.randomUUID(),
                    opprettet = LocalDateTime.now(),
                    deltakerId = deltaker.id,
                    ansvarlig = AnsvarligNavnOgEnhet("Nav Veiledersen", "Nav Grunerløkka"),
                    hendelse = UlestHendelseType.InnbyggerGodkjennUtkast,
                ),
            )

            val request = TiltaksKoordinatorDeltakerlisteRequest(
                gjennomforingId = deltakerliste.id,
                statuser = setOf(DeltakerStatus.Type.DELTAR),
            )

            val result = deltakerlisteRepository.getDeltakereCountPerStatus(request)

            result.handlingCounts[HandlingFilterValg.NyeDeltakere] shouldBe 1
        }

        @Test
        fun `skal telle er_ny_deltaker basert pa NavGodkjennUtkast-hendelse`() {
            val deltakerliste = lagDeltakerliste()
            val deltaker = lagDeltakerOld(deltakerliste = deltakerliste, status = lagDeltakerStatus(DeltakerStatus.Type.DELTAR))
            TestRepository.insert(deltaker)

            ulestHendelseRepository.upsert(
                UlestHendelse(
                    id = UUID.randomUUID(),
                    opprettet = LocalDateTime.now(),
                    deltakerId = deltaker.id,
                    ansvarlig = null,
                    hendelse = UlestHendelseType.NavGodkjennUtkast,
                ),
            )

            val request = TiltaksKoordinatorDeltakerlisteRequest(
                gjennomforingId = deltakerliste.id,
                statuser = setOf(DeltakerStatus.Type.DELTAR),
            )

            val result = deltakerlisteRepository.getDeltakereCountPerStatus(request)

            result.handlingCounts[HandlingFilterValg.NyeDeltakere] shouldBe 1
        }

        @Test
        fun `skal telle har_oppdatering_fra_nav basert pa AvsluttDeltakelse-hendelse`() {
            val deltakerliste = lagDeltakerliste()
            val deltaker = lagDeltakerOld(deltakerliste = deltakerliste, status = lagDeltakerStatus(DeltakerStatus.Type.HAR_SLUTTET))
            TestRepository.insert(deltaker)

            ulestHendelseRepository.upsert(
                UlestHendelse(
                    id = UUID.randomUUID(),
                    opprettet = LocalDateTime.now(),
                    deltakerId = deltaker.id,
                    ansvarlig = AnsvarligNavnOgEnhet("Nav Veiledersen"),
                    hendelse = UlestHendelseType.AvsluttDeltakelse(
                        aarsak = null,
                        sluttdato = LocalDate.now(),
                        begrunnelseFraNav = null,
                        begrunnelseFraArrangor = null,
                        endringFraForslag = null,
                    ),
                ),
            )

            val request = TiltaksKoordinatorDeltakerlisteRequest(
                gjennomforingId = deltakerliste.id,
                statuser = setOf(DeltakerStatus.Type.HAR_SLUTTET),
            )

            val result = deltakerlisteRepository.getDeltakereCountPerStatus(request)

            result.handlingCounts[HandlingFilterValg.NyeDeltakere] shouldBe 0
            result.handlingCounts[HandlingFilterValg.OppdateringFraNav] shouldBe 1
        }

        @Test
        fun `skal telle har_oppdatering_fra_nav basert pa IkkeAktuell-hendelse`() {
            val deltakerliste = lagDeltakerliste()
            val deltaker = lagDeltakerOld(deltakerliste = deltakerliste, status = lagDeltakerStatus(DeltakerStatus.Type.IKKE_AKTUELL))
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

            val request = TiltaksKoordinatorDeltakerlisteRequest(
                gjennomforingId = deltakerliste.id,
                statuser = setOf(DeltakerStatus.Type.IKKE_AKTUELL),
            )

            val result = deltakerlisteRepository.getDeltakereCountPerStatus(request)

            result.handlingCounts[HandlingFilterValg.OppdateringFraNav] shouldBe 1
        }

        @Test
        fun `skal telle har_oppdatering_fra_nav basert pa AvbrytDeltakelse-hendelse`() {
            val deltakerliste = lagDeltakerliste()
            val deltaker = lagDeltakerOld(deltakerliste = deltakerliste, status = lagDeltakerStatus(DeltakerStatus.Type.AVBRUTT))
            TestRepository.insert(deltaker)

            ulestHendelseRepository.upsert(
                UlestHendelse(
                    id = UUID.randomUUID(),
                    opprettet = LocalDateTime.now(),
                    deltakerId = deltaker.id,
                    ansvarlig = null,
                    hendelse = UlestHendelseType.AvbrytDeltakelse(
                        aarsak = null,
                        sluttdato = LocalDate.now(),
                        begrunnelseFraNav = null,
                        begrunnelseFraArrangor = null,
                        endringFraForslag = null,
                    ),
                ),
            )

            val request = TiltaksKoordinatorDeltakerlisteRequest(
                gjennomforingId = deltakerliste.id,
                statuser = setOf(DeltakerStatus.Type.AVBRUTT),
            )

            val result = deltakerlisteRepository.getDeltakereCountPerStatus(request)

            result.handlingCounts[HandlingFilterValg.OppdateringFraNav] shouldBe 1
        }

        @Test
        fun `skal telle har_oppdatering_fra_nav basert pa ReaktiverDeltakelse-hendelse`() {
            val deltakerliste = lagDeltakerliste()
            val deltaker = lagDeltakerOld(
                deltakerliste = deltakerliste,
                status = lagDeltakerStatus(DeltakerStatus.Type.VENTER_PA_OPPSTART),
            )
            TestRepository.insert(deltaker)

            ulestHendelseRepository.upsert(
                UlestHendelse(
                    id = UUID.randomUUID(),
                    opprettet = LocalDateTime.now(),
                    deltakerId = deltaker.id,
                    ansvarlig = null,
                    hendelse = UlestHendelseType.ReaktiverDeltakelse(begrunnelseFraNav = "Reaktivering"),
                ),
            )

            val request = TiltaksKoordinatorDeltakerlisteRequest(
                gjennomforingId = deltakerliste.id,
                statuser = setOf(DeltakerStatus.Type.VENTER_PA_OPPSTART),
            )

            val result = deltakerlisteRepository.getDeltakereCountPerStatus(request)

            result.handlingCounts[HandlingFilterValg.OppdateringFraNav] shouldBe 1
        }

        @Test
        fun `skal ikke telle deltakere fra en annen gjennomforing`() {
            val deltakerliste1 = lagDeltakerliste()
            val deltakerliste2 = lagDeltakerliste()
            val deltaker1 = lagDeltakerOld(deltakerliste = deltakerliste1, status = lagDeltakerStatus(DeltakerStatus.Type.DELTAR))
            val deltaker2 = lagDeltakerOld(deltakerliste = deltakerliste2, status = lagDeltakerStatus(DeltakerStatus.Type.DELTAR))
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

            val request = TiltaksKoordinatorDeltakerlisteRequest(
                gjennomforingId = deltakerliste1.id,
                statuser = setOf(DeltakerStatus.Type.DELTAR),
            )

            val result = deltakerlisteRepository.getDeltakereCountPerStatus(request)

            result.statusCounts[DeltakerStatus.Type.DELTAR] shouldBe 1
            result.handlingCounts[HandlingFilterValg.NyeDeltakere] shouldBe 0
        }

        @Test
        fun `skal ikke telle aktive forslag fra en annen gjennomforing`() {
            val deltakerliste1 = lagDeltakerliste()
            val deltakerliste2 = lagDeltakerliste()
            val deltaker1 = lagDeltakerOld(deltakerliste = deltakerliste1, status = lagDeltakerStatus(DeltakerStatus.Type.DELTAR))
            val deltaker2 = lagDeltakerOld(deltakerliste = deltakerliste2, status = lagDeltakerStatus(DeltakerStatus.Type.DELTAR))
            TestRepository.insert(deltaker1)
            TestRepository.insert(deltaker2)

            Database.query { session ->
                session.update(
                    queryOf(
                        """
                        INSERT INTO forslag (id, deltaker_id, arrangoransatt_id, opprettet, begrunnelse, endring, status)
                        VALUES (:id, :deltaker_id, :arrangoransatt_id, now(), 'Begrunnelse', '{}', '{"type":"VenterPaSvar"}')
                        """.trimIndent(),
                        mapOf(
                            "id" to UUID.randomUUID(),
                            "deltaker_id" to deltaker2.id,
                            "arrangoransatt_id" to UUID.randomUUID(),
                        ),
                    ),
                )
            }

            val request = TiltaksKoordinatorDeltakerlisteRequest(
                gjennomforingId = deltakerliste1.id,
                statuser = setOf(DeltakerStatus.Type.DELTAR),
            )

            val result = deltakerlisteRepository.getDeltakereCountPerStatus(request)

            result.handlingCounts[HandlingFilterValg.AktiveForslag] shouldBe 0
        }

        @Test
        fun `skal telle aktive forslag`() {
            val deltakerliste = lagDeltakerliste()
            val deltaker = lagDeltakerOld(deltakerliste = deltakerliste, status = lagDeltakerStatus(DeltakerStatus.Type.DELTAR))
            TestRepository.insert(deltaker)

            Database.query { session ->
                session.update(
                    queryOf(
                        """
                        INSERT INTO forslag (id, deltaker_id, arrangoransatt_id, opprettet, begrunnelse, endring, status)
                        VALUES (:id, :deltaker_id, :arrangoransatt_id, now(), 'Begrunnelse', '{}', '{"type":"VenterPaSvar"}')
                        """.trimIndent(),
                        mapOf(
                            "id" to UUID.randomUUID(),
                            "deltaker_id" to deltaker.id,
                            "arrangoransatt_id" to UUID.randomUUID(),
                        ),
                    ),
                )
            }

            val request = TiltaksKoordinatorDeltakerlisteRequest(
                gjennomforingId = deltakerliste.id,
                statuser = setOf(DeltakerStatus.Type.DELTAR),
            )

            val result = deltakerlisteRepository.getDeltakereCountPerStatus(request)

            result.handlingCounts[HandlingFilterValg.AktiveForslag] shouldBe 1
        }

        @Test
        fun `skal ikke telle avsluttede forslag`() {
            val deltakerliste = lagDeltakerliste()
            val deltaker = lagDeltakerOld(deltakerliste = deltakerliste, status = lagDeltakerStatus(DeltakerStatus.Type.DELTAR))
            TestRepository.insert(deltaker)

            Database.query { session ->
                session.update(
                    queryOf(
                        """
                        INSERT INTO forslag (id, deltaker_id, arrangoransatt_id, opprettet, begrunnelse, endring, status)
                        VALUES (:id, :deltaker_id, :arrangoransatt_id, now(), 'Begrunnelse', '{}', '{"type":"Godkjent"}')
                        """.trimIndent(),
                        mapOf(
                            "id" to UUID.randomUUID(),
                            "deltaker_id" to deltaker.id,
                            "arrangoransatt_id" to UUID.randomUUID(),
                        ),
                    ),
                )
            }

            val request = TiltaksKoordinatorDeltakerlisteRequest(
                gjennomforingId = deltakerliste.id,
                statuser = setOf(DeltakerStatus.Type.DELTAR),
            )

            val result = deltakerlisteRepository.getDeltakereCountPerStatus(request)

            result.handlingCounts[HandlingFilterValg.AktiveForslag] shouldBe 0
        }

        @Test
        fun `skal ikke dobbeltelle aktive forslag fra samme deltaker`() {
            val deltakerliste = lagDeltakerliste()
            val deltaker = lagDeltakerOld(deltakerliste = deltakerliste, status = lagDeltakerStatus(DeltakerStatus.Type.DELTAR))
            TestRepository.insert(deltaker)

            repeat(2) {
                Database.query { session ->
                    session.update(
                        queryOf(
                            """
                            INSERT INTO forslag (id, deltaker_id, arrangoransatt_id, opprettet, begrunnelse, endring, status)
                            VALUES (:id, :deltaker_id, :arrangoransatt_id, now(), 'Begrunnelse', '{}', '{"type":"VenterPaSvar"}')
                            """.trimIndent(),
                            mapOf(
                                "id" to UUID.randomUUID(),
                                "deltaker_id" to deltaker.id,
                                "arrangoransatt_id" to UUID.randomUUID(),
                            ),
                        ),
                    )
                }
            }

            val request = TiltaksKoordinatorDeltakerlisteRequest(
                gjennomforingId = deltakerliste.id,
                statuser = setOf(DeltakerStatus.Type.DELTAR),
            )

            val result = deltakerlisteRepository.getDeltakereCountPerStatus(request)

            result.handlingCounts[HandlingFilterValg.AktiveForslag] shouldBe 1
        }

        @Test
        fun `skal ikke dobbeltelle ny-deltaker-flagg fra flere hendelser for samme deltaker`() {
            val deltakerliste = lagDeltakerliste()
            val deltaker = lagDeltakerOld(deltakerliste = deltakerliste, status = lagDeltakerStatus(DeltakerStatus.Type.DELTAR))
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

            val request = TiltaksKoordinatorDeltakerlisteRequest(
                gjennomforingId = deltakerliste.id,
                statuser = setOf(DeltakerStatus.Type.DELTAR),
            )

            val result = deltakerlisteRepository.getDeltakereCountPerStatus(request)

            result.handlingCounts[HandlingFilterValg.NyeDeltakere] shouldBe 1
        }
    }

    @Nested
    inner class Upsert {
        @Test
        fun `ny deltakerliste - inserter`() {
            val arrangor = lagArrangor()
            arrangorRepository.upsert(arrangor)

            val deltakerliste = lagDeltakerliste(arrangor = arrangor)
            tiltakRepository.upsert(deltakerliste.tiltak)

            deltakerlisteRepository.upsert(deltakerliste)

            deltakerlisteRepository.get(deltakerliste.id).getOrNull() shouldBe deltakerliste
        }

        @Test
        fun `deltakerliste ny sluttdato - oppdaterer`() {
            val arrangor = lagArrangor()
            arrangorRepository.upsert(arrangor)

            val deltakerliste = lagDeltakerliste(arrangor = arrangor)
            tiltakRepository.upsert(deltakerliste.tiltak)

            deltakerlisteRepository.upsert(deltakerliste)

            val oppdatertListe = deltakerliste.copy(sluttDato = LocalDate.now())

            deltakerlisteRepository.upsert(oppdatertListe)

            deltakerlisteRepository.get(deltakerliste.id).getOrNull() shouldBe oppdatertListe
        }
    }

    @Test
    fun `delete - sletter deltakerliste`() {
        val arrangor = lagArrangor()
        arrangorRepository.upsert(arrangor)

        val deltakerliste = lagDeltakerliste(arrangor = arrangor)
        tiltakRepository.upsert(deltakerliste.tiltak)
        deltakerlisteRepository.upsert(deltakerliste)

        deltakerlisteRepository.delete(deltakerliste.id)

        deltakerlisteRepository.get(deltakerliste.id).shouldBeFailure<NoSuchElementException>()
    }

    @Test
    fun `get - deltakerliste og arrangor finnes - henter deltakerliste`() {
        val overordnetArrangor = lagArrangor()
        arrangorRepository.upsert(overordnetArrangor)

        val arrangor = lagArrangor(overordnetArrangorId = overordnetArrangor.id)
        arrangorRepository.upsert(arrangor)

        val deltakerliste = lagDeltakerliste(arrangor = arrangor, overordnetArrangor = overordnetArrangor)
        tiltakRepository.upsert(deltakerliste.tiltak)
        deltakerlisteRepository.upsert(deltakerliste)

        val deltakerlisteMedArrangor = deltakerlisteRepository.get(deltakerliste.id).getOrThrow()

        deltakerlisteMedArrangor shouldNotBe null
        deltakerlisteMedArrangor.navn shouldBe deltakerliste.navn
        deltakerlisteMedArrangor.arrangor.arrangor.navn shouldBe arrangor.navn
        deltakerlisteMedArrangor.arrangor.overordnetArrangorNavn shouldBe overordnetArrangor.navn
    }
}
