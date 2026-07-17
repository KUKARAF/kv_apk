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
import retrofit2.http.Query

const val BASE_URL = "https://kv.osmosis.page/"

data class ApprovalItem(
    val id: String,
    @SerializedName("api_key_label") val apiKeyLabel: String,
    @SerializedName("requested_at") val requestedAt: String,
    @SerializedName("expires_at") val expiresAt: String,
    val key: String? = null,
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
    @SerializedName("allowed_keys") val allowedKeys: List<String> = emptyList(),
    @SerializedName("expires_at") val expiresAt: String? = null,
    @SerializedName("last_used") val lastUsed: String? = null,
)

data class CreateKeyResponse(
    val key: String,
    val id: String,
)

data class KvEntryItem(
    val key: String,
    val value: String? = null,
    val zt: Boolean = false,
    @SerializedName("device_encrypted") val deviceEncrypted: Boolean = false,
)

data class DeviceItem(
    val id: String,
    val name: String,
    @SerializedName("created_at") val registeredAt: String,
    @SerializedName("key_type") val keyType: String? = null,
    @SerializedName("public_key") val publicKey: String? = null,
)

data class DeviceKvRecipient(
    @SerializedName("key_type") val keyType: String,
    @SerializedName("ephemeral_pub") val ephemeralPub: String,
    @SerializedName("dek_nonce") val dekNonce: String,
    @SerializedName("encrypted_dek") val encryptedDek: String,
)

data class DeviceKvPayload(
    val nonce: String,
    val ciphertext: String,
    val aad: String,
    val recipient: DeviceKvRecipient,
)

data class DeviceKvRecipientRequest(
    @SerializedName("device_id") val deviceId: String,
    @SerializedName("key_type") val keyType: String,
    @SerializedName("ephemeral_pub") val ephemeralPub: String,
    @SerializedName("dek_nonce") val dekNonce: String,
    @SerializedName("encrypted_dek") val encryptedDek: String,
)

data class ReEncryptRequest(
    val key: String,
    val nonce: String,
    val ciphertext: String,
    val aad: String,
    val recipients: List<DeviceKvRecipientRequest>,
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
    val value: String,
    val ttl: Int? = null,
    val sliding: Boolean = false,
    @SerializedName("open_access") val openAccess: Boolean = false,
    @SerializedName("one_time") val oneTime: Boolean = false,
    val approval: Boolean = false,
    @SerializedName("zero_trust") val zeroTrust: Boolean = false,
)

data class CreateApiKeyRequest(
    val label: String,
    val type: String,
    @SerializedName("expires_at") val expiresAt: String? = null,
    @SerializedName("allowed_keys") val allowedKeys: List<String> = emptyList(),
)

data class CreateShareRequest(
    @SerializedName("kv_key") val kvKey: String,
    val ciphertext: String,
    val nonce: String,
    @SerializedName("expires_in_hours") val expiresInHours: Double? = null,
)

data class CreateShareResponse(val id: String)

data class DeviceRegistrationRequest(
    val name: String,
    @SerializedName("public_key") val publicKey: String,
    @SerializedName("key_type") val keyType: String? = null,
)

data class DeviceRegistrationResponse(val id: String)

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

    @POST("api/admin/shares")
    suspend fun createShare(@Body body: CreateShareRequest): CreateShareResponse

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

    @GET("api/admin/session-requests/{id}")
    suspend fun getSessionRequest(@Path("id") id: String): SessionRequestDetails

    @POST("api/admin/session-requests/{id}/approve")
    suspend fun approveSessionRequest(
        @Path("id") id: String,
        @Body body: ApproveSessionRequestBody,
    ): Response<Unit>

    @POST("api/admin/session-requests/{id}/reject")
    suspend fun rejectSessionRequest(@Path("id") id: String): Response<Unit>

    @POST("api/devices")
    suspend fun registerDevice(@Body body: DeviceRegistrationRequest): Response<DeviceRegistrationResponse>

    @GET("api/admin/devices/{deviceId}/kv/{kvKey}")
    suspend fun getDeviceKv(
        @Path("deviceId") deviceId: String,
        @Path("kvKey") kvKey: String,
    ): DeviceKvPayload

    @POST("api/admin/kv/device")
    suspend fun setDeviceKvEntry(@Body body: ReEncryptRequest): Response<Unit>
}

data class ApproveSessionRequestBody(
    @SerializedName("approved_duration_hours") val approvedDurationHours: Long,
)

data class SessionRequestDetails(
    val id: String,
    val label: String? = null,
    val status: String,
    @SerializedName("requested_at") val requestedAt: String,
    @SerializedName("expires_at") val expiresAt: String,
    @SerializedName("requested_duration_hours") val requestedDurationHours: Long? = null,
)

data class CreateSessionRequestBody(
    val label: String? = null,
    @SerializedName("requested_duration_hours") val requestedDurationHours: Long? = null,
)

data class CreateSessionRequestResponse(
    val id: String,
    val url: String,
    @SerializedName("expires_at") val expiresAt: String,
    // Secret held only by this requester; required to poll for the session token.
    @SerializedName("poll_secret") val pollSecret: String,
)

data class SessionRequestStatus(
    val status: String,
    @SerializedName("session_token") val sessionToken: String? = null,
)

interface KvUnauthApi {
    @POST("api/session-request")
    suspend fun createSessionRequest(@Body body: CreateSessionRequestBody): CreateSessionRequestResponse

    @GET("api/session-request/{id}/status")
    suspend fun pollStatus(
        @Path("id") id: String,
        @Query("secret") secret: String,
    ): SessionRequestStatus
}

fun buildUnauthApi(): KvUnauthApi = Retrofit.Builder()
    .baseUrl(BASE_URL)
    .addConverterFactory(GsonConverterFactory.create())
    .build()
    .create(KvUnauthApi::class.java)

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

