package no.nav.amt.lib.models.deltaker

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import no.nav.amt.lib.models.arrangor.melding.EndringFraArrangor
import no.nav.amt.lib.models.arrangor.melding.Forslag
import no.nav.amt.lib.models.arrangor.melding.Vurderingstype
import no.nav.amt.lib.models.tiltakskoordinator.EndringFraTiltakskoordinator
import no.nav.amt.lib.utils.objectMapper
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

class DeltakerHistorikkTest {
    @Test
    fun `VurderingFraArrangor har korrekte properties`() {
        val vurderingFraArrangor = VurderingFraArrangorData(
            id = UUID.randomUUID(),
            deltakerId = UUID.randomUUID(),
            vurderingstype = Vurderingstype.OPPFYLLER_KRAVENE,
            begrunnelse = "Begrunnelse for vurdering",
            opprettetAvArrangorAnsattId = UUID.randomUUID(),
            opprettet = now.minusDays(2),
        )

        val historikk = DeltakerHistorikk.VurderingFraArrangor(vurderingFraArrangor)

        assertSoftly(historikk) {
            sistEndret shouldBe vurderingFraArrangor.opprettet
            navAnsatte().shouldBeEmpty()
            navEnheter().shouldBeEmpty()
        }
    }

    @Test
    fun `ImportertFraArena har korrekte properties`() {
        val importertFraArena = ImportertFraArena(
            deltakerId = UUID.randomUUID(),
            importertDato = now.minusDays(3),
            deltakerVedImport = DeltakerVedImport(
                deltakerId = UUID.randomUUID(),
                innsoktDato = now.toLocalDate(),
                startdato = null,
                sluttdato = null,
                dagerPerUke = null,
                deltakelsesprosent = null,
                status = DeltakerStatus(
                    id = UUID.randomUUID(),
                    type = DeltakerStatus.Type.IKKE_AKTUELL,
                    aarsak = null,
                    gyldigFra = now.minusDays(4),
                    gyldigTil = null,
                    opprettet = now.minusDays(5),
                ),
            ),
        )

        val historikk = DeltakerHistorikk.ImportertFraArena(importertFraArena)

        assertSoftly(historikk) {
            sistEndret shouldBe importertFraArena.importertDato
            navAnsatte().shouldBeEmpty()
            navEnheter().shouldBeEmpty()
        }
    }

    @Test
    fun `Endring har korrekte properties`() {
        val deltakerEndring = DeltakerEndring(
            id = UUID.randomUUID(),
            deltakerId = UUID.randomUUID(),
            endring = DeltakerEndring.Endring.EndreInnhold(
                ledetekst = null,
                innhold = emptyList(),
            ),
            endretAv = UUID.randomUUID(),
            endretAvEnhet = UUID.randomUUID(),
            endret = now.minusDays(5),
            forslag = null,
        )

        val historikk = DeltakerHistorikk.Endring(deltakerEndring)

        assertSoftly(historikk) {
            sistEndret shouldBe deltakerEndring.endret
            navAnsatte() shouldBe listOf(deltakerEndring.endretAv)
            navEnheter() shouldBe listOf(deltakerEndring.endretAvEnhet)
        }
    }

    @Test
    fun `Vedtak har korrekte properties`() {
        val vedtak = Vedtak(
            id = UUID.randomUUID(),
            deltakerId = UUID.randomUUID(),
            fattet = null,
            gyldigTil = null,
            deltakerVedVedtak = DeltakerVedVedtak(
                id = UUID.randomUUID(),
                startdato = null,
                sluttdato = null,
                deltakelsesprosent = null,
                dagerPerUke = null,
                bakgrunnsinformasjon = null,
                deltakelsesinnhold = null,
                status = DeltakerStatus(
                    id = UUID.randomUUID(),
                    type = DeltakerStatus.Type.IKKE_AKTUELL,
                    aarsak = null,
                    gyldigFra = now.minusDays(1),
                    gyldigTil = null,
                    opprettet = now.minusDays(2),
                ),
            ),
            fattetAvNav = true,
            opprettet = now.minusDays(6),
            opprettetAv = UUID.randomUUID(),
            opprettetAvEnhet = UUID.randomUUID(),
            sistEndret = now.minusDays(6),
            sistEndretAv = UUID.randomUUID(),
            sistEndretAvEnhet = UUID.randomUUID(),
        )

        val historikk = DeltakerHistorikk.Vedtak(vedtak)

        assertSoftly(historikk) {
            sistEndret shouldBe vedtak.sistEndret
            navAnsatte() shouldBe listOf(vedtak.sistEndretAv, vedtak.opprettetAv)
            navEnheter() shouldBe listOf(vedtak.sistEndretAvEnhet, vedtak.opprettetAvEnhet)
        }
    }

