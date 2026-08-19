package no.nav.amt.deltaker.api.response

import no.nav.amt.deltaker.model.Deltakerliste
import no.nav.amt.deltaker.repository.PrisinfoRepoAdapter
import no.nav.amt.deltaker.repository.dbo.PrisinfoDbo
import no.nav.amt.lib.models.deltaker.DeltakerEndring
import no.nav.amt.lib.models.deltaker.DeltakerHistorikk
import no.nav.amt.lib.models.deltaker.PrisinformasjonDto
import no.nav.amt.lib.models.deltakerliste.GjennomforingType
import java.util.UUID

object GjennomforingPrisinformasjon {
    data class Funnet(
        val prisinformasjon: PrisinformasjonDto?,
        val prisinformasjonTilGodkjenning: PrisinformasjonDto?,
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

        val (prisinformasjon, prisinformasjonTilGodkjenning) = hentPrisinfoPair(deltakerliste.id)

        val prisinformasjonTilGodkjenningMedBegrunnelse = leggTilPrisinformasjonBegrunnelse(
            prisinformasjon = prisinformasjonTilGodkjenning,
            prisinformasjonId = PrisinfoRepoAdapter.hentPrisinformasjonIdForEndring(deltakerliste.id),
            historikk = historikk,
        )

        return Funnet(
            prisinformasjon = prisinformasjon,
            prisinformasjonTilGodkjenning = prisinformasjonTilGodkjenningMedBegrunnelse,
        )
    }

    /**
     * Henter gjeldende- og prisinfo til endring.
     *
     * For deltakerstatuser SOKT_INN og senere, skal det alltid finnes en gjeldende prisinfo.
     * first i pair vil da inneholde gjeldende prisinfo, og second vil inneholde endring om det finnes.
     *
     * For deltakerstatuser KLADD og UTKAST, skal det kun finnes prisinfo til godkjenning (ENDRING)
     * first i pair vil da inneholde endring og second vil alltid inneholde null.
     *
     * @param gjennomforingId Deltakerliste-ID
     */
    fun hentPrisinfoPair(gjennomforingId: UUID): Pair<PrisinformasjonDto?, PrisinformasjonDto?> {
        val prisinfoMap = PrisinfoRepoAdapter.hentPrisinfoMap(gjennomforingId)

        if (prisinfoMap.isEmpty()) return Pair(null, null)

        val gjeldendePrisinfo = prisinfoMap[PrisinfoDbo.Rolle.GJELDENDE]
        val prisinfoTilGodkjenning = prisinfoMap[PrisinfoDbo.Rolle.ENDRING]

        return if (gjeldendePrisinfo == null) {
            Pair(prisinfoTilGodkjenning, null)
        } else {
            Pair(gjeldendePrisinfo, prisinfoTilGodkjenning)
        }
    }

    private fun leggTilPrisinformasjonBegrunnelse(
        prisinformasjon: PrisinformasjonDto?,
        prisinformasjonId: UUID?,
        historikk: List<DeltakerHistorikk>,
    ): PrisinformasjonDto? {
        val begrunnelse = finnPrisinformasjonBegrunnelse(prisinformasjonId, historikk)

        return when (prisinformasjon) {
            null -> null
            is PrisinformasjonDto.Anskaffelse -> prisinformasjon.copy(begrunnelse = begrunnelse)
            is PrisinformasjonDto.Tilskudd -> prisinformasjon.copy(begrunnelse = begrunnelse)
            is PrisinformasjonDto.IngenKostnader -> prisinformasjon.copy(begrunnelse = begrunnelse)
        }
    }

    private fun finnPrisinformasjonBegrunnelse(
        prisinformasjonId: UUID?,
        historikk: List<DeltakerHistorikk>,
    ): String? {
        if (prisinformasjonId == null) return null

        return historikk
            .asReversed()
            .asSequence()
            .filterIsInstance<DeltakerHistorikk.Endring>()
            .mapNotNull { it.endring.endring as? DeltakerEndring.Endring.EndrePrisinfo }
            .firstOrNull { it.prisinformasjonId == prisinformasjonId }
            ?.begrunnelse
            ?.takeIf { it.isNotBlank() }
    }
}
