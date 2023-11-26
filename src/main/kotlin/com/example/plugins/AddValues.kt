package com.example.plugins

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.interactive.form.PDField
import org.apache.pdfbox.pdmodel.interactive.form.PDNonTerminalField
import java.io.ByteArrayOutputStream
import java.util.*


private val LOGGER = KotlinLogging.logger("processor")

data class Result(
    val success: Boolean,
    val reason: String,
    val status: Int = 200,
    val data: Any
)

fun loadFromDocument(document: Document): PDDocument {
    return PDDocument.load(document.asByteArray())
}

fun saveToDocument(pdf: PDDocument): Document {
    val bytearray = ByteArrayOutputStream()
    pdf.save(bytearray)
    bytearray.flush()
    val data = bytearray.toByteArray()
    return Document(Base64.getEncoder().encodeToString(data))
}

fun Application.addValues() {
    routing {
        post("/add-values") {
            val requestId: String = call.request.headers["X-Cellosign-Request-Id"] ?: UUID.randomUUID().toString()
            val data = call.receive<RequestData>()
            val processor = Processor(requestId)
            val result = processor.addText(data.document)

            if (result.success) {
                val resultDocument: Document = result.data as Document
                call.respond(
                    HttpStatusCode.fromValue(result.status),
                    AppendValuesResponseData(resultDocument)
                )
            } else {
                call.respond(
                    HttpStatusCode.fromValue(result.status),
                    Failure(result.reason)
                )
            }

        }
    }
}


class Processor(private val requestId: String) {
    fun addText(document: Document): Result {
        LOGGER.info { "$requestId: handling pdf-add values" }

        loadFromDocument(document).use { pdfDocument ->

            /* todo get all inputs in this file*/
            val docCatalog = pdfDocument.getDocumentCatalog()
//            val acroForm = docCatalog.getAcroForm()
            val acroForm = docCatalog.acroForm
            val fields = acroForm.getFields()
            for (field in fields) {
//                LOGGER.info { "$requestId: field $field" }
                list(field)
            }

            val data = saveToDocument(pdfDocument)
            return Result(true, "", data = data)
        }
    }

    private fun list(field: PDField) {
//        println(field.getFullyQualifiedName())
//        println(field.partialName)

        LOGGER.info { "$requestId: field: ${field.getFullyQualifiedName()} -> ${field.valueAsString}" }

        if (field is PDNonTerminalField) {
            for (child in field.getChildren()) {
                list(child)
            }
        }
    }
}