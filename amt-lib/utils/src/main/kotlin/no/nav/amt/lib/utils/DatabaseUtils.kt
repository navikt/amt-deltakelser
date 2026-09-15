package no.nav.amt.lib.utils

import org.postgresql.util.PGobject
import tools.jackson.databind.ObjectMapper

const val JSON_TYPE = "json"

fun ObjectMapper.toPGObject(value: Any) = PGobject().also {
    it.type = JSON_TYPE
    it.value = this.writeValueAsString(value)
}

inline fun <reified T : Collection<*>> ObjectMapper.polymorphicCollectionToPGObject(value: T) = PGobject().also {
    it.type = JSON_TYPE
    it.value = this.writePolymorphicCollectionAsString(value)
}
