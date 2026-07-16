package no.nav.amt.lib.testing.architecture

import com.tngtech.archunit.core.domain.JavaClass.Predicates.assignableTo
import com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage
import com.tngtech.archunit.core.domain.JavaClass.Predicates.resideOutsideOfPackage
import com.tngtech.archunit.core.domain.JavaField
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.ArchCondition
import com.tngtech.archunit.lang.ConditionEvents
import com.tngtech.archunit.lang.SimpleConditionEvent
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields
import java.lang.reflect.GenericArrayType
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.TypeVariable
import java.lang.reflect.WildcardType

fun assertResponseFieldsUseAllowedTypes(
    importedPackages: List<String>,
    responsePackagePatterns: Array<String>,
    additionalAllowedPackagePatterns: Array<String> = emptyArray(),
) {
    val allowedPackagePatterns = responsePackagePatterns + additionalAllowedPackagePatterns

    val importedClasses = ClassFileImporter()
        .withImportOption(ImportOption.DoNotIncludeTests())
        .importPackages(*importedPackages.toTypedArray())

    fields()
        .that()
        .areDeclaredInClassesThat()
        .resideInAnyPackage(*responsePackagePatterns)
        .should()
        .haveRawType(
            resideOutsideOfPackage("no.nav..")
                .or(resideInAnyPackage(*allowedPackagePatterns))
                .or(assignableTo(Enum::class.java)),
        ).andShould(haveOnlyAllowedGenericTypeArguments(allowedPackagePatterns))
        .check(importedClasses)
}

private fun haveOnlyAllowedGenericTypeArguments(responsePackagePatterns: Array<String>): ArchCondition<JavaField> =
    object : ArchCondition<JavaField>("have only allowed generic type arguments") {
        override fun check(
            field: JavaField,
            events: ConditionEvents,
        ) {
            val runtimeField = runCatching {
                field.owner.reflect().getDeclaredField(field.name)
            }.getOrNull() ?: return

            if (runtimeField.isSynthetic || runtimeField.name.startsWith("$")) return

            val disallowedTypes = extractGenericTypeArguments(runtimeField.genericType)
                .filterNot { isAllowedResponseType(it, responsePackagePatterns) }
                .map { disallowedType ->
                    "${field.owner.name}.${field.name} -> ${disallowedType.name}"
                }

            disallowedTypes.forEach { violation ->
                events.add(SimpleConditionEvent.violated(field, violation))
            }
        }
    }

private fun isAllowedResponseType(
    type: Class<*>,
    responsePackagePatterns: Array<String>,
): Boolean {
    val typeName = type.name
    if (!typeName.startsWith("no.nav.")) return true
    if (type.isEnum) return true
    return responsePackagePatterns.any { pkgPattern -> pkgPattern.matchesClassName(typeName) }
}

private fun extractGenericTypeArguments(type: Type): List<Class<*>> = when (type) {
    is ParameterizedType -> type.actualTypeArguments.flatMap { extractReferencedClasses(it) }
    is GenericArrayType -> extractReferencedClasses(type.genericComponentType)
    else -> emptyList()
}

private fun extractReferencedClasses(type: Type): List<Class<*>> = when (type) {
    is Class<*> -> if (type.isArray) extractReferencedClasses(type.componentType) else listOf(type)
    is ParameterizedType -> {
        val raw = (type.rawType as? Class<*>)?.let { listOf(it) } ?: emptyList()
        raw + type.actualTypeArguments.flatMap { extractReferencedClasses(it) }
    }

    is GenericArrayType -> extractReferencedClasses(type.genericComponentType)
    is WildcardType -> (type.upperBounds + type.lowerBounds).flatMap { extractReferencedClasses(it) }
    is TypeVariable<*> -> type.bounds.flatMap { extractReferencedClasses(it) }
    else -> emptyList()
}

private fun String.matchesClassName(className: String): Boolean {
    val regex = toArchUnitPackageRegex()
    return regex.matches(className)
}

private fun String.toArchUnitPackageRegex(): Regex {
    val sb = StringBuilder("^")
    var i = 0
    while (i < length) {
        val current = this[i]
        val next = getOrNull(i + 1)
        if (current == '.' && next == '.') {
            sb.append(".*")
            i += 2
        } else {
            if (current == '.') sb.append("\\.") else sb.append(Regex.escape(current.toString()))
            i++
        }
    }
    sb.append("$")
    return Regex(sb.toString())
}
