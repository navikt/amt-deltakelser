package no.nav.amt.distribusjon.varsel.model

import no.nav.amt.distribusjon.hendelse.model.Hendelse
import no.nav.amt.distribusjon.journalforing.pdf.visningsnavn
import no.nav.amt.lib.models.deltakerliste.GjennomforingPameldingType
import no.nav.amt.lib.models.hendelse.HendelseType

const val OPPGAVE_TEKST = "Du har mottatt et utkast til påmelding på arbeidsmarkedstiltaket: %s hos %s. Svar på spørsmålet her."
const val OPPGAVE_TRENGER_GODKJENNING = "Du har mottatt et utkast til søknad på arbeidsmarkedstiltaket %s hos %s. Svar på spørsmålet her."
const val BESKJED_TEKST = "Ny endring på arbeidsmarkedstiltaket: %s hos %s."
const val MELDT_PA_DIREKTE_TEKST = "Du er meldt på arbeidsmarkedstiltaket: %s hos %s."
const val SOKT_INN_TRENGER_GODKJENNING_TEKST = "Du er søkt inn på arbeidsmarkedstiltaket %s hos %s."
const val FATT_PLASS = "Du har fått plass på arbeidsmarkedstiltaket: %s hos %s"

fun oppgaveTekst(hendelse: Hendelse): String {
    val tekst = if (hendelse.deltaker.deltakerliste.pameldingstype == GjennomforingPameldingType.TRENGER_GODKJENNING) {
        OPPGAVE_TRENGER_GODKJENNING
    } else {
        OPPGAVE_TEKST
    }
    return String.format(
        tekst,
        hendelse.deltaker.deltakerliste.tiltak.navn,
        hendelse.deltaker.deltakerliste.arrangor
            .visningsnavn(),
    )
}

fun beskjedTekst(hendelse: Hendelse): String {
    val tekst = when {
        (
            hendelse.deltaker.deltakerliste.pameldingstype == GjennomforingPameldingType.TRENGER_GODKJENNING &&
                (hendelse.payload is HendelseType.NavGodkjennUtkast || hendelse.payload is HendelseType.ReaktiverDeltakelse)
        )
        -> SOKT_INN_TRENGER_GODKJENNING_TEKST
        hendelse.payload is HendelseType.NavGodkjennUtkast -> MELDT_PA_DIREKTE_TEKST
        hendelse.payload is HendelseType.TildelPlass -> FATT_PLASS
        else -> BESKJED_TEKST
    }

    return String.format(
        tekst,
        hendelse.deltaker.deltakerliste.tiltak.navn,
        hendelse.deltaker.deltakerliste.arrangor
            .visningsnavn(),
    )
}
