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
//    return Loader.loadPDF(document.asByteArray());
    return PDDocument.load(document.asByteArray()) // v2.0.25
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

/*
* get fields ...
* https://stackoverflow.com/questions/50609478/how-to-substitute-missing-font-when-filling-a-form-with-pdfbox
*
* setValue
* https://stackoverflow.com/questions/14582478/how-to-set-a-value-in-pdf-form-using-java-pdfbox-api?rq=4
* */
class Processor(private val requestId: String) {
    fun addText(document: Document): Result {
        LOGGER.info { "$requestId: handling pdf-add values" }

        loadFromDocument(document).use { pdfDocument ->

            /* todo get all inputs in this file*/
//            val docCatalog = pdfDocument.getDocumentCatalog()
//            val acroForm = docCatalog.acroForm
            val acroForm = pdfDocument.documentCatalog.acroForm
            val fields = acroForm.getFields()

            val names = arrayOf("fill_10", "fill_11", "fill_12")  // manual naming for this testing

            for (field in fields) {
//                list(field)
                val inputName = field.getFullyQualifiedName()
                val found = Arrays.stream(names).anyMatch { t -> t == inputName }
                if (found) {
                    LOGGER.info { "$requestId: updating value in $inputName" }
                    field.setValue("newval-$inputName")
                }
            }

            val data = saveToDocument(pdfDocument)
            return Result(true, "", data = data)
        }
    }

    private fun list(field: PDField) {
        LOGGER.info { "$requestId: field: ${field.getFullyQualifiedName()} | ${field.partialName} -> ${field.valueAsString}" }
        if (field is PDNonTerminalField) {
            for (child in field.getChildren()) {
                list(child)
            }
        }
    }
}