package com.sharkking.assistant.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteCacheTest {
    private val base = "https://xxz-xyzw-res.hortorgames.com/remote/launcher/import/dd/"

    @Test fun `带哈希的 CDN 资源可缓存`() {
        assertTrue(RemoteCache.isCacheable(base + "dde836a7-a909-4f4c-ad83-4c2ffdac097d.6776e.json"))
        assertTrue(RemoteCache.isCacheable(base + "native/ab/abc.1a2b3.png"))
    }

    @Test fun `无哈希、带参数、其他域名不缓存`() {
        assertFalse(RemoteCache.isCacheable(base + "config.json"))
        assertFalse(RemoteCache.isCacheable(base + "a.6776e.json?t=1"))
        assertFalse(RemoteCache.isCacheable("https://xxz-xyzw.hortorgames.com/login/manifest.6776e.json"))
        assertFalse(RemoteCache.isCacheable("not a url"))
    }

    @Test fun `缓存键稳定且区分地址`() {
        val a = RemoteCache.keyOf(base + "a.11111.json")
        assertEquals(a, RemoteCache.keyOf(base + "a.11111.json"))
        assertNotEquals(a, RemoteCache.keyOf(base + "a.22222.json"))
        assertEquals(40, a.length)
    }

    @Test fun `MIME 推断`() {
        assertEquals("application/json", RemoteCache.mimeOf("x.1a2b3.json"))
        assertEquals("image/png", RemoteCache.mimeOf("x.1a2b3.PNG"))
        assertEquals("application/javascript", RemoteCache.mimeOf("x.1a2b3.js"))
    }
}
