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

/**
 * Adapter for prisinfo-repositoriet som håndterer konvertering mellom domenemodeller og database.
 *
 * Ansvaret er å:
 * - Konvertere mellom `PrisinformasjonDto` og `PrisinfoDbo`
 * - Håndtere to-trinns godkjenning av prisinfo (pending → godkjent)
 * - Publisere endringer til Kafka-topicen `arrangor-melding-v1` via event
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
     * @return `true` hvis prisinfo med `okonomiGodkjent=false` og samme ID finnes, ellers `false`
     */
    fun harPrisinfoSomVenterPaaOkonomiGodkjent(
        gjennomforingId: UUID,
        prisinfoId: UUID,
    ): Boolean = PrisinfoRepository
        .hentPrisinfo(
            gjennomforingId = gjennomforingId,
            okonomiGodkjent = false,
        )?.id == prisinfoId

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
        PrisinfoRepository.deletePrisinfo(
            gjennomforingId = gjennomforingId,
            okonomiGodkjent = true,
        )

        PrisinfoRepository.settGodkjent(gjennomforingId)
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
     * @return [PrisinformasjonDto], eller `null` hvis ingen finnes
     * @throws IllegalStateException hvis påkrevd felt mangler (f.eks. `anskaffelsePris` for Anskaffelse)
     */
    fun hentPrisinfo(gjennomforingId: UUID): PrisinformasjonDto? {
        val prisinfoDboList = PrisinfoRepository.hentPrisinfos(gjennomforingId)

        val prisinfoDbo = prisinfoDboList
            .maxByOrNull { it.okonomiGodkjent }
            ?: return null

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
     *
     * Operasjonen:
     * 1. Sletter eksisterende pending prisinfo-record
     * 2. Lagrer ny prisinfo med `okonomiGodkjent=false`
     * 3. For Tilskudd: Lagrer beløp for tilskudd i separat tabell
     **
     * @param gjennomforingId Gjennomføring-ID
     * @param prisinformasjon Prisinformasjonen som skal lagres (Anskaffelse | Tilskudd | IngenKostnader)
     */
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
     * All prisinfo lagres med `okonomiGodkjent=false` (pending).
     *
     * @param gjennomforingId Gjennomføring-ID
     * @return [PrisinfoDbo]
     */
    internal fun PrisinformasjonDto.toPrisinfoDbo(gjennomforingId: UUID): PrisinfoDbo = when (this) {
        is Anskaffelse -> PrisinfoDbo(
            gjennomforingId = gjennomforingId,
            prisinfoJsonSubtype = ANSKAFFELSE_SUB_TYPE,
            anskaffelsePris = this.pris,
        )

        is Tilskudd -> PrisinfoDbo(
            gjennomforingId = gjennomforingId,
            prisinfoJsonSubtype = TILSKUDD_SUB_TYPE,
            tilleggsopplysninger = this.tilleggsopplysninger,
        )

        is IngenKostnader -> PrisinfoDbo(
            gjennomforingId = gjennomforingId,
            prisinfoJsonSubtype = INGENKOSTNADER_SUB_TYPE,
            tilleggsopplysninger = this.tilleggsopplysninger,
            ingenkostnaderAarsak = this.aarsak,
        )
    }
}
