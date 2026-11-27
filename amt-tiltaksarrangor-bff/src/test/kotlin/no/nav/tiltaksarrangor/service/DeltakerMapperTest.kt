package no.nav.tiltaksarrangor.service

import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import no.nav.amt.lib.models.deltaker.DeltakerEndring
import no.nav.amt.lib.models.deltaker.DeltakerHistorikk
import no.nav.amt.lib.utils.unleash.CommonUnleashToggle
import no.nav.tiltaksarrangor.melding.forslag.ForslagService
import no.nav.tiltaksarrangor.model.DeltakelsesmengdeDto
import no.nav.tiltaksarrangor.model.DeltakelsesmengderDto
import no.nav.tiltaksarrangor.repositories.EndringsmeldingRepository
import no.nav.tiltaksarrangor.repositories.model.AnsattDbo
import no.nav.tiltaksarrangor.testutils.getDeltaker
import no.nav.tiltaksarrangor.testutils.getDeltakerliste
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.time.LocalDate
import java.util.UUID

class DeltakerMapperTest {
    private val ansattService = mockk<AnsattService>()
    private val forslagService = mockk<ForslagService>()
    private val endringsmeldingRepository = mockk<EndringsmeldingRepository>()
    private val unleashToggle = mockk<CommonUnleashToggle>()
    private val mapper = DeltakerMapper(ansattService, forslagService, endringsmeldingRepository, unleashToggle)

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `map - bruker fallback før like perioder slås sammen med og uten startdato`(harStartdato: Boolean) {
        // Arrange
        val opprinneligGyldigFra = LocalDate.now().minusDays(10)
        val startdato = opprinneligGyldigFra.plusDays(5).takeIf { harStartdato }
        val deltakerliste = getDeltakerliste(UUID.randomUUID())
        val ansatt = AnsattDbo(
            id = UUID.randomUUID(),
            personIdent = "12345678910",
            fornavn = "Fornavn",
            mellomnavn = null,
            etternavn = "Etternavn",
            roller = emptyList(),
            deltakerlister = emptyList(),
            veilederDeltakere = emptyList(),
        )
        val deltakerUtenHistorikk = getDeltaker(deltakerlisteId = deltakerliste.id).copy(startdato = startdato)
        val opprinneligEndring = DeltakerEndring(
            id = UUID.randomUUID(),
            deltakerId = deltakerUtenHistorikk.id,
            endring = DeltakerEndring.Endring.EndreDeltakelsesmengde(
                deltakelsesprosent = 100F,
                dagerPerUke = 3F,
                gyldigFra = opprinneligGyldigFra,
                begrunnelse = null,
            ),
            endretAv = UUID.randomUUID(),
            endretAvEnhet = UUID.randomUUID(),
            endret = opprinneligGyldigFra.atStartOfDay(),
            forslag = null,
        )
        val fremtidigEndringUtenProsent = opprinneligEndring.copy(
            id = UUID.randomUUID(),
            endring = DeltakerEndring.Endring.EndreDeltakelsesmengde(
                deltakelsesprosent = null,
                dagerPerUke = 3F,
                gyldigFra = LocalDate.now().plusDays(1),
                begrunnelse = null,
            ),
            endret = opprinneligEndring.endret.plusDays(1),
        )
        val deltaker = deltakerUtenHistorikk.copy(
            historikk = listOf(
                DeltakerHistorikk.Endring(opprinneligEndring),
                DeltakerHistorikk.Endring(fremtidigEndringUtenProsent),
            ),
        )
        every { ansattService.erVeilederForDeltaker(deltaker.id, deltakerliste.arrangorId, ansatt) } returns false
        every { ansattService.getVeiledereForDeltaker(deltaker.id) } returns emptyList()
        every { forslagService.getAktiveForslag(deltaker.id) } returns emptyList()
        every { unleashToggle.erKometMasterForTiltakstype(deltakerliste.tiltakskode) } returns true

        // Act
        val resultat = mapper.map(deltaker, deltakerliste, ansatt)

        // Assert
        resultat.deltakelsesmengder shouldBe DeltakelsesmengderDto(
            nesteDeltakelsesmengde = null,
            sisteDeltakelsesmengde = DeltakelsesmengdeDto(
                deltakelsesprosent = 100F,
                dagerPerUke = 3F,
                gyldigFra = startdato ?: opprinneligGyldigFra,
            ),
        )
    }
}
