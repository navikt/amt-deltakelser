package no.nav.amt.deltaker.api

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import no.nav.amt.deltaker.api.response.SharedResponseMappers
import no.nav.amt.deltaker.tiltaksarrangor.forslag.ForslagRepository
import no.nav.amt.deltaker.tiltaksarrangor.vurdering.VurderingRepository
import no.nav.amt.deltaker.utils.data.TestData
import no.nav.amt.lib.models.arrangor.melding.Forslag
import no.nav.amt.lib.models.arrangor.melding.Vurderingstype
import no.nav.amt.lib.models.deltaker.DeltakerHistorikk
import no.nav.amt.lib.models.deltaker.Vurdering
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

class SharedResponseMappersTest {
    @Test
    fun `utkastResponseFromDeltaker - mapper deltakerfelter og inkluderer historikk`() {
        // Arrange
        val deltaker = TestData.lagDeltaker(
            startdato = LocalDate.now(),
            sluttdato = LocalDate.now().plusMonths(3),
            dagerPerUke = 4F,
            deltakelsesprosent = 80F,
            bakgrunnsinformasjon = "~bakgrunn~",
            erManueltDeltMedArrangor = true,
        )
        val historikk = emptyList<DeltakerHistorikk>()

        // Act
        val response = SharedResponseMappers.utkastResponseFromDeltaker(deltaker, historikk)

        // Assert
        assertSoftly(response) {
            id shouldBe deltaker.id
            startdato shouldBe deltaker.startdato.shouldNotBeNull()
            sluttdato shouldBe deltaker.sluttdato.shouldNotBeNull()
            dagerPerUke shouldBe deltaker.dagerPerUke.shouldNotBeNull()
            deltakelsesprosent shouldBe deltaker.deltakelsesprosent.shouldNotBeNull()
            bakgrunnsinformasjon shouldBe deltaker.bakgrunnsinformasjon.shouldNotBeNull()
            deltakelsesinnhold shouldBe deltaker.deltakelsesinnhold
            status shouldBe deltaker.status
            this.historikk shouldBe historikk
            erManueltDeltMedArrangor shouldBe true
        }
    }

    @Test
    fun `opprettKladdResponseFromDeltaker - mapper deltakerlisteId og deltakelsesinnhold (non-null)`() {
        // Arrange
        val deltaker = TestData.lagDeltaker(
            bakgrunnsinformasjon = "~bakgrunn~",
        )

        // Act
        val response = SharedResponseMappers.opprettKladdResponseFromDeltaker(deltaker)

        // Assert
        assertSoftly(response) {
            id shouldBe deltaker.id
            navBruker shouldBe deltaker.navBruker
            deltakerlisteId shouldBe deltaker.deltakerliste.id
            startdato shouldBe deltaker.startdato.shouldNotBeNull()
            sluttdato shouldBe deltaker.sluttdato.shouldNotBeNull()
            dagerPerUke shouldBe deltaker.dagerPerUke.shouldNotBeNull()
            deltakelsesprosent shouldBe deltaker.deltakelsesprosent.shouldNotBeNull()
            bakgrunnsinformasjon shouldBe deltaker.bakgrunnsinformasjon.shouldNotBeNull()
            deltakelsesinnhold shouldBe deltaker.deltakelsesinnhold.shouldNotBeNull()
            status shouldBe deltaker.status
        }
    }

    @Test
    fun `deltakerEndringResponseFromDeltaker - mapper felter og inkluderer historikk`() {
        // Arrange
        val deltaker = TestData.lagDeltaker()
        val endring = TestData.lagDeltakerEndring(deltakerId = deltaker.id)
        val historikk = listOf(DeltakerHistorikk.Endring(endring))

        // Act
        val response = SharedResponseMappers.deltakerEndringResponseFromDeltaker(deltaker, historikk)

        // Assert
        assertSoftly(response) {
            id shouldBe deltaker.id
            startdato shouldBe deltaker.startdato
            sluttdato shouldBe deltaker.sluttdato
            dagerPerUke shouldBe deltaker.dagerPerUke
            deltakelsesprosent shouldBe deltaker.deltakelsesprosent
            bakgrunnsinformasjon shouldBe deltaker.bakgrunnsinformasjon
            deltakelsesinnhold shouldBe deltaker.deltakelsesinnhold
            status shouldBe deltaker.status
            this.historikk shouldBe historikk
        }
    }

