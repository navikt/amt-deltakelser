package no.nav.amt.lib.utils.unleash

import io.getunleash.Unleash
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import java.util.Collections.emptySet
import kotlin.collections.any

class CommonUnleashToggle(
    private val unleashClient: Unleash,
) {
    fun erKometMasterForTiltakstype(tiltakskode: String): Boolean = tiltakstyperKometErMasterFor.any { it.name == tiltakskode } ||
            (unleashClient.isEnabled(ENABLE_KOMET_DELTAKERE) && tiltakstyperKometKanskjeErMasterFor.any { it.name == tiltakskode })

    fun erKometMasterForTiltakstype(tiltakskode: Tiltakskode): Boolean = erKometMasterForTiltakstype(tiltakskode.name)

    fun skalLeseArenaDataForTiltakstype(tiltakskode: String): Boolean =
        unleashClient.isEnabled(LES_ARENA_DELTAKERE) && tiltakstyperKometKanLese.any { it.name == tiltakskode }

    fun skalLeseArenaDataForTiltakstype(tiltakskode: Tiltakskode): Boolean = skalLeseArenaDataForTiltakstype(tiltakskode.name)

    fun skalLeseGjennomforing(tiltakskode: String): Boolean =
        erKometMasterForTiltakstype(tiltakskode) || skalLeseArenaDataForTiltakstype(tiltakskode)

    fun skalProdusereTilDeltakerEksternTopic(): Boolean = unleashClient.isEnabled(PRODUSER_TIL_DELTAKER_EKSTERN_TOPIC)

    fun prioriterSynkronKommunikasjon(): Boolean = unleashClient.isEnabled("amt.prioriter-synkron-kommunikasjon")

    fun getFeaturetoggles(features: List<String>): Map<String, Boolean> = features.associateWith { unleashClient.isEnabled(it) }

    fun skalOppdatereUendredeAktivitetskort(): Boolean = unleashClient.isEnabled(OPPDATER_ALLE_AKTIVITETSKORT)

    companion object {
        const val ENABLE_KOMET_DELTAKERE = "amt.enable-komet-deltakere"
        const val LES_ARENA_DELTAKERE = "amt.les-arena-deltakere"
        const val PRODUSER_TIL_DELTAKER_EKSTERN_TOPIC = "amt.produser-deltakere-til-deltaker-ekstern-topic"
        const val OPPDATER_ALLE_AKTIVITETSKORT = "amt.oppdater-alle-aktivitetskort"

        private val tiltakstyperKometErMasterFor = setOf(
            Tiltakskode.ARBEIDSFORBEREDENDE_TRENING,
            Tiltakskode.OPPFOLGING,
            Tiltakskode.AVKLARING,
            Tiltakskode.ARBEIDSRETTET_REHABILITERING,
            Tiltakskode.DIGITALT_OPPFOLGINGSTILTAK,
            Tiltakskode.VARIG_TILRETTELAGT_ARBEID_SKJERMET,
            Tiltakskode.GRUPPE_ARBEIDSMARKEDSOPPLAERING,
            Tiltakskode.JOBBKLUBB,
            Tiltakskode.GRUPPE_FAG_OG_YRKESOPPLAERING,
            Tiltakskode.ARBEIDSMARKEDSOPPLAERING,
            Tiltakskode.NORSKOPPLAERING_GRUNNLEGGENDE_FERDIGHETER_FOV,
            Tiltakskode.STUDIESPESIALISERING,
            Tiltakskode.FAG_OG_YRKESOPPLAERING,
            Tiltakskode.HOYERE_YRKESFAGLIG_UTDANNING,
        )

        private val tiltakstyperKometKanLese = setOf(
            Tiltakskode.ENKELTPLASS_ARBEIDSMARKEDSOPPLAERING,
            Tiltakskode.ENKELTPLASS_FAG_OG_YRKESOPPLAERING,
            Tiltakskode.HOYERE_UTDANNING,
        )

        private val tiltakstyperKometKanskjeErMasterFor = emptySet<Tiltakskode>()
    }
}