package no.nav.amt.lib.utils

import org.postgresql.util.PGobject
import tools.jackson.databind.ObjectMapper

fun ObjectMapper.toPGObject(value: Any) = PGobject().also {
    it.type = "json"
    it.value = this.writeValueAsString(value)
}

inline fun <reified T : Any> ObjectMapper.polymorphicToPGObject(value: T) = PGobject().also {
    it.type = "json"
    it.value = this.writePolymorphicListAsString(value)
}