    @Test
    fun `hentEndringsforslagVenterPaSvar - returnerer kun forslag som venter paa svar`() {
        // Arrange
        val deltakerId = UUID.randomUUID()
        val forslagRepository = mockk<ForslagRepository>()

        val venter = lagForslag(deltakerId, Forslag.Status.VenterPaSvar)
        val tilbakekalt = lagForslag(
            deltakerId,
            Forslag.Status.Tilbakekalt(
                tilbakekaltAvArrangorAnsattId = UUID.randomUUID(),
                tilbakekalt = LocalDateTime.now(),
            ),
        )
        val godkjent = lagForslag(
            deltakerId,
            Forslag.Status.Godkjent(
                godkjentAv = Forslag.NavAnsatt(UUID.randomUUID(), UUID.randomUUID()),
                godkjent = LocalDateTime.now(),
            ),
        )

        every { forslagRepository.getForDeltaker(deltakerId) } returns listOf(venter, tilbakekalt, godkjent)

        // Act
        val resultat = SharedResponseMappers.hentEndringsforslagVenterPaSvar(forslagRepository, deltakerId)

        // Assert
        resultat shouldBe listOf(venter)
    }

    @Test
    fun `hentEndringsforslagVenterPaSvar - tomt resultat naar ingen venter`() {
        // Arrange
        val deltakerId = UUID.randomUUID()
        val forslagRepository = mockk<ForslagRepository>()
        every { forslagRepository.getForDeltaker(deltakerId) } returns emptyList()

        // Act
        val resultat = SharedResponseMappers.hentEndringsforslagVenterPaSvar(forslagRepository, deltakerId)

        // Assert
        resultat shouldHaveSize 0
    }

    @Test
    fun `hentSisteVurdering - returnerer vurdering med hoyest gyldigFra`() {
        // Arrange
        val deltakerId = UUID.randomUUID()
        val vurderingRepository = mockk<VurderingRepository>()

        val eldst = lagVurdering(deltakerId, LocalDateTime.now().minusDays(10), Vurderingstype.OPPFYLLER_IKKE_KRAVENE)
        val midt = lagVurdering(deltakerId, LocalDateTime.now().minusDays(5), Vurderingstype.OPPFYLLER_KRAVENE)
        val nyest = lagVurdering(deltakerId, LocalDateTime.now(), Vurderingstype.OPPFYLLER_IKKE_KRAVENE)

        every { vurderingRepository.getForDeltaker(deltakerId) } returns listOf(eldst, nyest, midt)

        // Act
        val resultat = SharedResponseMappers.hentSisteVurdering(vurderingRepository, deltakerId)

        // Assert
        resultat shouldBe nyest
    }

    @Test
    fun `hentSisteVurdering - returnerer null naar ingen vurderinger finnes`() {
        // Arrange
        val deltakerId = UUID.randomUUID()
        val vurderingRepository = mockk<VurderingRepository>()
        every { vurderingRepository.getForDeltaker(deltakerId) } returns emptyList()

        // Act
        val resultat = SharedResponseMappers.hentSisteVurdering(vurderingRepository, deltakerId)

        // Assert
        resultat shouldBe null
    }

    private fun lagForslag(
        deltakerId: UUID,
        status: Forslag.Status,
    ) = Forslag(
        id = UUID.randomUUID(),
        deltakerId = deltakerId,
        opprettetAvArrangorAnsattId = UUID.randomUUID(),
        opprettet = LocalDateTime.now(),
        begrunnelse = null,
        endring = Forslag.ForlengDeltakelse(LocalDate.now().plusWeeks(2)),
        status = status,
    )

    private fun lagVurdering(
        deltakerId: UUID,
        gyldigFra: LocalDateTime,
        type: Vurderingstype,
    ) = Vurdering(
        id = UUID.randomUUID(),
        deltakerId = deltakerId,
        opprettetAvArrangorAnsattId = UUID.randomUUID(),
        gyldigFra = gyldigFra,
        vurderingstype = type,
        begrunnelse = null,
    )
}
