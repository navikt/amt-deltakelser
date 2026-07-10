package no.nav.amt.distribusjon.hendelse.model

import no.nav.amt.internapi.hendelse.HendelseDeltaker
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakstype

fun HendelseDeltaker.Deltakerliste.deltakerAdresseDelesMedArrangor(): Boolean =
    this.tiltak.tiltakskode !in Tiltakstype.tiltakUtenDeltakerAdresseDeling
