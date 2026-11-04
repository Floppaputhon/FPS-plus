package com.redmagic.fps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppOrderingTest {
    @Test fun installedEntriesFollowUserPriorityBeforeOtherApps() {
        val apps = listOf(
            AppEntry("some.other", "Alpha", detectedGame = true), AppEntry("com.mojang.minecraftpe", "Minecraft"),
            AppEntry("com.roblox.client", "Roblox"), AppEntry("com.droiddeck.launcher", "DroidDeck"),
            AppEntry("com.winlator", "Winlator"), AppEntry("com.xiaoji.egggame", "GameHub")
        )
        val ordered = AppOrdering.merge(apps, emptyList(), emptySet(), null)
        assertEquals(listOf("GameHub", "Winlator", "DroidDeck", "Roblox", "Minecraft", "Alpha"), ordered.map { it.name })
        assertEquals(listOf("GameHub", "Winlator", "DroidDeck"), ordered.take(3).map { it.name })
    }

    @Test fun variantsAndUserChoiceAreRecognizedWithoutGuessingOtherPackages() {
        assertEquals(1, AppOrdering.priority(AppEntry("custom.win", "Winlator Frost"), null))
        assertEquals(2, AppOrdering.priority(AppEntry("custom.deck", "Droid Deck"), null))
        assertEquals(0, AppOrdering.priority(AppEntry("custom.hub", "Launcher"), "custom.hub"))
        assertEquals(5, AppOrdering.priority(AppEntry("not.deck", "Calculator"), null))
    }

    @Test fun savedGamesAreDeduplicatedAndHiddenGamesStayHidden() {
        val app = AppEntry("game", "Game", detectedGame = true)
        val missing = AppEntry("missing", "Missing", custom = true)
        val ordered = AppOrdering.merge(listOf(app, app, AppEntry("hidden", "Hidden")), listOf(app.copy(custom = true), missing), setOf("hidden"), null)
        assertEquals(listOf("game", "missing"), ordered.map { it.pkg })
        assertTrue(ordered.first().custom)
        assertTrue(ordered.first().available)
        assertFalse(ordered.last().available)
    }

    @Test fun excludesOrdinaryAppsButKeepsRecognizedAndManuallyAddedGames() {
        val gallery = AppEntry("gallery", "Gallery")
        val detected = AppEntry("game", "Arcade", detectedGame = true)
        val unclassified = AppEntry("unclassified", "My game")
        val ordered = AppOrdering.merge(listOf(gallery, detected, unclassified), listOf(unclassified.copy(custom = true)), emptySet(), null)
        assertEquals(listOf("game", "unclassified"), ordered.map { it.pkg })
        assertTrue(ordered.first().detectedGame)
        assertTrue(ordered.last().custom)
        assertFalse(ordered.any { it.pkg == "gallery" })
    }
}
