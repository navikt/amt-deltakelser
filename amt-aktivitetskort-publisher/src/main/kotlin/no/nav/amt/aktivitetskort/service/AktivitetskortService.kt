package no.nav.amt.aktivitetskort.service

import no.nav.amt.aktivitetskort.client.AktivitetArenaAclClient
import no.nav.amt.aktivitetskort.client.AmtArenaAclClient
import no.nav.amt.aktivitetskort.client.AmtDeltakerClient
import no.nav.amt.aktivitetskort.client.VeilarboppfolgingClient
import no.nav.amt.aktivitetskort.domain.AktivitetStatus
import no.nav.amt.aktivitetskort.domain.Aktivitetskort
import no.nav.amt.aktivitetskort.domain.Deltaker
import no.nav.amt.aktivitetskort.domain.DeltakerDbo
import no.nav.amt.aktivitetskort.domain.DeltakerStatusModel
import no.nav.amt.aktivitetskort.domain.EndretAv
import no.nav.amt.aktivitetskort.domain.Handling
import no.nav.amt.aktivitetskort.domain.IKKE_AVTALT_MED_NAV_STATUSER
import no.nav.amt.aktivitetskort.domain.IdentType
import no.nav.amt.aktivitetskort.domain.LenkeType
import no.nav.amt.aktivitetskort.domain.Melding
import no.nav.amt.aktivitetskort.domain.Oppfolgingsperiode
import no.nav.amt.aktivitetskort.domain.Oppgave
import no.nav.amt.aktivitetskort.domain.OppgaveWrapper
import no.nav.amt.aktivitetskort.domain.displayText
import no.nav.amt.aktivitetskort.domain.toAktivitetskortTiltakstype
import no.nav.amt.aktivitetskort.exceptions.FeilOppfolgingsperiodeException
import no.nav.amt.aktivitetskort.exceptions.HistoriskArenaDeltakerException
import no.nav.amt.aktivitetskort.exceptions.IngenOppfolgingsperiodeException
import no.nav.amt.aktivitetskort.kafka.producer.AktivitetskortProducer
import no.nav.amt.aktivitetskort.repositories.ArrangorRepository
import no.nav.amt.aktivitetskort.repositories.DeltakerRepository
import no.nav.amt.aktivitetskort.repositories.MeldingRepository
import no.nav.amt.aktivitetskort.repositories.OppfolgingsperiodeRepository
import no.nav.amt.aktivitetskort.service.StatusMapping.deltakerStatusTilAktivitetStatus
import no.nav.amt.aktivitetskort.service.StatusMapping.deltakerStatusTilEtikett
import no.nav.amt.aktivitetskort.unleash.UnleashConfig.Companion.AKTIVITETSKORT_APP_NAME
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltaker.DeltakerStatus.Companion.avsluttendeStatuser
import no.nav.amt.lib.models.deltaker.Kilde
import no.nav.amt.lib.utils.unleash.CommonUnleashToggle
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.dao.ConcurrencyFailureException
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.server.ResponseStatusException
import java.time.LocalDateTime
import java.util.UUID

