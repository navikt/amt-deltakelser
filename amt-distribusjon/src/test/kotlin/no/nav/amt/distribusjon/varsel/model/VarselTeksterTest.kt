package no.nav.amt.distribusjon.varsel.model

import io.kotest.matchers.shouldBe
import no.nav.amt.distribusjon.journalforing.pdf.visningsnavn
import no.nav.amt.distribusjon.utils.data.HendelseTypeData
import no.nav.amt.distribusjon.utils.data.Hendelsesdata
import no.nav.amt.lib.models.deltakerliste.Oppstartstype
import org.junit.jupiter.api.Test

class VarselTeksterTest {
    @Test
    fun `oppgaveTekst - bruker riktig tekst, tiltaksnavn og arrangornavn`() {
        val hendelse = Hendelsesdata.hendelse(HendelseTypeData.opprettUtkast())
        val tiltakNavn = hendelse.deltaker.deltakerliste.tiltak.navn
        val arrangorNavn = hendelse.deltaker.deltakerliste.arrangor.visningsnavn()

        oppgaveTekst(hendelse) shouldBe
            "Du har mottatt et utkast til påmelding på arbeidsmarkedstiltaket: $tiltakNavn hos $arrangorNavn. Svar på spørsmålet her."
    }

    @Test
    fun `oppgaveTekst - bruker felles oppstart-tekst, tiltaksnavn og arrangornavn når felles oppstart`() {
        val hendelse = Hendelsesdata.hendelse(
            HendelseTypeData.opprettUtkast(),
            deltaker = Hendelsesdata.lagDeltaker(
                deltakerliste = Hendelsesdata.lagDeltakerliste(
                    oppstartstype = Oppstartstype.FELLES,
                ),
            ),
        )
        val tiltakNavn = hendelse.deltaker.deltakerliste.tiltak.navn
        val arrangorNavn = hendelse.deltaker.deltakerliste.arrangor.visningsnavn()

        oppgaveTekst(hendelse) shouldBe
            "Du har mottatt et utkast til søknad på arbeidsmarkedstiltaket $tiltakNavn hos $arrangorNavn. Svar på spørsmålet her."
    }

    @Test
    fun `beskjedTekst - bruker riktig tekst, tiltaksnavn og arrangornavn`() {
        val hendelse = Hendelsesdata.hendelse(HendelseTypeData.opprettUtkast())
        val tiltakNavn = hendelse.deltaker.deltakerliste.tiltak.navn
        val arrangorNavn = hendelse.deltaker.deltakerliste.arrangor.visningsnavn()

        beskjedTekst(hendelse) shouldBe "Ny endring på arbeidsmarkedstiltaket: $tiltakNavn hos $arrangorNavn."
    }

    @Test
    fun `beskjedTekst - bruker tekst for søkt inn på hendelse godkjent utkast på felles oppstart`() {
        val hendelse = Hendelsesdata.hendelse(
            HendelseTypeData.navGodkjennUtkast(),
            deltaker = Hendelsesdata.lagDeltaker(
                deltakerliste = Hendelsesdata.lagDeltakerliste(
                    oppstartstype = Oppstartstype.FELLES,
                ),
            ),
        )
        val tiltakNavn = hendelse.deltaker.deltakerliste.tiltak.navn
        val arrangorNavn = hendelse.deltaker.deltakerliste.arrangor.visningsnavn()

        beskjedTekst(hendelse) shouldBe "Du er søkt inn på arbeidsmarkedstiltaket $tiltakNavn hos $arrangorNavn."

        beskjedTekst(hendelse) shouldBe "Du er søkt inn på arbeidsmarkedstiltaket $tiltakNavn hos $arrangorNavn."
    }

    @Test
    fun `beskjedTekst - bruker tekst endring på hendelse endret deltakelsesmengde`() {
        val hendelse = Hendelsesdata.hendelse(
            HendelseTypeData.endreDeltakelsesmengde(),
            deltaker = Hendelsesdata.lagDeltaker(
                deltakerliste = Hendelsesdata.lagDeltakerliste(
                    oppstartstype = Oppstartstype.LOPENDE,
                ),
            ),
        )
        val tiltakNavn = hendelse.deltaker.deltakerliste.tiltak.navn
        val arrangorNavn = hendelse.deltaker.deltakerliste.arrangor.visningsnavn()

        val hendelse2 = Hendelsesdata.hendelse(
            HendelseTypeData.endreDeltakelsesmengde(),
            deltaker = Hendelsesdata.lagDeltaker(
                deltakerliste = Hendelsesdata.lagDeltakerliste(
                    oppstartstype = Oppstartstype.FELLES,
                ),
            ),
        )
        val tiltakNavn2 = hendelse2.deltaker.deltakerliste.tiltak.navn
        val arrangorNavn2 = hendelse2.deltaker.deltakerliste.arrangor.visningsnavn()

        beskjedTekst(hendelse) shouldBe "Ny endring på arbeidsmarkedstiltaket: $tiltakNavn hos $arrangorNavn."
        beskjedTekst(hendelse2) shouldBe "Ny endring på arbeidsmarkedstiltaket: $tiltakNavn2 hos $arrangorNavn2."
    }

    @Test
    fun `beskjedTekst - bruker tekst for meldt på direkte på hendelse endret deltakelsesmengde på løpende oppstart`() {
        val hendelse = Hendelsesdata.hendelse(
            HendelseTypeData.navGodkjennUtkast(),
            deltaker = Hendelsesdata.lagDeltaker(
                deltakerliste = Hendelsesdata.lagDeltakerliste(
                    oppstartstype = Oppstartstype.LOPENDE,
                ),
            ),
        )
        val tiltakNavn = hendelse.deltaker.deltakerliste.tiltak.navn
        val arrangorNavn = hendelse.deltaker.deltakerliste.arrangor.visningsnavn()

        beskjedTekst(hendelse) shouldBe "Du er meldt på arbeidsmarkedstiltaket: $tiltakNavn hos $arrangorNavn."
    }
}
