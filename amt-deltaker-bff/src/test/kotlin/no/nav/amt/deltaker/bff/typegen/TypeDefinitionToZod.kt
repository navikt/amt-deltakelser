package no.nav.amt.deltaker.bff.typegen

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import kotlin.reflect.KClass

object TypeDefinitionToZod {
    fun toZodExpression(
        definition: TypeDefinition,
        prettyPrint: Boolean = true,
    ): String = toZodDefinitions(listOf(definition), prettyPrint)

    fun toZodExpressions(
        classes: Collection<KClass<*>>,
        prettyPrint: Boolean = true,
    ): String {
        val definitions = classes.map { KotlinTypeDefinitionParser.parse(it) }
        return SchemaBuilder(Formatting(prettyPrint)).build(definitions)
    }

    private fun toZodDefinitions(
        definitions: Collection<TypeDefinition>,
        prettyPrint: Boolean,
    ): String = SchemaBuilder(Formatting(prettyPrint)).build(definitions)

    private fun scalarClassToZod(kClass: KClass<*>): String? = when (kClass) {
        UUID::class -> "z.string().uuid()"
        LocalDate::class, LocalDateTime::class -> "z.string()"
        else -> null
    }

    private data class Formatting(
        val prettyPrint: Boolean,
    ) {
        fun indent(depth: Int): String = "  ".repeat(depth)

        fun objectExpression(
            fields: List<Pair<String, String>>,
            depth: Int,
        ): String {
            if (fields.isEmpty()) return "z.object({})"
            if (!prettyPrint) {
                return "z.object({${fields.joinToString(", ") { (name, value) -> "$name: $value" }}})"
            }
            val innerIndent = indent(depth + 1)
            val closingIndent = indent(depth)
            val body = fields.joinToString(",\n") { (name, value) -> "$innerIndent$name: $value" }
            return "z.object({\n$body\n$closingIndent})"
        }

        fun unionExpression(
            items: List<String>,
            depth: Int,
        ): String {
            if (items.size == 1) return items.first()
            if (!prettyPrint) return "z.union([${items.joinToString(", ")}])"
            val innerIndent = indent(depth + 1)
            val closingIndent = indent(depth)
            return "z.union([\n${items.joinToString(",\n") { "$innerIndent$it" }}\n$closingIndent])"
        }

        fun module(declarations: List<String>): String {
            if (!prettyPrint) return declarations.joinToString("\n")
            return declarations.joinToString("\n\n")
        }
    }

