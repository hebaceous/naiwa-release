package com.sharkking.assistant.core

import android.content.Context
import android.util.Log
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * 游戏 CDN 资源的持久缓存，边玩边存。
 *
 * 只缓存文件名里带内容哈希的资源（如 xxx.6776e.json），这类地址内容永不变化，
 * 游戏更新时会换新地址，所以命中后可以直接用，不必再问服务器。
 * 存在 filesDir 而不是 cacheDir，系统清理垃圾时不会被删。
 */
object RemoteCache {
    private const val TAG = "再攀-资源缓存"
    private const val HOST = "xxz-xyzw-res.hortorgames.com"
    private const val PREFS = "res_cache"
    private const val K_ON = "enabled"
    private const val MAX_BYTES = 2L * 1024 * 1024 * 1024

    /** 文件名末尾的 5~8 位十六进制哈希段 */
    private val HASHED = Regex("""\.[0-9a-f]{5,8}\.[A-Za-z0-9]+$""")

    @Volatile private var dir: File? = null
    @Volatile var enabled = true
        private set

    /** 已用空间的内存计数。-1 表示还没统计，首次写入时才遍历一次目录 */
    private val used = java.util.concurrent.atomic.AtomicLong(-1)

    fun init(ctx: Context) {
        dir = File(ctx.filesDir, "res_cache").apply { mkdirs() }
        enabled = ctx.getSharedPreferences(PREFS, 0).getBoolean(K_ON, true)
    }

    private fun usedBytes(): Long {
        if (used.get() < 0) used.compareAndSet(-1, sizeBytes())
        return used.get()
    }

    fun setEnabled(ctx: Context, on: Boolean) {
        enabled = on
        ctx.getSharedPreferences(PREFS, 0).edit().putBoolean(K_ON, on).apply()
    }

    fun sizeBytes(): Long = dir?.walkTopDown()?.filter { it.isFile }?.sumOf { it.length() } ?: 0L

    fun clear() {
        dir?.listFiles()?.forEach { it.deleteRecursively() }
        used.set(0)
    }

    /** 纯函数，便于单测：判定一个地址是否值得缓存 */
    fun isCacheable(url: String): Boolean {
        val u = runCatching { URL(url) }.getOrNull() ?: return false
        if (u.host != HOST) return false
        if (!u.query.isNullOrEmpty()) return false
        return HASHED.containsMatchIn(u.path)
    }

    fun keyOf(url: String): String =
        MessageDigest.getInstance("SHA-1").digest(url.toByteArray())
            .joinToString("") { "%02x".format(it) }

    fun mimeOf(url: String): String = when (url.substringAfterLast('.').lowercase()) {
        "json" -> "application/json"
        "js", "jsc" -> "application/javascript"
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "webp" -> "image/webp"
        "mp3" -> "audio/mpeg"
        "ogg" -> "audio/ogg"
        "wav" -> "audio/wav"
        "ttf" -> "font/ttf"
        "plist", "atlas", "txt", "fnt" -> "text/plain"
        else -> "application/octet-stream"
    }

    /** WebViewClient.shouldInterceptRequest 里调用；返回 null 表示交还 WebView 自己处理 */
    fun intercept(req: WebResourceRequest): WebResourceResponse? {
        if (!enabled || req.method != "GET") return null
        if (req.requestHeaders.keys.any { it.equals("Range", true) }) return null
        val url = req.url.toString()
        if (!isCacheable(url)) return null
        val d = dir ?: return null

        val f = File(d, keyOf(url).let { "${it.take(2)}/$it" })
        val bytes = if (f.exists()) f.readBytes() else download(url, f) ?: return null
        return respond(url, bytes)
    }

    private fun download(url: String, out: File): ByteArray? = runCatching {
        val c = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10000
            readTimeout = 20000
        }
        try {
            if (c.responseCode != 200) return null
            val b = c.inputStream.use { it.readBytes() }
            if (usedBytes() + b.size < MAX_BYTES) {
                out.parentFile?.mkdirs()
                val tmp = File(out.path + ".tmp")
                tmp.writeBytes(b)
                if (tmp.renameTo(out)) used.addAndGet(b.size.toLong())
            }
            b
        } finally {
            c.disconnect()
        }
    }.onFailure { Log.w(TAG, "下载失败 $url: ${it.message}") }.getOrNull()

    private fun respond(url: String, bytes: ByteArray): WebResourceResponse {
        // 页面跑在本地 http 源上，跨域头缺了 fetch 会直接失败，上次黑屏就是这个原因
        val headers = mapOf(
            "Access-Control-Allow-Origin" to "*",
            "Cache-Control" to "max-age=31536000, immutable",
            "Content-Length" to bytes.size.toString(),
        )
        val mime = mimeOf(url)
        val enc = if (mime.startsWith("application/json") || mime.startsWith("text/") ||
            mime == "application/javascript") "utf-8" else null
        return WebResourceResponse(mime, enc, 200, "OK", headers, ByteArrayInputStream(bytes))
    }
}
