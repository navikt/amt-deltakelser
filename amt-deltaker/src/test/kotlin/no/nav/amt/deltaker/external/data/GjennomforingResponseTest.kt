package no.nav.amt.deltaker.external.data

import com.fasterxml.jackson.annotation.JsonInclude
import io.kotest.assertions.json.schema.shouldMatchSchema
import no.nav.amt.deltaker.api.external.response.ArrangorResponse
import no.nav.amt.deltaker.api.external.response.GjennomforingResponse
import no.nav.amt.deltaker.external.data.GjennomforingResponseJsonSchemas.gjennomforingResponseSchema
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import org.junit.jupiter.api.Test
import tools.jackson.module.kotlin.jacksonMapperBuilder
import java.util.UUID

class GjennomforingResponseTest {
    @Test
    fun `fullt populert V1 skal matche skjema`() {
        val nonNullMapper = jacksonMapperBuilder()
            .changeDefaultPropertyInclusion { it.withValueInclusion(JsonInclude.Include.NON_NULL) }
            .build()

        val json = nonNullMapper.writeValueAsString(fullyPopulatedGjennomforingResponseInTest)

        json.shouldMatchSchema(gjennomforingResponseSchema)
    }

    companion object {
        private val idIdInTest = UUID.randomUUID()

        private val fullyPopulatedGjennomforingResponseInTest = GjennomforingResponse(
            id = idIdInTest,
            navn = "~navn~",
            type = "~type~",
            tiltakskode = Tiltakskode.ARBEIDSFORBEREDENDE_TRENING,
            tiltakstypeNavn = "~tiltakstypeNavn~",
            arrangor = ArrangorResponse(
                virksomhetsnummer = "~virksomhetsnummer~",
                navn = "~arrangorNavn~",
            ),
        )
    }
}
