package com.example.data.remote.cache

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import okhttp3.CacheControl
import okhttp3.Interceptor
import okhttp3.Response
import java.util.concurrent.TimeUnit

/**
 * Interceptors for OkHttp HTTP Disk Cache:
 *
 * 1. [OfflineCacheInterceptor] (Application Interceptor via addInterceptor):
 *    - Detects network availability before hitting the network.
 *    - When offline, forces OkHttp to serve from the HTTP disk cache with max-stale.
 *    - Never intercepts non-GET requests or streaming audio.
 *
 * 2. [NetworkCacheInterceptor] (Network Interceptor via addNetworkInterceptor):
 *    - Rewrites wire response headers so read-only GET endpoints are cached in OkHttp's disk cache
 *      even when the backend server omits Cache-Control headers.
 *    - Respects server explicit "no-store" or "private" directives.
 *    - Honors conditional caching headers (ETag, Last-Modified, 304 Not Modified).
 */
class OfflineCacheInterceptor(private val context: Context) : Interceptor {

    private fun isNetworkAvailable(): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val network = cm?.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (_: Exception) {
            true // fallback to true on permission check issues
        }
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        var request = chain.request()

        // Only handle GET / read-only requests
        if (!request.method.equals("GET", ignoreCase = true)) {
            return chain.proceed(request)
        }

        // Streaming audio is handled exclusively by Media3 AudioCache
        if (request.url.encodedPath.contains("/stream")) {
            return chain.proceed(request)
        }

        // When offline, instruct OkHttp to serve from disk cache if available
        if (!isNetworkAvailable()) {
            val offlineCacheControl = CacheControl.Builder()
                .onlyIfCached()
                .maxStale(7, TimeUnit.DAYS)
                .build()
            request = request.newBuilder()
                .cacheControl(offlineCacheControl)
                .build()
        }

        return chain.proceed(request)
    }
}

class NetworkCacheInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)

        // Only cache GET / read-only requests
        if (!request.method.equals("GET", ignoreCase = true)) {
            return response
        }

        // Streaming audio is never stored in OkHttp cache
        if (request.url.encodedPath.contains("/stream")) {
            return response
        }

        val existingHeader = response.header("Cache-Control")
        // Respect explicit server directives for private/no-store
        if (existingHeader != null && (existingHeader.contains("no-store", ignoreCase = true) || existingHeader.contains("private", ignoreCase = true))) {
            return response
        }

        // If the server didn't specify a Cache-Control max-age header, supply a sensible default
        if (existingHeader.isNullOrBlank() || !existingHeader.contains("max-age", ignoreCase = true)) {
            val path = request.url.encodedPath
            val maxAgeSeconds = when {
                path.contains("/search") -> 60        // 1 minute for search results
                path.contains("/songs") -> 300        // 5 minutes for general songs catalog
                path.contains("/playlists") -> 180    // 3 minutes for playlist endpoints
                path.contains("/artists") || path.contains("/albums") -> 1800 // 30 minutes for artist/album metadata
                else -> 120                           // 2 minutes for general read-only endpoints
            }

            return response.newBuilder()
                .removeHeader("Pragma") // Remove HTTP 1.0 no-cache directive if present
                .header("Cache-Control", "public, max-age=$maxAgeSeconds")
                .build()
        }

        return response
    }
}

/**
 * Unified alias for backwards compatibility.
 */
class OkHttpCacheInterceptor(private val context: Context) : Interceptor {
    private val offline = OfflineCacheInterceptor(context)
    override fun intercept(chain: Interceptor.Chain): Response = offline.intercept(chain)
}
