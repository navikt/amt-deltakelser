package no.nav.tiltaksarrangor.service

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import no.nav.amt.lib.models.arrangor.melding.Vurderingstype
import org.springframework.stereotype.Service

private const val INNLOGGING_METRIC = "tiltaksarrangorbff_innlogging"
private const val FJERNET_DELTAKER_METRIC = "tiltaksarrangorbff_fjernet_deltaker"
private const val LAGT_TIL_DELTAKERLISTE_METRIC = "tiltaksarrangorbff_lagttil_deltakerliste"
private const val FJERNET_DELTAKERLISTE_METRIC = "tiltaksarrangorbff_fjernet_deltakerliste"
private const val TILDELT_VEILEDER_METRIC = "tiltaksarrangorbff_tildelt_veileder"
private const val TILBAKEKALT_EM_METRIC = "tiltaksarrangorbff_tilbakekalt_em"
private const val VURDERING_OPPRETTET_METRIC = "tiltaksarrangorbff_vurdering_opprettet"

@Service
class MetricsService(
	private val registry: MeterRegistry,
) {
	private val innloggetKoordinatorCounter = registry.counter(INNLOGGING_METRIC, "rolle", RollePermutasjon.KOORDINATOR.name)
	private val innloggetVeilederCounter = registry.counter(INNLOGGING_METRIC, "rolle", RollePermutasjon.VEILEDER.name)
	private val innloggetKoordinatorOgVeilederCounter =
		registry.counter(
			INNLOGGING_METRIC,
			"rolle",
			RollePermutasjon.KOORDINATOR_OG_VEILEDER.name,
		)
	private val innloggetTotaltCounter = registry.counter(INNLOGGING_METRIC, "rolle", RollePermutasjon.TOTALT.name)
	private val fjernetDeltakerCounter = registry.counter(FJERNET_DELTAKER_METRIC)
	private val lagtTilDeltakerlisteCounter = registry.counter(LAGT_TIL_DELTAKERLISTE_METRIC)
	private val fjernetDeltakerlisteCounter = registry.counter(FJERNET_DELTAKERLISTE_METRIC)
	private val tildeltVeilederCounter = registry.counter(TILDELT_VEILEDER_METRIC)
	private val tilbakekaltEndringsmeldingCounter = registry.counter(TILBAKEKALT_EM_METRIC)

	fun incInnloggetAnsatt(roller: List<String>) {
		if (roller.isEmpty()) {
			return
		}
		if (roller.size == 1) {
			if (roller.contains("KOORDINATOR")) {
				innloggetKoordinatorCounter.increment()
			} else {
				innloggetVeilederCounter.increment()
			}
		} else {
			innloggetKoordinatorOgVeilederCounter.increment()
		}
		innloggetTotaltCounter.increment()
	}

	fun incFjernetDeltaker() {
		fjernetDeltakerCounter.increment()
	}

	fun incLagtTilDeltakerliste() {
		lagtTilDeltakerlisteCounter.increment()
	}

	fun incFjernetDeltakerliste() {
		fjernetDeltakerlisteCounter.increment()
	}

	fun incTildeltVeileder() {
		tildeltVeilederCounter.increment()
	}

	fun incTilbakekaltEndringsmelding() {
		tilbakekaltEndringsmeldingCounter.increment()
	}

	fun incVurderingOpprettet(vurderingstype: Vurderingstype) {
		Counter
			.builder(VURDERING_OPPRETTET_METRIC)
			.tags("vurderingstype", vurderingstype.name)
			.register(registry)
			.increment()
	}

	enum class RollePermutasjon {
		KOORDINATOR,
		VEILEDER,
		KOORDINATOR_OG_VEILEDER,
		TOTALT,
	}
}
