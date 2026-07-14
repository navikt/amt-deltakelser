package no.nav.amt.deltaker.repository

import no.nav.amt.deltaker.repository.dbo.PrisinfoDbo
import no.nav.amt.deltaker.repository.dbo.Priskomponent
import no.nav.amt.lib.models.deltaker.ANSKAFFELSE_SUB_TYPE
import no.nav.amt.lib.models.deltaker.INGENKOSTNADER_SUB_TYPE
import no.nav.amt.lib.models.deltaker.PrisinformasjonDto
import no.nav.amt.lib.models.deltaker.PrisinformasjonDto.Anskaffelse
import no.nav.amt.lib.models.deltaker.PrisinformasjonDto.IngenKostnader
import no.nav.amt.lib.models.deltaker.PrisinformasjonDto.Tilskudd
import no.nav.amt.lib.models.deltaker.PrisinformasjonDto.Tilskudd.TilskuddInfo
import no.nav.amt.lib.models.deltaker.TILSKUDD_SUB_TYPE
import java.util.UUID

object PrisinfoRepoAdapter {
    fun hentPrisinfo(gjennomforingId: UUID): PrisinformasjonDto? {
        val prisinfoDbo = PrisinfoRepository.hentPrisinfo(gjennomforingId)
            ?: return null

        return when (prisinfoDbo.prisinfoJsonSubtype) {
            ANSKAFFELSE_SUB_TYPE -> Anskaffelse(
                prisinfoDbo.anskaffelsePris
                    ?: throw IllegalStateException("Anskaffelsepris kan ikke være null"),
            )

            TILSKUDD_SUB_TYPE -> Tilskudd(
                tilleggsopplysninger = prisinfoDbo.tilleggsopplysninger,
                tilskudd = PrisinfoBelopRepository
                    .hentPrisinfoBelop(gjennomforingId)
                    .map {
                        TilskuddInfo(
                            type = it.type,
                            pris = it.pris,
                        )
                    }.sortedBy { it.type.sortOrder },
            )

            INGENKOSTNADER_SUB_TYPE -> IngenKostnader(
                tilleggsopplysninger = prisinfoDbo.tilleggsopplysninger,
                aarsak = prisinfoDbo.ingenkostnaderAarsak
                    ?: throw IllegalStateException("Årsak for ingen kostnader kan ikke være null"),
            )

            else -> throw IllegalStateException("Ukjent prisinfoJsonSubtype: ${prisinfoDbo.prisinfoJsonSubtype}")
        }
    }

    fun lagrePrisinfo(
        gjennomforingId: UUID,
        prisinformasjon: PrisinformasjonDto,
    ) {
        PrisinfoRepository.upsertPrisinfo(
            gjennomforingId = gjennomforingId,
            insertDbo = prisinformasjon.toPrisinfoDbo(),
        )

        PrisinfoBelopRepository.deleteForGjennomforing(gjennomforingId)

        if (prisinformasjon is Tilskudd) {
            PrisinfoBelopRepository.lagrePrisinfoBelop(
                gjennomforingId = gjennomforingId,
                belop = prisinformasjon.toPriskomponentSet(),
            )
        }
    }

    internal fun Tilskudd.toPriskomponentSet(): Set<Priskomponent> = this.tilskudd
        .map {
            Priskomponent(
                type = it.type,
                pris = it.pris,
            )
        }.toSet()

    internal fun PrisinformasjonDto.toPrisinfoDbo(): PrisinfoDbo = when (this) {
        is Anskaffelse -> PrisinfoDbo(
            prisinfoJsonSubtype = ANSKAFFELSE_SUB_TYPE,
            anskaffelsePris = this.pris,
        )

        is Tilskudd -> PrisinfoDbo(
            prisinfoJsonSubtype = TILSKUDD_SUB_TYPE,
            tilleggsopplysninger = this.tilleggsopplysninger,
        )

        is IngenKostnader -> PrisinfoDbo(
            prisinfoJsonSubtype = INGENKOSTNADER_SUB_TYPE,
            tilleggsopplysninger = this.tilleggsopplysninger,
            ingenkostnaderAarsak = this.aarsak,
        )
    }
}
