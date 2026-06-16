package dev.kv.apk.data

import com.google.gson.annotations.SerializedName
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

const val BASE_URL = "https://kv.osmosis.page/"

data class ApprovalItem(
    val id: String,
    @SerializedName("api_key_label") val apiKeyLabel: String,
    @SerializedName("requested_at") val requestedAt: String,
    @SerializedName("expires_at") val expiresAt: String,
    val key: String? = null,
    val scope: String? = null,
    val requester: String? = null,
    val ip: String? = null,
)

data class ApproveRequest(val confirm: String)

data class EmojiEntry(val e: String, val n: String)

data class ApiKeyItem(
    val id: String,
    val label: String,
    @SerializedName("key_type") val keyType: String,
    val status: String,
    val scopes: List<ScopeItem>,
    @SerializedName("expires_at") val expiresAt: String? = null,
    @SerializedName("last_used") val lastUsed: String? = null,
)

data class ScopeItem(
    val scope: String,
    val ops: String,
)

data class CreateKeyResponse(
    val key: String,
    val id: String,
)

data class KvEntryItem(
    val key: String,
    val value: String,
    val scope: String,
    val zt: Boolean = false,
)

data class DeviceItem(
    val id: String,
    val name: String,
    @SerializedName("registered_at") val registeredAt: String,
)

data class HardwareKeyItem(
    val id: String,
    val label: String,
    @SerializedName("cred_id") val credId: String,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("last_used") val lastUsed: String? = null,
)

data class BlockedIpItem(
    val id: String,
    val ip: String,
    val failures: Int,
    @SerializedName("blocked_at") val blockedAt: String,
    @SerializedName("last_seen") val lastSeen: String,
)

data class RateLimitRow(
    val ip: String,
    val count: Int,
)

data class AccessLogEntry(
    val time: String,
    val ip: String,
    val op: String,
    val key: String,
    @SerializedName("key_id") val keyId: String,
)

data class RateLimitsResponse(
    val rows: List<RateLimitRow>,
    @SerializedName("access_log") val accessLog: List<AccessLogEntry>,
)

data class SessionInfo(
    val email: String,
    val subject: String,
    @SerializedName("expires_at") val expiresAt: String,
    @SerializedName("created_at") val createdAt: String,
)

data class CreateKvRequest(
    val key: String,
    val scope: String,
    val value: String,
    val ttl: Int? = null,
    val sliding: Boolean = false,
    @SerializedName("open_access") val openAccess: Boolean = false,
    @SerializedName("one_time") val oneTime: Boolean = false,
    val approval: Boolean = false,
    @SerializedName("zero_trust") val zeroTrust: Boolean = false,
)

data class ScopeRule(
    val scope: String,
    val ops: List<String>,
)

data class CreateApiKeyRequest(
    val label: String,
    val type: String,
    @SerializedName("expires_at") val expiresAt: String? = null,
    val scopes: List<ScopeRule> = emptyList(),
)

data class DeviceRegistrationRequest(
    val name: String,
    @SerializedName("public_key") val publicKey: String,
)

data class DeviceRegistrationResponse(
    val token: String,
)

interface KvApi {
    @GET("api/admin/approvals")
    suspend fun listApprovals(): List<ApprovalItem>

    @POST("api/admin/approvals/{id}/approve")
    suspend fun approve(@Path("id") id: String, @Body body: ApproveRequest): Response<Unit>

    @POST("api/admin/approvals/{id}/reject")
    suspend fun reject(@Path("id") id: String): Response<Unit>

    @GET("api/admin/keys")
    suspend fun listKeys(): List<ApiKeyItem>

    @POST("api/admin/keys")
    suspend fun createKey(@Body body: CreateApiKeyRequest): Response<CreateKeyResponse>

    @DELETE("api/admin/keys/{id}")
    suspend fun revokeKey(@Path("id") id: String): Response<Unit>

    @GET("api/admin/kv")
    suspend fun listKvEntries(): List<KvEntryItem>

    @POST("api/admin/kv")
    suspend fun setKvEntry(@Body body: CreateKvRequest): Response<Unit>

    @DELETE("api/admin/kv/{key}")
    suspend fun deleteKvEntry(@Path("key") key: String): Response<Unit>

    @GET("api/admin/devices")
    suspend fun listDevices(): List<DeviceItem>

    @DELETE("api/admin/devices/{id}")
    suspend fun deleteDevice(@Path("id") id: String): Response<Unit>

    @GET("api/admin/hardware-keys")
    suspend fun listHardwareKeys(): List<HardwareKeyItem>

    @DELETE("api/admin/hardware-keys/{id}")
    suspend fun deleteHardwareKey(@Path("id") id: String): Response<Unit>

    @GET("api/admin/rate-limits/blocked")
    suspend fun listBlockedIps(): List<BlockedIpItem>

    @POST("api/admin/rate-limits/blocked/{ip}/unblock")
    suspend fun unblockIp(@Path("ip") ip: String): Response<Unit>

    @GET("api/admin/rate-limits")
    suspend fun getRateLimits(): RateLimitsResponse

    @GET("api/admin/session")
    suspend fun getSession(): SessionInfo

    @POST("api/admin/session-key")
    suspend fun createSessionKey(): Response<CreateKeyResponse>

    @DELETE("api/admin/session-key")
    suspend fun revokeSessionKey(): Response<Unit>
}

interface DeviceRegistrationApi {
    @POST("api/device/register")
    suspend fun register(@Body body: DeviceRegistrationRequest): DeviceRegistrationResponse
}

fun buildApi(token: String): KvApi {
    val client = OkHttpClient.Builder()
        .addInterceptor(Interceptor { chain ->
            val request = chain.request().newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
            chain.proceed(request)
        })
        .build()

    return Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(KvApi::class.java)
}

fun buildRegistrationApi(): DeviceRegistrationApi =
    Retrofit.Builder()
        .baseUrl(BASE_URL)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(DeviceRegistrationApi::class.java)
