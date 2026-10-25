package com.carecircle.data

import com.carecircle.BuildConfig
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.http.GET
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

interface CareApi {
    @GET("api/medicine-info") suspend fun medicineInfo(@Query("salt") salt: String): MedicineInfoResponse
}

object ApiFactory {
    val json = Json { ignoreUnknownKeys = true; isLenient = true }

    val api: CareApi by lazy {
        val client = OkHttpClient.Builder()
            .callTimeout(30, TimeUnit.SECONDS)
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
