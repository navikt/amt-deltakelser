package no.nav.tiltaksarrangor.config

import no.nav.tiltaksarrangor.client.amtarrangor.AmtArrangorApi
import no.nav.tiltaksarrangor.client.amtarrangor.HentArrangorApi
import no.nav.tiltaksarrangor.client.amtperson.AmtPersonApi
import org.springframework.context.annotation.Configuration
import org.springframework.web.service.registry.ImportHttpServices

@Configuration
@ImportHttpServices(group = "amt-arrangor-tokenx", types = [AmtArrangorApi::class])
@ImportHttpServices(group = "amt-arrangor-aad", types = [HentArrangorApi::class])
@ImportHttpServices(group = "amt-person-aad", types = [AmtPersonApi::class])
class ClientConfig
