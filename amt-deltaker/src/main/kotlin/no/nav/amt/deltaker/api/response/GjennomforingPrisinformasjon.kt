package no.nav.amt.deltaker.api.response

import no.nav.amt.deltaker.model.Gjennomforing
import no.nav.amt.deltaker.repository.PrisinfoRepoAdapter
import no.nav.amt.deltaker.repository.dbo.PrisinfoDbo
import no.nav.amt.lib.models.deltaker.PrisinformasjonDto
import java.util.UUID

object GjennomforingPrisinformasjon {
    data class PrisinformasjonData(
        val aktiv: PrisinformasjonDto,
        val tilGodkjenning: PrisinformasjonDto?,
    )

    fun hent(
        gjennomforing: Gjennomforing,
        includeOpplaringKategorisering: Boolean,
    ): PrisinformasjonData? {
        val skalHenteEnkeltplassValg = includeOpplaringKategorisering && gjennomforing.erNyForskriftOpplaring

        if (!skalHenteEnkeltplassValg) return null

        return finnPrisInfo(gjennomforing.id)
    }

    /**
     * Henter gjeldende- og prisinfo til endring.
     *
     * For deltakerstatuser SOKT_INN og senere, skal det alltid finnes en gjeldende prisinfo (GJELDENDE i databasen).
     * `aktiv` vil da inneholde gjeldende prisinfo, og `tilGodkjenning` vil inneholde endring om det finnes.
     *
     * For deltakerstatuser KLADD og UTKAST, skal det kun finnes prisinfo til godkjenning (ENDRING i databasen).
     * `aktiv` vil da inneholde endringen.
     *
     * @param gjennomforingId Deltakerliste-ID
     */
    fun finnPrisInfo(gjennomforingId: UUID): PrisinformasjonData? {
        val prisinfoMap = PrisinfoRepoAdapter.hentPrisinfoMap(gjennomforingId)

        if (prisinfoMap.isEmpty()) return null

        val gjeldendePrisinfo = prisinfoMap[PrisinfoDbo.Rolle.GJELDENDE]
        val prisinfoTilGodkjenning = prisinfoMap[PrisinfoDbo.Rolle.ENDRING]

        if (gjeldendePrisinfo == null) {
            if (prisinfoTilGodkjenning == null) {
                return null
            }
            return PrisinformasjonData(
                aktiv = prisinfoTilGodkjenning,
                tilGodkjenning = null,
            )
        } else {
            return PrisinformasjonData(
                aktiv = gjeldendePrisinfo,
                tilGodkjenning = prisinfoTilGodkjenning,
            )
        }
    }
}
