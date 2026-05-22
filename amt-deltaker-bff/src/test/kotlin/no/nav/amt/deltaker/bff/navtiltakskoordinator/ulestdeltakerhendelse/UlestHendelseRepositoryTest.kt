package no.nav.amt.deltaker.bff.navtiltakskoordinator.ulestdeltakerhendelse

import io.kotest.matchers.shouldBe
import no.nav.amt.deltaker.bff.navtiltakskoordinator.extensions.toUlestHendelse
import no.nav.amt.deltaker.bff.navtiltakskoordinator.ulestdeltakerhendelse.model.UlestHendelseFlags
import no.nav.amt.deltaker.bff.navtiltakskoordinator.ulestdeltakerhendelse.model.UlestHendelseTypeCounts
import no.nav.amt.deltaker.bff.utils.TestData.lagDeltaker
import no.nav.amt.deltaker.bff.utils.TestData.lagDeltakerliste
import no.nav.amt.deltaker.bff.utils.TestData.lagHendelse
import no.nav.amt.deltaker.bff.utils.TestData.lagTiltakstype
import no.nav.amt.deltaker.bff.utils.TestRepository
import no.nav.amt.lib.models.deltaker.DeltakerEndring
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import no.nav.amt.lib.models.hendelse.HendelseType
import no.nav.amt.lib.models.hendelse.UtkastDto
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

    private fun lagDeltakerOgLagreHendelse(payload: HendelseType): UUID {
        val deltakerliste = lagDeltakerliste(
            tiltakstype = lagTiltakstype(tiltakskode = Tiltakskode.GRUPPE_ARBEIDSMARKEDSOPPLAERING),
        )
        val deltaker = lagDeltaker(deltakerliste = deltakerliste)
        TestRepository.insert(deltaker)

        val hendelse = lagHendelse(
            deltaker = deltaker,
            payload = payload,
        )
        hendelse.toUlestHendelse()?.let { repository.upsert(it) }

        return deltaker.id
    }

    @Test
    fun `getForDeltakere - tomt input-sett - returnerer tomt map`() {
        val result = repository.getForDeltakere(emptySet())

        result shouldBe emptyMap()
    }

    @Test
    fun `getForDeltakere - flere deltakere med hendelser - returnerer flagg per deltaker`() {
        val (deltakerId1, _) = lagDeltakerMedHendelse()
        val (deltakerId2, _) = lagDeltakerMedHendelse()

        val result = repository.getForDeltakere(setOf(deltakerId1, deltakerId2))

        result.size shouldBe 2
        result[deltakerId1] shouldBe UlestHendelseFlags(
            erNyDeltaker = false,
            harOppdateringFraNav = true,
        )
        result[deltakerId2] shouldBe UlestHendelseFlags(
            erNyDeltaker = false,
            harOppdateringFraNav = true,
        )
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
    fun `getForDeltakere - deltaker med flere hendelser - flagg kombineres`() {
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
            payload = HendelseType.NavGodkjennUtkast(
                utkast = UtkastDto(
                    startdato = LocalDate.now(),
                    sluttdato = LocalDate.now().plusMonths(1),
                    dagerPerUke = 5f,
                    deltakelsesprosent = 100f,
                    bakgrunnsinformasjon = "Godkjent utkast",
                    innhold = null,
                ),
            ),
        )

        hendelse1.toUlestHendelse()?.let { repository.upsert(it) }
        hendelse2.toUlestHendelse()?.let { repository.upsert(it) }

        val result = repository.getForDeltakere(setOf(deltaker.id))

        result.size shouldBe 1
        result[deltaker.id] shouldBe UlestHendelseFlags(
            erNyDeltaker = true,
            harOppdateringFraNav = true,
        )
    }

    @Test
    fun `getTypeCountsForDeltakere - tomt input-sett - returnerer nullstillte tellere`() {
        val result = repository.getTypeCountsForDeltakere(emptySet())

        result shouldBe UlestHendelseTypeCounts(
            erNyDeltaker = 0,
            harOppdateringFraNav = 0,
        )
    }

    @Test
    fun `getTypeCountsForDeltakere - teller begge kategorier korrekt`() {
        val nyDeltaker1 = lagDeltakerOgLagreHendelse(
            HendelseType.InnbyggerGodkjennUtkast(
                utkast = UtkastDto(
                    startdato = LocalDate.now(),
                    sluttdato = LocalDate.now().plusWeeks(1),
                    dagerPerUke = 5f,
                    deltakelsesprosent = 100f,
                    bakgrunnsinformasjon = "innbygger-godkjenning",
                    innhold = null,
                ),
            ),
        )
        val nyDeltaker2 = lagDeltakerOgLagreHendelse(
            HendelseType.NavGodkjennUtkast(
                utkast = UtkastDto(
                    startdato = LocalDate.now(),
                    sluttdato = LocalDate.now().plusWeeks(2),
                    dagerPerUke = 5f,
                    deltakelsesprosent = 100f,
                    bakgrunnsinformasjon = "nav-godkjenning",
                    innhold = null,
                ),
            ),
        )
        val oppdateringFraNav1 = lagDeltakerOgLagreHendelse(
            HendelseType.IkkeAktuell(
                aarsak = DeltakerEndring.Aarsak(
                    type = DeltakerEndring.Aarsak.Type.ANNET,
                    beskrivelse = "Annen årsak",
                ),
                begrunnelseFraNav = null,
                begrunnelseFraArrangor = null,
                endringFraForslag = null,
            ),
        )
        val oppdateringFraNav2 = lagDeltakerOgLagreHendelse(
            HendelseType.AvbrytDeltakelse(
                aarsak = null,
                sluttdato = LocalDate.now(),
                begrunnelseFraNav = null,
                begrunnelseFraArrangor = null,
                endringFraForslag = null,
            ),
        )
        val ikkeTellbar = lagDeltakerOgLagreHendelse(
            HendelseType.EndreStartdato(
                startdato = LocalDate.now(),
                sluttdato = null,
                begrunnelseFraNav = null,
                begrunnelseFraArrangor = null,
                endringFraForslag = null,
            ),
        )

        val result = repository.getTypeCountsForDeltakere(
            setOf(nyDeltaker1, nyDeltaker2, oppdateringFraNav1, oppdateringFraNav2, ikkeTellbar),
        )

        result shouldBe UlestHendelseTypeCounts(
            erNyDeltaker = 2,
            harOppdateringFraNav = 2,
        )
    }

    @Test
    fun `getTypeCountsForDeltakere - teller kun for oppgitte deltakere`() {
        val inkludert = lagDeltakerOgLagreHendelse(
            HendelseType.NavGodkjennUtkast(
                utkast = UtkastDto(
                    startdato = LocalDate.now(),
                    sluttdato = LocalDate.now().plusWeeks(1),
                    dagerPerUke = 5f,
                    deltakelsesprosent = 100f,
                    bakgrunnsinformasjon = "inkludert",
                    innhold = null,
                ),
            ),
        )
        lagDeltakerOgLagreHendelse(
            HendelseType.AvbrytDeltakelse(
                aarsak = null,
                sluttdato = LocalDate.now(),
                begrunnelseFraNav = null,
                begrunnelseFraArrangor = null,
                endringFraForslag = null,
            ),
        )

        val result = repository.getTypeCountsForDeltakere(setOf(inkludert))

        result shouldBe UlestHendelseTypeCounts(
            erNyDeltaker = 1,
            harOppdateringFraNav = 0,
        )
    }

    @Test
    fun `getTypeCountsForDeltakere - teller unike deltakere per kategori`() {
        val deltakerliste = lagDeltakerliste(
            tiltakstype = lagTiltakstype(tiltakskode = Tiltakskode.GRUPPE_ARBEIDSMARKEDSOPPLAERING),
        )
        val deltaker = lagDeltaker(deltakerliste = deltakerliste)
        TestRepository.insert(deltaker)

        val hendelse1 = lagHendelse(
            deltaker = deltaker,
            payload = HendelseType.NavGodkjennUtkast(
                utkast = UtkastDto(
                    startdato = LocalDate.now(),
                    sluttdato = LocalDate.now().plusWeeks(1),
                    dagerPerUke = 5f,
                    deltakelsesprosent = 100f,
                    bakgrunnsinformasjon = "første utkast",
                    innhold = null,
                ),
            ),
        )
        val hendelse2 = lagHendelse(
            deltaker = deltaker,
            payload = HendelseType.InnbyggerGodkjennUtkast(
                utkast = UtkastDto(
                    startdato = LocalDate.now(),
                    sluttdato = LocalDate.now().plusWeeks(2),
                    dagerPerUke = 5f,
                    deltakelsesprosent = 100f,
                    bakgrunnsinformasjon = "andre utkast",
                    innhold = null,
                ),
            ),
        )

        hendelse1.toUlestHendelse()?.let { repository.upsert(it) }
        hendelse2.toUlestHendelse()?.let { repository.upsert(it) }

        val result = repository.getTypeCountsForDeltakere(setOf(deltaker.id))

        result shouldBe UlestHendelseTypeCounts(
            erNyDeltaker = 1,
            harOppdateringFraNav = 0,
        )
    }
}
