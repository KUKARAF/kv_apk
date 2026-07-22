package dev.kv.apk.data

import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

private const val OPENROUTER_BASE_URL = "https://openrouter.ai/"

data class OpenRouterKeyData(
    val hash: String? = null,
    val id: String? = null,
    val name: String,
    val disabled: Boolean = false,
) {
    // Same hash/id ambiguity kv_cli's providers/openrouter.rs flags — prefer hash, fall back to id.
    val keyId: String get() = hash ?: id ?: ""
}

data class OpenRouterListResponse(val data: List<OpenRouterKeyData>)

data class OpenRouterCreateKeyRequest(val name: String, val limit: Double? = null)

data class OpenRouterCreateKeyResponse(
    val data: OpenRouterKeyData,
    val key: String,
)

interface OpenRouterApi {
    @GET("api/v1/keys")
    suspend fun listKeys(@Header("Authorization") auth: String): OpenRouterListResponse

    @POST("api/v1/keys/")
    suspend fun createKey(
        @Header("Authorization") auth: String,
        @Body body: OpenRouterCreateKeyRequest,
    ): OpenRouterCreateKeyResponse

    @DELETE("api/v1/keys/{id}")
    suspend fun revokeKey(@Header("Authorization") auth: String, @Path("id") id: String): Response<Unit>
}

fun buildOpenRouterApi(): OpenRouterApi = Retrofit.Builder()
    .baseUrl(OPENROUTER_BASE_URL)
    .addConverterFactory(GsonConverterFactory.create())
    .build()
    .create(OpenRouterApi::class.java)
