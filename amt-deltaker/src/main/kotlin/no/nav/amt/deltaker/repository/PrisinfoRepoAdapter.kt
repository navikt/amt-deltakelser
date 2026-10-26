package no.nav.amt.deltaker.repository

import no.nav.amt.deltaker.repository.dbo.PrisinfoDbo
import no.nav.amt.deltaker.repository.dbo.Priskomponent
import no.nav.amt.internapi.deltaker.request.EndretPrisinfoRequest
import no.nav.amt.lib.models.deltaker.ANSKAFFELSE_SUB_TYPE
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltaker.INGENKOSTNADER_SUB_TYPE
import no.nav.amt.lib.models.deltaker.OkonomiGodkjentForHistorikk
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
    fun hentPrisinfoById(prisinformasjonId: UUID): PrisinformasjonDto? = PrisinfoRepository
        .hentPrisinfo(prisinformasjonId)
        ?.hentBelopOgKonverterTilDto()

    /**
     * Godkjenner prisinfo for økonomi.
     *
     * Utfører tre steg:
     * 1. Sletter ENDRING-koblingen mellom gjennomføring og prisinfo
     * 2. Oppretter GJELDENDE-kobling mellom gjennomføring og prisinfo
     * 3. Setter status på prisinfo til GODKJENT og lagrer hvem som godkjente
     *
     * @param gjennomforingId ID til gjennomføringen prisinfo tilhører
     * @param prisinformasjonId ID til prisinfoen som skal godkjennes
     * @param godkjentAv ID til Nav-ansatt som godkjente prisinfoen
     * @param godkjentAvEnhet ID til Nav-enheten som godkjente prisinfoen
     * @return `true` hvis godkjenningen var vellykket, `false` hvis prisinfoen ikke finnes eller ikke er ENDRING
     */
    fun godkjennOkonomi(
        gjennomforingId: UUID,
        prisinformasjonId: UUID,
        godkjentAv: UUID,
        godkjentAvEnhet: UUID,
    ): Boolean {
        val endringFinnes = PrisinfoGjennomforingLinkRepository.delete(
            gjennomforingId = gjennomforingId,
            prisinformasjonId = prisinformasjonId,
            rolle = PrisinfoDbo.Rolle.ENDRING,
        ) > 0

        if (!endringFinnes) return false

        PrisinfoGjennomforingLinkRepository.upsert(
            gjennomforingId = gjennomforingId,
            prisinformasjonId = prisinformasjonId,
            rolle = PrisinfoDbo.Rolle.GJELDENDE,
        )

        PrisinfoRepository.settGodkjent(
            prisinformasjonId = prisinformasjonId,
            godkjentAv = godkjentAv,
            godkjentAvEnhet = godkjentAvEnhet,
        )

        return true
    }

    fun hentPrisinfoMap(gjennomforingId: UUID): Map<PrisinfoDbo.Rolle, PrisinformasjonDto> = PrisinfoRepository
        .hentPrisinfoMap(gjennomforingId)
        .mapValues { (_, prisinfo) -> prisinfo.hentBelopOgKonverterTilDto() }

    fun hentGodkjentPrisinfoForHistorikkEldsteForst(deltakerId: UUID): List<OkonomiGodkjentForHistorikk> {
        val godkjente = PrisinfoRepository.hentGodkjentPrisinfoForDeltakerEldsteForst(deltakerId)

        // Hent tilskuddsbeløp i batch for å kunne effektivt lage Dto nedenfor
        val tilskuddIder = godkjente
            .map { it.prisinfo }
            .filter { it.prisinfoJsonSubtype == TILSKUDD_SUB_TYPE }
            .map { it.id }

        val belopPerPrisinfo: Map<UUID, List<Priskomponent>> = PrisinfoBelopRepository.hentPrisinfoBelop(tilskuddIder)

        return godkjente.map {
            OkonomiGodkjentForHistorikk(
                sistEndret = it.sistEndret,
                sistEndretAvNavAnsattId = it.godkjentAvNavAnsattId,
                sistEndretAvNavEnhetId = it.godkjentAvNavEnhetId,
                erForsteGodkjenning = it.erForsteGodkjenning,
                prisinformasjon = it.prisinfo
                    .toPrisinformasjonDto(belopPerPrisinfo[it.prisinfo.id].orEmpty()),
            )
        }
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
    ): PrisinformasjonDto? = if (rolle != null) {
        PrisinfoRepository
            .hentPrisinfo(
                gjennomforingId = gjennomforingId,
                rolle = rolle,
            )?.hentBelopOgKonverterTilDto()
    } else {
        val prisinfoMap = hentPrisinfoMap(gjennomforingId)
        prisinfoMap[PrisinfoDbo.Rolle.GJELDENDE] ?: prisinfoMap[PrisinfoDbo.Rolle.ENDRING]
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
        val eksisterendePrisinfoId = PrisinfoGjennomforingLinkRepository.hentPrisinformasjonIdForEndring(
            gjennomforingId = gjennomforingId,
        )

        val faktiskPrisinfoId = eksisterendePrisinfoId ?: UUID.randomUUID()

        PrisinfoRepository.upsertPrisinfo(
            prisinformasjon.toPrisinfoDbo(
                prisinfoId = faktiskPrisinfoId,
                gjennomforingId = gjennomforingId,
            ),
        )

        if (eksisterendePrisinfoId == null) {
            // opprett kopling mellom gjennomføring og prisinfo
            PrisinfoGjennomforingLinkRepository.upsert(
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
     * @param begrunnelse Begrunnelsen for prisendringen
     * @return ID for lagret prisinfo
     */
    fun lagrePrisinfoEndring(
        gjennomforingId: UUID,
        prisinformasjon: PrisinformasjonDto,
        begrunnelse: String,
    ): UUID {
        // her skal vi sette inn en ny prisinfo
        val nyPrisinfoId = UUID.randomUUID()

        PrisinfoRepository.upsertPrisinfo(
            prisinformasjon.toPrisinfoDbo(
                prisinfoId = nyPrisinfoId,
                gjennomforingId = gjennomforingId,
                status = PrisinfoDbo.TotrinnskontrollStatus.SENDT,
                begrunnelse = begrunnelse,
            ),
        )

        // opprett kopling mellom gjennomføring og prisinfo
        PrisinfoGjennomforingLinkRepository.upsert(
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
     * Håndhever at selve *typen* prisinformasjon kun kan endres så lenge deltakelsen
     * ikke har passert søknadsfasen (status UTKAST_TIL_PAMELDING eller SOKT_INN).
     *
     * Dette speiler valideringen i amt-deltaker-bff ([validerPrisinfoTypeKanEndres]), men
     * utføres her innenfor skrivetransaksjonen slik at en samtidig økonomigodkjenning —
     * som kan endre både status og gjeldende prisinfo mellom BFF-sjekken og skrivingen —
     * ikke kan omgå regelen (TOCTOU).
     *
     * Rene verdiendringer innenfor samme type er alltid tillatt.
     *
     * @throws IllegalArgumentException hvis typen endres og status ikke tillater det
     */
    fun validerPrisinfoTypeKanEndres(
        gjennomforingId: UUID,
        nyPrisinformasjon: PrisinformasjonDto,
        deltakerStatus: DeltakerStatus.Type,
    ) {
        val gjeldendePrisinfo = hentPrisinfo(gjennomforingId, PrisinfoDbo.Rolle.GJELDENDE) ?: return

        val endrerType = gjeldendePrisinfo::class != nyPrisinformasjon::class
        if (!endrerType) return

        require(
            deltakerStatus in setOf(
                DeltakerStatus.Type.UTKAST_TIL_PAMELDING,
                DeltakerStatus.Type.SOKT_INN,
            ),
        ) {
            "Kan ikke endre type prisinformasjon når deltakelsen har status $deltakerStatus"
        }
    }

    /**
     * Tilbakekaller prisinfo som er pending totrinnskontroll ved å fjerne
     * record fra mellomlagringstabellen `prisinformasjon_gjennomforing_link`.
     *
     * @param gjennomforingId Deltakerliste-ID
     * @return ID for prisinfo som er tilbakekalt
     */
    fun tilbakekallPrisinfoEndring(gjennomforingId: UUID): UUID {
        val prisinformasjonId = PrisinfoGjennomforingLinkRepository
            .hentPrisinformasjonIdForEndring(gjennomforingId)
            ?: throw IllegalArgumentException("Fant ingen prisinformasjon som venter på godkjenning")

        // fjern kopling mellom deltakerliste og prisinfo
        PrisinfoGjennomforingLinkRepository.delete(
            gjennomforingId = gjennomforingId,
            prisinformasjonId = prisinformasjonId,
            rolle = PrisinfoDbo.Rolle.ENDRING,
        )

        return prisinformasjonId
    }

    /**
     * Henter tilskuddsbeløp der det er relevant (databasekall) og konverterer til DTO.
     */
    private fun PrisinfoDbo.hentBelopOgKonverterTilDto(): PrisinformasjonDto = toPrisinformasjonDto(
        if (prisinfoJsonSubtype == TILSKUDD_SUB_TYPE) {
            PrisinfoBelopRepository.hentPrisinfoBelop(id)
        } else {
            emptyList()
        },
    )

    fun PrisinfoDbo.toPrisinformasjonDto(belop: List<Priskomponent>): PrisinformasjonDto = when (prisinfoJsonSubtype) {
        ANSKAFFELSE_SUB_TYPE -> Anskaffelse(
            this.anskaffelsePris
                ?: throw IllegalStateException("Anskaffelsepris kan ikke være null"),
            begrunnelse = this.begrunnelse,
        )

        TILSKUDD_SUB_TYPE -> Tilskudd(
            tilleggsopplysninger = this.tilleggsopplysninger,
            begrunnelse = this.begrunnelse,
            tilskudd = belop
                .map {
                    TilskuddInfo(
                        type = it.type,
                        pris = it.pris,
                    )
                }.sortedBy { it.type.sortOrder },
        )

        INGENKOSTNADER_SUB_TYPE -> IngenKostnader(
            tilleggsopplysninger = this.tilleggsopplysninger.orEmpty(),
            begrunnelse = this.begrunnelse.orEmpty(),
        )

        else -> throw IllegalStateException("Ukjent prisinfoJsonSubtype: ${this.prisinfoJsonSubtype}")
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
     * - **IngenKostnader:** Lagrer tilleggsopplysninger
     *
     * @return [PrisinfoDbo]
     */
    internal fun PrisinformasjonDto.toPrisinfoDbo(
        prisinfoId: UUID,
        gjennomforingId: UUID,
        status: PrisinfoDbo.TotrinnskontrollStatus = PrisinfoDbo.TotrinnskontrollStatus.KLADD_UTKAST,
        begrunnelse: String? = null,
    ): PrisinfoDbo = when (this) {
        is Anskaffelse -> PrisinfoDbo(
            id = prisinfoId,
            gjennomforingId = gjennomforingId,
            status = status,
            begrunnelse = begrunnelse,
            prisinfoJsonSubtype = ANSKAFFELSE_SUB_TYPE,
            anskaffelsePris = this.pris,
        )

        is Tilskudd -> PrisinfoDbo(
            id = prisinfoId,
            gjennomforingId = gjennomforingId,
            status = status,
            begrunnelse = begrunnelse,
            prisinfoJsonSubtype = TILSKUDD_SUB_TYPE,
            tilleggsopplysninger = this.tilleggsopplysninger,
        )

        is IngenKostnader -> PrisinfoDbo(
            id = prisinfoId,
            gjennomforingId = gjennomforingId,
            status = status,
            begrunnelse = begrunnelse,
            prisinfoJsonSubtype = INGENKOSTNADER_SUB_TYPE,
            tilleggsopplysninger = this.tilleggsopplysninger,
        )
    }

    /**
     * Sjekker om en prisinfo-endring er identisk med lagret data.
     *
     * Prioritet:
     * 1. Bruk ENDRING-prisinfo hvis den finnes og ikke er RETURNERT
     * 2. Ellers bruk GJELDENDE-prisinfo
     *
     * RETURNERT behandles som "ikke aktiv" fordi en ny innsending skal kunne opprette
     * ny totrinnskontroll selv om det finnes en tidligere, returnert endring.
     */
    fun erUendretPrisinfo(
        gjennomforingId: UUID,
        endringRequest: EndretPrisinfoRequest,
    ): Boolean {
        val eksisterendePrisinfo = PrisinfoRepository
            .hentPrisinfo(
                gjennomforingId = gjennomforingId,
                rolle = PrisinfoDbo.Rolle.ENDRING,
            )?.takeUnless { it.status == PrisinfoDbo.TotrinnskontrollStatus.RETURNERT }
            ?.hentBelopOgKonverterTilDto()
            ?: hentPrisinfo(
                gjennomforingId = gjennomforingId,
                rolle = PrisinfoDbo.Rolle.GJELDENDE,
            )

        return eksisterendePrisinfo?.utenBegrunnelse()?.sanitize() == endringRequest.prisinfo.utenBegrunnelse().sanitize()
    }

    private fun PrisinformasjonDto.utenBegrunnelse(): PrisinformasjonDto = when (this) {
        is Anskaffelse -> copy(begrunnelse = null)
        is Tilskudd -> copy(begrunnelse = null)
        is IngenKostnader -> copy(begrunnelse = "")
    }
}
