package no.nav.amt.lib.utils.unleash

import io.getunleash.Unleash
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode

class CommonUnleashToggle(
    private val unleashClient: Unleash,
) {
    // Når komet er master for tiltakstype så skal det være tillatt å gjøre endringer og vi skal dele data på deltakelsene med eksterne
    fun erKometMasterForTiltakstype(tiltakskode: Tiltakskode): Boolean = tiltakstyperKometErMasterFor.any { it == tiltakskode } ||
        (unleashClient.isEnabled(ENABLE_KOMET_DELTAKERE) && tiltakstyperKometKanskjeErMasterFor.any { it == tiltakskode })

    fun skalLeseArenaDataForTiltakstype(tiltakskode: Tiltakskode): Boolean =
        unleashClient.isEnabled(LES_ARENA_DELTAKERE) && tiltakstyperKometKanLese.any { it == tiltakskode }

    fun skalLeseGjennomforing(tiltakskode: String): Boolean =
        tiltakstyperKometErMasterFor.any { it.name == tiltakskode } || tiltakstyperKometKanLese.any { it.name == tiltakskode }

    fun getFeaturetoggles(features: List<String>): Map<String, Boolean> = features.associateWith { unleashClient.isEnabled(it) }

    fun skalOppdatereUendredeAktivitetskort(): Boolean = unleashClient.isEnabled(OPPDATER_ALLE_AKTIVITETSKORT)

    companion object {
        const val ENABLE_KOMET_DELTAKERE = "amt.enable-komet-deltakere"
        const val LES_ARENA_DELTAKERE = "amt.les-arena-deltakere"
        const val OPPDATER_ALLE_AKTIVITETSKORT = "amt.oppdater-alle-aktivitetskort"

        /**
         Denne skal inneholde tiltakstyper som vi har tatt over og er master for
         i både dev og prod
         */
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
            Tiltakskode.TILRETTELAGT_ARBEID_ORDINAER,
        )

        /**
         Denne skal inneholde tiltakstyper som vi jobber med å bli master for. Toggle vil da ofte være på i dev og av i prod
         mens vi utvikler funksjonalitet for de nye typene
         */
        internal val tiltakstyperKometKanskjeErMasterFor = setOf(
            Tiltakskode.ENKELTPLASS_ARBEIDSMARKEDSOPPLAERING,
            Tiltakskode.ENKELTPLASS_FAG_OG_YRKESOPPLAERING,
            Tiltakskode.HOYERE_UTDANNING,
        )

        /**
         Denne skal inneholde tiltakstyper som vi skal lese arenadata for (og eksponere videre internt)
         Må oppdateres når vi skal lese inn en ny tiltakstype.
         Denne vil ofte være lik tiltakstyperKometKanskjeErMasterFor (med mindre vi både skal lese tiltakstyper og bli master for andre tiltakstyper)
         */
        private val tiltakstyperKometKanLese = tiltakstyperKometKanskjeErMasterFor
    }
}
