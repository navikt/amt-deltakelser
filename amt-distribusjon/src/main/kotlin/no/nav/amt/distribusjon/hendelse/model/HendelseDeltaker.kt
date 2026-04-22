package no.nav.amt.distribusjon.hendelse.model

import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakstype
import no.nav.amt.lib.models.hendelse.HendelseDeltaker

fun HendelseDeltaker.Deltakerliste.deltakerAdresseDelesMedArrangor(): Boolean =
    this.tiltak.tiltakskode !in Tiltakstype.tiltakUtenDeltakerAdresseDeling
