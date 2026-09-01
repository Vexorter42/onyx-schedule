package com.vexorter.onyx.data.remote

import android.content.Context
import kotlinx.serialization.json.Json
import okhttp3.Cache
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

object NetworkModule {

    fun json(): Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
        isLenient = true
    }

    fun okHttpClient(context: Context): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .cache(Cache(context.cacheDir.resolve("http"), 4L * 1024 * 1024))
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("User-Agent", "RucSchedule/1.0 (Android)")
                .build()
            chain.proceed(request)
        }
        .build()
}
