package dev.kv.apk.data

import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
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

// WebAuthn-gated two-step device registration (replaces the old single-shot POST api/devices).
// Step 1 issues a WebAuthn assertion challenge bound to the account's existing passkey; step 2
// submits the signed assertion and, on success, persists the new device.
data class DeviceRegisterBeginRequest(
    val name: String,
    @SerializedName("public_key") val publicKey: String,
    @SerializedName("key_type") val keyType: String,
)

data class DeviceRegisterBeginResponse(
    @SerializedName("challenge_id") val challengeId: String,
    // Opaque PublicKeyCredentialRequestOptions (WebAuthn auth challenge) to feed the platform
    // Credential Manager / FIDO2 API. Kept as raw JSON — the app does not model its internals.
    val options: JsonObject,
)

data class DeviceRegisterFinishRequest(
    @SerializedName("challenge_id") val challengeId: String,
    // Raw WebAuthn assertion (PublicKeyCredential) produced by the platform authenticator.
    val assertion: JsonObject,
)

data class DeviceRegisterFinishResponse(val id: String)

data class ManagementKeyRow(
    val id: String,
    val provider: String,
    val label: String,
    val status: String,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("last_used_at") val lastUsedAt: String? = null,
    @SerializedName("default_limit") val defaultLimit: Double? = null,
    @SerializedName("default_limit_reset") val defaultLimitReset: String? = null,
)

data class CreateManagementKeyRequest(
    val provider: String,
    val label: String,
    val nonce: String,
    val ciphertext: String,
    val aad: String,
    val recipients: List<DeviceKvRecipientRequest>,
    @SerializedName("default_limit") val defaultLimit: Double? = null,
    @SerializedName("default_limit_reset") val defaultLimitReset: String? = null,
)

data class UpdateManagementKeyDefaultsRequest(
    @SerializedName("default_limit") val defaultLimit: Double?,
    @SerializedName("default_limit_reset") val defaultLimitReset: String?,
)

data class CreateManagementKeyResponse(val id: String)

data class ProvisionedKeyRow(
    val id: String,
    val provider: String,
    @SerializedName("provider_key_id") val providerKeyId: String,
    val label: String,
    val status: String,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("revoked_at") val revokedAt: String? = null,
)

data class CreateProvisionedKeyRequest(
    @SerializedName("provider_key_id") val providerKeyId: String,
    val label: String,
    val nonce: String,
    val ciphertext: String,
    val aad: String,
    val recipients: List<DeviceKvRecipientRequest>,
)

data class CreateProvisionedKeyResponse(val id: String)

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

    @POST("api/devices/register/begin")
    suspend fun deviceRegisterBegin(@Body body: DeviceRegisterBeginRequest): DeviceRegisterBeginResponse

    @POST("api/devices/register/finish")
    suspend fun deviceRegisterFinish(@Body body: DeviceRegisterFinishRequest): DeviceRegisterFinishResponse

    @GET("api/admin/devices/{deviceId}/kv/{kvKey}")
    suspend fun getDeviceKv(
        @Path("deviceId") deviceId: String,
        @Path("kvKey") kvKey: String,
    ): DeviceKvPayload

    @POST("api/admin/kv/device")
    suspend fun setDeviceKvEntry(@Body body: ReEncryptRequest): Response<Unit>

    @POST("api/admin/management-keys")
    suspend fun createManagementKey(@Body body: CreateManagementKeyRequest): Response<CreateManagementKeyResponse>

    @GET("api/admin/management-keys")
    suspend fun listManagementKeys(): List<ManagementKeyRow>

    @GET("api/admin/management-keys/{id}/devices/{deviceId}")
    suspend fun getManagementKeyEnvelope(
        @Path("id") id: String,
        @Path("deviceId") deviceId: String,
    ): DeviceKvPayload

    @POST("api/admin/management-keys/{id}/revoke")
    suspend fun revokeManagementKey(@Path("id") id: String): Response<Unit>

    @PATCH("api/admin/management-keys/{id}")
    suspend fun updateManagementKeyDefaults(
        @Path("id") id: String,
        @Body body: UpdateManagementKeyDefaultsRequest,
    ): Response<Unit>

    @POST("api/admin/management-keys/{id}/provisioned-keys")
    suspend fun createProvisionedKey(
        @Path("id") id: String,
        @Body body: CreateProvisionedKeyRequest,
    ): Response<CreateProvisionedKeyResponse>

    @GET("api/admin/management-keys/{id}/provisioned-keys")
    suspend fun listProvisionedKeys(@Path("id") id: String): List<ProvisionedKeyRow>

    @GET("api/admin/management-keys/{id}/provisioned-keys/{pkId}/devices/{deviceId}")
    suspend fun getProvisionedKeyEnvelope(
        @Path("id") id: String,
        @Path("pkId") pkId: String,
        @Path("deviceId") deviceId: String,
    ): DeviceKvPayload

    // Hard delete — called after the key has already been deleted on the provider, so the
    // stored encrypted copy is no longer recoverable-but-useless.
    @DELETE("api/admin/management-keys/{id}/provisioned-keys/{pkId}")
    suspend fun deleteProvisionedKey(
        @Path("id") id: String,
        @Path("pkId") pkId: String,
    ): Response<Unit>
}

data class ApproveSessionRequestBody(
    @SerializedName("approved_duration_hours") val approvedDurationHours: Long,
    @SerializedName("approval_token") val approvalToken: String,
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
    // Required: uuid of a device already registered on the account. The approved session token
    // is delivered ECDH-wrapped to this device's public key; the server 404s if it's unknown.
    @SerializedName("device_id") val deviceId: String,
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
    // Present only when status == "approved": the session token ECDH-wrapped to the requesting
    // device's public key (same envelope scheme as device-encrypted KV -> DeviceKvPayload).
    // Subsequent polls after first delivery return status "delivered" with a null envelope.
    val envelope: DeviceKvPayload? = null,
    // Present while status != "approved" (idempotent, re-fetchable each poll): a device-KV
    // envelope wrapping the one-time approval token the requester shows the approver. Same
    // envelope scheme as [envelope]; decrypt with the device private key (AAD = request id).
    @SerializedName("approval_envelope") val approvalEnvelope: DeviceKvPayload? = null,
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

