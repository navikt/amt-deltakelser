package no.nav.amt.deltaker.repository

import no.nav.amt.deltaker.model.Deltakerliste

/**
 * Resultat fra [getGjennomforing] — deltakerliste med overordnet arrangør-navn.
 */
data class GjennomforingRow(
    val deltakerliste: Deltakerliste,
    val overordnetArrangorNavn: String?,
)