    @Test
    fun `InnsokPaaFellesOppstart har korrekte properties`() {
        val innsokPaaFellesOppstart = InnsokPaaFellesOppstart(
            id = UUID.randomUUID(),
            deltakerId = UUID.randomUUID(),
            innsokt = now.minusDays(1),
            innsoktAv = UUID.randomUUID(),
            innsoktAvEnhet = UUID.randomUUID(),
            deltakelsesinnholdVedInnsok = null,
            utkastDelt = null,
            utkastGodkjentAvNav = false,
        )

        val historikk = DeltakerHistorikk.InnsokPaaFellesOppstart(innsokPaaFellesOppstart)

        assertSoftly(historikk) {
            sistEndret shouldBe innsokPaaFellesOppstart.innsokt
            navAnsatte() shouldBe listOf(innsokPaaFellesOppstart.innsoktAv)
            navEnheter() shouldBe listOf(innsokPaaFellesOppstart.innsoktAvEnhet)
        }
    }

    @Test
    fun `Forslag har korrekte properties`() {
        val forslag = Forslag(
            id = UUID.randomUUID(),
            deltakerId = UUID.randomUUID(),
            opprettetAvArrangorAnsattId = UUID.randomUUID(),
            opprettet = now.minusDays(7),
            begrunnelse = null,
            endring = Forslag.ForlengDeltakelse(
                sluttdato = now.plusDays(8).toLocalDate(),
            ),
            status = Forslag.Status.Avvist(
                avvistAv = Forslag.NavAnsatt(
                    id = UUID.randomUUID(),
                    enhetId = UUID.randomUUID(),
                ),
                avvist = now.minusDays(2),
                begrunnelseFraNav = "Begrunnelse fra NAV",
            ),
        )

        val historikk = DeltakerHistorikk.Forslag(forslag)

        assertSoftly(historikk) {
            sistEndret shouldBe forslag.sistEndret
            navAnsatte() shouldBe listOf(forslag.getNavAnsatt().shouldNotBeNull().id)
            navEnheter() shouldBe listOf(forslag.getNavAnsatt().shouldNotBeNull().enhetId)
        }
    }

    @Test
    fun `EndringFraArrangor har korrekte properties`() {
        val endringFraArrangor = EndringFraArrangor(
            id = UUID.randomUUID(),
            deltakerId = UUID.randomUUID(),
            opprettetAvArrangorAnsattId = UUID.randomUUID(),
            opprettet = now.minusDays(3),
            endring = EndringFraArrangor.LeggTilOppstartsdato(
                now.plusDays(5).toLocalDate(),
                null,
            ),
        )

        val historikk = DeltakerHistorikk.EndringFraArrangor(endringFraArrangor)

        assertSoftly(historikk) {
            sistEndret shouldBe endringFraArrangor.opprettet
            navAnsatte().shouldBeEmpty()
            navEnheter().shouldBeEmpty()
        }
    }

    @Test
    fun `EndringFraTiltakskoordinator har korrekte properties`() {
        val historikk = DeltakerHistorikk.EndringFraTiltakskoordinator(endringFraTiltakskoordinator)

        assertSoftly(historikk) {
            sistEndret shouldBe endringFraTiltakskoordinator.endret
            navAnsatte() shouldBe listOf(endringFraTiltakskoordinator.endretAv)
            navEnheter() shouldBe listOf(endringFraTiltakskoordinator.endretAvEnhet)
        }
    }

    @Test
    fun `endring serialiseres til JSON uten interface-navn`() {
        val historikk = DeltakerHistorikk.EndringFraTiltakskoordinator(endringFraTiltakskoordinator)

        objectMapper.writeValueAsString(historikk) shouldContain
            """"type" : "EndringFraTiltakskoordinator""""
    }

    companion object {
        private val now = LocalDateTime.now()

        private val endringFraTiltakskoordinator = EndringFraTiltakskoordinator(
            id = UUID.randomUUID(),
            deltakerId = UUID.randomUUID(),
            endring = EndringFraTiltakskoordinator.TildelPlass,
            endretAv = UUID.randomUUID(),
            endretAvEnhet = UUID.randomUUID(),
            endret = now.minusDays(4),
        )
    }
}
