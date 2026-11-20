package no.nav.tiltaksarrangor

import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.verify
import no.nav.amt.lib.models.deltaker.DeltakerHistorikk
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.kafka.Kontaktinformasjon
import no.nav.amt.lib.models.tiltakskoordinator.EndringFraTiltakskoordinator
import no.nav.tiltaksarrangor.client.amtperson.NavAnsattResponse
import no.nav.tiltaksarrangor.consumer.ConsumerTestUtils.arrangorInTest
import no.nav.tiltaksarrangor.consumer.ConsumerTestUtils.gjennomforingPayloadInTest
import no.nav.tiltaksarrangor.consumer.DeltakerDtoCtx
import no.nav.tiltaksarrangor.consumer.DeltakerlisteConsumerService
import no.nav.tiltaksarrangor.consumer.KafkaConsumerService
import no.nav.tiltaksarrangor.consumer.model.NavEnhet
import no.nav.tiltaksarrangor.consumer.model.toAnsattDbo
import no.nav.tiltaksarrangor.consumer.model.toArrangorDbo
import no.nav.tiltaksarrangor.consumer.model.toDeltakerDbo
import no.nav.tiltaksarrangor.melding.forslag.ForslagRepository
import no.nav.tiltaksarrangor.model.Oppdatering
import no.nav.tiltaksarrangor.repositories.ArrangorRepository
import no.nav.tiltaksarrangor.repositories.DeltakerRepository
import no.nav.tiltaksarrangor.repositories.DeltakerlisteRepository
import no.nav.tiltaksarrangor.repositories.NavEnhetRepository
import no.nav.tiltaksarrangor.repositories.TiltaksarrangorAnsattRepository
import no.nav.tiltaksarrangor.repositories.UlestEndringRepository
import no.nav.tiltaksarrangor.service.AnsattService
import no.nav.tiltaksarrangor.testutils.DeltakerContext
import no.nav.tiltaksarrangor.testutils.getDeltaker
import no.nav.tiltaksarrangor.testutils.getForslag
import no.nav.tiltaksarrangor.testutils.getMockAnsatt
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.queryForObject
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