    private class SchemaBuilder(
        private val formatting: Formatting,
    ) {
        private val schemaNames = linkedMapOf<KClass<*>, String>()
        private val usedNames = mutableSetOf<String>()
        private val inProgress = mutableSetOf<KClass<*>>()
        private val emittedBodies = linkedMapOf<KClass<*>, String>()

        fun build(definitions: Collection<TypeDefinition>): String {
            definitions.forEach { ensureSchema(it.kClass) }
            val declarations = emittedBodies.entries.map { (kClass, body) ->
                val qualifiedName = kClass.qualifiedName ?: kClass.simpleName ?: "Unknown"
                "/** $qualifiedName */\nconst ${schemaNameFor(kClass)} = $body"
            }
            return formatting.module(declarations)
        }

        private fun ensureSchema(kClass: KClass<*>): String {
            val schemaName = schemaNameFor(kClass)
            if (emittedBodies.containsKey(kClass)) return schemaName
            if (!inProgress.add(kClass)) return schemaName

            val body = if (kClass.isSealed) {
                buildSealedBody(kClass)
            } else {
                buildObjectBody(KotlinTypeDefinitionParser.parse(kClass))
            }

            inProgress.remove(kClass)
            emittedBodies[kClass] = body
            return schemaName
        }

        private fun buildObjectBody(definition: TypeDefinition): String {
            val fields = definition.fields.map { field ->
                field.name to toZodType(field.type, depth = 1)
            }
            return formatting.objectExpression(fields, depth = 0)
        }

        private fun buildSealedBody(kClass: KClass<*>): String {
            val subclasses = kClass.sealedSubclasses.sortedBy { it.qualifiedName ?: it.simpleName ?: "" }
            if (subclasses.isEmpty()) {
                throw IllegalArgumentException("Sealed type has no subclasses: ${kClass.qualifiedName}")
            }

            val items = subclasses.map { subclass ->
                val referenced = ensureSchema(subclass)
                if (subclass in inProgress) "z.lazy(() => $referenced)" else referenced
            }
            return formatting.unionExpression(items, depth = 0)
        }

        private fun toZodType(
            type: TypeReference,
            depth: Int,
        ): String {
            val base = when (type.kind) {
                TypeKind.PRIMITIVE -> primitiveToZod(type)
                TypeKind.COLLECTION -> collectionToZod(type, depth)
                TypeKind.MAP -> mapToZod(type, depth)
                TypeKind.ENUM -> enumToZod(type)
                TypeKind.CLASS -> classReferenceToZod(type)
                TypeKind.SEALED -> sealedReferenceToZod(type)
                TypeKind.UNKNOWN -> throw IllegalArgumentException("Unsupported unknown type: ${type.kType}")
            }
            return if (type.nullable) "$base.nullable()" else base
        }

        private fun primitiveToZod(type: TypeReference): String = when (type.kClass) {
            String::class -> "z.string()"
            Boolean::class -> "z.boolean()"
            Byte::class, Short::class, Int::class, Long::class, Float::class, Double::class -> "z.number()"
            Char::class -> "z.string().length(1)"
            else -> throw IllegalArgumentException("Unsupported primitive type: ${type.kClass?.qualifiedName ?: type.kType}")
        }

        private fun collectionToZod(
            type: TypeReference,
            depth: Int,
        ): String {
            val elementType = type.genericArguments.singleOrNull()
                ?: throw IllegalArgumentException("Collection must have exactly one generic argument: ${type.kType}")
            return "z.array(${toZodType(elementType, depth)})"
        }

        private fun mapToZod(
            type: TypeReference,
            depth: Int,
        ): String {
            val keyType = type.genericArguments.getOrNull(0)
                ?: throw IllegalArgumentException("Map is missing key type: ${type.kType}")
            val valueType = type.genericArguments.getOrNull(1)
                ?: throw IllegalArgumentException("Map is missing value type: ${type.kType}")

            if (keyType.kClass != String::class || keyType.nullable) {
                throw IllegalArgumentException("Only non-null String map keys are supported: ${type.kType}")
            }
            return "z.record(${toZodType(valueType, depth)})"
        }

        private fun enumToZod(type: TypeReference): String {
            val enumClass = type.kClass
                ?: throw IllegalArgumentException("Enum type has no class: ${type.kType}")
            val values = enumClass.java.enumConstants
                ?.map { constant -> "\"$constant\"" }
                ?: throw IllegalArgumentException("Enum constants missing for type: ${type.kType}")
            return "z.enum([${values.joinToString(", ")}])"
        }

        private fun classReferenceToZod(type: TypeReference): String {
            val kClass = type.kClass
                ?: throw IllegalArgumentException("Custom class type has no classifier: ${type.kType}")

            scalarClassToZod(kClass)?.let { return it }

            val referenced = ensureSchema(kClass)
            return if (kClass in inProgress) "z.lazy(() => $referenced)" else referenced
        }

        private fun sealedReferenceToZod(type: TypeReference): String {
            val sealedClass = type.kClass
                ?: throw IllegalArgumentException("Sealed type has no classifier: ${type.kType}")
            val referenced = ensureSchema(sealedClass)
            return if (sealedClass in inProgress) "z.lazy(() => $referenced)" else referenced
        }

        private fun schemaNameFor(kClass: KClass<*>): String {
            return schemaNames.getOrPut(kClass) {
                val base = "${kClass.nestedTypeName()}Schema".lowercaseFirstChar()
                if (usedNames.add(base)) {
                    base
                } else {
                    val packagePrefixed = kClass.schemaPackagePrefix()?.let { prefix ->
                        "${prefix}_${kClass.nestedTypeName()}Schema".lowercaseFirstChar()
                    }

                    if (packagePrefixed != null && usedNames.add(packagePrefixed)) {
                        return@getOrPut packagePrefixed
                    }

                    val fallbackBase = packagePrefixed ?: base
                    var index = 2
                    var candidate = "${fallbackBase}_$index"
                    while (!usedNames.add(candidate)) {
                        index++
                        candidate = "${fallbackBase}_$index"
                    }
                    candidate
                }
            }
        }
    }
}
