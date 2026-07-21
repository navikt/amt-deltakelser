package no.nav.amt.deltaker.repository

import no.nav.amt.deltaker.repository.dbo.PrisinfoDbo
import no.nav.amt.deltaker.repository.dbo.PrisinfoUpsertDbo
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

/**
 * Adapter for prisinfo-repositoriet som håndterer konvertering mellom domenemodeller og database.
 *
 * Ansvaret er å:
 * - Konvertere mellom `PrisinformasjonDto` og `PrisinfoDbo`
 * - Håndtere to-trinns godkjenning av prisinfo (pending → godkjent)
 *
 * Prisinfo livssyklus:
 * 1. `lagrePrisinfo()` → lagrer som `okonomiGodkjent=false` (pending)
 * 2. `harPrisinfoSomVenterPaaOkonomiGodkjent()` → verifiserer at pending finnes
 * 3. `godkjennOkonomi()` → sletter gamle godkjente records og markerer pending som godkjent
 * 4. `hentPrisinfo()` → returnerer godkjent dersom den finnes, ellers pending
 */
object PrisinfoRepoAdapter {
    /**
     * Sjekker om det finnes en ugodkjent (pending) prisinfo med gitt ID for gjennomføring.
     *
     * Brukes for å validere at prisinfo venter på økonomi-godkjenning før den endres.
     *
     * @param gjennomforingId Gjennomføring-ID
     * @param prisinfoId Prisinfo-ID
     * @return `true` hvis det finnes en endring for [prisinfoId], ellers `false`
     */
    fun harPrisinfoSomVenterPaaOkonomiGodkjent(
        gjennomforingId: UUID,
        prisinfoId: UUID,
    ): Boolean = Deltakerliste2PrisinfoRepository
        .hentPrisinformasjonId(
            gjennomforingId = gjennomforingId,
            rolle = PrisinfoDbo.Rolle.ENDRING,
        ) == prisinfoId

    /**
     * Godkjenner prisinfo for økonomi.
     *
     * Operasjonen:
     * 1. Sletter tidligere godkjent prisinfo-record for gjennomføring
     * 2. Markerer nåværende pending-record som godkjent (`okonomiGodkjent=true`)
     *
     * @param gjennomforingId Gjennomføring-ID
     */
    fun godkjennOkonomi(gjennomforingId: UUID) {
        val prisinfoId = Deltakerliste2PrisinfoRepository.hentPrisinformasjonId(
            gjennomforingId = gjennomforingId,
            rolle = PrisinfoDbo.Rolle.ENDRING,
        ) ?: error("Fant ingen prisnformasjon for gjennomføring $gjennomforingId med rolle ENDRING")

        Deltakerliste2PrisinfoRepository.delete(
            gjennomforingId = gjennomforingId,
            rolle = PrisinfoDbo.Rolle.ENDRING,
        )

        Deltakerliste2PrisinfoRepository.upsert(
            gjennomforingId = gjennomforingId,
            prisinformasjonId = prisinfoId,
            rolle = PrisinfoDbo.Rolle.GJELDENDE,
        )

        PrisinfoRepository.oppdaterStatus(
            prisinformasjonId = prisinfoId,
            status = PrisinfoDbo.PrisinfoStatus.GODKJENT,
        )
    }

