package no.nav.amt.deltaker.bff.navtiltakskoordinator.ulestdeltakerhendelse

import io.kotest.matchers.shouldBe
import no.nav.amt.deltaker.bff.navtiltakskoordinator.extensions.toUlestHendelse
import no.nav.amt.deltaker.bff.utils.TestData.lagDeltaker
import no.nav.amt.deltaker.bff.utils.TestData.lagDeltakerliste
import no.nav.amt.deltaker.bff.utils.TestData.lagHendelse
import no.nav.amt.deltaker.bff.utils.TestData.lagTiltakstype
import no.nav.amt.deltaker.bff.utils.TestRepository
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import no.nav.amt.lib.models.hendelse.HendelseType
import no.nav.amt.lib.testing.DatabaseTestExtension
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.LocalDate
import java.util.UUID

class UlestHendelseRepositoryTest {
    private val repository = UlestHendelseRepository()

    companion object {
        @RegisterExtension
        val dbExtension = DatabaseTestExtension()
    }

    private fun lagDeltakerMedHendelse(): Pair<UUID, UUID> {
        val deltakerliste = lagDeltakerliste(
            tiltakstype = lagTiltakstype(tiltakskode = Tiltakskode.GRUPPE_ARBEIDSMARKEDSOPPLAERING),
        )
        val deltaker = lagDeltaker(deltakerliste = deltakerliste)
        TestRepository.insert(deltaker)

        val hendelse = lagHendelse(
            deltaker = deltaker,
            payload = HendelseType.AvbrytDeltakelse(
                aarsak = null,
                sluttdato = LocalDate.now(),
                begrunnelseFraNav = null,
                begrunnelseFraArrangor = null,
                endringFraForslag = null,
            ),
        )

        hendelse.toUlestHendelse()?.let { repository.upsert(it) }

        return deltaker.id to hendelse.id
    }

    @Test
    fun `getForDeltakere - tomt input-sett - returnerer tomt map`() {
        val result = repository.getForDeltakere(emptySet())

        result shouldBe emptyMap()
    }

    @Test
    fun `getForDeltakere - flere deltakere med hendelser - returnerer gruppert per deltaker`() {
        val (deltakerId1, hendelseId1) = lagDeltakerMedHendelse()
        val (deltakerId2, hendelseId2) = lagDeltakerMedHendelse()

        val result = repository.getForDeltakere(setOf(deltakerId1, deltakerId2))

        result.size shouldBe 2
        result[deltakerId1]!!.size shouldBe 1
        result[deltakerId1]!!.single().id shouldBe hendelseId1
        result[deltakerId2]!!.size shouldBe 1
        result[deltakerId2]!!.single().id shouldBe hendelseId2
    }

    @Test
    fun `getForDeltakere - deltaker uten hendelser - finnes ikke i resultatet`() {
        val (deltakerId1, _) = lagDeltakerMedHendelse()
        val deltakerUtenHendelse = lagDeltaker(
            deltakerliste = lagDeltakerliste(
                tiltakstype = lagTiltakstype(tiltakskode = Tiltakskode.GRUPPE_ARBEIDSMARKEDSOPPLAERING),
            ),
        )
        TestRepository.insert(deltakerUtenHendelse)

        val result = repository.getForDeltakere(setOf(deltakerId1, deltakerUtenHendelse.id))

        result.size shouldBe 1
        result.containsKey(deltakerId1) shouldBe true
        result.containsKey(deltakerUtenHendelse.id) shouldBe false
    }

    @Test
    fun `getForDeltakere - deltaker med flere hendelser - alle returneres`() {
        val deltakerliste = lagDeltakerliste(
            tiltakstype = lagTiltakstype(tiltakskode = Tiltakskode.GRUPPE_ARBEIDSMARKEDSOPPLAERING),
        )
        val deltaker = lagDeltaker(deltakerliste = deltakerliste)
        TestRepository.insert(deltaker)

        val hendelse1 = lagHendelse(
            deltaker = deltaker,
            payload = HendelseType.AvbrytDeltakelse(
                aarsak = null,
                sluttdato = LocalDate.now(),
                begrunnelseFraNav = null,
                begrunnelseFraArrangor = null,
                endringFraForslag = null,
            ),
        )
        val hendelse2 = lagHendelse(
            deltaker = deltaker,
            payload = HendelseType.EndreBakgrunnsinformasjon(
                bakgrunnsinformasjon = "Ny bakgrunnsinformasjon",
            ),
        )

        hendelse1.toUlestHendelse()?.let { repository.upsert(it) }
        hendelse2.toUlestHendelse()?.let { repository.upsert(it) }

        val result = repository.getForDeltakere(setOf(deltaker.id))

        result.size shouldBe 1
        result[deltaker.id]!!.size shouldBe 2
        result[deltaker.id]!!.map { it.id }.toSet() shouldBe setOf(hendelse1.id, hendelse2.id)
    }
}
