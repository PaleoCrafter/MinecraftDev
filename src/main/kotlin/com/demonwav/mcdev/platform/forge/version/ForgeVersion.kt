/*
 * Minecraft Dev for IntelliJ
 *
 * https://minecraftdev.org
 *
 * Copyright (c) 2017 minecraft-dev
 *
 * MIT License
 */

package com.demonwav.mcdev.platform.forge.version

import com.demonwav.mcdev.util.sortVersions
import com.google.gson.Gson
import java.io.IOException
import java.net.URL
import java.util.ArrayList

class ForgeVersion private constructor(private val map: Map<*, *>) {

    val sortedMcVersions: List<String> by lazy {
        sortVersions((map["mcversion"] as Map<*, *>).keys)
    }

    fun getRecommended(versions: List<String>): String {
        var recommended = "1.7"
        for (version in versions) {
            getPromo(version) ?: continue

            if (recommended < version) {
                recommended = version
            }
        }

        return recommended
    }

    fun getPromo(version: String): Double? {
        val promos = map["promos"] as? Map<*, *>
        if (promos != null) {
            return promos[version + "-recommended"] as? Double
        }
        return null
    }

    fun getForgeVersions(version: String): List<String> {
        val list = ArrayList<String>()
        val numbers = map["number"] as? Map<*, *>
        numbers?.forEach { _, v ->
            if (v is Map<*, *>) {
                val number = v
                val currentVersion = number["mcversion"] as? String

                if (currentVersion == version) {
                    list.add(number["version"] as? String ?: return@forEach)
                }
            }
        }
        return list
    }

    fun getFullVersion(version: String): String? {
        val numbers = map["number"] as? Map<*, *> ?: return null
        val parts = version.split(".").dropLastWhile(String::isEmpty).toTypedArray()
        val versionSmall = parts.last()
        val number = numbers[versionSmall] as? Map<*, *> ?: return null

        val branch = number["branch"] as? String
        val mcVersion = number["mcversion"] as? String ?: return null
        val finalVersion = number["version"] as? String ?: return null

        if (branch == null) {
            return "$mcVersion-$finalVersion"
        } else {
            return "$mcVersion-$finalVersion-$branch"
        }
    }

    companion object {
        fun downloadData(): ForgeVersion? {
            try {
                val text = URL("https://files.minecraftforge.net/maven/net/minecraftforge/forge/json").readText()

                val map = Gson().fromJson(text, Map::class.java)
                val forgeVersion = ForgeVersion(map)
                forgeVersion.sortedMcVersions // sort em up
                return forgeVersion
            } catch (e: IOException) {
                e.printStackTrace()
            }
            return null
        }
    }
}
