package com.ingpsy.designate

////////////////////////////////////////////////////////////////
// manages the data transfer to TUD-DataShareSystem (nextcloud)

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.InternalAPI
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.*
import kotlin.io.readBytes

class NextcloudUploader(
    private val baseUrl: String,
    private val username: String,
    private val password: String
) {

    private val client = HttpClient(CIO)

    @OptIn(InternalAPI::class)
    fun uploadFile(localFilePath: String, remotePath: String) = runBlocking {

        val file = File(localFilePath)
        val fullUrl = "$baseUrl/remote.php/dav/files/$username/$remotePath"

        val authHeader = "Basic " + Base64.getEncoder()
            .encodeToString("$username:$password".toByteArray())

        val response: HttpResponse = client.put(fullUrl) {
            header(HttpHeaders.Authorization, authHeader)
            body = file.readBytes()
            contentType(ContentType.Application.OctetStream)
        }
        response.status
    }


    suspend fun createNextcloudDirectoryWithAuthHeader(dirName: String?): Boolean {
        val client = HttpClient(CIO)

        // crate Basic Auth Header
        val credentials = "$username:$password"
        val encodedCredentials = Base64.getEncoder().encodeToString(credentials.toByteArray())
        val authHeader = "Basic $encodedCredentials"
        val fullUrl = "$baseUrl/remote.php/dav/files/$username/Designate/data/$dirName"

        val response: HttpResponse = client.request(fullUrl) {
            method = HttpMethod("MKCOL")
            headers {
                append(HttpHeaders.Authorization, authHeader)
            }
        }

        client.close()
        return response.status.isSuccess()
    }

}
