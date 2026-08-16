package no.nav.tiltaksarrangor.consumer

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.slot
import no.nav.amt.lib.models.deltaker.DeltakerEndring
import no.nav.amt.lib.models.deltaker.DeltakerHistorikk
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltaker.Kontaktinformasjon
import no.nav.amt.lib.models.deltakerliste.GjennomforingStatusType
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import no.nav.tiltaksarrangor.IntegrationTestBase
import no.nav.tiltaksarrangor.client.amtarrangor.dto.ArrangorMedOverordnetArrangor
import no.nav.tiltaksarrangor.client.amtperson.NavAnsattResponse
import no.nav.tiltaksarrangor.client.amtperson.NavEnhetResponse
import no.nav.tiltaksarrangor.consumer.ConsumerTestUtils.arrangorInTest
import no.nav.tiltaksarrangor.consumer.ConsumerTestUtils.deltakerlisteIdInTest
import no.nav.tiltaksarrangor.consumer.ConsumerTestUtils.gjennomforingPayloadInTest
import no.nav.tiltaksarrangor.consumer.ConsumerTestUtils.tiltakstypePayloadInTest
import no.nav.tiltaksarrangor.consumer.ConsumerUtils.toDeltakerlisteDbo
import no.nav.tiltaksarrangor.consumer.KafkaConsumer.Companion.ARRANGOR_ANSATT_TOPIC
import no.nav.tiltaksarrangor.consumer.KafkaConsumer.Companion.ARRANGOR_TOPIC
import no.nav.tiltaksarrangor.consumer.KafkaConsumer.Companion.DELTAKERLISTE_V2_TOPIC
import no.nav.tiltaksarrangor.consumer.KafkaConsumer.Companion.DELTAKER_TOPIC
import no.nav.tiltaksarrangor.consumer.KafkaConsumer.Companion.ENDRINGSMELDING_TOPIC
import no.nav.tiltaksarrangor.consumer.KafkaConsumer.Companion.TILTAKSTYPE_TOPIC
import no.nav.tiltaksarrangor.consumer.model.AnsattDto
import no.nav.tiltaksarrangor.consumer.model.AnsattPersonaliaDto
import no.nav.tiltaksarrangor.consumer.model.AnsattRolle
import no.nav.tiltaksarrangor.consumer.model.ArrangorDto
import no.nav.tiltaksarrangor.consumer.model.EndringsmeldingDto
import no.nav.tiltaksarrangor.consumer.model.EndringsmeldingType
import no.nav.tiltaksarrangor.consumer.model.Innhold
import no.nav.tiltaksarrangor.consumer.model.NavnDto
import no.nav.tiltaksarrangor.consumer.model.TilknyttetArrangorDto
import no.nav.tiltaksarrangor.consumer.model.VeilederDto
import no.nav.tiltaksarrangor.consumer.model.toAnsattDbo
import no.nav.tiltaksarrangor.consumer.model.toArrangorDbo
import no.nav.tiltaksarrangor.consumer.model.toDeltakerDbo
import no.nav.tiltaksarrangor.consumer.model.toEndringsmeldingDbo
import no.nav.tiltaksarrangor.model.Endringsmelding
import no.nav.tiltaksarrangor.model.Veiledertype
import no.nav.tiltaksarrangor.repositories.ArrangorRepository
import no.nav.tiltaksarrangor.repositories.DeltakerRepository
import no.nav.tiltaksarrangor.repositories.DeltakerlisteRepository
import no.nav.tiltaksarrangor.repositories.EndringsmeldingRepository
import no.nav.tiltaksarrangor.repositories.TiltaksarrangorAnsattRepository
import no.nav.tiltaksarrangor.repositories.TiltakstypeRepository
import no.nav.tiltaksarrangor.testutils.getDeltaker
import no.nav.tiltaksarrangor.testutils.getDeltakerliste
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.kafka.support.Acknowledgment
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