@Service
class AktivitetskortService(
    private val meldingRepository: MeldingRepository,
    private val arrangorRepository: ArrangorRepository,
    private val aktivitetArenaAclClient: AktivitetArenaAclClient,
    private val amtArenaAclClient: AmtArenaAclClient,
    private val unleashToggle: CommonUnleashToggle,
    private val veilarboppfolgingClient: VeilarboppfolgingClient,
    private val oppfolgingsperiodeRepository: OppfolgingsperiodeRepository,
    private val deltakerRepository: DeltakerRepository,
    private val aktivitetskortProducer: AktivitetskortProducer,
    private val transactionTemplate: TransactionTemplate,
    private val amtDeltakerClient: AmtDeltakerClient,
    @Value($$"${VEILEDERURL_BASEPATH}") private val veilederUrlBasePath: String,
    @Value($$"${DELTAKERURL_BASEPATH}") private val deltakerUrlBasePath: String,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun getSisteMeldingForDeltaker(deltakerId: UUID) = meldingRepository
        .getByDeltakerId(deltakerId)
        .maxByOrNull { it.createdAt }

    fun lagAktivitetskort(deltaker: Deltaker): Aktivitetskort? = tryOpprettMelding(deltaker.id) { opprettMelding(deltaker) }?.aktivitetskort

    fun lagAktivitetskort(deltakerId: UUID): Aktivitetskort? {
        val historikk = hentMeldingshistorikk(deltakerId)
        val deltaker = Deltaker.fromDeltakerResponse(amtDeltakerClient.getDeltaker(deltakerId))
        return lagreAktivitetskort(forberedMelding(deltaker, historikk = historikk))
    }

    fun lagreAktivitetskort(forberedtMelding: ForberedtMelding): Aktivitetskort? =
        tryOpprettMelding(forberedtMelding.deltakerId) { lagreOgPubliser(forberedtMelding) }?.aktivitetskort

    fun lagreAktivitetskortGruppe(gruppe: ForberedtGruppe) {
        transactionTemplate.executeWithoutResult {
            laasAktivitetskortGruppe(gruppe.utvalg, gruppe)
            validerGruppeutvalg(gruppe)
            validerOgLagreMeldinger(gruppe.meldinger)
        }
        gruppe.meldinger.forEach { forberedt ->
            tryOpprettMelding(forberedt.deltakerId) { meldingEllerFeil(forberedt) }
        }
    }

    private fun validerGruppeutvalg(gruppe: ForberedtGruppe) {
        val gjeldende = hentGruppeutvalg(gruppe.utvalg).groupBy { it.deltakerId }
        val opprinnelige = gruppe.utvalgteMeldinger.groupBy { it.deltakerId }
        if (opprinnelige.keys != gjeldende.keys ||
            opprinnelige.any { (id, meldinger) -> !Meldingshistorikk(id, meldinger).erLik(gjeldende.getValue(id)) }
        ) {
            throw ConcurrencyFailureException("Gruppeutvalget er endret. Aktivitetskortene må forberedes på nytt.")
        }
    }

    /**
     * Sletter den lokale deltakeren og avbryter kortet hvis det fortsatt er aktivt.
     * Vi bruker det lagrede kortet fordi deltakeren allerede kan være slettet i amt-deltaker.
     * Kortendringen, meldingen til Kafka og slettingen lagres sammen, eller angres sammen hvis noe feiler.
     */
    fun slettDeltaker(deltakerId: UUID) {
        transactionTemplate.executeWithoutResult {
            meldingRepository.advisoryLockMedlemshistorikk(deltakerId)
            // Et nytt kort kan ha blitt lagret mens slettingen ventet.
            // Vi leser derfor deltakeren og kortet etter at vi har fått låsen, så vi avbryter riktig kort.
            val deltaker = deltakerRepository.get(deltakerId) ?: return@executeWithoutResult
            val melding = getSisteMeldingForDeltaker(deltakerId)
            if (melding != null &&
                melding.aktivitetskort.aktivitetStatus in
                setOf(AktivitetStatus.FORSLAG, AktivitetStatus.PLANLAGT, AktivitetStatus.GJENNOMFORES)
            ) {
                oppdaterAktivitetskortForSlettetDeltaker(deltaker, melding)
            }
            deltakerRepository.delete(deltakerId)
            log.info("Slettet lokal deltaker $deltakerId")
        }
    }

    private fun oppdaterAktivitetskortForSlettetDeltaker(
        deltaker: DeltakerDbo,
        melding: Melding,
    ) {
        val avbruttStatus = DeltakerStatusModel(
            type = DeltakerStatus.Type.AVBRUTT,
            aarsak = null,
        )

        val aktivitetskort = melding.aktivitetskort.copy(
            personident = deltaker.personident,
            aktivitetStatus = AktivitetStatus.AVBRUTT,
            endretTidspunkt = LocalDateTime.now(),
            oppgave = null,
            detaljer = melding.aktivitetskort.detaljer.map {
                if (it.label == Aktivitetskort.STATUS_FOR_DELTAKELSE) it.copy(verdi = displayText(avbruttStatus)) else it
            },
            etiketter = listOfNotNull(deltakerStatusTilEtikett(avbruttStatus)),
        )

        val oppdatertMelding = melding.copy(aktivitetskort = aktivitetskort)

        meldingRepository.upsertLaast(oppdatertMelding)
        aktivitetskortProducer.send(oppdatertMelding.aktivitetskort)
    }

    fun forberedAktivitetskort(gjennomforingId: UUID): ForberedtGruppe = forberedAktivitetskort(Gruppeutvalg.Deltakerliste(gjennomforingId))

    fun forberedAktivitetskortForArrangor(arrangorId: UUID): ForberedtGruppe = forberedAktivitetskort(Gruppeutvalg.Arrangor(arrangorId))

    private fun hentGruppeutvalg(utvalg: Gruppeutvalg): List<Melding> = when (utvalg) {
        is Gruppeutvalg.Deltakerliste -> meldingRepository.getByDeltakerlisteId(utvalg.id)
        is Gruppeutvalg.Arrangor -> hentArrangorIder(utvalg.id)
            .flatMap { meldingRepository.getByArrangorId(it) }
            .filter { it.aktivitetskort.erAktivDeltaker() }
    }

    private fun hentArrangorIder(arrangorId: UUID): List<UUID> =
        listOf(arrangorId) + arrangorRepository.getUnderordnedeArrangorer(arrangorId).map { it.id }

    private fun forberedAktivitetskort(utvalg: Gruppeutvalg): ForberedtGruppe {
        val gruppeId: (Melding) -> UUID = when (utvalg) {
            is Gruppeutvalg.Deltakerliste -> { melding -> melding.deltakerlisteId }
            is Gruppeutvalg.Arrangor -> { melding -> melding.arrangorId }
        }
        val meldinger = hentGruppeutvalg(utvalg)
        val historikk = meldinger.map { it.deltakerId }.distinct().associateWith { hentMeldingshistorikk(it) }
        val forberedteMeldinger = meldinger.map { melding ->
            val deltakerHistorikk = historikk.getValue(melding.deltakerId)
            val valgt = deltakerHistorikk.meldinger.filter { gruppeId(it) == gruppeId(melding) }.maxByOrNull { it.createdAt }
            if (valgt?.id != melding.id || !deltakerHistorikk.inneholder(melding)) {
                throw endretMeldingshistorikk(melding.deltakerId)
            }
            val deltaker = Deltaker.fromDeltakerResponse(amtDeltakerClient.getDeltaker(melding.deltakerId))
            forberedMelding(deltaker, meldingId = melding.id, historikk = deltakerHistorikk)
        }
        return ForberedtGruppe(utvalg, meldinger, forberedteMeldinger)
    }

    sealed interface Gruppeutvalg {
        data class Deltakerliste(
            val id: UUID,
        ) : Gruppeutvalg

        data class Arrangor(
            val id: UUID,
        ) : Gruppeutvalg
    }

    data class ForberedtGruppe(
        val utvalg: Gruppeutvalg,
        val utvalgteMeldinger: List<Melding>,
        val meldinger: List<ForberedtMelding>,
    )

    fun hentMeldingshistorikk(deltakerId: UUID) = Meldingshistorikk(deltakerId, meldingRepository.getByDeltakerId(deltakerId))

    fun laasAktivitetskortGruppe(
        utvalg: Gruppeutvalg,
        gruppe: ForberedtGruppe?,
    ) = laasMedlemskapOgHistorikk(gruppe?.meldinger.orEmpty(), medlemskapFor(utvalg))

    fun laasMeldingshistorikkForGruppe(forberedteMeldinger: List<ForberedtMelding>) =
        laasMedlemskapOgHistorikk(forberedteMeldinger, emptySet())

    private fun laasMedlemskapOgHistorikk(
        forberedteMeldinger: List<ForberedtMelding>,
        eksklusiveMedlemskap: Set<Medlemskap>,
    ) {
        val alleMedlemskap = eksklusiveMedlemskap + medlemskapFor(forberedteMeldinger)
        alleMedlemskap
            .sortedWith(compareBy<Medlemskap> { it.type }.thenBy { it.id.toString() })
            .forEach {
                meldingRepository.advisoryLockMedlemskap(it.type, it.id, delt = it !in eksklusiveMedlemskap)
            }
        laasDeltakerhistorikkForGruppe(forberedteMeldinger)
    }

    fun laasMeldingshistorikk(deltakerId: UUID) = meldingRepository.advisoryLockMedlemshistorikk(deltakerId)

    private fun laasDeltakerhistorikkForGruppe(forberedteMeldinger: List<ForberedtMelding>) {
        forberedteMeldinger.map { it.deltakerId }.distinct().sortedBy { it.toString() }.forEach {
            meldingRepository.advisoryLockMedlemshistorikk(it)
        }
    }

    private fun medlemskapFor(forberedteMeldinger: List<ForberedtMelding>): Set<Medlemskap> = forberedteMeldinger
        .flatMap { forberedt ->
            val meldinger = forberedt.historikk.meldinger + when (forberedt) {
                is ForberedtMelding.Avvist -> emptyList()
                is ForberedtMelding.Klar -> listOf(forberedt.melding)
            }
            meldinger.flatMap {
                listOf(
                    Medlemskap("deltakerliste", it.deltakerlisteId),
                    Medlemskap("arrangor", it.arrangorId),
                )
            }
        }.toSet()

    private fun medlemskapFor(utvalg: Gruppeutvalg): Set<Medlemskap> = when (utvalg) {
        is Gruppeutvalg.Deltakerliste -> setOf(Medlemskap("deltakerliste", utvalg.id))
        is Gruppeutvalg.Arrangor -> hentArrangorIder(utvalg.id).map { Medlemskap("arrangor", it) }.toSet()
    }

    private data class Medlemskap(
        val type: String,
        val id: UUID,
    )

    private fun endretMeldingshistorikk(deltakerId: UUID) = ConcurrencyFailureException(
        "Meldingshistorikken for deltaker $deltakerId er endret. Aktivitetskortet må forberedes på nytt.",
    )

    data class Meldingshistorikk(
        val deltakerId: UUID,
        val meldinger: List<Melding>,
    ) {
        private val meldingerPerId = meldinger.associateBy { it.id }
        val sisteMelding: Melding? get() = meldinger.maxByOrNull { it.createdAt }

        fun erLik(gjeldende: List<Melding>): Boolean = meldinger.size == gjeldende.size && gjeldende.all { inneholder(it) }

        fun inneholder(melding: Melding): Boolean {
            val lagret = meldingerPerId[melding.id] ?: return false
            // Vanlig sammenligning av Melding ser bort fra blant annet oppfølgingsperioden.
            // Et kort som er flyttet til en annen periode, må også gjøre at vi lager forslaget på nytt.
            return lagret == melding &&
                lagret.oppfolgingperiode == melding.oppfolgingperiode &&
                lagret.createdAt == melding.createdAt &&
                lagret.modifiedAt == melding.modifiedAt
        }
    }

    /*
    Henter aktivitetskortId for deltaker
     * Komet deltakere som ble opprettet før vi begynte å koble aktivitetskort til oppfølgingsperiode
     * Arenadeltakere må alltid hente id fra dab fordi de kan ha opprettet flere aktivitetskort for en
     deltaker uten komets innblanding
     *
     */
    private fun getAktivitetskortId(
        deltaker: Deltaker,
        oppfolgingsperiode: Oppfolgingsperiode,
        historikk: Meldingshistorikk,
    ): UUID {
        val nyesteAktivitetskortForDeltaker = historikk.sisteMelding
        val nyesteAktivitetskortId = nyesteAktivitetskortForDeltaker?.id

        if (deltaker.kilde == Kilde.KOMET) {
            return if (nyesteAktivitetskortForDeltaker?.oppfolgingperiode != null &&
                oppfolgingsperiode.id != nyesteAktivitetskortForDeltaker.oppfolgingperiode
            ) {
                log.info("Oppretter nytt aktivitetskort på deltaker ${deltaker.id} som har fått ny oppfølgingsperiode.")
                UUID.randomUUID()
            } else {
                nyesteAktivitetskortId
                    ?: UUID
                        .randomUUID()
                        .also { log.info("Definerer egen aktivitetskortId: $it for deltaker med id ${deltaker.id}") }
            }
        }

        return hentAktivitetskortIdForArenaDeltaker(deltaker.id)
    }

    fun hentAktivitetskortIdForArenaDeltaker(deltakerId: UUID): UUID =
        // Vi MÅ kalle dab for å generere id for arena deltakere for at de skal generere mappingen
        // Selv om vi har en aktivitetskort id på deltaker så kan dab ha opprettet en ny pga endringer i oppfølgingsperiode
        amtArenaAclClient
            .getArenaIdForAmtId(deltakerId)
            ?.also { log.info("deltaker $deltakerId er opprettet i arena med id $it. Henter aktivitetskort id fra AKAS..") }
            ?.let { aktivitetArenaAclClient.getAktivitetIdForArenaId(it) }
            ?.also { log.info("deltaker $deltakerId skal ha aktivitetId: $it") }
            ?: throw IllegalStateException("Arenadeltaker $deltakerId fikk ikke id fra AKAS")

    private fun tryOpprettMelding(
        deltakerId: UUID,
        opprett: () -> Melding,
    ): Melding? {
        try {
            return opprett()
        } catch (e: IngenOppfolgingsperiodeException) {
            log.warn("Kan ikke opprette aktivitetskort for deltaker $deltakerId uten oppfølgingsperiode", e)
        } catch (e: HistoriskArenaDeltakerException) {
            log.error("Kan ikke opprette aktivitetskort for historisk arena deltaker $deltakerId", e)
        } catch (e: FeilOppfolgingsperiodeException) {
            log.info("Kan ikke opprette aktivitetskort for deltaker $deltakerId", e)
        }
        return null
    }

    fun opprettMelding(
        deltaker: Deltaker,
        meldingId: UUID? = null,
    ): Melding = lagreOgPubliser(forberedMelding(deltaker, meldingId))

    // Vi leser kortene før vi spør andre tjenester. Mens vi venter på svar, kan for eksempel
    // /internal/publiser lage et nytt kort. Ved lagring sjekker vi at kortene fortsatt er de samme.
    // Vi holder ingen lås mens vi venter på svar, så andre forespørsler kan fortsette å lagre kort.
    fun forberedMelding(
        deltaker: Deltaker,
        meldingId: UUID? = null,
        historikk: Meldingshistorikk = hentMeldingshistorikk(deltaker.id),
    ): ForberedtMelding {
        val oppfolgingsperiode = veilarboppfolgingClient.hentOppfolgingperiode(deltaker.personident)
            ?: return ForberedtMelding.Avvist(
                historikk = historikk,
                feil = IngenOppfolgingsperiodeException(
                    "Kan ikke opprette aktivitetskort på deltaker ${deltaker.id} som ikke er under oppfølging",
                ),
            )

        val nyesteAktivitetskortForDeltaker = historikk.sisteMelding

        if (deltaker.kilde == Kilde.ARENA &&
            nyesteAktivitetskortForDeltaker == null &&
            deltaker.status.type in avsluttendeStatuser &&
            deltaker.status.gyldigFra?.isBefore(oppfolgingsperiode.startDato) == true
        ) {
            return ForberedtMelding.Avvist(
                historikk = historikk,
                feil = FeilOppfolgingsperiodeException(
                    "Lager ikke aktivitetskort for ukjent arenadeltaker i oppfølgingsperiode: ${oppfolgingsperiode.id}" +
                        "deltaker ${deltaker.id} er avsluttet ${deltaker.status.gyldigFra} " +
                        "før nåværende oppfølgingsperiode startet ${oppfolgingsperiode.startDato}",
                ),
                oppfolgingsperiode = oppfolgingsperiode,
            )
        }

        val aktivitetskortId = try {
            meldingId ?: getAktivitetskortId(
                deltaker = deltaker,
                oppfolgingsperiode = oppfolgingsperiode,
                historikk = historikk,
            )
        } catch (e: HistoriskArenaDeltakerException) {
            return ForberedtMelding.Avvist(historikk = historikk, feil = e)
        }

        val aktivitetskort = lagAktivitetskort(
            id = aktivitetskortId,
            deltaker = deltaker,
        )

        val melding = Melding(
            id = aktivitetskortId,
            deltakerId = deltaker.id,
            deltakerlisteId = deltaker.gjennomforing.id,
            arrangorId = deltaker.gjennomforing.arrangor.id,
            aktivitetskort = aktivitetskort,
            oppfolgingperiode = oppfolgingsperiode.id,
        )

        return ForberedtMelding.Klar(melding, oppfolgingsperiode, historikk)
    }

    private fun lagreOgPubliser(forberedtMelding: ForberedtMelding): Melding {
        lagreMeldinger(listOf(forberedtMelding))
        // Selv om kortet avvises, skal en funnet oppfølgingsperiode beholdes.
        // Derfor melder vi fra om avvisningen etter lagringen, uten å angre den.
        return meldingEllerFeil(forberedtMelding)
    }

    private fun lagreMeldinger(forberedteMeldinger: List<ForberedtMelding>) {
        transactionTemplate.executeWithoutResult {
            laasMeldingshistorikkForGruppe(forberedteMeldinger)
            validerOgLagreMeldinger(forberedteMeldinger)
        }
    }

    private fun validerOgLagreMeldinger(forberedteMeldinger: List<ForberedtMelding>) {
        // Et forberedt kort kan bygge på gamle opplysninger hvis noen har lagret et kort i mellomtiden.
        // Vi sjekker alle kortene til deltakeren, også eldre kort og at et første kort fortsatt mangler.
        // Låsen hindrer nye endringer fra denne sjekken til lagringen er ferdig.
        forberedteMeldinger.map { it.historikk }.distinctBy { it.deltakerId }.forEach { historikk ->
            if (!historikk.erLik(meldingRepository.getByDeltakerId(historikk.deltakerId))) {
                throw endretMeldingshistorikk(historikk.deltakerId)
            }
        }

        // Hvis lagring av ett kort feiler, angres også de andre kortene og meldingene som skulle sendes til Kafka.
        // Da kan hele liste- eller arrangørendringen forsøkes på nytt uten en halvferdig oppdatering.
        forberedteMeldinger
            .mapNotNull { it.oppfolgingsperiode }
            .sortedBy { it.id.toString() }
            .forEach { oppfolgingsperiodeRepository.upsert(it) }
        forberedteMeldinger.forEach { forberedt ->
            if (forberedt is ForberedtMelding.Klar) {
                meldingRepository.upsertLaast(forberedt.melding)
                aktivitetskortProducer.send(forberedt.melding.aktivitetskort)
            }
        }
    }

    private fun meldingEllerFeil(forberedt: ForberedtMelding): Melding = when (forberedt) {
        is ForberedtMelding.Avvist -> throw forberedt.feil
        is ForberedtMelding.Klar -> {
            log.info("Opprettet nytt aktivitetskort: ${forberedt.melding.id} for deltaker: ${forberedt.deltakerId}")
            forberedt.melding
        }
    }

    sealed interface ForberedtMelding {
        val historikk: Meldingshistorikk
        val deltakerId: UUID get() = historikk.deltakerId
        val oppfolgingsperiode: Oppfolgingsperiode?

        data class Klar(
            val melding: Melding,
            override val oppfolgingsperiode: Oppfolgingsperiode,
            override val historikk: Meldingshistorikk,
        ) : ForberedtMelding

        data class Avvist(
            override val historikk: Meldingshistorikk,
            val feil: RuntimeException,
            override val oppfolgingsperiode: Oppfolgingsperiode? = null,
        ) : ForberedtMelding
    }

    fun opprettNyttKort(deltakerId: UUID): Melding {
        val historikk = hentMeldingshistorikk(deltakerId)
        val deltaker = deltakerRepository.get(deltakerId) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
        val sisteMelding = historikk.sisteMelding
            ?: throw ResponseStatusException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Denne deltakelsen har ingen tidligere meldinger og skal opprettes",
            )
        if (sisteMelding.oppfolgingperiode != null) {
            throw ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Siste melding for deltaker $deltakerId har oppfølgingsperiode ${sisteMelding.oppfolgingperiode}." +
                    "Endepunktet skal kun brukes for meldinger uten info om oppfølgingsperiode",
            )
        }
        val arenaId = if (deltaker.kilde == Kilde.ARENA) hentAktivitetskortIdForArenaDeltaker(deltakerId) else null
        val meldingId = arenaId?.takeIf { it != sisteMelding.id } ?: UUID.randomUUID()
        val deltakerModel = Deltaker.fromDeltakerResponse(amtDeltakerClient.getDeltaker(deltakerId))
        return lagreOgPubliser(forberedMelding(deltakerModel, meldingId, historikk))
    }

    fun resendSisteMelding(deltakerId: UUID) {
        transactionTemplate.executeWithoutResult {
            meldingRepository.advisoryLockMedlemshistorikk(deltakerId)
            val melding = getSisteMeldingForDeltaker(deltakerId)
                ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Fant ikke melding")
            aktivitetskortProducer.send(melding.aktivitetskort)
        }
    }

    private fun lagAktivitetskort(
        id: UUID,
        deltaker: Deltaker,
    ) = Aktivitetskort(
        id = id,
        personident = deltaker.personident,
        tittel = deltaker.gjennomforing.visningsnavn,
        aktivitetStatus = deltakerStatusTilAktivitetStatus(deltaker.status.type).getOrThrow(),
        startDato = deltaker.startdato,
        sluttDato = deltaker.sluttdato,
        beskrivelse = null,
        endretAv = EndretAv(AKTIVITETSKORT_APP_NAME, IdentType.SYSTEM),
        endretTidspunkt = LocalDateTime.now(),
        avtaltMedNav = deltaker.status.type !in IKKE_AVTALT_MED_NAV_STATUSER,
        oppgave = oppgaver(deltaker),
        handlinger = getHandlinger(deltaker),
        detaljer = Aktivitetskort.lagDetaljer(deltaker),
        etiketter = listOfNotNull(deltakerStatusTilEtikett(deltaker.status)),
        tiltakstype = deltaker.gjennomforing.tiltakstype.tiltakskode
            .toAktivitetskortTiltakstype(),
    )

    private fun oppgaver(deltaker: Deltaker): OppgaveWrapper? {
        if (!unleashToggle.erKometMasterForTiltakstype(deltaker.gjennomforing.tiltakstype.tiltakskode)) {
            return null
        }

        if (deltaker.status.type != DeltakerStatus.Type.UTKAST_TIL_PAMELDING) {
            return null
        }

        return OppgaveWrapper(
            ekstern = Oppgave(
                tekst = "Du har mottatt et utkast til påmelding",
                subtekst = "Les gjennom utkastet før du godkjenner det. Når du godkjenner, blir du meldt på aktiviteten.",
                url = deltaker.deltakerUrl(),
            ),
            intern = null,
        )
    }

    private fun getHandlinger(deltaker: Deltaker): List<Handling> = listOf(
        Handling(
            tekst = "Les mer om din deltakelse",
            subtekst = "",
            url = "$veilederUrlBasePath/${deltaker.id}",
            lenkeType = LenkeType.INTERN,
        ),
        Handling(
            tekst = "Les mer om din deltakelse",
            subtekst = "",
            url = deltaker.deltakerUrl(),
            lenkeType = LenkeType.EKSTERN,
        ),
    )

    private fun Deltaker.deltakerUrl() = "$deltakerUrlBasePath/${this.id}"
}
