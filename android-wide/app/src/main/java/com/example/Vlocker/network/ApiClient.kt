package com.example.Vlocker.network

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {
    // 에뮬레이터 → 현재 맥북 FastAPI 서버 접속 기본 주소
    private const val DEFAULT_BASE_URL = "http://10.0.2.2:8000/"

    @Volatile
    private var currentBaseUrl: String = DEFAULT_BASE_URL

    @Volatile
    private var cachedService: VlockerApiService? = null

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    val service: VlockerApiService
        get() = cachedService ?: synchronized(this) {
            cachedService ?: buildService(currentBaseUrl).also { cachedService = it }
        }

    // 필요 시 실차/팀원 PC IP 등으로 서버 주소 변경 가능
    fun normalizeBaseUrl(rawUrl: String): String {
        val trimmed = rawUrl.trim().ifEmpty { DEFAULT_BASE_URL }
        val withScheme = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else {
            "http://$trimmed"
        }
        return if (withScheme.endsWith("/")) withScheme else "$withScheme/"
    }

    fun updateBaseUrl(newUrl: String): Boolean {
        val normalized = normalizeBaseUrl(newUrl)
        return runCatching {
            val built = buildService(normalized)
            synchronized(this) {
                currentBaseUrl = normalized
                cachedService = built
            }
        }.isSuccess
    }

    private fun buildService(baseUrl: String): VlockerApiService {
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(VlockerApiService::class.java)
    }
}