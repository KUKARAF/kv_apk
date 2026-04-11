package dev.kv.apk.data

import com.google.gson.annotations.SerializedName
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

const val BASE_URL = "https://kv.osmosis.page/"

data class ApprovalItem(
    val id: String,
    @SerializedName("api_key_label") val apiKeyLabel: String,
    @SerializedName("emoji_sequence") val emojiSequence: String,
    @SerializedName("requested_at") val requestedAt: String,
    @SerializedName("expires_at") val expiresAt: String,
)

interface KvApi {
    @GET("api/admin/approvals")
    suspend fun listApprovals(): List<ApprovalItem>

    @POST("api/admin/approvals/{id}/approve")
    suspend fun approve(@Path("id") id: String): Response<Unit>

    @POST("api/admin/approvals/{id}/reject")
    suspend fun reject(@Path("id") id: String): Response<Unit>
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
