package no.nav.tiltaksarrangor.service

import com.ninjasquad.springmockk.MockkBean
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.clearMocks
import io.mockk.coEvery
import no.nav.tiltaksarrangor.IntegrationTest
import no.nav.tiltaksarrangor.client.amtarrangor.AmtArrangorClient
import no.nav.tiltaksarrangor.consumer.model.AnsattDto
import no.nav.tiltaksarrangor.consumer.model.AnsattPersonaliaDto
import no.nav.tiltaksarrangor.consumer.model.AnsattRolle
import no.nav.tiltaksarrangor.consumer.model.NavnDto
import no.nav.tiltaksarrangor.consumer.model.TilknyttetArrangorDto
import no.nav.tiltaksarrangor.consumer.model.VeilederDto
import no.nav.tiltaksarrangor.consumer.model.toAnsattDbo
import no.nav.tiltaksarrangor.model.Veiledertype
import no.nav.tiltaksarrangor.model.exceptions.UnauthorizedException
import no.nav.tiltaksarrangor.repositories.DeltakerRepository
import no.nav.tiltaksarrangor.repositories.TiltaksarrangorAnsattRepository
import no.nav.tiltaksarrangor.testutils.DbTestDataUtils.shouldBeCloseTo
import no.nav.tiltaksarrangor.testutils.getDeltaker
import no.nav.tiltaksarrangor.utils.sqlParameters
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDateTime
import java.util.UUID

