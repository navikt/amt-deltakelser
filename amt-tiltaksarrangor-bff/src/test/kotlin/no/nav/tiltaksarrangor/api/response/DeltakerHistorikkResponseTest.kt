package no.nav.tiltaksarrangor.api.response

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import no.nav.amt.lib.models.deltaker.DeltakerEndring
import no.nav.amt.lib.models.deltaker.DeltakerHistorikk
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltaker.DeltakerVedImport
import no.nav.amt.lib.models.deltaker.DeltakerVedVedtak
import no.nav.amt.lib.models.deltaker.ImportertFraArena
import no.nav.amt.lib.models.deltaker.Innsok
import no.nav.amt.lib.models.deltaker.Vedtak
import no.nav.amt.lib.models.deltakerliste.Oppstartstype
import no.nav.amt.lib.utils.objectMapper
import no.nav.tiltaksarrangor.consumer.model.NavAnsatt
import no.nav.tiltaksarrangor.consumer.model.NavEnhet
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.time.LocalDateTime
import java.util.UUID

class DeltakerHistorikkResponseTest {
    @ParameterizedTest
    @CsvSource(
        value = ["null, null", "null, 3", "100, null", "100, 3", "80.5, null", "80.5, 3"],
        nullValues = ["null"],
    )
    fun `historikkrespons bevarer prosent og dager uten fallback`(
        deltakelsesprosent: Float?,
        dagerPerUke: Float?,
    ) {
        // Arrange
        val tidspunkt = LocalDateTime.of(2026, 1, 15, 12, 0)
        val deltakerId = UUID.randomUUID()
        val ansatt = NavAnsatt(UUID.randomUUID(), "Z123456", "Veileder", null, null)
        val enhet = NavEnhet(UUID.randomUUID(), "1234", "Nav-kontor")
        val status = DeltakerStatus(
            id = UUID.randomUUID(),
            type = DeltakerStatus.Type.DELTAR,
            aarsak = null,
            gyldigFra = tidspunkt,
            gyldigTil = null,
            opprettet = tidspunkt,
        )
        val vedtak = Vedtak(
            id = UUID.randomUUID(),
            deltakerId = deltakerId,
            fattet = tidspunkt,
            gyldigTil = null,
            deltakerVedVedtak = DeltakerVedVedtak(
                id = deltakerId,
                startdato = tidspunkt.toLocalDate(),
                sluttdato = null,
                dagerPerUke = dagerPerUke,
                deltakelsesprosent = deltakelsesprosent,
                bakgrunnsinformasjon = null,
                deltakelsesinnhold = null,
                status = status,
            ),
            fattetAvNav = true,
            opprettet = tidspunkt,
            opprettetAv = ansatt.id,
            opprettetAvEnhet = enhet.id,
            sistEndret = tidspunkt,
            sistEndretAv = ansatt.id,
            sistEndretAvEnhet = enhet.id,
        )
        val importertFraArena = ImportertFraArena(
            deltakerId = deltakerId,
            importertDato = tidspunkt,
            deltakerVedImport = DeltakerVedImport(
                deltakerId = deltakerId,
                innsoktDato = tidspunkt.toLocalDate(),
                startdato = tidspunkt.toLocalDate(),
                sluttdato = null,
                dagerPerUke = dagerPerUke,
                deltakelsesprosent = deltakelsesprosent,
                status = status,
            ),
        )
        val mengdeendring = DeltakerEndring.Endring.EndreDeltakelsesmengde(
            deltakelsesprosent = deltakelsesprosent,
            dagerPerUke = dagerPerUke,
            gyldigFra = tidspunkt.toLocalDate(),
            begrunnelse = "Endret etter avtale",
            pavirkerPris = true,
        )
        val endring = DeltakerEndring(
            id = UUID.randomUUID(),
            deltakerId = deltakerId,
            endring = mengdeendring,
            endretAv = ansatt.id,
            endretAvEnhet = enhet.id,
            endret = tidspunkt,
            forslag = null,
        )
        val innsok = Innsok(
            id = UUID.randomUUID(),
            deltakerId = deltakerId,
            innsokt = tidspunkt,
            innsoktAv = ansatt.id,
            innsoktAvEnhet = enhet.id,
            startdato = tidspunkt.toLocalDate(),
            sluttdato = null,
            deltakelsesinnholdVedInnsok = null,
            opplaringKategoriseringVedInnsok = null,
            utkastDelt = null,
            utkastGodkjentAvNav = true,
        )
        val historikk = listOf(
            DeltakerHistorikk.Vedtak(vedtak),
            DeltakerHistorikk.ImportertFraArena(importertFraArena),
            DeltakerHistorikk.Endring(endring),
            DeltakerHistorikk.InnsokPaaFellesOppstart(innsok),
        )

        // Act
        val resultat = DeltakerHistorikkResponse.fromModels(
            models = historikk,
            ansatte = mapOf(ansatt.id to ansatt),
            arrangornavn = "Arrangør",
            enheter = mapOf(enhet.id to enhet),
            oppstartstype = Oppstartstype.LOPENDE,
        )

        // Assert
        resultat.size shouldBe 4
        val vedtakResponse = resultat[0].shouldBeInstanceOf<VedtakResponse>()
        vedtakResponse.deltakelsesprosent shouldBe deltakelsesprosent
        vedtakResponse.dagerPerUke shouldBe dagerPerUke
        val importertResponse = resultat[1].shouldBeInstanceOf<ImportertFraArenaResponse>()
        importertResponse.deltakelsesprosent shouldBe deltakelsesprosent
        importertResponse.dagerPerUke shouldBe dagerPerUke
        resultat[2].shouldBeInstanceOf<DeltakerEndringResponse>().endring shouldBe
            DeltakerEndringEndringResponse.EndreDeltakelsesmengde(
                deltakelsesprosent = deltakelsesprosent,
                dagerPerUke = dagerPerUke,
                gyldigFra = mengdeendring.gyldigFra,
                begrunnelse = mengdeendring.begrunnelse,
                pavirkerPris = mengdeendring.pavirkerPris,
            )
        val innsokResponse = resultat[3].shouldBeInstanceOf<InnsokPaaFellesOppstartResponse>()
        val innsokJson = objectMapper.readTree(objectMapper.writeValueAsString(innsokResponse))
        innsokJson.has("deltakelsesprosent") shouldBe false
        vedtak.deltakerVedVedtak.deltakelsesprosent shouldBe deltakelsesprosent
        importertFraArena.deltakerVedImport.deltakelsesprosent shouldBe deltakelsesprosent
        mengdeendring.deltakelsesprosent shouldBe deltakelsesprosent
    }
}
