/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package io.chaldeaprjkt.gamespace.charge

import io.chaldeaprjkt.gamespace.R

/**
 * Quick settings tile for "Bypass while gaming": toggles the persistent game
 * list switch. Actual bypass requests it enables are still guarded by
 * ThermalService when they are made.
 */
class BypassGamingTileService : BypassToggleTileService() {

    override val settingKey = BypassChargeUtils.SETTING_BYPASS_GAMING
    override val defaultValue = 1
    override val labelRes = R.string.bypass_gaming_title
}
