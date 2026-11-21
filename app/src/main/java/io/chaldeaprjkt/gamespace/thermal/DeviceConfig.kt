/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package io.chaldeaprjkt.gamespace.thermal

import android.os.SystemProperties
import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import java.io.File

/**
 * Device profile for the thermal/bypass backends. Device-specific paths and
 * values live here so the engine code stays generic: a device tree ships
 * /vendor/etc/gamespace/<ro.product.device>.json and reuses everything else.
 * When no profile file exists, the built-in onyx profile is used.
 */

data class ThermalProfile(
    val node: String = "",
    @SerializedName("default") val defaultScene: Int = -1,
    val phone: Int = -1,
    val camera: Int = -1,
    val standard: Int = -1,
    val performance: Int = -1,
    @SerializedName("battery_saver") val batterySaver: Int = -1,
)

data class BypassProfile(
    val node: String = "",
    val client: String = "gamespace",
)

data class DeviceProfile(
    val thermal: ThermalProfile = ThermalProfile(),
    val bypass: BypassProfile = BypassProfile(),
)

class DeviceConfig private constructor(val profile: DeviceProfile, val hasThermal: Boolean) {

    companion object {
        private const val TAG = "DeviceConfig"
        private const val CONFIG_DIR = "/vendor/etc/gamespace"

        @Volatile
        private var sInstance: DeviceConfig? = null

        @JvmStatic
        fun getInstance(): DeviceConfig =
            sInstance ?: synchronized(this) {
                sInstance ?: load().also { sInstance = it }
            }

        private fun load(): DeviceConfig {
            val device = SystemProperties.get("ro.product.device", "")
            val file = File("$CONFIG_DIR/$device.json")
            if (file.isFile) {
                try {
                    val profile = Gson().fromJson(file.readText(), DeviceProfile::class.java)
                    Log.i(TAG, "Loaded device profile from ${file.path}")
                    return DeviceConfig(profile, canWrite(profile.thermal.node))
                } catch (e: Exception) {
                    Log.w(TAG, "Cannot parse ${file.path}", e)
                }
            }
            val onyx = DeviceProfile(
                thermal = ThermalProfile(
                    node = "/sys/class/thermal/thermal_message/sconfig",
                    defaultScene = 19,  // MGAME, boot default (keeps fast charging)
                    phone = 5,
                    camera = 15,
                    standard = 18,     // TGAME
                    performance = 25,  // XINGTIE
                    batterySaver = 7,  // CLASS0
                ),
                bypass = BypassProfile(
                    node = "/sys/class/xm_power/charger/charge_interface/charge_enable",
                    client = "onyx_parts",
                ),
            )
            return DeviceConfig(onyx, canWrite(onyx.thermal.node))
        }

        private fun canWrite(path: String): Boolean =
            path.isNotEmpty() && File(path).canWrite()
    }
}
