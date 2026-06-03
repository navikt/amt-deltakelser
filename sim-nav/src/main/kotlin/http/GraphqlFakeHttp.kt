package http

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.convertValue
import graphql.ExecutionInput
import graphql.GraphQL
import io.ktor.http.*
import io.ktor.server.application.*

suspend fun respondGraphqlFake(
    call: ApplicationCall,
    objectMapper: ObjectMapper,
    graphql: GraphQL,
) {
    val body = readRequestBody(call)
    val request = runCatching { objectMapper.readTree(body) }
        .getOrElse {
            respondJson(call, HttpStatusCode.BadRequest, graphqlErrorResponse(objectMapper, "Invalid JSON payload"))
            return
        }

    val query = request.path("query").asText("").trim()
    if (query.isBlank()) {
        respondJson(call, HttpStatusCode.BadRequest, graphqlErrorResponse(objectMapper, "Missing GraphQL query"))
        return
    }

    val variablesNode = request.path("variables")
    if (!variablesNode.isMissingNode && !variablesNode.isNull && !variablesNode.isObject) {
        respondJson(call, HttpStatusCode.BadRequest, graphqlErrorResponse(objectMapper, "'variables' must be a JSON object"))
        return
    }

    val variables: Map<String, Any?> = if (variablesNode.isObject) {
        objectMapper.convertValue(variablesNode)
    } else {
        emptyMap()
    }

    val operationName = request.path("operationName")
        .asText("")
        .takeIf { it.isNotBlank() }

    val executionInput = ExecutionInput.newExecutionInput()
        .query(query)
        .operationName(operationName)
        .variables(variables)
        .build()

    val executionResult = graphql.execute(executionInput)
    val response = objectMapper.writeValueAsString(executionResult.toSpecification())
    val status = if (executionResult.errors.isEmpty()) HttpStatusCode.OK else HttpStatusCode.BadRequest

    respondJson(call, status, response)
}

private fun graphqlErrorResponse(objectMapper: ObjectMapper, message: String): String = objectMapper.writeValueAsString(
    mapOf(
        "errors" to listOf(
            mapOf(
                "message" to message,
                "extensions" to mapOf("code" to "BAD_REQUEST"),
            ),
        ),
        "data" to null,
    ),
)

