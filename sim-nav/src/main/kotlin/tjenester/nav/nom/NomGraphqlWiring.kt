package tjenester.nav.nom

import graphql.GraphQL
import graphql.scalars.ExtendedScalars
import graphql.schema.DataFetcher
import graphql.schema.GraphQLSchema
import graphql.schema.idl.RuntimeWiring
import graphql.schema.idl.SchemaGenerator
import graphql.schema.idl.SchemaParser

private const val NOM_QUERY_SCHEMA_RESOURCE = "/nom/nom-query.graphqls"
private const val NOM_TYPES_SCHEMA_RESOURCE = "/nom/nom-types.graphqls"

fun createNomGraphql(ressurserDataFetcher: DataFetcher<Any?>): GraphQL {
    val executableSchema = createNomExecutableSchema(ressurserDataFetcher)
    return GraphQL.newGraphQL(executableSchema).build()
}

fun createNomExecutableSchema(ressurserDataFetcher: DataFetcher<Any?>): GraphQLSchema {
    val typeDefinitionRegistry = loadNomTypeDefinitions()

    val runtimeWiring = RuntimeWiring.newRuntimeWiring()
        .scalar(ExtendedScalars.Date)
        .scalar(ExtendedScalars.DateTime)
        .scalar(ExtendedScalars.Json)
        .type("SearchResult") { typeWiring ->
            typeWiring.typeResolver { environment ->
                val value = environment.getObject<Any?>()
                when (value) {
                    is Map<*, *> -> {
                        val objectType = if (value.containsKey("navident")) "Ressurs" else "OrgEnhet"
                        environment.schema.getObjectType(objectType)
                    }

                    else -> null
                }
            }
        }
        .type("Query") { typeWiring ->
            typeWiring.dataFetcher("ressurser", ressurserDataFetcher)
        }
        .build()

    return SchemaGenerator().makeExecutableSchema(typeDefinitionRegistry, runtimeWiring)
}

private fun loadNomTypeDefinitions() =
    SchemaParser().parse(
        "${loadResource(NOM_QUERY_SCHEMA_RESOURCE)}\n${loadResource(NOM_TYPES_SCHEMA_RESOURCE)}",
    )

private fun loadResource(resourcePath: String): String =
    object {}.javaClass.getResourceAsStream(resourcePath)?.use { stream ->
        stream.bufferedReader().readText()
    } ?: error("Missing schema resource: $resourcePath")


