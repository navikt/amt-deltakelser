package no.nav.amt.deltaker.api.response

import no.nav.amt.deltaker.model.Deltakerliste
import no.nav.amt.deltaker.repository.PrisinfoRepoAdapter
import no.nav.amt.deltaker.repository.dbo.PrisinfoDbo
import no.nav.amt.lib.models.deltaker.DeltakerEndring.Endring.EndrePrisinfo
import no.nav.amt.lib.models.deltaker.DeltakerHistorikk
import no.nav.amt.lib.models.deltaker.DeltakerHistorikk.Endring
import no.nav.amt.lib.models.deltaker.PrisinformasjonDto
import no.nav.amt.lib.models.deltakerliste.GjennomforingType
import java.util.UUID

object GjennomforingPrisinformasjon {
    data class Funnet(
        val gjeldende: PrisinformasjonDto?,
        val tilGodkjenning: PrisinformasjonDto?,
    )

    fun hent(
        deltakerliste: Deltakerliste,
        includeOpplaringKategorisering: Boolean,
        historikk: List<DeltakerHistorikk>,
    ): Funnet {
        val skalHenteEnkeltplassValg =
            includeOpplaringKategorisering &&
                deltakerliste.gjennomforingstype == GjennomforingType.Enkeltplass &&
                !deltakerliste.tiltakstype.tiltakskode.erArenaEnkeltplass()

        if (!skalHenteEnkeltplassValg) return Funnet(null, null)

        val funnetPrisinfo = finnPrisInfo(deltakerliste.id)

        val prisinformasjonTilGodkjenningMedBegrunnelse = leggTilPrisinformasjonBegrunnelse(
            prisinformasjon = funnetPrisinfo.tilGodkjenning,
            prisinformasjonId = PrisinfoRepoAdapter.hentPrisinformasjonIdForEndring(deltakerliste.id),
            historikk = historikk,
        )

        return Funnet(
            gjeldende = funnetPrisinfo.gjeldende,
            tilGodkjenning = prisinformasjonTilGodkjenningMedBegrunnelse,
        )
    }

    /**
     * Henter gjeldende- og prisinfo til endring.
     *
     * For deltakerstatuser SOKT_INN og senere, skal det alltid finnes en gjeldende prisinfo.
     * gjeldende vil da inneholde gjeldende prisinfo, og tilGodkjenning vil inneholde endring om det finnes.
     *
     * For deltakerstatuser KLADD og UTKAST, skal det kun finnes prisinfo til godkjenning (ENDRING)
     * gjeldende vil da inneholde null og tilGodkjenning vil inneholde endring.
     *
     * @param gjennomforingId Deltakerliste-ID
     */
    fun finnPrisInfo(gjennomforingId: UUID): Funnet {
        val prisinfoMap = PrisinfoRepoAdapter.hentPrisinfoMap(gjennomforingId)

        if (prisinfoMap.isEmpty()) return Funnet(null, null)

        val gjeldendePrisinfo = prisinfoMap[PrisinfoDbo.Rolle.GJELDENDE]
        val prisinfoTilGodkjenning = prisinfoMap[PrisinfoDbo.Rolle.ENDRING]

        return Funnet(
            gjeldende = gjeldendePrisinfo,
            tilGodkjenning = prisinfoTilGodkjenning,
        )
    }

    private fun leggTilPrisinformasjonBegrunnelse(
        prisinformasjon: PrisinformasjonDto?,
        prisinformasjonId: UUID?,
        historikk: List<DeltakerHistorikk>,
    ): PrisinformasjonDto? {
        if (prisinformasjon == null || prisinformasjonId == null) {
            return null
        }
        val begrunnelse = historikk
            .asReversed()
            .filterIsInstance<Endring>()
            .mapNotNull { it.endring.endring as? EndrePrisinfo }
            .firstOrNull { it.prisinformasjonId == prisinformasjonId }
            ?.begrunnelse

        return when (prisinformasjon) {
            is PrisinformasjonDto.Anskaffelse -> prisinformasjon.copy(begrunnelse = begrunnelse)
            is PrisinformasjonDto.Tilskudd -> prisinformasjon.copy(begrunnelse = begrunnelse)
            is PrisinformasjonDto.IngenKostnader -> prisinformasjon.copy(begrunnelse = begrunnelse)
        }
    }
}
