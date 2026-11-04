package com.carecircle.data

import com.carecircle.BuildConfig
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import com.carecircle.domain.PolicySummary
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

@Serializable data class Source(val name: String, val url: String? = null)

@Serializable
data class MedicineCard(
    val generic: String,
    val rxcui: String? = null,
    val whyYouTakeIt: String = "",
    val howToTake: String = "",
    val commonSideEffects: List<String> = emptyList(),
    val callDoctorIf: List<String> = emptyList(),
    val keyInteractions: List<String> = emptyList(),
    val sources: List<Source> = emptyList(),
    val summarizedBy: String = "extract",
    val disclaimer: String = "Informational only, not medical advice.",
)

@Serializable data class MedicineInfoResponse(val cards: List<MedicineCard> = emptyList(), val unresolved: List<String> = emptyList())

@Serializable
data class InteractionPair(val a: String, val b: String, val snippet: String, val source: String, val sourceUrl: String? = null)

@Serializable
data class InteractionResponse(
    val pairs: List<InteractionPair> = emptyList(),
    val unresolved: List<String> = emptyList(),
    val message: String = "",
    val disclaimer: String = "",
)

@Serializable data class PolicyPageText(val page: Int, val text: String)
@Serializable data class PolicyRequest(val pages: List<PolicyPageText>)

@Serializable
data class PolicyResponse(
    val summary: PolicySummary = PolicySummary(),
    val unverified: List<String> = emptyList(),
    val mode: String = "",
    val disclaimer: String = "",
)

@Serializable data class PolicyQuestionRequest(val question: String, val clauses: List<com.carecircle.domain.ClauseDto>)

@Serializable
data class CitationDto(val page: Int = 0, val clause: String? = null, val quote: String = "", val clauseRef: String = "", val excerpt: String = "")

@Serializable
data class PolicyQuestionResponse(
    val answer: String = "UNCLEAR",
    val explanation: String = "",
    val citations: List<CitationDto> = emptyList(),
    val confidence: Double = 0.0,
    val mode: String = "",
    val disclaimer: String = "",
)

@Serializable data class NotifyMissedRequest(val linkId: String, val slotId: String, val slotLabel: String)
@Serializable data class NotifyMissedResponse(val sent: Boolean = false, val reason: String = "")

interface CareApi {
    @POST("api/notify-missed") suspend fun notifyMissed(@retrofit2.http.Header("Authorization") auth: String, @Body req: NotifyMissedRequest): NotifyMissedResponse
    @POST("api/policy-question") suspend fun policyQuestion(@Body req: PolicyQuestionRequest): PolicyQuestionResponse
    @POST("api/policy-summary") suspend fun policySummary(@Body req: PolicyRequest): PolicyResponse
    @GET("api/interactions") suspend fun interactions(@Query("salts") salts: String): InteractionResponse
    @GET("api/medicine-info") suspend fun medicineInfo(@Query("salt") salt: String): MedicineInfoResponse
}

object ApiFactory {
    val json = Json { ignoreUnknownKeys = true; isLenient = true }

    val api: CareApi by lazy {
        val client = OkHttpClient.Builder()
            .callTimeout(60, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val b = chain.request().newBuilder()
                if (BuildConfig.APP_KEY.isNotEmpty()) b.header("x-app-key", BuildConfig.APP_KEY)
                chain.proceed(b.build())
            }.build()
        Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build().create(CareApi::class.java)
    }
}
