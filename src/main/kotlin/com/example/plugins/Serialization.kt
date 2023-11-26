package com.example.plugins

import com.typesafe.config.Optional
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

import java.util.Base64


@Serializable
data class Document(val base64: String) {
    fun asByteArray(): ByteArray? {
        return Base64.getDecoder().decode(base64)
    }
}

@Serializable
data class RequestData(
    val document: Document
)

@Serializable
data class AppendValuesResponseData(val document: Document)

@Serializable
data class Failure(val error: String)

fun Application.configureSerialization() {
    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            isLenient = true
            ignoreUnknownKeys = true
        })
    }
}