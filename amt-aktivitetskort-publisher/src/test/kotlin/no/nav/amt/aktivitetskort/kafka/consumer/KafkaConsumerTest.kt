package no.nav.amt.aktivitetskort.kafka.consumer

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.amt.aktivitetskort.IntegrationTest
import no.nav.amt.aktivitetskort.TestUtils.staticObjectMapper
import no.nav.amt.aktivitetskort.database.TestData
import no.nav.amt.aktivitetskort.database.TestData.toDto
import no.nav.amt.aktivitetskort.domain.AktivitetStatus
import no.nav.amt.aktivitetskort.domain.DeltakerStatusModel
import no.nav.amt.aktivitetskort.repositories.ArrangorRepository
import no.nav.amt.aktivitetskort.repositories.DeltakerRepository
import no.nav.amt.aktivitetskort.repositories.DeltakerlisteRepository
import no.nav.amt.aktivitetskort.repositories.MeldingRepository
import no.nav.amt.aktivitetskort.repositories.TiltakstypeRepository
import no.nav.amt.aktivitetskort.utils.shouldBeCloseTo
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import org.apache.kafka.clients.producer.ProducerRecord
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.kafka.core.KafkaTemplate
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.TimeUnit

class KafkaConsumerTest(
    private val arrangorRepository: ArrangorRepository,
    private val tiltakstypeRepository: TiltakstypeRepository,
    private val deltakerlisteRepository: DeltakerlisteRepository,
    private val deltakerRepository: DeltakerRepository,
    private val meldingRepository: MeldingRepository,
    private val kafkaTemplate: KafkaTemplate<String, String>,
) : IntegrationTest() {
    private val offset: Long = 0

    @BeforeEach
    fun setup() = mockAktivitetArenaAclServer.clearResponses()

    @Test
    fun `listen - melding om ny arrangor - arrangor upsertes`() {
        val arrangor = TestData.lagArrangor()

        kafkaTemplate
            .send(
                ProducerRecord(
                    ARRANGOR_TOPIC,
                    arrangor.id.toString(),
                    staticObjectMapper.writeValueAsString(arrangor.toDto()),
                ),
            ).get()

        await().untilAsserted {
            arrangorRepository.get(arrangor.id).shouldNotBeNull()
        }
    }

    @Test
    fun `listen - melding om ny tiltakstype - tiltakstype upsertes`() {
        val ctx = TestData.MockContext()

        kafkaTemplate.send(
            ProducerRecord(
                TILTAKSTYPE_TOPIC,
                ctx.tiltakstype.id.toString(),
                staticObjectMapper.writeValueAsString(ctx.tiltakstype),
            ),
        )

        await().atMost(5, TimeUnit.SECONDS).until {
            tiltakstypeRepository.getById(ctx.tiltakstype.id) != null
        }
    }

    @Test
    fun `listen - melding om ny deltakerliste - deltakerliste upsertes`() {
        val ctx = TestData.MockContext()
        arrangorRepository.upsert(ctx.arrangor)
        tiltakstypeRepository.upsert(ctx.tiltakstype)

        val deltakerlistePayload = ctx.deltakerlisteGruppePayload.copy(
            tiltakskode = Tiltakskode.OPPFOLGING,
        )

        kafkaTemplate.send(
            ProducerRecord(
                DELTAKERLISTE_V2_TOPIC,
                deltakerlistePayload.id.toString(),
                staticObjectMapper.writeValueAsString(deltakerlistePayload),
            ),
        )

        await().untilAsserted {
            deltakerlisteRepository.get(deltakerlistePayload.id).shouldNotBeNull()
        }
    }

    @Test
    fun `listen - melding om ny deltaker - deltaker upsertes og aktivitetskort opprettes`() {
        val ctx = TestData.MockContext()
        arrangorRepository.upsert(ctx.arrangor)
        deltakerlisteRepository.upsert(ctx.deltakerliste)

        mockAmtArenaAclServer.addArenaIdResponse(ctx.deltaker.id, 1234)
        mockAktivitetArenaAclServer.addAktivitetsIdResponse(1234, ctx.melding.id)
        mockVeilarboppfolgingServer.addResponse()

        kafkaTemplate.send(
            ProducerRecord(
                DELTAKER_TOPIC,
                ctx.deltaker.id.toString(),
                staticObjectMapper.writeValueAsString(ctx.deltaker.toDto()),
            ),
        )

        await().untilAsserted {
            ctx.melding.id shouldBe ctx.aktivitetskortId
            ctx.melding.aktivitetskort.id shouldBe ctx.aktivitetskortId
            val deltaker = deltakerRepository.get(ctx.deltaker.id)

            deltaker.shouldNotBeNull()
            deltaker shouldBe ctx.deltaker

            val aktivitetskort = meldingRepository
                .getByDeltakerId(deltaker.id)
                .first()
                .aktivitetskort

            assertSoftly(aktivitetskort) {
                personident shouldBe ctx.aktivitetskort.personident
                tittel shouldBe ctx.aktivitetskort.tittel
                aktivitetStatus shouldBe ctx.aktivitetskort.aktivitetStatus
                startDato shouldBe ctx.aktivitetskort.startDato
                sluttDato shouldBe ctx.aktivitetskort.sluttDato
                beskrivelse shouldBe ctx.aktivitetskort.beskrivelse
                endretAv shouldBe ctx.aktivitetskort.endretAv
                endretTidspunkt shouldBeCloseTo ctx.aktivitetskort.endretTidspunkt
                avtaltMedNav shouldBe ctx.aktivitetskort.avtaltMedNav
                oppgave shouldBe ctx.aktivitetskort.oppgave
                handlinger shouldNotBe null
                detaljer shouldBe ctx.aktivitetskort.detaljer
                etiketter shouldBe ctx.aktivitetskort.etiketter
            }
        }
    }

    @Test
    fun `listen - melding om oppdatert deltaker, dab sender ny id - nytt aktivitetskort opprettes med mottatt id`() {
        val ctx = TestData.MockContext(oppfolgingsperiodeId = null)
        val endretDeltaker = ctx.deltaker.copy(
            sluttdato = LocalDate.now().plusDays(1),
        )
        val nyId = UUID.randomUUID()
        arrangorRepository.upsert(ctx.arrangor)
        deltakerlisteRepository.upsert(ctx.deltakerliste)
        deltakerRepository.upsert(ctx.deltaker, -1)
        meldingRepository.upsert(ctx.melding)

        mockAmtArenaAclServer.addArenaIdResponse(ctx.deltaker.id, 1234)
        mockAktivitetArenaAclServer.addAktivitetsIdResponse(1234, nyId)
        mockVeilarboppfolgingServer.addResponse()

        kafkaTemplate.send(
            ProducerRecord(
                DELTAKER_TOPIC,
                ctx.deltaker.id.toString(),
                staticObjectMapper.writeValueAsString(endretDeltaker.toDto()),
            ),
        )

        await().untilAsserted {
            val deltaker = deltakerRepository.get(ctx.deltaker.id)
            deltaker.shouldNotBeNull()
            deltaker shouldBe endretDeltaker

            val aktivitetskort = meldingRepository
                .getByDeltakerId(deltaker.id)

            aktivitetskort.size shouldBe 2
            val nyttAktivitetskort = aktivitetskort
                .first()
                .aktivitetskort
            nyttAktivitetskort.id shouldBe nyId
            nyttAktivitetskort shouldBe ctx.aktivitetskort.copy(
                id = nyId,
                sluttDato = endretDeltaker.sluttdato,
            )
        }
    }

    @Test
    fun `listen - tombstone for deltaker som har aktivt aktivitetskort - deltaker slettes og aktivitetskort avbrytes`() {
        val ctx = TestData.MockContext(oppfolgingsperiodeId = UUID.randomUUID())
        ctx.oppfolgingsperiodeId.shouldNotBeNull()

        arrangorRepository.upsert(ctx.arrangor)
        deltakerlisteRepository.upsert(ctx.deltakerliste)
        deltakerRepository.upsert(ctx.deltaker, offset)
        testDatabase.insertAktivOppfolgingsperiode(id = ctx.oppfolgingsperiodeId)
        meldingRepository.upsert(ctx.melding)

        mockAmtArenaAclServer.addArenaIdResponse(ctx.deltaker.id, 1234)
        mockAktivitetArenaAclServer.addAktivitetsIdResponse(1234, ctx.aktivitetskort.id)
        mockVeilarboppfolgingServer.addResponse()

        kafkaTemplate.send(
            ProducerRecord(
                DELTAKER_TOPIC,
                ctx.deltaker.id.toString(),
                null,
            ),
        )

        await().untilAsserted {
            val aktivitetskort = meldingRepository
                .getByDeltakerId(ctx.deltaker.id)
                .first()
                .aktivitetskort
            aktivitetskort.aktivitetStatus shouldBe AktivitetStatus.AVBRUTT

            deltakerRepository.get(ctx.deltaker.id) shouldBe null
        }
    }

    @Test
    fun `listen - tombstone for deltaker som har inaktivt aktivitetskort - deltaker slettes og aktivitetskort endres ikke`() {
        val ctx = TestData.MockContext(
            oppfolgingsperiodeId = UUID.randomUUID(),
            deltaker = TestData.lagDeltaker(status = DeltakerStatusModel(DeltakerStatus.Type.HAR_SLUTTET, null)),
        )
        ctx.oppfolgingsperiodeId.shouldNotBeNull()

        arrangorRepository.upsert(ctx.arrangor)
        deltakerlisteRepository.upsert(ctx.deltakerliste)
        deltakerRepository.upsert(ctx.deltaker, offset)
        testDatabase.insertAktivOppfolgingsperiode(id = ctx.oppfolgingsperiodeId)
        meldingRepository.upsert(ctx.melding)

        mockAmtArenaAclServer.addArenaIdResponse(ctx.deltaker.id, 1234)
        mockAktivitetArenaAclServer.addAktivitetsIdResponse(1234, ctx.aktivitetskort.id)

        kafkaTemplate.send(
            ProducerRecord(
                DELTAKER_TOPIC,
                ctx.deltaker.id.toString(),
                null,
            ),
        )

        await().untilAsserted {
            val aktivitetskort = meldingRepository
                .getByDeltakerId(ctx.deltaker.id)
                .first()
                .aktivitetskort

            aktivitetskort.aktivitetStatus shouldBe AktivitetStatus.FULLFORT
            deltakerRepository.get(ctx.deltaker.id) shouldBe null
        }
    }

    @Test
    fun `listen - tombstone for deltaker som ikke aktivitetskort - deltaker slettes og aktivitetskort opprettes ikke`() {
        val deltaker = TestData.lagDeltaker(status = DeltakerStatusModel(DeltakerStatus.Type.PABEGYNT_REGISTRERING, null))
        val deltakerliste = TestData.lagDeltakerliste(deltaker.deltakerlisteId)
        val arrangor = TestData.lagArrangor(deltakerliste.arrangorId)
        arrangorRepository.upsert(arrangor)
        deltakerlisteRepository.upsert(deltakerliste)
        deltakerRepository.upsert(deltaker, offset)

        kafkaTemplate.send(
            ProducerRecord(
                DELTAKER_TOPIC,
                deltaker.id.toString(),
                null,
            ),
        )

        await().untilAsserted {
            deltakerRepository.get(deltaker.id) shouldBe null
            meldingRepository.getByDeltakerId(deltaker.id) shouldBe emptyList()
        }
    }
}
