package no.nav.amt.deltaker.external.data

import io.kotest.assertions.json.schema.jsonSchema
import io.kotest.assertions.json.schema.obj
import io.kotest.assertions.json.schema.string

object GjennomforingResponseJsonSchemas {
    val arrangorSchema = jsonSchema {
        obj {
            withProperty("virksomhetsnummer") { string() }
            withProperty("navn") { string() }
            additionalProperties = false
        }
    }

    val gjennomforingResponseSchema = jsonSchema {
        obj {
            withProperty("id") { string() }
            withProperty("navn") { string() }
            withProperty("type") { string() }
            withProperty("tiltakskode") {
                obj {
                    withProperty("kode") { string() }
                    withProperty("visningsnavn") { string() }
                    additionalProperties = false
                }
            }
            withProperty("tiltakstypeNavn") { string() }
            withProperty("arrangor") { arrangorSchema() }
            additionalProperties = false
        }
    }
}
