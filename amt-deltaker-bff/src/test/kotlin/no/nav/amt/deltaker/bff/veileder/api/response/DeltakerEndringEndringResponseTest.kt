package no.nav.amt.deltaker.bff.veileder.api.response

import io.kotest.matchers.types.shouldBeInstanceOf
import no.nav.amt.deltaker.bff.commonresponse.PrisinformasjonResponse
import no.nav.amt.lib.models.deltaker.DeltakerEndring
import no.nav.amt.lib.models.deltaker.PrisinformasjonDto
import org.junit.jupiter.api.Test

class DeltakerEndringEndringResponseTest {
    @Test
    fun `endrePrisinfo - anskaffelse - mappes korrekt`() {
        // Arrange
        val prisinfo = PrisinformasjonDto.Anskaffelse(pris = 5000)
        val endring = DeltakerEndring.Endring.EndrePrisinfo(prisinfo = prisinfo)

        // Act
        val response = DeltakerEndringEndringResponse.fromModel(endring, null)

        // Assert
        response.shouldBeInstanceOf<DeltakerEndringEndringResponse.EndrePrisinfo>()
        response.prisinfo.shouldBeInstanceOf<PrisinformasjonResponse.Anskaffelse>()
    }

    @Test
    fun `endrePrisinfo - tilskudd - mappes korrekt`() {
        // Arrange
        val tilskuddInfo = PrisinformasjonDto.Tilskudd.TilskuddInfo(
            type = PrisinformasjonDto.Tilskudd.Tilskuddstype.EKSAMENSGEBYR,
            pris = 3000,
        )
        val prisinfo = PrisinformasjonDto.Tilskudd(
            tilskudd = listOf(tilskuddInfo),
            tilleggsopplysninger = "Noen opplysninger",
        )
        val endring = DeltakerEndring.Endring.EndrePrisinfo(prisinfo = prisinfo)

        // Act
        val response = DeltakerEndringEndringResponse.fromModel(endring, null)

        // Assert
        response.shouldBeInstanceOf<DeltakerEndringEndringResponse.EndrePrisinfo>()
        response
            .prisinfo
            .shouldBeInstanceOf<PrisinformasjonResponse.Tilskudd>()
    }

    @Test
    fun `endrePrisinfo - ingen kostnader - mappes korrekt`() {
        // Arrange
        val prisinfo = PrisinformasjonDto.IngenKostnader(
            aarsak = PrisinformasjonDto.IngenKostnader.Aarsak.OPPLAERINGEN_ER_KOSTNADSFRI,
            tilleggsopplysninger = null,
        )
        val endring = DeltakerEndring.Endring.EndrePrisinfo(prisinfo = prisinfo)

        // Act
        val response = DeltakerEndringEndringResponse.fromModel(endring, null)

        // Assert
        response.shouldBeInstanceOf<DeltakerEndringEndringResponse.EndrePrisinfo>()
        response
            .prisinfo
            .shouldBeInstanceOf<PrisinformasjonResponse.IngenKostnader>()
    }
}
