package app.pagedrop.update

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/** The newest GitHub release: its version and where its APK and checksum live. */
data class ReleaseInfo(
    val version: AppVersion,
    val tag: String,
    val apkName: String,
    val apkUrl: String,
    val sha256Url: String,
)

object ReleaseParser {
    /** Release assets must come from this repository's own release downloads, over HTTPS. */
    const val ASSET_PREFIX = "https://github.com/spandan-kumar/PageDrop/releases/download/"

    /** Null when the JSON isn't a usable release (draft, prerelease, bad tag, missing or foreign assets). */
    fun parse(json: String): ReleaseInfo? {
        val root = runCatching { Json.parseToJsonElement(json) as? JsonObject }.getOrNull() ?: return null
        fun flag(name: String) = runCatching { root[name]?.jsonPrimitive?.boolean }.getOrNull() == true
        if (flag("draft") || flag("prerelease")) return null
        val tag = root["tag_name"]?.jsonPrimitive?.contentOrNull ?: return null
        val version = AppVersion.parse(tag) ?: return null
        val assets = (root["assets"] as? JsonArray)?.mapNotNull { it as? JsonObject } ?: return null
        fun url(a: JsonObject) = a["browser_download_url"]?.jsonPrimitive?.contentOrNull
        fun name(a: JsonObject) = a["name"]?.jsonPrimitive?.contentOrNull
        val apk = assets.firstOrNull { name(it)?.endsWith(".apk") == true } ?: return null
        val apkName = name(apk) ?: return null
        val sha = assets.firstOrNull { name(it) == "$apkName.sha256" } ?: return null
        val apkUrl = url(apk)?.takeIf { it.startsWith(ASSET_PREFIX) } ?: return null
        val shaUrl = url(sha)?.takeIf { it.startsWith(ASSET_PREFIX) } ?: return null
        return ReleaseInfo(version, tag, apkName, apkUrl, shaUrl)
    }

    /**
     * Reads the hash from `sha256sum` output ("<hex>  <file name>"; a lone hex string also works). Null when there is
     * no 64-digit hash, or the line names a different file than [apkName].
     */
    fun parseSha256(text: String, apkName: String): String? {
        val line = text.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() } ?: return null
        val hash = line.substringBefore(' ').substringBefore('\t').lowercase()
        if (hash.length != 64 || !hash.all { it in '0'..'9' || it in 'a'..'f' }) return null
        val file = line.substring(hash.length).trim().removePrefix("*")
        if (file.isNotEmpty() && file != apkName) return null
        return hash
    }
}
