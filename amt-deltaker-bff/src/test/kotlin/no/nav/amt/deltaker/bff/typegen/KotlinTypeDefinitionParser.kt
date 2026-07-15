package no.nav.amt.deltaker.bff.typegen

import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.full.memberProperties

data class TypeDefinition(
    val kClass: KClass<*>,
    val typeName: String,
    val fields: List<FieldDefinition>,
)

data class FieldDefinition(
    val name: String,
    val type: TypeReference,
)

data class TypeReference(
    val kType: KType,
    val kClass: KClass<*>?,
    val typeName: String?,
    val kind: TypeKind,
    val nullable: Boolean,
    val genericArguments: List<TypeReference>,
    val sealedSubclasses: List<KClass<*>>,
)

enum class TypeKind {
    PRIMITIVE,
    COLLECTION,
    MAP,
    ENUM,
    SEALED,
    CLASS,
    UNKNOWN,
}

object KotlinTypeDefinitionParser {
    fun parse(kClass: KClass<*>): TypeDefinition {
        val fields = kClass.memberProperties
            .sortedBy { it.name }
            .map { property ->
                FieldDefinition(
                    name = property.name,
                    type = property.returnType.toTypeReference(),
                )
            }

        return TypeDefinition(
            kClass = kClass,
            typeName = kClass.nestedTypeName(),
            fields = fields,
        )
    }

    private fun KType.toTypeReference(): TypeReference {
        val classifier = classifier as? KClass<*>
        val genericArguments = arguments.mapNotNull { it.type?.toTypeReference() }
        val sealedSubclasses = if (classifier?.isSealed == true) {
            classifier.sealedSubclasses.sortedBy { it.qualifiedName ?: it.simpleName ?: "" }
        } else {
            emptyList()
        }

        return TypeReference(
            kType = this,
            kClass = classifier,
            typeName = classifier?.nestedTypeName(),
            kind = classifier.toTypeKind(),
            nullable = isMarkedNullable,
            genericArguments = genericArguments,
            sealedSubclasses = sealedSubclasses,
        )
    }

    private fun KClass<*>?.toTypeKind(): TypeKind {
        if (this == null) return TypeKind.UNKNOWN
        if (this in primitiveLikeTypes) return TypeKind.PRIMITIVE
        if (Map::class.java.isAssignableFrom(this.java)) return TypeKind.MAP
        if (Collection::class.java.isAssignableFrom(this.java)) return TypeKind.COLLECTION
        if (this.java.isEnum) return TypeKind.ENUM
        if (isSealed) return TypeKind.SEALED
        return TypeKind.CLASS
    }

    private val primitiveLikeTypes = setOf(
        String::class,
        Boolean::class,
        Byte::class,
        Short::class,
        Int::class,
        Long::class,
        Float::class,
        Double::class,
        Char::class,
    )
}
