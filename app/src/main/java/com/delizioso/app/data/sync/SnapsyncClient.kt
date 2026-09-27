package com.delizioso.app.data.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class SnapsyncClient(
    private val http: OkHttpClient,
    baseUrl: String,
    private val token: String,
    appName: String,
) : SnapshotStore {

    private val root = baseUrl.trimEnd('/') + "/v1/" + appName

    override suspend fun head(): String? = call(request("/snapshot").head()) { r ->
        when (r.code) {
            404 -> null
            200 -> r.etag()
            else -> fail(r)
        }
    }

    override suspend fun download(): Pair<String, ByteArray> = call(request("/snapshot").get()) { r ->
        if (r.code != 200) fail(r)
        r.etag() to r.body!!.bytes()
    }

    override suspend fun missing(hashes: Collection<String>): Set<String> {
        if (hashes.isEmpty()) return emptySet()
        val body = JSONObject().put("hashes", JSONArray(hashes)).toString().toRequestBody(JSON)
        return call(request("/blobs/missing").post(body)) { r ->
            if (r.code != 200) fail(r)
            JSONObject(r.body!!.string()).getJSONArray("missing").toStringList().toSet()
        }
    }

    override suspend fun uploadBlob(sha: String, file: File) {
        call(request("/blobs/$sha").put(file.asRequestBody(OCTET))) { r -> if (r.code != 201) fail(r) else Unit }
    }

    override suspend fun downloadBlob(sha: String, target: File) {
        call(request("/blobs/$sha").get()) { r ->
            if (r.code != 200) fail(r)
            target.parentFile?.mkdirs()
            r.body!!.byteStream().use { input -> target.outputStream().use { input.copyTo(it) } }
        }
        if (sha256Of(target) != sha) {
            target.delete()
            throw SnapsyncException("il file $sha è arrivato danneggiato")
        }
    }

    override suspend fun push(body: ByteArray, baseEtag: String?): PushResult {
        val builder = request("/snapshot").put(body.toRequestBody(JSON))
        if (baseEtag == null) builder.header("If-None-Match", "*") else builder.header("If-Match", baseEtag)
        return call(builder) { r ->
            when (r.code) {
                201 -> PushResult.Created(r.etag())
                412 -> PushResult.Stale
                422 -> PushResult.MissingBlobs
                else -> fail(r)
            }
        }
    }

    private fun request(path: String): Request.Builder =
        Request.Builder().url(root + path).header("Authorization", "Bearer $token")

    private suspend fun <T> call(builder: Request.Builder, handle: (Response) -> T): T =
        withContext(Dispatchers.IO) { http.newCall(builder.build()).execute().use(handle) }

    private fun Response.etag(): String = header("ETag") ?: throw SnapsyncException("il Pi non ha inviato un ETag")

    private fun fail(r: Response): Nothing = throw SnapsyncException(
        if (r.code == 401) "token rifiutato dal Pi" else "il Pi ha risposto HTTP ${r.code} a ${r.request.method} ${r.request.url.encodedPath}"
    )

    private companion object {
        val JSON = "application/json".toMediaType()
        val OCTET = "application/octet-stream".toMediaType()
    }
}
