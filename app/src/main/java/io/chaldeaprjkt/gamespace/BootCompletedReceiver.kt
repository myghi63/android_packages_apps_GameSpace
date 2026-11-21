/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package io.chaldeaprjkt.gamespace

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.chaldeaprjkt.gamespace.charge.BypassChargeUtils
import io.chaldeaprjkt.gamespace.thermal.ThermalUtils

class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // The forced bypass and the forced thermal scene are requests for the current
        // session only: they must not survive a reboot.
        BypassChargeUtils.getInstance(context).clearAlwaysRequest()
        ThermalUtils.getInstance(context).clearAlwaysProfile()
        ThermalUtils.startService(context)
    }
}
