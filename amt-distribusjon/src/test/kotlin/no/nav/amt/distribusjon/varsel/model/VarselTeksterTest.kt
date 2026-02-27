package no.nav.amt.distribusjon.varsel.model

import io.kotest.matchers.shouldBe
import no.nav.amt.distribusjon.journalforing.pdf.visningsnavn
import no.nav.amt.distribusjon.utils.data.HendelseTypeData
import no.nav.amt.distribusjon.utils.data.Hendelsesdata
import no.nav.amt.lib.models.hendelse.HendelseDeltaker
import org.junit.jupiter.api.Test

class VarselTeksterTest {
    @Test
    fun `oppgaveTekst - bruker riktig tekst, tiltaksnavn og arrangornavn`() {
        val hendelse = Hendelsesdata.hendelse(HendelseTypeData.opprettUtkast())

        oppgaveTekst(hendelse) shouldBe String.format(
            OPPGAVE_TEKST,
            hendelse.deltaker.deltakerliste.tiltak.navn,
            hendelse.deltaker.deltakerliste.arrangor
                .visningsnavn(),
        )
    }

    @Test
    fun `oppgaveTekst - bruker felles oppstart-tekst, tiltaksnavn og arrangornavn når felles oppstart`() {
        val hendelse = Hendelsesdata.hendelse(
            HendelseTypeData.opprettUtkast(),
            deltaker = Hendelsesdata.lagDeltaker(
                deltakerliste = Hendelsesdata.lagDeltakerliste(
                    oppstartstype = HendelseDeltaker.Deltakerliste.Oppstartstype.FELLES,
                ),
            ),
        )

        oppgaveTekst(hendelse) shouldBe String.format(
            OPPGAVE_TRENGER_GODKJENNING,
            hendelse.deltaker.deltakerliste.tiltak.navn,
            hendelse.deltaker.deltakerliste.arrangor
                .visningsnavn(),
        )
    }

    @Test
    fun `beskjedTekst - bruker riktig tekst, tiltaksnavn og arrangornavn`() {
        val hendelse = Hendelsesdata.hendelse(HendelseTypeData.opprettUtkast())

        beskjedTekst(hendelse) shouldBe String.format(
            BESKJED_TEKST,
            hendelse.deltaker.deltakerliste.tiltak.navn,
            hendelse.deltaker.deltakerliste.arrangor
                .visningsnavn(),
        )
    }

    @Test
    fun `beskjedTekst - bruker tekst for søkt inn på hendelse godkjent utkast på felles oppstart`() {
        val hendelse = Hendelsesdata.hendelse(
            HendelseTypeData.navGodkjennUtkast(),
            deltaker = Hendelsesdata.lagDeltaker(
                deltakerliste = Hendelsesdata.lagDeltakerliste(
                    oppstartstype = HendelseDeltaker.Deltakerliste.Oppstartstype.FELLES,
                ),
            ),
        )

        beskjedTekst(hendelse) shouldBe String.format(
            SOKT_INN_TRENGER_GODKJENNING_TEKST,
            hendelse.deltaker.deltakerliste.tiltak.navn,
            hendelse.deltaker.deltakerliste.arrangor
                .visningsnavn(),
        )

        beskjedTekst(hendelse) shouldBe String.format(
            SOKT_INN_TRENGER_GODKJENNING_TEKST,
            hendelse.deltaker.deltakerliste.tiltak.navn,
            hendelse.deltaker.deltakerliste.arrangor
                .visningsnavn(),
        )
    }

    @Test
    fun `beskjedTekst - bruker tekst endring på hendelse endret deltakelsesmengde`() {
        val hendelse = Hendelsesdata.hendelse(
            HendelseTypeData.endreDeltakelsesmengde(),
            deltaker = Hendelsesdata.lagDeltaker(
                deltakerliste = Hendelsesdata.lagDeltakerliste(
                    oppstartstype = HendelseDeltaker.Deltakerliste.Oppstartstype.LOPENDE,
                ),
            ),
        )

        val hendelse2 = Hendelsesdata.hendelse(
            HendelseTypeData.endreDeltakelsesmengde(),
            deltaker = Hendelsesdata.lagDeltaker(
                deltakerliste = Hendelsesdata.lagDeltakerliste(
                    oppstartstype = HendelseDeltaker.Deltakerliste.Oppstartstype.FELLES,
                ),
            ),
        )

        beskjedTekst(hendelse) shouldBe String.format(
            BESKJED_TEKST,
            hendelse.deltaker.deltakerliste.tiltak.navn,
            hendelse.deltaker.deltakerliste.arrangor
                .visningsnavn(),
        )

        beskjedTekst(hendelse2) shouldBe String.format(
            BESKJED_TEKST,
            hendelse.deltaker.deltakerliste.tiltak.navn,
            hendelse.deltaker.deltakerliste.arrangor
                .visningsnavn(),
        )
    }

    @Test
    fun `beskjedTekst - bruker tekst for meldt på direkte på hendelse endret deltakelsesmengde på løpende oppstart`() {
        val hendelse = Hendelsesdata.hendelse(
            HendelseTypeData.navGodkjennUtkast(),
            deltaker = Hendelsesdata.lagDeltaker(
                deltakerliste = Hendelsesdata.lagDeltakerliste(
                    oppstartstype = HendelseDeltaker.Deltakerliste.Oppstartstype.LOPENDE,
                ),
            ),
        )

        beskjedTekst(hendelse) shouldBe String.format(
            MELDT_PA_DIREKTE_TEKST,
            hendelse.deltaker.deltakerliste.tiltak.navn,
            hendelse.deltaker.deltakerliste.arrangor
                .visningsnavn(),
        )
    }
}
