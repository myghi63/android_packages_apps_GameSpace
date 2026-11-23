/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package io.chaldeaprjkt.gamespace.thermal

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.SystemProperties
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import java.io.File

/**
 * Applies Xiaomi thermal scenes (mi_thermald sconfig) based on the foreground app,
 * following the GameSpace game list and each app's configured game mode.
 * Scene values were extracted from this device's decrypted thermal map:
 * writing them also switches the charging scene (mca_smartchg), so anything
 * unknown or idle must restore the default scene.
 * Nodes and scene values come from [DeviceConfig]; only this file knows them.
 */
class ThermalUtils private constructor(private val context: Context) {

    companion object {
        private const val TAG = "ThermalUtils"
        private const val PROP_AUTO = "persist.sys.gamespace.thermal.auto"

        /** Game list owned by the GameSpace framework, "pkg=mode" entries. */
        const val SETTINGS_GAME_LIST = "gamespace_game_list"

        /** Apps the GameSpace framework refused to auto-add. */
        const val SETTINGS_GAME_LIST_DENIED = "gamespace_denied_list"

        /** Switch that lets apps declaring FLAG_IS_GAME join the list automatically. */
        const val SETTINGS_GAME_LIST_AUTO = "gamespace_auto_game_detect"

        /** Switch that applies the gaming scene to the game list. Default on. */
        const val SETTINGS_THERMAL_GAMING = "onyx_thermal_gaming"

        /** Switch that applies the gaming scene whatever the foreground app is.
         * Cleared on boot, so it never survives a reboot.
         */
        const val SETTINGS_THERMAL_ALWAYS = "onyx_thermal_always"

        /** Switch that lets the service touch the thermal scene at all. Default on.
         * Turning it off restores the boot scene once and leaves sconfig alone:
         * the scene is then managed by hand.
         */
        const val SETTINGS_THERMAL_MANAGEMENT = "gamespace_thermal_management"

        // GameManager game modes stored in the list entry
        // (GameManager.GAME_MODE_STANDARD / _PERFORMANCE / _BATTERY_SAVER).
        private const val DEFAULT_MODE = 1
        private const val MODE_PERFORMANCE = 2
        private const val MODE_BATTERY_SAVER = 3

        @Volatile
        private var sInstance: ThermalUtils? = null

        @JvmStatic
        fun getInstance(context: Context): ThermalUtils =
            sInstance ?: synchronized(this) {
                sInstance ?: ThermalUtils(context.applicationContext).also { sInstance = it }
            }

        @JvmStatic
        fun startService(context: Context) {
            if (!SystemProperties.getBoolean(PROP_AUTO, true)) return
            val config = DeviceConfig.getInstance()
            if (!config.hasThermal) return
            context.startService(Intent(context, ThermalService::class.java))
        }
    }

    private val profile = DeviceConfig.getInstance().profile.thermal

    private fun writeScene(scene: Int): Boolean {
        return try {
            val file = File(profile.node)
            val current = file.readText().trim().toIntOrNull()
            if (current != scene) {
                file.writeText("$scene\n")
            }
            true
        } catch (e: Exception) {
            Log.w(TAG, "Cannot write scene $scene", e)
            false
        }
    }

    fun writeDefault(): Boolean = writeScene(profile.defaultScene)

    fun writeSceneFor(packageName: String): Boolean = writeScene(sceneFor(packageName))

    private fun sceneFor(packageName: String): Int {
        if (isThermalAlwaysEnabled()) return profile.standard

        if (packageName.isEmpty()) return profile.defaultScene

        if (isDefaultDialer(packageName)) return profile.phone

        if (isCameraApp(packageName)) return profile.camera
        if (isGame(packageName) && isGamingProfileEnabled()) {
            return sceneForMode(gameModes()[packageName] ?: DEFAULT_MODE)
        }

        return profile.defaultScene
    }

    private fun sceneForMode(mode: Int): Int = when (mode) {
        MODE_PERFORMANCE -> profile.performance
        MODE_BATTERY_SAVER -> profile.batterySaver
        else -> profile.standard
    }

