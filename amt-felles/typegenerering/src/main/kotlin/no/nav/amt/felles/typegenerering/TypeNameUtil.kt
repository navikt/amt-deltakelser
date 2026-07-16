package no.nav.amt.felles.typegenerering

import kotlin.reflect.KClass

fun KClass<*>.nestedTypeName(): String {
    val classes = generateSequence(this.java) { it.enclosingClass }
        .toList()
        .asReversed()

    return classes.joinToString("_") { javaClass ->
        javaClass.simpleName.takeIf { it.isNotBlank() } ?: "Anonymous"
    }
}

fun KClass<*>.schemaPackagePrefix(rootPackage: String = "no.nav.amt.deltaker.bff"): String? {
    val packageName = this.java.packageName.takeIf { it.isNotBlank() } ?: return null
    val suffix = when {
        packageName == rootPackage -> ""
        packageName.startsWith("$rootPackage.") -> packageName.removePrefix("$rootPackage.")
        else -> packageName
    }
    if (suffix.isBlank()) return null
    return suffix.replace('.', '_')
}

fun String.lowercaseFirstChar(): String = replaceFirstChar { it.lowercase() }
