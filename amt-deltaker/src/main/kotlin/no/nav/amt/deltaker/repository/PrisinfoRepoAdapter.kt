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
    fun harPrisinfoSomVenterPaaOkonomiGodkjent(
        gjennomforingId: UUID,
        prisinfoId: UUID,
    ): Boolean = PrisinfoRepository
        .hentPrisinfo(
            gjennomforingId = gjennomforingId,
            okonomiGodkjent = false,
        )?.id == prisinfoId

    fun godkjennOkonomi(gjennomforingId: UUID) {
        // slett eksisterende godkjent prisinfo
        PrisinfoRepository.deletePrisinfo(
            gjennomforingId = gjennomforingId,
            okonomiGodkjent = true,
        )

        // sett prisinfo til godkjent
        PrisinfoRepository.settGodkjent(gjennomforingId)
    }

    fun hentPrisinfo(gjennomforingId: UUID): PrisinformasjonDto? {
        val prisinfoDboList = PrisinfoRepository.hentPrisinfos(gjennomforingId)

        if (prisinfoDboList.isEmpty()) return null

        // hvis en record med okonomiGodkjent finnes, bruk denne, ellers hent den siste
        val prisinfoDbo = prisinfoDboList.maxBy { it.okonomiGodkjent }

        return when (prisinfoDbo.prisinfoJsonSubtype) {
            ANSKAFFELSE_SUB_TYPE -> Anskaffelse(
                prisinfoDbo.anskaffelsePris
                    ?: throw IllegalStateException("Anskaffelsepris kan ikke være null"),
            )

            TILSKUDD_SUB_TYPE -> Tilskudd(
                tilleggsopplysninger = prisinfoDbo.tilleggsopplysninger,
                tilskudd = PrisinfoBelopRepository
                    .hentPrisinfoBelop(prisinfoDbo.id)
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
        PrisinfoRepository.deletePrisinfo(
            gjennomforingId = gjennomforingId,
            okonomiGodkjent = false,
        )

        val prisinfoFromDb = PrisinfoRepository.insertPendingTotrinnskontrollPrisinfo(prisinformasjon.toPrisinfoDbo(gjennomforingId))

        if (prisinformasjon is Tilskudd) {
            PrisinfoBelopRepository.lagrePrisinfoBelop(
                prisinformasjonId = prisinfoFromDb.id,
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

    internal fun PrisinformasjonDto.toPrisinfoDbo(gjennomforingId: UUID): PrisinfoDbo = when (this) {
        is Anskaffelse -> PrisinfoDbo(
            gjennomforingId = gjennomforingId,
            okonomiGodkjent = false,
            prisinfoJsonSubtype = ANSKAFFELSE_SUB_TYPE,
            anskaffelsePris = this.pris,
        )

        is Tilskudd -> PrisinfoDbo(
            id = UUID.randomUUID(), // TODO
            gjennomforingId = gjennomforingId,
            okonomiGodkjent = false, // TODO
            prisinfoJsonSubtype = TILSKUDD_SUB_TYPE,
            tilleggsopplysninger = this.tilleggsopplysninger,
        )

        is IngenKostnader -> PrisinfoDbo(
            id = UUID.randomUUID(), // TODO
            gjennomforingId = gjennomforingId,
            okonomiGodkjent = false, // TODO
            prisinfoJsonSubtype = INGENKOSTNADER_SUB_TYPE,
            tilleggsopplysninger = this.tilleggsopplysninger,
            ingenkostnaderAarsak = this.aarsak,
        )
    }
}
