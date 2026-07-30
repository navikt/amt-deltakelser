package no.nav.tiltaksarrangor.unleash

import no.nav.amt.lib.utils.unleash.CommonUnleashToggle
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/unleash/api/feature")
class UnleashAPI(
    private val unleashToggle: CommonUnleashToggle,
) {
    @GetMapping
    fun getFeaturetoggles(
        @RequestParam("feature") features: List<String>,
    ): Map<String, Boolean> = unleashToggle.getFeaturetoggles(features)
}
