package no.nav.amt.deltaker.enkeltplass

import no.nav.amt.deltaker.deltaker.DeltakerService
import no.nav.amt.deltaker.deltaker.DeltakerUtils.nyDeltakerStatus
import no.nav.amt.deltaker.deltaker.db.DeltakerRepository
import no.nav.amt.deltaker.enkeltplass.kafka.GjennomforingRequestPayload
import no.nav.amt.deltaker.enkeltplass.kafka.GjennomforingRequestProducer
import no.nav.amt.internapi.enkeltplass.MeldPaaDirekteEnkeltplassRequest
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltakerliste.GjennomforingStatusType
import no.nav.amt.lib.models.deltakerliste.GjennomforingType
import no.nav.amt.lib.utils.database.Database
import org.slf4j.LoggerFactory
import java.util.UUID

class EnkeltplassService(
    private val gjennomforingRequestProducer: GjennomforingRequestProducer,
    private val deltakerRepository: DeltakerRepository,
    private val deltakerService: DeltakerService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    suspend fun opprettGjennomforingRemote(
        deltakerId: UUID,
        request: MeldPaaDirekteEnkeltplassRequest, // TODO
    ) {
        val deltaker = deltakerRepository.get(deltakerId).getOrThrow()

        val gjennomforing = deltaker.deltakerliste

        if (gjennomforing.gjennomforingstype != GjennomforingType.Enkeltplass) {
            log.warn(
                "Kan ikke opprette gjennomforing hos Mulighetsrommet for " +
                    "gjennomforingstype ${gjennomforing.gjennomforingstype} for deltaker $deltakerId",
            )
            return
        }

        // TODO: Har vi noe annet vi kan sjekke her?
        if (gjennomforing.status != GjennomforingStatusType.KLADD) {
            log.info("Kan ikke opprette gjennomforing hos Mulighetsrommet fordi gjennomforing med id ${gjennomforing.id} ikke er i kladd")
            return
        }

        // oppdater deltaker

        // oppdater gjennomforing

        Database.transaction {
            deltakerService.lagreDeltakerStatus(
                deltakerId = deltaker.id,
                nyDeltakerStatus = nyDeltakerStatus(type = DeltakerStatus.Type.SOKT_INN),
                erDeltakerSluttdatoEndret = false,
            )

            gjennomforingRequestProducer.produce(
                GjennomforingRequestPayload.OpprettEnkeltplass(
                    gjennomforingId = gjennomforing.id,
                    tiltakskode = gjennomforing.tiltakstype.tiltakskode,
                    prisinformasjon = gjennomforing.prisinformasjon ?: throw IllegalStateException("Prisinformasjon mangler"),
                    organisasjonsnummer = gjennomforing.arrangor?.organisasjonsnummer ?: throw IllegalStateException("Arrangør mangler"),
                ),
            )
        }
    }
}
