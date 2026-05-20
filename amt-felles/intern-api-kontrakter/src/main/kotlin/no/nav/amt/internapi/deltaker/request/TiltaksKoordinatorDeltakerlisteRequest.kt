package no.nav.amt.internapi.deltaker.request

import no.nav.amt.lib.models.deltaker.DeltakerStatus
import java.util.UUID

/**
 * Request for å hente deltakere til tiltakskoordinator-visningen for en gjennomføring.
 *
 * Støtter filtrering på deltakerstatus og om deltakeren har aktive forslag
 * fra arrangør, samt paging og sortering av resultatet.
 *
 * @property gjennomforingId ID til gjennomføringen deltakerne tilhører.
 * @property harForslagFraArrangor Hvis true returneres kun deltakere med aktive forslag fra arrangør.
 * @property statuser Filtrerer på aktive deltakerstatuser. Tomt sett betyr ingen statusfiltrering.
 * @property pageRequest Paging- og sorteringsparametere for resultatlisten.
 */
data class TiltaksKoordinatorDeltakerlisteRequest(
    val gjennomforingId: UUID,
    val harForslagFraArrangor: Boolean = false,
    val statuser: Set<DeltakerStatus.Type> = emptySet(),
    val pageRequest: PageRequest<SortColumn> = PageRequest(
        sort = SortColumn.SOKT_INN_DATO,
        order = PageRequest.SortDirection.DESC,
    ),
) {
    enum class SortColumn {
        NAVN,
        NAV_ENHET,
        SOKT_INN_DATO,
        STARTDATO,
        SLUTTDATO,
        STATUS,
    }

    /**
     * Genererer en stabil cache-nøkkel for antall deltakere basert på filterverdiene.
     *
     * Statusene sorteres for å sikre at samme kombinasjon gir identisk cache-nøkkel
     * uavhengig av rekkefølgen i settet. Tomt statussett representeres som "ALL".
     */
    fun itemCountCacheKey() = listOf(
        gjennomforingId,
        harForslagFraArrangor,
        statuser
            .map { it.name }
            .ifEmpty { listOf("ALL") }
            .sorted(),
    ).joinToString(":")
}
