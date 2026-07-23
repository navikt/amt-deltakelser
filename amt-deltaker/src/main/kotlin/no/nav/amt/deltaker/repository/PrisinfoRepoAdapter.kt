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
 * - Håndtere to-trinns godkjenning av prisinfo (ENDRING → GJELDENDE)
 *
 * Prisinfo livssyklus:
 * 1. `lagrePrisinfoForKladdOgUtkast()` → lagrer med rolle=ENDRING og status=KLADD_UTKAST (for statuser før SOKT_INN)
 * 2. `lagrePrisinfoEndring()` → lagrer med rolle=ENDRING og status=SENDT (for SOKT_INN og senere)
 * 3. `godkjennOkonomi()` → sletter ENDRING-kobling, oppretter GJELDENDE-kobling og setter status=GODKJENT
 * 4. `hentPrisinfo()` → returnerer GJELDENDE dersom den finnes, ellers ENDRING
 */
object PrisinfoRepoAdapter {
    /**
     * Godkjenner prisinfo for økonomi.
     *
     * Utfører tre steg:
     * 1. Sletter ENDRING-koblingen mellom gjennomføring og prisinfo
     * 2. Oppretter GJELDENDE-kobling mellom gjennomføring og prisinfo
     * 3. Setter status på prisinfo til GODKJENT
     *
     * @param gjennomforingId ID til gjennomføringen prisinfo tilhører
     * @param prisinformasjonId ID til prisinfoen som skal godkjennes
     */
    fun godkjennOkonomi(
        gjennomforingId: UUID,
        prisinformasjonId: UUID,
    ) {
        Deltakerliste2PrisinfoRepository.delete(
            gjennomforingId = gjennomforingId,
            prisinformasjonId = prisinformasjonId,
            rolle = PrisinfoDbo.Rolle.ENDRING,
        )

        Deltakerliste2PrisinfoRepository.upsert(
            gjennomforingId = gjennomforingId,
            prisinformasjonId = prisinformasjonId,
            rolle = PrisinfoDbo.Rolle.GJELDENDE,
        )

        PrisinfoRepository.oppdaterStatus(
            prisinformasjonId = prisinformasjonId,
            status = PrisinfoDbo.PrisinfoStatus.GODKJENT,
        )
    }

    /**
     * Henter prisinfo for en gjennomføring, med prioritet på godkjente records.
     *
     * Typekonvertering: Konverterer fra database-format (`PrisinfoDbo`) til DTO
     * basert på `prisinfoJsonSubtype` (Anskaffelse | Tilskudd | IngenKostnader).
     *
     * @param gjennomforingId Gjennomføring-ID
     * @param rolle Spesifiserer hvilken rolle prisinfo skal hentes for. Hvis `null`, hentes GJELDENDE, ellers ENDRING.
     * @return [PrisinformasjonDto], eller `null` hvis ingen finnes
     * @throws IllegalStateException hvis påkrevd felt mangler (f.eks. `anskaffelsePris` for Anskaffelse)
     */
    fun hentPrisinfo(
        gjennomforingId: UUID,
        rolle: PrisinfoDbo.Rolle? = null,
    ): PrisinformasjonDto? {
        val prisinfoDbo = if (rolle != null) {
            PrisinfoRepository.hentPrisinfo(
                gjennomforingId = gjennomforingId,
                rolle = rolle,
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
        val eksisterendePrisinfoId = Deltakerliste2PrisinfoRepository.hentPrisinformasjonIdForEndring(
            gjennomforingId = gjennomforingId,
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
     * Tilbakekaller prisinfo som er pending totrinnskontroll ved å fjerne
     * record fra mellomlagringstabellen `deltakerliste_2_prisinformasjon`.
     *
     * @param gjennomforingId Deltakerliste-ID
     * @return ID for prisinfo som er tilbakekalt
     */
    fun tilbakekallPrisinfoEndring(gjennomforingId: UUID): UUID {
        val prisinformasjonId = Deltakerliste2PrisinfoRepository
            .hentPrisinformasjonIdForEndring(gjennomforingId)
            ?: throw IllegalArgumentException("Fant ingen prisinformasjon som venter på godkjenning")

        // fjern kopling mellom deltakerliste og prisinfo
        Deltakerliste2PrisinfoRepository.delete(
            gjennomforingId = gjennomforingId,
            prisinformasjonId = prisinformasjonId,
            rolle = PrisinfoDbo.Rolle.ENDRING,
        )

        return prisinformasjonId
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
