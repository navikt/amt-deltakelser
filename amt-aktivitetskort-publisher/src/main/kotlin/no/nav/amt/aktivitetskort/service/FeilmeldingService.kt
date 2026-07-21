package no.nav.amt.aktivitetskort.service

import no.nav.amt.aktivitetskort.kafka.consumer.SOURCE
import no.nav.amt.aktivitetskort.kafka.consumer.dto.AktivitetskortFeilmelding
import no.nav.amt.aktivitetskort.repositories.FeilmeldingRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class FeilmeldingService(
    private val feilmeldingRepository: FeilmeldingRepository,
    private val metricsService: MetricsService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun handleFeilmelding(
        key: UUID,
        feilmelding: AktivitetskortFeilmelding?,
    ) {
        if (feilmelding == null || feilmelding.source != SOURCE) {
            return
        }
        log.warn("Mottok feilmelding fra AkaaS for key $key: ${feilmelding.errorMessage}, ${feilmelding.errorType}")
        feilmeldingRepository.insert(feilmelding)
        metricsService.incMottattFeilmelding(feilmelding.errorType)
        log.info("Lagret feilmelding for key $key")
    }
}
