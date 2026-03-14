package dev.codex.mobile.core.data.usagewrapped

import dev.codex.mobile.core.model.HostProfile
import dev.codex.mobile.core.model.UsageWrappedSummary
import dev.codex.mobile.core.model.usageWrappedPort
import java.io.IOException
import okhttp3.OkHttpClient
import okhttp3.Request

internal class UsageWrappedServiceClient(
    private val okHttpClient: OkHttpClient,
) {
    fun fetchSummary(host: HostProfile): UsageWrappedSummary {
        val url = "http://${host.address}:${host.usageWrappedPort()}/usage-wrapped"
        val request: Request = Request.Builder()
            .url(url)
            .get()
            .build()
        okHttpClient.newCall(request).execute().use { response ->
            val responseBody: String = response.body.string()
            if (!response.isSuccessful) {
                throw IOException(
                    responseBody.ifBlank { "Usage wrapped service returned HTTP ${response.code}." },
                )
            }
            return usageWrappedJson.decodeFromString<UsageWrappedSummaryDto>(responseBody).toModel()
        }
    }
}