    // android.telecom.DefaultDialerManager is @hide in a mainline module (telecomm)
    // and invisible to this module's Kotlin classpath; the default dialer is the
    // ROLE_DIALER holder, which wins ACTION_DIAL resolution, so resolve instead.
    private fun isDefaultDialer(packageName: String): Boolean =
        Intent(Intent.ACTION_DIAL, Uri.parse("tel:"))
            .resolveActivity(context.packageManager)?.packageName == packageName

    private fun isCameraApp(packageName: String): Boolean = packageName in cameraApps

    private val cameraApps: Set<String> by lazy {
        val pm = context.packageManager
        val actions = listOf(
            MediaStore.ACTION_IMAGE_CAPTURE,
            MediaStore.ACTION_IMAGE_CAPTURE_SECURE,
            MediaStore.ACTION_VIDEO_CAPTURE,
        )
        actions.flatMap { action ->
            pm.queryIntentActivities(Intent(action), 0)
                .map { it.activityInfo.packageName }
        }.toSet()
    }

    private val gameFlags = HashMap<String, Boolean>()

    private fun hasGameFlag(packageName: String): Boolean =
        gameFlags.getOrPut(packageName) {
            try {
                val appInfo = context.packageManager.getApplicationInfo(packageName, 0)
                (appInfo.flags and ApplicationInfo.FLAG_IS_GAME) != 0
            } catch (e: Exception) {
                false
            }
        }

    /**
     * Membership of the game list, which is what decides the gaming scene and
     * the bypass: the GameSpace list (authoritative, carries each app's mode),
     * plus apps declaring FLAG_IS_GAME while the framework auto-detect switch is
     * on. A removal from the GameSpace UI writes the denied list, and that beats
     * every membership source.
     */
    fun isGame(packageName: String): Boolean =
        !isDenied(packageName) && (
            gameModes().containsKey(packageName) ||
            (isAutoGameEnabled() && hasGameFlag(packageName))
        )

    private fun isAutoGameEnabled(): Boolean =
        Settings.System.getInt(context.contentResolver, SETTINGS_GAME_LIST_AUTO, 1) == 1

    /** Whether the gaming scene follows the game list. Default on. */
    fun isGamingProfileEnabled(): Boolean =
        Settings.System.getInt(context.contentResolver, SETTINGS_THERMAL_GAMING, 1) == 1

    /** Whether the automatic thermal management writes sconfig at all. Default on. */
    fun isThermalManagementEnabled(): Boolean =
        Settings.System.getInt(context.contentResolver, SETTINGS_THERMAL_MANAGEMENT, 1) == 1

    /** Whether the gaming scene is forced whatever the foreground app is. */
    fun isThermalAlwaysEnabled(): Boolean =
        Settings.System.getInt(context.contentResolver, SETTINGS_THERMAL_ALWAYS, 0) == 1

    /** Drops the forced scene: it is a one-shot request for the current session. */
    fun clearAlwaysProfile() {
        Settings.System.putInt(context.contentResolver, SETTINGS_THERMAL_ALWAYS, 0)
    }

    /** pkg -> configured game mode, straight from the framework's list entry. */
    private fun gameModes(): Map<String, Int> =
        parseEntries(Settings.System.getString(context.contentResolver, SETTINGS_GAME_LIST))

    private fun isDenied(packageName: String): Boolean =
        parseEntries(Settings.System.getString(context.contentResolver, SETTINGS_GAME_LIST_DENIED))
            .containsKey(packageName)

    /** "pkg=mode;pkg=mode" -> pkg to mode; bare "pkg" entries read as the standard mode. */
    private fun parseEntries(raw: String?): Map<String, Int> {
        if (raw.isNullOrEmpty()) return emptyMap()
        val result = HashMap<String, Int>()
        for (entry in raw.split(';')) {
            val parts = entry.split('=', limit = 2)
            val pkg = parts[0].trim()
            if (pkg.isEmpty()) continue
            val mode = parts.getOrNull(1)?.trim()?.toIntOrNull() ?: DEFAULT_MODE
            result[pkg] = mode
        }
        return result
    }
}