    /**
     * Henter prisinfo for en gjennomføring, med prioritet på godkjente records.
     *
     * Prioritering:
     * - Hvis både godkjent (`okonomiGodkjent=true`) og ugodkjent (`okonomiGodkjent=false`) finnes,
     *   returneres den godkjente
     * - Hvis kun okonomiGodkjent=false finnes, returneres den
     * - Hvis ingen finnes, returneres `null`
     *
     * Typekonvertering: Konverterer fra database-format (`PrisinfoDbo`) til DTO
     * basert på `prisinfoJsonSubtype` (Anskaffelse | Tilskudd | IngenKostnader).
     *
     * @param gjennomforingId Gjennomføring-ID
     * @param brukEndring Hvis `true`, henter kun pending-record.
     * @return [PrisinformasjonDto], eller `null` hvis ingen finnes
     * @throws IllegalStateException hvis påkrevd felt mangler (f.eks. `anskaffelsePris` for Anskaffelse)
     */
    fun hentPrisinfo(
        gjennomforingId: UUID,
        brukEndring: Boolean = false,
    ): PrisinformasjonDto? {
        val prisinfoDbo = if (brukEndring) {
            PrisinfoRepository.hentPrisinfo(
                gjennomforingId = gjennomforingId,
                rolle = PrisinfoDbo.Rolle.ENDRING,
            )
        } else {
            val prisinfos = PrisinfoRepository.hentPrisinfos(gjennomforingId)
            prisinfos.firstOrNull { it.rolle == PrisinfoDbo.Rolle.GJELDENDE }
                ?: prisinfos.firstOrNull { it.rolle == PrisinfoDbo.Rolle.ENDRING }
        } ?: return null

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

    /**
     * Lagrer prisinfo som pending totrinnskontroll.
     * Benyttes ved lagring av prisinfo for status tidligere enn SOKT_INN.
     *
     * @param gjennomforingId Gjennomføring-ID
     * @param prisinformasjon Prisinformasjonen som skal lagres (Anskaffelse | Tilskudd | IngenKostnader)
     * @return ID for lagret prisinfo
     */
    fun lagrePrisinfoForKladdOgUtkast(
        gjennomforingId: UUID,
        prisinformasjon: PrisinformasjonDto,
    ): UUID {
        val eksisterendePrisinfoId = Deltakerliste2PrisinfoRepository.hentPrisinformasjonId(
            gjennomforingId = gjennomforingId,
            rolle = PrisinfoDbo.Rolle.ENDRING,
        )

        val faktiskPrisinfoId = eksisterendePrisinfoId ?: UUID.randomUUID()

        PrisinfoRepository.upsertPrisinfo(
            prisinformasjon.toPrisinfoUpsertDbo(
                prisinfoId = faktiskPrisinfoId,
                gjennomforingId = gjennomforingId,
            ),
        )

        if (eksisterendePrisinfoId == null) {
            // opprett kopling mellom gjennomføring og prisinfo
            Deltakerliste2PrisinfoRepository.upsert(
                gjennomforingId = gjennomforingId,
                prisinformasjonId = faktiskPrisinfoId,
                rolle = PrisinfoDbo.Rolle.ENDRING,
            )
        }

        PrisinfoBelopRepository.deleteForPrisinfo(faktiskPrisinfoId)
        if (prisinformasjon is Tilskudd) {
            PrisinfoBelopRepository.lagrePrisinfoBelop(
                prisinformasjonId = faktiskPrisinfoId,
                belop = prisinformasjon.toPriskomponentSet(),
            )
        }

        return faktiskPrisinfoId
    }

    /**
     * Lagrer prisinfo som pending totrinnskontroll.
     * Benyttes ved lagring av prisinfo for statuser fra og med SOKT_INN.
     *
     * @param gjennomforingId Gjennomføring-ID
     * @param prisinformasjon Prisinformasjonen som skal lagres (Anskaffelse | Tilskudd | IngenKostnader)
     * @return ID for lagret prisinfo
     */
    fun lagrePrisinfoEndring(
        gjennomforingId: UUID,
        prisinformasjon: PrisinformasjonDto,
    ): UUID {
        // her skal vi sette inn en ny prisinfo
        val nyPrisinfoId = UUID.randomUUID()

        PrisinfoRepository.upsertPrisinfo(
            prisinformasjon.toPrisinfoUpsertDbo(
                prisinfoId = nyPrisinfoId,
                gjennomforingId = gjennomforingId,
                status = PrisinfoDbo.PrisinfoStatus.SENDT,
            ),
        )

        // opprett kopling mellom gjennomføring og prisinfo
        Deltakerliste2PrisinfoRepository.upsert(
            gjennomforingId = gjennomforingId,
            prisinformasjonId = nyPrisinfoId,
            rolle = PrisinfoDbo.Rolle.ENDRING,
        )

        if (prisinformasjon is Tilskudd) {
            PrisinfoBelopRepository.lagrePrisinfoBelop(
                prisinformasjonId = nyPrisinfoId,
                belop = prisinformasjon.toPriskomponentSet(),
            )
        }

        return nyPrisinfoId
    }

    /**
     * Konverterer liste av tilskuddskomponenter fra DTO til dbo.
     *
     * Brukes internt for å lagre tilskuddsinformasjon i `prisinfo_belop`-tabellen.
     *
     * @return Sett av `Priskomponent`
     */
    internal fun Tilskudd.toPriskomponentSet(): Set<Priskomponent> = this.tilskudd
        .map {
            Priskomponent(
                type = it.type,
                pris = it.pris,
            )
        }.toSet()

    /**
     * Konverterer prisinformasjon fra DTO til database-format (DBO).
     *
     * Håndterer tre typer prisinfo:
     * - **Anskaffelse:** Lagrer `anskaffelsePris`
     * - **Tilskudd:** Lagrer `tilleggsopplysninger` (beløpene lagres separat via `lagrePrisinfoBelop`)
     * - **IngenKostnader:** Lagrer årsak og tilleggsopplysninger
     *
     * @return [PrisinfoUpsertDbo]
     */
    internal fun PrisinformasjonDto.toPrisinfoUpsertDbo(
        prisinfoId: UUID,
        gjennomforingId: UUID,
        status: PrisinfoDbo.PrisinfoStatus = PrisinfoDbo.PrisinfoStatus.KLADD_UTKAST,
    ): PrisinfoUpsertDbo = when (this) {
        is Anskaffelse -> PrisinfoUpsertDbo(
            id = prisinfoId,
            gjennomforingId = gjennomforingId,
            status = status,
            prisinfoJsonSubtype = ANSKAFFELSE_SUB_TYPE,
            anskaffelsePris = this.pris,
        )

        is Tilskudd -> PrisinfoUpsertDbo(
            id = prisinfoId,
            gjennomforingId = gjennomforingId,
            status = status,
            prisinfoJsonSubtype = TILSKUDD_SUB_TYPE,
            tilleggsopplysninger = this.tilleggsopplysninger,
        )

        is IngenKostnader -> PrisinfoUpsertDbo(
            id = prisinfoId,
            gjennomforingId = gjennomforingId,
            status = status,
            prisinfoJsonSubtype = INGENKOSTNADER_SUB_TYPE,
            tilleggsopplysninger = this.tilleggsopplysninger,
            ingenkostnaderAarsak = this.aarsak,
        )
    }
}
