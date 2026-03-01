package no.nav.amt.deltaker.deltaker.extensions

import no.nav.amt.lib.models.arrangor.melding.Forslag

fun Forslag.skalInkluderesIHistorikk() = when (this.status) {
    is Forslag.Status.Avvist,
    is Forslag.Status.Erstattet,
    is Forslag.Status.Tilbakekalt,
    -> true

    is Forslag.Status.Godkjent,
    Forslag.Status.VenterPaSvar,
    -> false
}
