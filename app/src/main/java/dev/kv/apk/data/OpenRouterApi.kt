package dev.kv.apk.data

import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path

private const val OPENROUTER_BASE_URL = "https://openrouter.ai/"

data class OpenRouterKeyData(
    val hash: String? = null,
    val id: String? = null,
    val name: String,
    val disabled: Boolean = false,
    val limit: Double? = null,
    @SerializedName("limit_reset") val limitReset: String? = null,
) {
    // Same hash/id ambiguity kv_cli's providers/openrouter.rs flags — prefer hash, fall back to id.
    val keyId: String get() = hash ?: id ?: ""
}

data class OpenRouterListResponse(val data: List<OpenRouterKeyData>)

data class OpenRouterGetResponse(val data: OpenRouterKeyData)

data class OpenRouterCreateKeyRequest(val name: String, val limit: Double? = null)

data class OpenRouterCreateKeyResponse(
    val data: OpenRouterKeyData,
    val key: String,
)

// Per OpenRouter's docs, limit_reset is documented on the update (PATCH) endpoint only,
// not on create — applying a default reset cadence takes a create-then-patch sequence.
data class OpenRouterUpdateKeyRequest(@SerializedName("limit_reset") val limitReset: String?)

interface OpenRouterApi {
    @GET("api/v1/keys")
    suspend fun listKeys(@Header("Authorization") auth: String): OpenRouterListResponse

    @GET("api/v1/keys/{id}")
    suspend fun getKey(
        @Header("Authorization") auth: String,
        @Path("id") id: String,
    ): OpenRouterGetResponse

    @POST("api/v1/keys")
    suspend fun createKey(
        @Header("Authorization") auth: String,
        @Body body: OpenRouterCreateKeyRequest,
    ): OpenRouterCreateKeyResponse

    @PATCH("api/v1/keys/{id}")
    suspend fun updateKey(
        @Header("Authorization") auth: String,
        @Path("id") id: String,
        @Body body: OpenRouterUpdateKeyRequest,
    ): Response<Unit>

    @DELETE("api/v1/keys/{id}")
    suspend fun revokeKey(@Header("Authorization") auth: String, @Path("id") id: String): Response<Unit>
}

fun buildOpenRouterApi(): OpenRouterApi = Retrofit.Builder()
    .baseUrl(OPENROUTER_BASE_URL)
    .addConverterFactory(GsonConverterFactory.create())
    .build()
    .create(OpenRouterApi::class.java)
