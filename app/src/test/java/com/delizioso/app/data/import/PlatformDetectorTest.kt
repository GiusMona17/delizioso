package com.delizioso.app.data.import

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlatformDetectorTest {

    @Test
    fun `detects instagram reel and post`() {
        assertEquals(Platform.INSTAGRAM, PlatformDetector.detect("https://www.instagram.com/reel/CxAbCd12345/"))
        assertEquals(Platform.INSTAGRAM, PlatformDetector.detect("https://instagram.com/p/Chunk8-jurw/"))
        assertEquals(Platform.INSTAGRAM, PlatformDetector.detect("https://www.instagram.com/reels/AbCdEf12345/"))
        assertEquals("Chunk8-jurw", PlatformDetector.instagramCode("https://www.instagram.com/p/Chunk8-jurw/"))
    }

    @Test
    fun `detects tiktok including short links`() {
        assertEquals(Platform.TIKTOK, PlatformDetector.detect("https://www.tiktok.com/@chef/video/6718335390845095173"))
        assertEquals(Platform.TIKTOK, PlatformDetector.detect("https://vm.tiktok.com/ZM1234567/"))
        assertEquals(Platform.TIKTOK, PlatformDetector.detect("https://vt.tiktok.com/AbCdEf/"))
    }

    @Test
    fun `detects youtube forms`() {
        assertEquals(Platform.YOUTUBE, PlatformDetector.detect("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertEquals(Platform.YOUTUBE, PlatformDetector.detect("https://youtu.be/dQw4w9WgXcQ"))
        assertEquals(Platform.YOUTUBE, PlatformDetector.detect("https://www.youtube.com/shorts/dQw4w9WgXcQ"))
        assertEquals("dQw4w9WgXcQ", PlatformDetector.youtubeId("https://youtu.be/dQw4w9WgXcQ"))
    }

    @Test
    fun `detects facebook`() {
        assertEquals(Platform.FACEBOOK, PlatformDetector.detect("https://www.facebook.com/watch/?v=123456789"))
        assertEquals(Platform.FACEBOOK, PlatformDetector.detect("https://fb.watch/AbCdEfGh/"))
        assertEquals(Platform.FACEBOOK, PlatformDetector.detect("https://www.facebook.com/reel/123456789"))
        // Share-redirect links (/share/r|v|p/<id>) are how the app's share sheet sends reels.
        assertEquals(Platform.FACEBOOK, PlatformDetector.detect("https://www.facebook.com/share/r/1BXXmnVkag/"))
        assertEquals(Platform.FACEBOOK, PlatformDetector.detect("https://www.facebook.com/share/v/1BXXmnVkag/"))
    }

    @Test
    fun `generic http urls are blogs`() {
        assertEquals(Platform.BLOG, PlatformDetector.detect("https://www.bbcgoodfood.com/recipes/spaghetti-puttanesca"))
        assertNull(PlatformDetector.detect("not a url"))
    }
}
