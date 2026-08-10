package com.delizioso.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SharedLinkTest {

    @Test
    fun `pulls the link out of Instagram's share blurb`() {
        assertEquals(
            "https://www.instagram.com/reel/Dam-vrmB1PI/",
            SharedLink.extract("Guarda questo reel\nhttps://www.instagram.com/reel/Dam-vrmB1PI/"),
        )
    }

    @Test
    fun `keeps query strings intact`() {
        assertEquals(
            "https://www.instagram.com/reel/Dam-vrmB1PI/?igsh=MTIncXpvQ==",
            SharedLink.extract("Look https://www.instagram.com/reel/Dam-vrmB1PI/?igsh=MTIncXpvQ== nice"),
        )
    }

    @Test
    fun `accepts a bare url`() {
        assertEquals("https://vm.tiktok.com/ZGeabc123/", SharedLink.extract("https://vm.tiktok.com/ZGeabc123/"))
    }

    @Test
    fun `trims trailing sentence punctuation`() {
        assertEquals("https://example.com/recipe", SharedLink.extract("Try this: https://example.com/recipe."))
        assertEquals("https://example.com/recipe", SharedLink.extract("(https://example.com/recipe)"))
    }

    @Test
    fun `shares without a link yield null`() {
        assertNull(SharedLink.extract("just some text about pasta"))
    }
}