class DatabaseAtomicityTest(
    private val deltakerRepository: DeltakerRepository,
    private val deltakerlisteRepository: DeltakerlisteRepository,
    private val forslagRepository: ForslagRepository,
    private val ulestEndringRepository: UlestEndringRepository,
    private val navEnhetRepository: NavEnhetRepository,
    private val arrangorRepository: ArrangorRepository,
    private val ansattRepository: TiltaksarrangorAnsattRepository,
    private val kafkaConsumerService: KafkaConsumerService,
    private val deltakerlisteConsumerService: DeltakerlisteConsumerService,
    private val ansattService: AnsattService,
) : IntegrationTestBase() {
    @Test
    fun `deltakersletting ruller tilbake alle tilhorende data ved feil og kan gjentas`() {
        val context = deltakerMedTilknyttedeData()
        val opprinnelig = databaseSnapshot()

        medSlettefeil("deltaker") {
            assertThrows<DataIntegrityViolationException> {
                deltakerRepository.deleteDeltaker(context.deltaker.id)
            }
        }

        databaseSnapshot() shouldBe opprinnelig
        deltakerRepository.deleteDeltaker(context.deltaker.id) shouldBe 1
        deltakerRepository.deleteDeltaker(context.deltaker.id) shouldBe 0
        listOf("deltaker", "veileder_deltaker", "endringsmelding", "forslag", "ulest_endring").forEach {
            jdbcTemplate.queryForObject<Int>("SELECT count(*) FROM $it") shouldBe 0
        }
    }

    @Test
    fun `deltakerlistesletting ruller tilbake flere deltakere og koordinatorer samlet`() {
        val context = deltakerMedTilknyttedeData()
        deltakerRepository.insertOrUpdateDeltaker(getDeltaker(deltakerlisteId = context.deltakerliste.id))
        val opprinnelig = databaseSnapshot()

        medSlettefeil("deltakerliste") {
            assertThrows<DataIntegrityViolationException> {
                deltakerlisteRepository.deleteDeltakerlisteOgDeltakere(context.deltakerliste.id)
            }
        }

        databaseSnapshot() shouldBe opprinnelig
        deltakerlisteRepository.deleteDeltakerlisteOgDeltakere(context.deltakerliste.id) shouldBe 1
        listOf("deltakerliste", "deltaker", "koordinator_deltakerliste", "veileder_deltaker", "endringsmelding", "forslag", "ulest_endring")
            .forEach { jdbcTemplate.queryForObject<Int>("SELECT count(*) FROM $it") shouldBe 0 }
    }

    @Test
    fun `direkte listesletting av deltakere ruller tilbake tidligere deltakere ved feil`() {
        val context = deltakerMedTilknyttedeData()
        deltakerRepository.insertOrUpdateDeltaker(getDeltaker(deltakerlisteId = context.deltakerliste.id))
        val opprinnelig = databaseSnapshot()
        jdbcTemplate.execute("CREATE SEQUENCE test_delete_attempt")
        jdbcTemplate.execute(
            """
            CREATE FUNCTION test_fail_second_delete() RETURNS trigger LANGUAGE plpgsql AS ${'$'}${'$'}
            BEGIN
                IF nextval('test_delete_attempt') = 2 THEN
                    RAISE EXCEPTION 'Test delete failure' USING ERRCODE = '23514';
                END IF;
                RETURN OLD;
            END;
            ${'$'}${'$'}
            """.trimIndent(),
        )
        jdbcTemplate.execute(
            "CREATE TRIGGER test_delete_failure BEFORE DELETE ON deltaker FOR EACH ROW EXECUTE FUNCTION test_fail_second_delete()",
        )
        try {
            assertThrows<DataIntegrityViolationException> {
                deltakerRepository.deleteDeltakereForDeltakerliste(context.deltakerliste.id)
            }
        } finally {
            jdbcTemplate.execute("DROP TRIGGER test_delete_failure ON deltaker")
            jdbcTemplate.execute("DROP FUNCTION test_fail_second_delete()")
            jdbcTemplate.execute("DROP SEQUENCE test_delete_attempt")
        }

        databaseSnapshot() shouldBe opprinnelig
        deltakerRepository.deleteDeltakereForDeltakerliste(context.deltakerliste.id) shouldBe 2
    }

    @Test
    fun `ny deltaker og referansedata rulles tilbake sammen og hendelsen kan behandles pa nytt`() {
        val payload = deltakerMedHistorikk()
        medConstraintFeil("ulest_endring") {
            assertThrows<DataIntegrityViolationException> {
                kafkaConsumerService.lagreDeltaker(payload.id, objectMapper.writeValueAsString(payload))
            }
        }

        deltakerRepository.getDeltaker(payload.id) shouldBe null
        jdbcTemplate.queryForObject<Int>("SELECT count(*) FROM nav_enhet") shouldBe 0
        jdbcTemplate.queryForObject<Int>("SELECT count(*) FROM nav_ansatt") shouldBe 0
        ulestEndringRepository.getMany(payload.id).size shouldBe 0

        kafkaConsumerService.lagreDeltaker(payload.id, objectMapper.writeValueAsString(payload))

        val deltaker = deltakerRepository.getDeltaker(payload.id).shouldNotBeNull()
        deltaker.telefonnummer shouldBe "00000000"
        deltaker.epost shouldBe "syntetisk@example.test"
        ulestEndringRepository.getMany(payload.id).size shouldBe 1
        jdbcTemplate.queryForObject<Int>("SELECT count(*) FROM nav_enhet") shouldBe 1
        jdbcTemplate.queryForObject<Int>("SELECT count(*) FROM nav_ansatt") shouldBe 1

        kafkaConsumerService.lagreDeltaker(payload.id, objectMapper.writeValueAsString(payload))
        ulestEndringRepository.getMany(payload.id).size shouldBe 1
        verify(exactly = 2) { amtPersonClient.hentOppdatertKontaktinfo(any<String>()) }
        verify(exactly = 2) { amtPersonClient.hentEnhet(any()) }
        verify(exactly = 2) { amtPersonClient.hentNavAnsatt(any()) }
    }

    @Test
    fun `eksisterende deltaker uleste endringer og oppfriskede referansedata rulles tilbake sammen`() {
        val payload = deltakerMedHistorikk()
        deltakerRepository.insertOrUpdateDeltaker(payload.copy(historikk = emptyList()).toDeltakerDbo(null))
        val opprinneligEnhet = NavEnhet(
            (payload.historikk!!.single() as DeltakerHistorikk.EndringFraTiltakskoordinator).endringFraTiltakskoordinator.endretAvEnhet,
            "0000",
            "Tidligere enhet",
        )
        navEnhetRepository.upsert(opprinneligEnhet)
        jdbcTemplate.update("UPDATE nav_enhet SET modified_at = ? WHERE id = ?", LocalDateTime.now().minusMonths(2), opprinneligEnhet.id)
        val opprinnelig = databaseSnapshot()

        medConstraintFeil("deltaker") {
            assertThrows<DataIntegrityViolationException> {
                kafkaConsumerService.lagreDeltaker(payload.id, objectMapper.writeValueAsString(payload))
            }
        }

        databaseSnapshot() shouldBe opprinnelig
        kafkaConsumerService.lagreDeltaker(payload.id, objectMapper.writeValueAsString(payload))
        navEnhetRepository.get(opprinneligEnhet.id).shouldNotBeNull().navn shouldBe "Syntetisk enhet"
        ulestEndringRepository.getMany(payload.id).size shouldBe 1
        verify(exactly = 0) { amtPersonClient.hentOppdatertKontaktinfo(any<String>()) }
    }

    @Test
    fun `ansattoppdatering og sist innlogget rulles tilbake samlet ved feil`() {
        val ansatt = getMockAnsatt(personIdent = "00000000000")
        ansattRepository.insertOrUpdateAnsatt(ansatt.toAnsattDbo())
        val opprinnelig = databaseSnapshot()
        every { amtArrangorClient.getAnsatt() } answers {
            TransactionSynchronizationManager.isActualTransactionActive() shouldBe false
            ansatt.copy(arrangorer = emptyList())
        }
        jdbcTemplate.execute("ALTER TABLE ansatt ADD CONSTRAINT test_login_failure CHECK (sist_innlogget IS NULL)")
        try {
            assertThrows<DataIntegrityViolationException> { ansattService.oppdaterOgHentMineRoller() }
        } finally {
            jdbcTemplate.execute("ALTER TABLE ansatt DROP CONSTRAINT test_login_failure")
        }

        databaseSnapshot() shouldBe opprinnelig
        ansattService.oppdaterOgHentMineRoller() shouldBe emptyList()
        ansattRepository.getAnsatt(ansatt.id).shouldNotBeNull().roller shouldBe emptyList()
        jdbcTemplate.queryForObject<LocalDateTime>("SELECT sist_innlogget FROM ansatt WHERE id = ?", ansatt.id).shouldNotBeNull()
    }

    @Test
    fun `retry av innlogging starter en ny transaksjon for hele ansattoppdateringen`() {
        val ansatt = getMockAnsatt(personIdent = "00000000000")
        ansatt.arrangorer.flatMap { it.veileder }.forEach {
            deltakerRepository.insertOrUpdateDeltaker(getDeltaker(it.deltakerId))
        }
        every { amtArrangorClient.getAnsatt() } answers {
            TransactionSynchronizationManager.isActualTransactionActive() shouldBe false
            ansatt
        }
        jdbcTemplate.execute("CREATE SEQUENCE test_login_attempt")
        jdbcTemplate.execute(
            """
            CREATE FUNCTION test_fail_first_login() RETURNS trigger LANGUAGE plpgsql AS ${'$'}${'$'}
            BEGIN
                IF nextval('test_login_attempt') = 1 THEN
                    RAISE EXCEPTION 'Test deadlock' USING ERRCODE = '40P01';
                END IF;
                RETURN NEW;
            END;
            ${'$'}${'$'}
            """.trimIndent(),
        )
        jdbcTemplate.execute(
            "CREATE TRIGGER test_login_failure BEFORE UPDATE OF sist_innlogget ON ansatt FOR EACH ROW EXECUTE FUNCTION test_fail_first_login()",
        )
        try {
            ansattService.oppdaterOgHentMineRoller().toSet() shouldBe ansatt.arrangorer
                .flatMap { it.roller }
                .map { it.name }
                .toSet()
            jdbcTemplate.queryForObject<Long>("SELECT last_value FROM test_login_attempt") shouldBe 2L
        } finally {
            jdbcTemplate.execute("DROP TRIGGER test_login_failure ON ansatt")
            jdbcTemplate.execute("DROP FUNCTION test_fail_first_login()")
            jdbcTemplate.execute("DROP SEQUENCE test_login_attempt")
        }

        val lagretAnsatt = ansattRepository.getAnsatt(ansatt.id).shouldNotBeNull()
        val forventetAnsatt = ansatt.toAnsattDbo()
        lagretAnsatt.roller.toSet() shouldBe forventetAnsatt.roller.toSet()
        lagretAnsatt.deltakerlister.toSet() shouldBe forventetAnsatt.deltakerlister.toSet()
        lagretAnsatt.veilederDeltakere.toSet() shouldBe forventetAnsatt.veilederDeltakere.toSet()
        jdbcTemplate.queryForObject<LocalDateTime>("SELECT sist_innlogget FROM ansatt WHERE id = ?", ansatt.id).shouldNotBeNull()
        verify(exactly = 1) { amtArrangorClient.getAnsatt() }
    }

    @Test
    fun `ny arrangor rulles tilbake hvis deltakerlisten ikke kan lagres`() {
        every { hentArrangorClient.getArrangor(arrangorInTest.organisasjonsnummer) } answers {
            TransactionSynchronizationManager.isActualTransactionActive() shouldBe false
            arrangorInTest
        }
        medConstraintFeil("deltakerliste") {
            assertThrows<DataIntegrityViolationException> {
                deltakerlisteConsumerService.handleGjennomforing(
                    gjennomforingPayloadInTest.id,
                    objectMapper.writeValueAsString(gjennomforingPayloadInTest),
                )
            }
        }

        arrangorRepository.getArrangor(arrangorInTest.id) shouldBe null
        deltakerlisteRepository.getDeltakerliste(gjennomforingPayloadInTest.id) shouldBe null

        repeat(2) {
            deltakerlisteConsumerService.handleGjennomforing(
                gjennomforingPayloadInTest.id,
                objectMapper.writeValueAsString(gjennomforingPayloadInTest),
            )
        }
        arrangorRepository.getArrangor(arrangorInTest.id) shouldBe arrangorInTest.toArrangorDbo()
        deltakerlisteRepository.getDeltakerliste(gjennomforingPayloadInTest.id).shouldNotBeNull()
        verify(exactly = 2) { hentArrangorClient.getArrangor(any()) }
    }

    private fun deltakerMedTilknyttedeData(): DeltakerContext = DeltakerContext(applicationContext).also {
        it.medEndringsmelding()
        forslagRepository.upsert(getForslag(it.deltaker.id).copy(opprettetAvArrangorAnsattId = it.koordinator.id))
        ulestEndringRepository.insert(it.deltaker.id, Oppdatering.NyDeltaker(null, null, LocalDate.now()))
    }

    private fun deltakerMedHistorikk() = DeltakerDtoCtx()
        .apply {
            medStatus(DeltakerStatus.Type.DELTAR)
            medHistorikk(
                listOf(
                    DeltakerHistorikk.EndringFraTiltakskoordinator(
                        EndringFraTiltakskoordinator(
                            id = UUID.randomUUID(),
                            deltakerId = deltakerDto.id,
                            endring = EndringFraTiltakskoordinator.DelMedArrangor,
                            endretAv = UUID.randomUUID(),
                            endretAvEnhet = UUID.randomUUID(),
                            endret = LocalDateTime.now(),
                        ),
                    ),
                ),
            )
            every { amtPersonClient.hentEnhet(any()) } answers {
                TransactionSynchronizationManager.isActualTransactionActive() shouldBe false
                NavEnhet(firstArg(), "0000", "Syntetisk enhet")
            }
            every { amtPersonClient.hentNavAnsatt(any()) } answers {
                TransactionSynchronizationManager.isActualTransactionActive() shouldBe false
                NavAnsattResponse(firstArg(), "X000000", "Syntetisk ansatt", null, null)
            }
            every { amtPersonClient.hentOppdatertKontaktinfo(any<String>()) } answers {
                TransactionSynchronizationManager.isActualTransactionActive() shouldBe false
                Kontaktinformasjon(epost = "syntetisk@example.test", telefonnummer = "00000000")
            }
        }.deltakerDto

    private fun databaseSnapshot(): Map<String, List<String>> = listOf(
        "deltaker",
        "deltakerliste",
        "veileder_deltaker",
        "koordinator_deltakerliste",
        "endringsmelding",
        "forslag",
        "ulest_endring",
        "nav_enhet",
        "nav_ansatt",
        "ansatt",
        "ansatt_rolle",
    ).associateWith { table ->
        jdbcTemplate.query("SELECT row_to_json(t)::text FROM $table t ORDER BY row_to_json(t)::text") { rs, _ -> rs.getString(1) }
    }

    private fun medConstraintFeil(
        table: String,
        block: () -> Unit,
    ) {
        jdbcTemplate.execute("ALTER TABLE $table ADD CONSTRAINT test_write_failure CHECK (false) NOT VALID")
        try {
            block()
        } finally {
            jdbcTemplate.execute("ALTER TABLE $table DROP CONSTRAINT test_write_failure")
        }
    }

    private fun medSlettefeil(
        table: String,
        block: () -> Unit,
    ) {
        jdbcTemplate.execute(
            """
            CREATE FUNCTION test_fail_delete() RETURNS trigger LANGUAGE plpgsql AS ${'$'}${'$'}
            BEGIN
                RAISE EXCEPTION 'Test delete failure' USING ERRCODE = '23514';
            END;
            ${'$'}${'$'}
            """.trimIndent(),
        )
        jdbcTemplate.execute(
            "CREATE TRIGGER test_delete_failure BEFORE DELETE ON $table FOR EACH ROW EXECUTE FUNCTION test_fail_delete()",
        )
        try {
            block()
        } finally {
            jdbcTemplate.execute("DROP TRIGGER test_delete_failure ON $table")
            jdbcTemplate.execute("DROP FUNCTION test_fail_delete()")
        }
    }
}
