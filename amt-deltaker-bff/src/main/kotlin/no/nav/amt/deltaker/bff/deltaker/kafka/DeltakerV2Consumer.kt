package no.nav.amt.deltaker.bff.deltaker.kafka

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.amt.deltaker.bff.Environment
import no.nav.amt.deltaker.bff.deltaker.DeltakerService
import no.nav.amt.deltaker.bff.deltaker.db.DeltakerRepository
import no.nav.amt.deltaker.bff.deltaker.model.Deltaker
import no.nav.amt.deltaker.bff.deltaker.model.Deltakeroppdatering
import no.nav.amt.deltaker.bff.deltaker.navbruker.NavBrukerService
import no.nav.amt.deltaker.bff.deltaker.vurdering.VurderingService
import no.nav.amt.deltaker.bff.deltakerliste.Deltakerliste
import no.nav.amt.deltaker.bff.deltakerliste.DeltakerlisteRepository
import no.nav.amt.deltaker.bff.utils.KafkaConsumerFactory.buildManagedKafkaConsumer
import no.nav.amt.lib.kafka.Consumer
import no.nav.amt.lib.models.deltaker.DeltakerKafkaPayload
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltaker.Kilde
import no.nav.amt.lib.models.person.NavBruker
import no.nav.amt.lib.utils.database.Database
import no.nav.amt.lib.utils.objectMapper
import no.nav.amt.lib.utils.unleash.CommonUnleashToggle
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.util.UUID

class DeltakerV2Consumer(
    private val deltakerRepository: DeltakerRepository,
    private val deltakerService: DeltakerService,
    private val deltakerlisteRepository: DeltakerlisteRepository,
    private val vurderingService: VurderingService,
    private val navBrukerService: NavBrukerService,
    private val unleashToggle: CommonUnleashToggle,
) : Consumer<UUID, String?> {
    private val log = LoggerFactory.getLogger(javaClass)

    private val consumer =
        buildManagedKafkaConsumer(
            topic = Environment.AMT_DELTAKERV2_TOPIC,
            consumeFunc = ::consume,
        )

    suspend fun consume(
        key: UUID,
        value: String?,
    ) {
        if (value == null) {
            log.info("Mottok tombstone for deltaker $key - sletter deltaker")
            Database.transaction {
                deltakerService.deleteDeltaker(key)
            }
            return
        }

        val deltakerPayload = objectMapper.readValue<DeltakerKafkaPayload>(value)
        val deltakerliste = deltakerlisteRepository.get(deltakerPayload.deltakerliste.id).getOrThrow()
        val tiltakskode = deltakerliste.tiltak.tiltakskode

        if (!unleashToggle.erKometMasterForTiltakstype(tiltakskode) &&
            !unleashToggle.skalLeseArenaDataForTiltakstype(
                tiltakskode,
            )
        ) {
            log.info("Ignorerer deltaker $key på tiltakstype $tiltakskode som ikke er støttet enda")
            return
        }

        val lagretDeltaker = deltakerRepository.get(deltakerPayload.id).getOrNull()
        val deltakerFinnes = lagretDeltaker != null
        val ukjentDeltaker = !deltakerFinnes || deltakerPayload.kilde == Kilde.ARENA
        val navBruker = navBrukerService.getOrCreate(deltakerPayload.personalia.personident).getOrThrow()

        if (ukjentDeltaker) {
            // Når deltakeren ikke finnes så skal det bety at det er en arenadeltaker som kommer fra arena-acl
            // eller at en deltaker har blitt relastet
            // kan muligens forekomme race condition med at et utkast kommer på kafka før vi rekker å lagre i databasen
            log.info("Inserter ny $tiltakskode deltaker med id ${deltakerPayload.id}")
            val deltaker = deltakerPayload.toDeltaker(navBruker, deltakerliste)

            Database.transaction {
                deltakerRepository.upsert(deltaker)
                deltakerService.lagreDeltakerStatus(deltaker.id, deltaker.status)

                deltakerService.oppdaterDeltakerLaas(
                    deltakerId = deltaker.id,
                    personident = deltaker.navBruker.personident,
                    deltakerlisteId = deltaker.deltakerliste.id,
                )
                vurderingService.upsertMany(deltakerPayload.vurderingerFraArrangor.orEmpty())
            }
        } else {
            log.info("Oppdaterer deltaker med id ${deltakerPayload.id}")
            deltakerService.oppdaterDeltaker(
                deltakeroppdatering = deltakerPayload.toDeltakerOppdatering(),
                afterUpsert = {
                    deltakerService.oppdaterDeltakerLaas(
                        deltakerId = deltakerPayload.id,
                        personident = navBruker.personident,
                        deltakerlisteId = deltakerPayload.deltakerliste.id,
                    )

                    vurderingService.upsertMany(deltakerPayload.vurderingerFraArrangor.orEmpty())
                },
            )

            lagretDeltaker.navBruker.let {
                if (it.adresse == null) {
                    log.info("Oppdaterer navbruker som mangler adresse for deltakerid ${deltakerPayload.id}")
                    navBrukerService.update(it.personident)
                }
            }
        }
    }

    override fun start() = consumer.start()

    override suspend fun close() = consumer.close()

    companion object {
        private fun DeltakerKafkaPayload.toDeltaker(
            navBruker: NavBruker,
            deltakerliste: Deltakerliste,
        ) = Deltaker(
            id = id,
            navBruker = navBruker,
            deltakerliste = deltakerliste,
            startdato = oppstartsdato,
            sluttdato = sluttdato,
            dagerPerUke = dagerPerUke,
            deltakelsesprosent = prosentStilling?.toFloat(),
            bakgrunnsinformasjon = bestillingTekst,
            deltakelsesinnhold = innhold,
            status =
                DeltakerStatus(
                    id = status.id ?: throw IllegalStateException("deltakerstatus mangler id $id"),
                    type = status.type,
                    aarsak = status.aarsak?.let { DeltakerStatus.Aarsak(it, status.aarsaksbeskrivelse) },
                    gyldigFra = status.gyldigFra,
                    gyldigTil = null,
                    opprettet = status.opprettetDato,
                ),
            historikk = historikk.orEmpty(),
            kanEndres = true,
            sistEndret = sistEndret ?: LocalDateTime.now(),
            erManueltDeltMedArrangor = erManueltDeltMedArrangor,
            opprettet = LocalDateTime.now(),
        )

        private fun DeltakerKafkaPayload.toDeltakerOppdatering(): Deltakeroppdatering {
            require(status.id != null) { "Kan ikke håndtere deltakerstatus uten id for deltaker $id" }

            return Deltakeroppdatering(
                id = id,
                startdato = oppstartsdato,
                sluttdato = sluttdato,
                dagerPerUke = dagerPerUke,
                deltakelsesprosent = prosentStilling?.toFloat(),
                bakgrunnsinformasjon = bestillingTekst,
                deltakelsesinnhold = innhold,
                status =
                    DeltakerStatus(
                        id = status.id ?: throw IllegalStateException("deltakerstatus mangler id $id"),
                        type = status.type,
                        aarsak = status.aarsak?.let { DeltakerStatus.Aarsak(it, status.aarsaksbeskrivelse) },
                        gyldigFra = status.gyldigFra,
                        gyldigTil = null,
                        opprettet = status.opprettetDato,
                    ),
                historikk = historikk.orEmpty(),
                sistEndret = sistEndret ?: LocalDateTime.now(),
                erManueltDeltMedArrangor = erManueltDeltMedArrangor,
                forcedUpdate = forcedUpdate,
            )
        }
    }
}
