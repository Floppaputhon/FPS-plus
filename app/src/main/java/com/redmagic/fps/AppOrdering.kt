package com.redmagic.fps

import java.util.Locale

/** A launcher entry; nothing here queries, transmits, or persists the list. */
internal data class AppEntry(
    val pkg: String,
    val name: String,
    val custom: Boolean = false,
    val gameHub: Boolean = false,
    val available: Boolean = true,
    val detectedGame: Boolean = false,
)

internal object AppOrdering {
    fun priority(app: AppEntry, selectedGameHub: String?): Int {
        val name = app.name.lowercase(Locale.ROOT).filter { it.isLetterOrDigit() }
        val pkg = app.pkg.lowercase(Locale.ROOT)
        return when {
            (selectedGameHub != null && pkg == selectedGameHub.lowercase(Locale.ROOT)) ||
                pkg == "com.xiaoji.egggame" || name.startsWith("gamehub") || name.startsWith("gamefusion") -> 0
            pkg == "com.winlator" || name.startsWith("winlator") -> 1
            pkg == "com.droiddeck.launcher" || name.startsWith("droiddeck") -> 2
            pkg == "com.roblox.client" || name == "roblox" -> 3
            pkg == "com.mojang.minecraftpe" || name.startsWith("minecraft") -> 4
            else -> 5
        }
    }

    /** The launcher list takes precedence; saved entries that are no longer launchable remain available in the full library. */
    fun merge(installed: List<AppEntry>, saved: List<AppEntry>, hidden: Set<String>, selectedGameHub: String?): List<AppEntry> {
        val savedByPackage = saved.associateBy { it.pkg }
        val unique = installed.distinctBy { it.pkg }.filterNot { it.pkg in hidden }.filter {
            it.detectedGame || it.pkg in savedByPackage || priority(it, selectedGameHub) < 5
        }
        val installedPackages = unique.mapTo(mutableSetOf()) { it.pkg }
        val merged = unique.map { it.copy(custom = it.pkg in savedByPackage, available = true) } +
            saved.filter { it.pkg !in installedPackages && it.pkg !in hidden }.distinctBy { it.pkg }.map { it.copy(custom = true, available = false) }
        return merged.map { it.copy(gameHub = priority(it, selectedGameHub) == 0) }
            .sortedWith(compareBy<AppEntry>({ !it.available }, { priority(it, selectedGameHub) }, { it.name.lowercase(Locale.ROOT) }, { it.pkg }))
    }
}
