package no.nav.amt.deltaker.repository

import no.nav.amt.deltaker.repository.dbo.PrisinfoDbo
import no.nav.amt.lib.models.deltakerliste.ANSKAFFELSE_SUB_TYPE
import no.nav.amt.lib.models.deltakerliste.INGENKOSTNADER_SUB_TYPE
import no.nav.amt.lib.models.deltakerliste.Prisinformasjon
import no.nav.amt.lib.models.deltakerliste.Priskomponent
import no.nav.amt.lib.models.deltakerliste.TILSKUDD_SUB_TYPE
import java.util.UUID

class PrisinfoRepoService {
    fun hentPrisinfo(gjennomforingId: UUID): Prisinformasjon? {
        val prisinfoDbo = PrisinfoRepository.hentPrisinfo(gjennomforingId)
            ?: return null

        return when (prisinfoDbo.prisinfoJsonSubtype) {
            ANSKAFFELSE_SUB_TYPE -> Prisinformasjon.Anskaffelse(
                prisinfoDbo.anskaffelsePris
                    ?: throw IllegalStateException("Anskaffelsepris kan ikke være null"),
            )

            TILSKUDD_SUB_TYPE -> Prisinformasjon.Tilskudd(
                tilleggsopplysninger = prisinfoDbo.tilleggsopplysninger,
                tilskudd = PrisinfoBelopRepository
                    .hentPrisinfoBelop(gjennomforingId)
                    .associate { it.pristype to it.pris },
            )

            INGENKOSTNADER_SUB_TYPE -> Prisinformasjon.IngenKostnader(
                tilleggsopplysninger = prisinfoDbo.tilleggsopplysninger,
                aarsak = prisinfoDbo.ingenkostnaderAarsak
                    ?: throw IllegalStateException("Årsak for ingen kostnader kan ikke være null"),
            )

            else -> throw IllegalStateException("Ukjent prisinfoJsonSubtype: ${prisinfoDbo.prisinfoJsonSubtype}")
        }
    }

    fun lagrePrisinfo(
        gjennomforingId: UUID,
        prisinfo: Prisinformasjon,
    ) {
        PrisinfoRepository.lagrePrisinfo(
            gjennomforingId = gjennomforingId,
            insertDbo = prisinfo.toPrisinfoDbo(),
        )

        PrisinfoBelopRepository.deleteForGjennomforing(gjennomforingId)

        if (prisinfo is Prisinformasjon.Tilskudd) {
            PrisinfoBelopRepository.lagrePrisinfoBelop(
                gjennomforingId = gjennomforingId,
                belop = prisinfo.toPriskomponentListe(),
            )
        }
    }

    companion object {
        internal fun Prisinformasjon.Tilskudd.toPriskomponentListe(): Set<Priskomponent> = this.tilskudd
            .map { Priskomponent(it.key, it.value) }
            .toSet()

        internal fun Prisinformasjon.toPrisinfoDbo(): PrisinfoDbo = when (this) {
            is Prisinformasjon.Anskaffelse -> PrisinfoDbo(
                prisinfoJsonSubtype = ANSKAFFELSE_SUB_TYPE,
                anskaffelsePris = this.pris,
            )

            is Prisinformasjon.Tilskudd -> PrisinfoDbo(
                prisinfoJsonSubtype = TILSKUDD_SUB_TYPE,
                tilleggsopplysninger = this.tilleggsopplysninger,
            )

            is Prisinformasjon.IngenKostnader -> PrisinfoDbo(
                prisinfoJsonSubtype = INGENKOSTNADER_SUB_TYPE,
                tilleggsopplysninger = this.tilleggsopplysninger,
                ingenkostnaderAarsak = this.aarsak,
            )
        }
    }
}
