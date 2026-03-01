package no.nav.amt.deltaker.bff.application.metrics

import io.prometheus.metrics.core.metrics.Counter

const val METRICS_NS = "amtdeltakerbff"

object MetricRegister {
    val OPPRETTET_KLADD: Counter = Counter
        .builder()
        .name("${METRICS_NS}_opprettet_kladd_count")
        .help("Antall opprettede kladder for påmelding")
        .withoutExemplars()
        .register()

    val DELT_UTKAST: Counter = Counter
        .builder()
        .name("${METRICS_NS}_delt_utkast_count")
        .help("Antall delte utkast for påmelding")
        .withoutExemplars()
        .register()

    val MELDT_PA_DIREKTE_UTEN_UTKAST: Counter = Counter
        .builder()
        .name("${METRICS_NS}_direkte_uten_utkast_count")
        .help("Antall direkte påmeldinger uten utkast")
        .withoutExemplars()
        .register()

    val MELDT_PA_DIREKTE_MED_UTKAST: Counter = Counter
        .builder()
        .name("${METRICS_NS}_direkte_med_utkast_count")
        .help("Antall direkte påmeldinger med utkast")
        .withoutExemplars()
        .register()

    val AVBRUTT_UTKAST: Counter = Counter
        .builder()
        .name("${METRICS_NS}_avbrutt_utkast_count")
        .help("Antall avbrutte utkast for påmelding")
        .withoutExemplars()
        .register()

    val GODKJENT_UTKAST: Counter = Counter
        .builder()
        .name("${METRICS_NS}_godkjent_utkast_count")
        .help("Antall godkjente utkast for påmelding")
        .withoutExemplars()
        .register()
}