class KafkaConsumerTest(
    private val arrangorRepository: ArrangorRepository,
    private val tiltaksarrangorAnsattRepository: TiltaksarrangorAnsattRepository,
    private val deltakerRepository: DeltakerRepository,
    private val deltakerlisteRepository: DeltakerlisteRepository,
    private val endringsmeldingRepository: EndringsmeldingRepository,
    private val tiltakstypeRepository: TiltakstypeRepository,
    private val kafkaConsumer: KafkaConsumer,
) : IntegrationTestBase() {
    private val ack = Acknowledgment { }

    @BeforeEach
    fun setup() {
        val enhetIdSlot = slot<UUID>()
        every { amtPersonClient.hentEnhet(capture(enhetIdSlot)) } answers {
            NavEnhetResponse(
                id = enhetIdSlot.captured,
                enhetId = "0000",
                navn = "Ukjent enhet",
            ).toNavEnhet()
        }

        val ansattIdSlot = slot<UUID>()
        every { amtPersonClient.hentNavAnsatt(capture(ansattIdSlot)) } answers {
            NavAnsattResponse(
                id = ansattIdSlot.captured,
                navIdent = "X000000",
                navn = "Ukjent ansatt",
                epost = null,
                telefon = null,
            )
        }

        every { amtPersonClient.hentOppdatertKontaktinfo(any<String>()) } answers {
            Kontaktinformasjon(epost = null, telefonnummer = null)
        }

        every { hentArrangorClient.getArrangor(any()) } returns ArrangorMedOverordnetArrangor(
            id = UUID.randomUUID(),
            navn = "Arrangør AS",
            organisasjonsnummer = "88888888",
            overordnetArrangor = null,
        )
    }

    private fun consumerRecord(
        topic: String,
        key: String,
        value: String?,
    ) = ConsumerRecord<String, String>(topic, 0, 0L, key, value)

    @Test
    fun `skal lagre tiltakstype i database`() {
        kafkaConsumer.listen(
            consumerRecord(
                TILTAKSTYPE_TOPIC,
                tiltakstypePayloadInTest.id.toString(),
                objectMapper.writeValueAsString(tiltakstypePayloadInTest),
            ),
            ack,
        )

        tiltakstypeRepository.getByTiltakskode(tiltakstypePayloadInTest.tiltakskode) shouldNotBe null
    }

    @Nested
    inner class ListenDeltakerliste {
        @Test
        fun `skal lagre deltakerliste i database`() {
            tiltakstypeRepository.upsert(tiltakstypePayloadInTest.toModel())
            arrangorRepository.insertOrUpdateArrangor(arrangorInTest.toArrangorDbo())

            kafkaConsumer.listen(
                consumerRecord(
                    DELTAKERLISTE_V2_TOPIC,
                    deltakerlisteIdInTest.toString(),
                    objectMapper.writeValueAsString(gjennomforingPayloadInTest),
                ),
                ack,
            )

            deltakerlisteRepository.getDeltakerliste(deltakerlisteIdInTest) shouldNotBe null
        }

        @Test
        fun `skal slette deltakerliste i database`() {
            deltakerlisteRepository.insertOrUpdateDeltakerliste(
                gjennomforingPayloadInTest.toDeltakerlisteDbo(
                    arrangorId = arrangorInTest.id,
                    navnTiltakstype = Tiltakskode.GRUPPE_ARBEIDSMARKEDSOPPLAERING.name,
                ),
            )
            deltakerlisteRepository.getDeltakerliste(deltakerlisteIdInTest) shouldNotBe null

            kafkaConsumer.listen(
                consumerRecord(
                    DELTAKERLISTE_V2_TOPIC,
                    deltakerlisteIdInTest.toString(),
                    null,
                ),
                ack,
            )

            deltakerlisteRepository.getDeltakerliste(deltakerlisteIdInTest) shouldBe null
        }

        @Test
        fun `skal slette deltakerliste og deltaker i database`() {
            deltakerlisteRepository.insertOrUpdateDeltakerliste(
                gjennomforingPayloadInTest.toDeltakerlisteDbo(
                    arrangorId = arrangorInTest.id,
                    navnTiltakstype = Tiltakskode.GRUPPE_ARBEIDSMARKEDSOPPLAERING.name,
                ),
            )
            deltakerlisteRepository.getDeltakerliste(deltakerlisteIdInTest) shouldNotBe null

            val deltaker = getDeltaker(deltakerId = UUID.randomUUID(), deltakerlisteId = deltakerlisteIdInTest)
            deltakerRepository.insertOrUpdateDeltaker(deltaker)
            deltakerRepository.getDeltaker(deltaker.id) shouldNotBe null

            val avsluttetDeltakerlisteDto = gjennomforingPayloadInTest.copy(status = GjennomforingStatusType.AVSLUTTET)

            kafkaConsumer.listen(
                consumerRecord(
                    DELTAKERLISTE_V2_TOPIC,
                    deltakerlisteIdInTest.toString(),
                    objectMapper.writeValueAsString(avsluttetDeltakerlisteDto),
                ),
                ack,
            )

            deltakerlisteRepository.getDeltakerliste(deltakerlisteIdInTest) shouldBe null
            deltakerRepository.getDeltaker(deltaker.id) shouldBe null
        }
    }

    @Test
    fun `listen - melding pa arrangor-topic - lagres i database`() {
        val arrangorId = UUID.randomUUID()
        val arrangorDto =
            ArrangorDto(
                id = arrangorId,
                navn = "Arrangør AS",
                organisasjonsnummer = "88888888",
                overordnetArrangorId = UUID.randomUUID(),
            )

        kafkaConsumer.listen(
            consumerRecord(
                ARRANGOR_TOPIC,
                arrangorId.toString(),
                objectMapper.writeValueAsString(arrangorDto),
            ),
            ack,
        )

        arrangorRepository.getArrangor(arrangorId) shouldNotBe null
    }

    @Test
    fun `listen - tombstonemelding pa arrangor-topic - slettes i database`() {
        val arrangorId = UUID.randomUUID()
        val arrangorDto = ArrangorDto(
            id = arrangorId,
            navn = "Arrangør AS",
            organisasjonsnummer = "77777777",
            overordnetArrangorId = null,
        )
        arrangorRepository.insertOrUpdateArrangor(arrangorDto.toArrangorDbo())
        kafkaConsumer.listen(
            consumerRecord(
                ARRANGOR_TOPIC,
                arrangorId.toString(),
                null,
            ),
            ack,
        )

        arrangorRepository.getArrangor(arrangorId) shouldBe null
    }

    @Test
    fun `listen - melding pa arrangor-ansatt-topic - lagres i database`() {
        val deltaker = getDeltaker(UUID.randomUUID())
        deltakerRepository.insertOrUpdateDeltaker(deltaker)
        val ansattId = UUID.randomUUID()
        val ansattDto =
            AnsattDto(
                id = ansattId,
                personalia =
                    AnsattPersonaliaDto(
                        personident = "12345678910",
                        navn =
                            NavnDto(
                                fornavn = "Fornavn",
                                mellomnavn = null,
                                etternavn = "Etternavn",
                            ),
                    ),
                arrangorer =
                    listOf(
                        TilknyttetArrangorDto(
                            arrangorId = UUID.randomUUID(),
                            roller = listOf(AnsattRolle.KOORDINATOR, AnsattRolle.VEILEDER),
                            veileder = listOf(VeilederDto(deltaker.id, Veiledertype.VEILEDER)),
                            koordinator = listOf(UUID.randomUUID()),
                        ),
                    ),
            )
        kafkaConsumer.listen(
            consumerRecord(
                ARRANGOR_ANSATT_TOPIC,
                ansattId.toString(),
                objectMapper.writeValueAsString(ansattDto),
            ),
            ack,
        )

        tiltaksarrangorAnsattRepository.getAnsatt(ansattId) shouldNotBe null
        tiltaksarrangorAnsattRepository.getAnsattRolleListe(ansattId).size shouldBe 2
        tiltaksarrangorAnsattRepository.getKoordinatorDeltakerlisteDboListe(ansattId).size shouldBe 1
        tiltaksarrangorAnsattRepository.getVeilederDeltakerDboListe(ansattId).size shouldBe 1
    }

    @Test
    fun `listen - tombstonemelding pa arrangor-ansatt-topic - slettes i database`() {
        val deltaker = getDeltaker(UUID.randomUUID())
        deltakerRepository.insertOrUpdateDeltaker(deltaker)
        val ansattId = UUID.randomUUID()
        val ansattDto =
            AnsattDto(
                id = ansattId,
                personalia =
                    AnsattPersonaliaDto(
                        personident = "12345678910",
                        navn =
                            NavnDto(
                                fornavn = "Fornavn",
                                mellomnavn = null,
                                etternavn = "Etternavn",
                            ),
                    ),
                arrangorer =
                    listOf(
                        TilknyttetArrangorDto(
                            arrangorId = UUID.randomUUID(),
                            roller = listOf(AnsattRolle.KOORDINATOR, AnsattRolle.VEILEDER),
                            veileder = listOf(VeilederDto(deltaker.id, Veiledertype.VEILEDER)),
                            koordinator = listOf(UUID.randomUUID()),
                        ),
                    ),
            )
        tiltaksarrangorAnsattRepository.insertOrUpdateAnsatt(ansattDto.toAnsattDbo())
        kafkaConsumer.listen(
            consumerRecord(
                ARRANGOR_ANSATT_TOPIC,
                ansattId.toString(),
                null,
            ),
            ack,
        )

        tiltaksarrangorAnsattRepository.getAnsattRolleListe(ansattId) shouldBe emptyList()
        tiltaksarrangorAnsattRepository.getKoordinatorDeltakerlisteDboListe(ansattId) shouldBe emptyList()
        tiltaksarrangorAnsattRepository.getVeilederDeltakerDboListe(ansattId) shouldBe emptyList()
        tiltaksarrangorAnsattRepository.getAnsatt(ansattId) shouldBe null
    }

    @Test
    fun `listen - melding pa deltaker-topic - lagres i database`() {
        val enhetId = UUID.randomUUID()
        val ansattId = UUID.randomUUID()
        with(DeltakerDtoCtx()) {
            deltakerlisteRepository.insertOrUpdateDeltakerliste(
                getDeltakerliste(
                    id = deltakerDto.id,
                    arrangorId = UUID.randomUUID(),
                    lopenummer = null,
                ),
            )
            val avbrytDeltakelseEndring = DeltakerEndring.Endring.AvbrytDeltakelse(
                DeltakerEndring.Aarsak(DeltakerEndring.Aarsak.Type.TRENGER_ANNEN_STOTTE, null),
                sluttdato = LocalDate.now().minusWeeks(4),
                begrunnelse = null,
            )
            val dto = deltakerDto.copy(
                historikk = listOf(
                    DeltakerHistorikk.Endring(
                        endring = DeltakerEndring(
                            id = UUID.randomUUID(),
                            endring = avbrytDeltakelseEndring,
                            deltakerId = deltakerDto.id,
                            endretAv = ansattId,
                            endretAvEnhet = enhetId,
                            forslag = null,
                            endret = LocalDateTime.now(),
                        ),
                    ),
                ),
            )
            medVurderinger()

            kafkaConsumer.listen(
                consumerRecord(
                    DELTAKER_TOPIC,
                    dto.id.toString(),
                    objectMapper.writeValueAsString(dto),
                ),
                ack,
            )

            deltakerRepository.getDeltaker(dto.id) shouldNotBe null
        }
    }

    @Test
    fun `listen - tombstonemelding pa deltaker-topic - slettes i database`() {
        val deltaker = getDeltaker(UUID.randomUUID())

        deltakerRepository.insertOrUpdateDeltaker(deltaker)
        kafkaConsumer.listen(
            consumerRecord(
                DELTAKER_TOPIC,
                deltaker.id.toString(),
                null,
            ),
            ack,
        )

        deltakerRepository.getDeltaker(deltaker.id) shouldBe null
    }

    @Test
    fun `listen - avsluttet deltaker-melding pa deltaker-topic og deltaker finnes i db - sletter deltaker fra db`() {
        with(DeltakerDtoCtx()) {
            deltakerlisteRepository.insertOrUpdateDeltakerliste(getDeltakerliste(id = deltakerDto.id, UUID.randomUUID(), lopenummer = null))
            deltakerRepository.insertOrUpdateDeltaker(deltakerDto.toDeltakerDbo(null))

            medStatus(DeltakerStatus.Type.HAR_SLUTTET, 50)

            kafkaConsumer.listen(
                consumerRecord(
                    DELTAKER_TOPIC,
                    deltakerDto.id.toString(),
                    objectMapper.writeValueAsString(deltakerDto),
                ),
                ack,
            )

            deltakerRepository.getDeltaker(deltakerDto.id) shouldBe null
        }
    }

    @Test
    fun `listen - melding pa endringsmelding-topic - lagres i database`() {
        val endringsmeldingId = UUID.randomUUID()
        val endringsmeldingDto =
            EndringsmeldingDto(
                id = endringsmeldingId,
                deltakerId = UUID.randomUUID(),
                utfortAvNavAnsattId = null,
                opprettetAvArrangorAnsattId = UUID.randomUUID(),
                utfortTidspunkt = null,
                status = Endringsmelding.Status.AKTIV,
                type = EndringsmeldingType.ENDRE_SLUTTDATO,
                innhold = Innhold.EndreSluttdatoInnhold(sluttdato = LocalDate.now().plusWeeks(3)),
                createdAt = LocalDateTime.now(),
            )
        kafkaConsumer.listen(
            consumerRecord(
                ENDRINGSMELDING_TOPIC,
                endringsmeldingId.toString(),
                objectMapper.writeValueAsString(endringsmeldingDto),
            ),
            ack,
        )

        endringsmeldingRepository.getEndringsmelding(endringsmeldingId) shouldNotBe null
    }

    @Test
    fun `listen - tombstonemelding pa endringsmelding-topic - slettes i database`() {
        val endringsmeldingId = UUID.randomUUID()
        val endringsmeldingDto =
            EndringsmeldingDto(
                id = endringsmeldingId,
                deltakerId = UUID.randomUUID(),
                utfortAvNavAnsattId = null,
                opprettetAvArrangorAnsattId = UUID.randomUUID(),
                utfortTidspunkt = null,
                status = Endringsmelding.Status.AKTIV,
                type = EndringsmeldingType.ENDRE_SLUTTDATO,
                innhold = Innhold.EndreSluttdatoInnhold(sluttdato = LocalDate.now().plusWeeks(3)),
                createdAt = LocalDateTime.now(),
            )
        endringsmeldingRepository.insertOrUpdateEndringsmelding(endringsmeldingDto.toEndringsmeldingDbo())
        kafkaConsumer.listen(
            consumerRecord(
                ENDRINGSMELDING_TOPIC,
                endringsmeldingId.toString(),
                null,
            ),
            ack,
        )

        endringsmeldingRepository.getEndringsmelding(endringsmeldingId) shouldBe null
    }

    @Test
    fun `utfort endringsmelding-melding pa endringmelding-topic og endringsmelding finnes i db - oppdaterer endringsmelding i db`() {
        val endringsmeldingId = UUID.randomUUID()
        val endringsmeldingDto =
            EndringsmeldingDto(
                id = endringsmeldingId,
                deltakerId = UUID.randomUUID(),
                utfortAvNavAnsattId = null,
                opprettetAvArrangorAnsattId = UUID.randomUUID(),
                utfortTidspunkt = null,
                status = Endringsmelding.Status.AKTIV,
                type = EndringsmeldingType.ENDRE_SLUTTDATO,
                innhold = Innhold.EndreSluttdatoInnhold(sluttdato = LocalDate.now().plusWeeks(3)),
                createdAt = LocalDateTime.now(),
            )
        endringsmeldingRepository.insertOrUpdateEndringsmelding(endringsmeldingDto.toEndringsmeldingDbo())
        val utfortEndringsmeldingDto =
            EndringsmeldingDto(
                id = endringsmeldingId,
                deltakerId = UUID.randomUUID(),
                utfortAvNavAnsattId = null,
                opprettetAvArrangorAnsattId = UUID.randomUUID(),
                utfortTidspunkt = null,
                status = Endringsmelding.Status.UTFORT,
                type = EndringsmeldingType.ENDRE_SLUTTDATO,
                innhold = Innhold.EndreSluttdatoInnhold(sluttdato = LocalDate.now().plusWeeks(3)),
                createdAt = LocalDateTime.now(),
            )
        kafkaConsumer.listen(
            consumerRecord(
                ENDRINGSMELDING_TOPIC,
                endringsmeldingId.toString(),
                objectMapper.writeValueAsString(utfortEndringsmeldingDto),
            ),
            ack,
        )

        endringsmeldingRepository.getEndringsmelding(endringsmeldingId)?.status shouldBe Endringsmelding.Status.UTFORT
    }
}
