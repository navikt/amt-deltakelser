package no.nav.amt.deltaker.bff.model

import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import java.util.UUID

/**
 * Slank tiltak-representasjon for bff sin persisterte [Deltakerliste].
 *
 * Den persisterte deltakerliste-stien (tiltakskoordinator-tilgang) bruker kun tiltakets id, og
 * trenger ikke `innhold`/`innsatsgrupper`. Disse feltene ligger fortsatt på den delte
 * [no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakstype], som brukes i respons-stien via
 * [GjennomforingModel] (hentet fra amt-deltaker). Vi holder de to modellene adskilt slik at bff
 * ikke persisterer data den ikke bruker.
 */
data class Tiltak(
    val id: UUID,
    val navn: String,
    val tiltakskode: Tiltakskode,
)