class AnsattServiceTest(
    private val tiltaksarrangorAnsattRepository: TiltaksarrangorAnsattRepository,
    private val deltakerRepository: DeltakerRepository,
    private val ansattService: AnsattService,
    @MockkBean private val amtArrangorClient: AmtArrangorClient,
) : IntegrationTest() {
    @AfterEach
    fun tearDown() {
        clearMocks(amtArrangorClient)
    }

    @Test
    fun `oppdaterOgHentMineRoller - ansatt finnes ikke - lagres i database og returnerer riktige roller`() {
        val deltakerId = UUID.randomUUID()
        val deltakerId2 = UUID.randomUUID()
        deltakerRepository.insertOrUpdateDeltaker(getDeltaker(deltakerId))
        deltakerRepository.insertOrUpdateDeltaker(getDeltaker(deltakerId2))
        val ansattId = UUID.randomUUID()
        val personIdentInTest = "12345678910"
        coEvery { amtArrangorClient.getAnsatt() } returns getAnsatt(ansattId, personIdentInTest, deltakerId, deltakerId2)

        val rollerInTest = ansattService.oppdaterOgHentMineRoller()

        rollerInTest.size shouldBe 2
        rollerInTest.find { it == AnsattRolle.VEILEDER.name } shouldNotBe null
        rollerInTest.find { it == AnsattRolle.KOORDINATOR.name } shouldNotBe null

        val ansatt = tiltaksarrangorAnsattRepository.getAnsatt(ansattId).shouldNotBeNull()
        assertSoftly(ansatt) {
            personIdent shouldBe personIdentInTest
            roller.size shouldBe 4
            deltakerlister.size shouldBe 2
            veilederDeltakere.size shouldBe 2
        }

        val sistInnlogget = getSistInnlogget(ansattId).shouldNotBeNull()
        sistInnlogget shouldBeCloseTo LocalDateTime.now()
    }

    @Test
    fun `oppdaterOgHentMineRoller - ansatt finnes allerede - oppdateres i database og returnerer riktige roller`() {
        val deltakerId = UUID.randomUUID()
        val deltakerId2 = UUID.randomUUID()
        deltakerRepository.insertOrUpdateDeltaker(getDeltaker(deltakerId))
        deltakerRepository.insertOrUpdateDeltaker(getDeltaker(deltakerId2))
        val ansattId = UUID.randomUUID()
        val personIdentInTest = "12345678910"
        val ansatt = getAnsatt(ansattId, personIdentInTest, deltakerId, deltakerId2)
        tiltaksarrangorAnsattRepository.insertOrUpdateAnsatt(ansatt.toAnsattDbo())

        getSistInnlogget(ansattId) shouldBe null

        val oppdaterteArrangorer =
            listOf(
                TilknyttetArrangorDto(
                    arrangorId = UUID.randomUUID(),
                    roller = listOf(AnsattRolle.KOORDINATOR),
                    veileder = emptyList(),
                    koordinator = listOf(UUID.randomUUID()),
                ),
            )
        coEvery {
            amtArrangorClient.getAnsatt()
        } returns getAnsatt(ansattId, personIdentInTest, deltakerId, deltakerId2).copy(arrangorer = oppdaterteArrangorer)

        val rollerInTest = ansattService.oppdaterOgHentMineRoller()

        rollerInTest.size shouldBe 1
        rollerInTest.find { it == AnsattRolle.KOORDINATOR.name } shouldNotBe null

        val oppdatertAnsatt = tiltaksarrangorAnsattRepository.getAnsatt(ansattId).shouldNotBeNull()

        assertSoftly(oppdatertAnsatt) {
            personIdent shouldBe personIdentInTest
            roller.size shouldBe 1
            deltakerlister.size shouldBe 1
            veilederDeltakere.size shouldBe 0
        }

        val oppdatertSistInnlogget = getSistInnlogget(ansattId).shouldNotBeNull()
        oppdatertSistInnlogget shouldBeCloseTo LocalDateTime.now()
    }

    @Test
    fun `oppdaterOgHentMineRoller - ansatt har ingen roller - lagres ikke i database og returnerer tom liste`() {
        val personIdent = "1234"
        coEvery { amtArrangorClient.getAnsatt() } returns null

        ansattService.oppdaterOgHentMineRoller()

        ansattFinnes(personIdent) shouldBe false
    }

    @Test
    fun `oppdaterOgHentMineRoller - amt-arrangor svarer med feilmelding - lagres ikke i database og returnerer feilmelding`() {
        val personIdent = "1234"
        coEvery { amtArrangorClient.getAnsatt() } throws UnauthorizedException("Fant ikke ansatt")

        assertThrows<UnauthorizedException> {
            ansattService.oppdaterOgHentMineRoller()
        }

        ansattFinnes(personIdent) shouldBe false
    }

    private fun ansattFinnes(personIdent: String): Boolean = template.queryForObject(
        "SELECT EXISTS(SELECT id FROM ansatt WHERE personident = :personIdent)",
        sqlParameters("personIdent" to personIdent),
        Boolean::class.java,
    ) ?: false

    private fun getSistInnlogget(ansattId: UUID): LocalDateTime? = template.queryForObject(
        "SELECT sist_innlogget FROM ansatt WHERE id = :ansattId",
        sqlParameters("ansattId" to ansattId),
        LocalDateTime::class.java,
    )

    private fun getAnsatt(
        ansattId: UUID,
        personIdent: String,
        deltakerIdForVeileder: UUID,
        deltakerIdForVeileder2: UUID,
    ): AnsattDto = AnsattDto(
        id = ansattId,
        personalia =
            AnsattPersonaliaDto(
                personident = personIdent,
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
                    veileder = listOf(VeilederDto(deltakerIdForVeileder, Veiledertype.VEILEDER)),
                    koordinator = listOf(UUID.randomUUID()),
                ),
                TilknyttetArrangorDto(
                    arrangorId = UUID.randomUUID(),
                    roller = listOf(AnsattRolle.KOORDINATOR),
                    veileder = emptyList(),
                    koordinator = listOf(UUID.randomUUID()),
                ),
                TilknyttetArrangorDto(
                    arrangorId = UUID.randomUUID(),
                    roller = listOf(AnsattRolle.VEILEDER),
                    veileder = listOf(VeilederDto(deltakerIdForVeileder2, Veiledertype.MEDVEILEDER)),
                    koordinator = emptyList(),
                ),
            ),
    )
}
