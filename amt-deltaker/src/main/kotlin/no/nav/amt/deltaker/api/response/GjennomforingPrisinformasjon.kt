package no.nav.amt.deltaker.api.response

import no.nav.amt.deltaker.model.Gjennomforing
import no.nav.amt.deltaker.repository.PrisinfoRepoAdapter
import no.nav.amt.deltaker.repository.dbo.PrisinfoDbo
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltaker.PrisinformasjonDto
import java.util.UUID

object GjennomforingPrisinformasjon {
    data class PrisinformasjonData(
        val aktiv: PrisinformasjonDto?,
        val tilGodkjenning: PrisinformasjonDto?,
    )

    fun hent(
        gjennomforing: Gjennomforing,
        includePrisinfo: Boolean,
        deltakerStatus: DeltakerStatus.Type? = null,
    ): PrisinformasjonData? {
        val skalHenteEnkeltplassPrisinfo = includePrisinfo && gjennomforing.erEnkeltplassOpplaring

        if (!skalHenteEnkeltplassPrisinfo) return null

        return finnPrisInfo(gjennomforing.id, deltakerStatus)
    }

    /**
     * Henter gjeldende prisinfo for deltakelse.
     *
     * ENDRING uten GJELDENDE betyr at prisinfo ikke er godkjent ennå.
     *
     * For KLADD og UTKAST_TIL_PAMELDING er ENDRING den aktive prisinformasjonen forslaget
     * (siden disse vises flatt på deltakelsen og godkjennes i tiltaksadministrasjon sammen med deltakelsen i sin helhet).
     * For andre deltakelsesstatuser skal endringen være GJELDENDE.
     *
     * @param gjennomforingId Deltakerliste-ID
     * @param deltakerStatus Deltakerens status
     */
    fun finnPrisInfo(
        gjennomforingId: UUID,
        deltakerStatus: DeltakerStatus.Type? = null,
    ): PrisinformasjonData? {
        val prisinfoMap = PrisinfoRepoAdapter.hentPrisinfoMap(gjennomforingId)

        if (prisinfoMap.isEmpty()) return null

        val gjeldendePrisinfo = prisinfoMap[PrisinfoDbo.Rolle.GJELDENDE]
        val prisinfoTilGodkjenning = prisinfoMap[PrisinfoDbo.Rolle.ENDRING]

        if (gjeldendePrisinfo != null) {
            return PrisinformasjonData(
                aktiv = gjeldendePrisinfo,
                tilGodkjenning = prisinfoTilGodkjenning,
            )
        }

        if (prisinfoTilGodkjenning == null) {
            return null
        }

        return if (deltakerStatus in setOf(DeltakerStatus.Type.KLADD, DeltakerStatus.Type.UTKAST_TIL_PAMELDING)) {
            // Hvis statusen er kladd eller utkast, så er prisinformasjon med status ENDRING den aktive endringen på deltakelsen
            PrisinformasjonData(
                aktiv = prisinfoTilGodkjenning,
                tilGodkjenning = null,
            )
        } else {
            // Arenadeltakere kan ha andre statuser enn kladd og utkast og i tillegg mangle prisinformasjon(uten at FORSLAG blir aktiv)
            PrisinformasjonData(
                aktiv = null, // gjeldendePrisinfo
                tilGodkjenning = prisinfoTilGodkjenning,
            )
        }
    }
}
