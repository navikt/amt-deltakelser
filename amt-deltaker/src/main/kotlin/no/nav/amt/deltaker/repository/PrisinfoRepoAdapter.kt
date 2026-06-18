package no.nav.amt.deltaker.repository

import no.nav.amt.deltaker.repository.dbo.PrisinfoDbo
import no.nav.amt.deltaker.repository.dbo.Priskomponent
import no.nav.amt.internapi.enkeltplass.ANSKAFFELSE_SUB_TYPE
import no.nav.amt.internapi.enkeltplass.INGENKOSTNADER_SUB_TYPE
import no.nav.amt.internapi.enkeltplass.PrisinformasjonDto
import no.nav.amt.internapi.enkeltplass.TILSKUDD_SUB_TYPE
import java.util.UUID

object PrisinfoRepoAdapter {
    fun hentPrisinfo(gjennomforingId: UUID): PrisinformasjonDto? {
        val prisinfoDbo = PrisinfoRepository.hentPrisinfo(gjennomforingId)
            ?: return null

        return when (prisinfoDbo.prisinfoJsonSubtype) {
            ANSKAFFELSE_SUB_TYPE -> PrisinformasjonDto.Anskaffelse(
                prisinfoDbo.anskaffelsePris
                    ?: throw IllegalStateException("Anskaffelsepris kan ikke være null"),
            )

            TILSKUDD_SUB_TYPE -> PrisinformasjonDto.Tilskudd(
                tilleggsopplysninger = prisinfoDbo.tilleggsopplysninger,
                tilskudd = PrisinfoBelopRepository
                    .hentPrisinfoBelop(gjennomforingId)
                    .map {
                        PrisinformasjonDto.Tilskudd.TilskuddInfo(
                            type = it.type,
                            pris = it.pris,
                        )
                    }.sortedBy { it.type.sortOrder },
            )

            INGENKOSTNADER_SUB_TYPE -> PrisinformasjonDto.IngenKostnader(
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

        if (prisinformasjon is PrisinformasjonDto.Tilskudd) {
            PrisinfoBelopRepository.lagrePrisinfoBelop(
                gjennomforingId = gjennomforingId,
                belop = prisinformasjon.toPriskomponentSet(),
            )
        }
    }

    internal fun PrisinformasjonDto.Tilskudd.toPriskomponentSet(): Set<Priskomponent> = this.tilskudd
        .map {
            Priskomponent(
                type = it.type,
                pris = it.pris,
            )
        }.toSet()

    internal fun PrisinformasjonDto.toPrisinfoDbo(): PrisinfoDbo = when (this) {
        is PrisinformasjonDto.Anskaffelse -> PrisinfoDbo(
            prisinfoJsonSubtype = ANSKAFFELSE_SUB_TYPE,
            anskaffelsePris = this.pris,
        )

        is PrisinformasjonDto.Tilskudd -> PrisinfoDbo(
            prisinfoJsonSubtype = TILSKUDD_SUB_TYPE,
            tilleggsopplysninger = this.tilleggsopplysninger,
        )

        is PrisinformasjonDto.IngenKostnader -> PrisinfoDbo(
            prisinfoJsonSubtype = INGENKOSTNADER_SUB_TYPE,
            tilleggsopplysninger = this.tilleggsopplysninger,
            ingenkostnaderAarsak = this.aarsak,
        )
    }
}
