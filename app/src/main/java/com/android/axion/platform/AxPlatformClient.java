/*
 * Copyright (C) 2025-2026 AxionOS / crDroid Android Project
 * Adapted for LineageOS: this file is a vendored in-app replacement for the
 * ax_platform_hooks SDK client. The public API surface (constants, Listener,
 * toggle/getState/addListener/removeListener) is kept identical so upstream
 * GameSpace call sites remain untouched. The SystemUI binder backend is
 * replaced with direct feature controllers built on standard platform APIs.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.axion.platform;

import android.app.ActivityManager;
import android.app.NotificationManager;
import android.app.UiModeManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.database.ContentObserver;
import android.hardware.SensorPrivacyManager;
import android.hardware.camera2.CameraManager;
import android.hardware.display.ColorDisplayManager;
import android.net.ConnectivityManager;
import android.net.NetworkPolicyManager;
import android.nfc.NfcAdapter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.Process;
import android.os.SystemClock;
import android.os.UserHandle;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.WindowManager;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

public class AxPlatformClient {
    private static final String TAG = "AxPlatformClient";

    public static final String FEATURE_WIFI = "wifi";
    public static final String FEATURE_MOBILE_DATA = "mobile_data";
    public static final String FEATURE_BLUETOOTH = "bluetooth";
    public static final String FEATURE_HOTSPOT = "hotspot";
    public static final String FEATURE_FLASHLIGHT = "flashlight";
    public static final String FEATURE_LOCATION = "location";
    public static final String FEATURE_ROTATION = "rotation";
    public static final String FEATURE_BATTERY_SAVER = "battery_saver";
    public static final String FEATURE_ZEN = "zen";
    public static final String FEATURE_AOD = "aod";
    public static final String FEATURE_DATA_SAVER = "data_saver";
    public static final String FEATURE_AIRPLANE_MODE = "airplane_mode";
    public static final String FEATURE_NFC = "nfc";
    public static final String FEATURE_DARK_MODE = "dark_mode";
    public static final String FEATURE_NIGHT_LIGHT = "night_light";
    public static final String FEATURE_COLOR_INVERSION = "color_inversion";
    public static final String FEATURE_COLOR_CORRECTION = "color_correction";
    public static final String FEATURE_REDUCE_BRIGHTNESS = "reduce_brightness";
    public static final String FEATURE_ONE_HANDED_MODE = "one_handed_mode";
    public static final String FEATURE_HEADS_UP = "heads_up";
    public static final String FEATURE_AUTO_SYNC = "auto_sync";
    public static final String FEATURE_CAMERA_PRIVACY = "camera_privacy";
    public static final String FEATURE_MIC_PRIVACY = "mic_privacy";
    public static final String FEATURE_USB_TETHER = "usb_tether";
    public static final String FEATURE_DREAM = "dream";
    public static final String FEATURE_CAFFEINE = "caffeine";
    public static final String FEATURE_SCREENSHOT = "screenshot";

    public static final String SETTING_NIGHT_DISPLAY = "night_display_activated";
    public static final String SETTING_REDUCE_BRIGHT = "reduce_bright_colors_activated";
    public static final String SETTING_ONE_HANDED = "one_handed_mode_enabled";

    public static final int TILE_STATE_UNAVAILABLE = 0;
    public static final int TILE_STATE_INACTIVE = 1;
    public static final int TILE_STATE_ACTIVE = 2;

    private static final long CAFFEINE_DURATION_MS = 5L * 60 * 1000;

    private static final Map<String, String> FEATURE_TO_CATEGORY = new HashMap<>();

    static {
        FEATURE_TO_CATEGORY.put(FEATURE_WIFI, "connectivity");
        FEATURE_TO_CATEGORY.put(FEATURE_MOBILE_DATA, "connectivity");
        FEATURE_TO_CATEGORY.put(FEATURE_BLUETOOTH, "connectivity");
        FEATURE_TO_CATEGORY.put(FEATURE_HOTSPOT, "connectivity");
        FEATURE_TO_CATEGORY.put(FEATURE_AIRPLANE_MODE, "connectivity");
        FEATURE_TO_CATEGORY.put(FEATURE_NFC, "connectivity");
        FEATURE_TO_CATEGORY.put(FEATURE_USB_TETHER, "connectivity");
        FEATURE_TO_CATEGORY.put(FEATURE_DATA_SAVER, "connectivity");
        FEATURE_TO_CATEGORY.put(FEATURE_DARK_MODE, "display");
        FEATURE_TO_CATEGORY.put(FEATURE_NIGHT_LIGHT, "display");
        FEATURE_TO_CATEGORY.put(FEATURE_COLOR_INVERSION, "display");
        FEATURE_TO_CATEGORY.put(FEATURE_COLOR_CORRECTION, "display");
        FEATURE_TO_CATEGORY.put(FEATURE_REDUCE_BRIGHTNESS, "display");
        FEATURE_TO_CATEGORY.put(FEATURE_ROTATION, "display");
        FEATURE_TO_CATEGORY.put(FEATURE_AOD, "display");
        FEATURE_TO_CATEGORY.put(FEATURE_ZEN, "sound");
        FEATURE_TO_CATEGORY.put(FEATURE_HEADS_UP, "sound");
        FEATURE_TO_CATEGORY.put(FEATURE_CAMERA_PRIVACY, "privacy");
        FEATURE_TO_CATEGORY.put(FEATURE_MIC_PRIVACY, "privacy");
        FEATURE_TO_CATEGORY.put(FEATURE_BATTERY_SAVER, "power");
        FEATURE_TO_CATEGORY.put(FEATURE_FLASHLIGHT, "power");
        FEATURE_TO_CATEGORY.put(FEATURE_CAFFEINE, "system");
        FEATURE_TO_CATEGORY.put(FEATURE_LOCATION, "system");
        FEATURE_TO_CATEGORY.put(FEATURE_AUTO_SYNC, "system");
        FEATURE_TO_CATEGORY.put(FEATURE_DREAM, "system");
        FEATURE_TO_CATEGORY.put(FEATURE_SCREENSHOT, "system");
    }

    public static int getTileState(Bundle state) {
        return state.getInt("tileState", TILE_STATE_INACTIVE);
    }

    public static String getLabel(Bundle state) {
        return state.getString("label");
    }

    public static String getSecondaryLabel(Bundle state) {
        return state.getString("secondaryLabel");
    }

    public abstract static class Listener {
        public void onFeatureChanged(String feature, boolean active) {}
        public void onStateChanged(String key, Bundle state) {}
    }

    private static volatile AxPlatformClient sInstance;

    private Context mContext;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final CopyOnWriteArrayList<Listener> mListeners = new CopyOnWriteArrayList<>();
    private final Map<String, Feature> mFeatures = new LinkedHashMap<>();

    private AxPlatformClient() {}

    public static AxPlatformClient getInstance() {
        if (sInstance == null) {
            synchronized (AxPlatformClient.class) {
                if (sInstance == null) {
                    sInstance = new AxPlatformClient();
                }
            }
        }
        return sInstance;
    }

    public synchronized void init(Context context) {
        if (mContext != null) return;
        mContext = context.getApplicationContext();
        buildFeatures();
        registerObservers();
    }

    public boolean isAvailable() {
        return mContext != null;
    }

    public boolean isSupported(String feature) {
        return mFeatures.containsKey(feature);
    }

    public String[] getSupportedFeatures() {
        return mFeatures.keySet().toArray(new String[0]);
    }

    public void toggle(String feature) {
        Feature f = mFeatures.get(feature);
        if (f == null) {
            Log.w(TAG, "toggle: unsupported feature " + feature);
            return;
        }
        try {
            f.toggle();
            notifyFeature(feature, f.isActive());
        } catch (Exception e) {
            Log.e(TAG, "toggle " + feature, e);
        }
    }

    public void setEnabled(String feature, boolean enabled) {
        Feature f = mFeatures.get(feature);
        if (f == null) return;
        try {
            f.setEnabled(enabled);
            notifyFeature(feature, f.isActive());
        } catch (Exception e) {
            Log.e(TAG, "setEnabled " + feature, e);
        }
    }

    public Bundle getState(String feature) {
        Feature f = mFeatures.get(feature);
        if (f == null) return Bundle.EMPTY;
        Bundle b = new Bundle();
        boolean active;
        try {
            active = f.isActive();
        } catch (Exception e) {
            Log.e(TAG, "getState " + feature, e);
            return Bundle.EMPTY;
        }
        b.putBoolean("active", active);
        b.putInt("tileState", active ? TILE_STATE_ACTIVE : TILE_STATE_INACTIVE);
        return b;
    }

    public Bundle getAllStates() {
        Bundle all = new Bundle();
        for (String feature : mFeatures.keySet()) {
            Bundle state = getState(feature);
            if (!state.isEmpty()) all.putBundle(feature, state);
        }
        return all;
    }

    public boolean isFeatureActive(String feature) {
        return getState(feature).getBoolean("active", false);
    }

    public void addListener(Listener listener) {
        if (!mListeners.contains(listener)) mListeners.add(listener);
    }

    public void removeListener(Listener listener) {
        mListeners.remove(listener);
    }

    private void notifyFeature(String feature, boolean active) {
        Bundle state = getState(feature);
        mHandler.post(() -> {
            for (Listener l : mListeners) {
                l.onStateChanged(feature, state);
                l.onFeatureChanged(feature, active);
            }
        });
    }

    private void notifySetting(String feature, String uriName) {
        Feature f = mFeatures.get(feature);
        if (f == null) return;
        try {
            notifyFeature(feature, f.isActive());
        } catch (Exception e) {
            Log.e(TAG, "notifySetting " + feature, e);
        }
    }

    // Feature implementations -------------------------------------------------

    private interface Feature {
        boolean isActive() throws Exception;
        void setEnabled(boolean enabled) throws Exception;
        default void toggle() throws Exception { setEnabled(!isActive()); }
    }

    private void buildFeatures() {
        ContentResolver cr = mContext.getContentResolver();

        mFeatures.put(FEATURE_WIFI, new Feature() {
            public boolean isActive() {
                return Settings.Global.getInt(cr, Settings.Global.WIFI_ON, 0) != 0;
            }
            public void setEnabled(boolean enabled) {
                Settings.Global.putInt(cr, Settings.Global.WIFI_ON, enabled ? 1 : 0);
            }
        });

        mFeatures.put(FEATURE_MOBILE_DATA, new Feature() {
            public boolean isActive() {
                TelephonyManager tm = mContext.getSystemService(TelephonyManager.class);
                return tm != null && tm.isDataEnabled();
            }
            public void setEnabled(boolean enabled) {
                TelephonyManager tm = mContext.getSystemService(TelephonyManager.class);
                if (tm != null) tm.setDataEnabled(enabled);
            }
        });

        BluetoothAdapter adapter = null;
        BluetoothManager bm = mContext.getSystemService(BluetoothManager.class);
        if (bm != null) adapter = bm.getAdapter();
        if (adapter != null) {
            final BluetoothAdapter bt = adapter;
            mFeatures.put(FEATURE_BLUETOOTH, new Feature() {
                public boolean isActive() { return bt.isEnabled(); }
                @SuppressWarnings("deprecation")
                public void setEnabled(boolean enabled) {
                    if (enabled) bt.enable(); else bt.disable();
                }
            });
        }

        ConnectivityManager cm = mContext.getSystemService(ConnectivityManager.class);
        android.net.TetheringManager tm = (android.net.TetheringManager)
                mContext.getSystemService(Context.TETHERING_SERVICE);
        android.net.wifi.WifiManager wifi = (android.net.wifi.WifiManager)
                mContext.getSystemService(Context.WIFI_SERVICE);
        if (cm != null && tm != null) {
            final ConnectivityManager.OnStartTetheringCallback tetherCb =
                    new ConnectivityManager.OnStartTetheringCallback() {
                        @Override public void onTetheringStarted() {}
                        @Override public void onTetheringFailed() {}
                    };
            mFeatures.put(FEATURE_HOTSPOT, new Feature() {
                // Same state source as Settings' TetherEnabler.
                public boolean isActive() { return wifi != null && wifi.isWifiApEnabled(); }
                public void setEnabled(boolean enabled) {
                    try {
                        if (enabled) cm.startTethering(ConnectivityManager.TETHERING_WIFI,
                                false, tetherCb);
                        else cm.stopTethering(ConnectivityManager.TETHERING_WIFI);
                    } catch (Exception ignored) {}
                }
            });
            mFeatures.put(FEATURE_USB_TETHER, new Feature() {
                public boolean isActive() { return isUsbTethered(tm); }
                public void setEnabled(boolean enabled) {
                    try {
                        if (enabled) cm.startTethering(ConnectivityManager.TETHERING_USB,
                                false, tetherCb);
                        else cm.stopTethering(ConnectivityManager.TETHERING_USB);
                    } catch (Exception ignored) {}
                }
            });
        }

        CameraManager camera = mContext.getSystemService(CameraManager.class);
        if (camera != null) {
            mFeatures.put(FEATURE_FLASHLIGHT, new Feature() {
                // A16 has no torch-status query: the tile tracks what it toggled.
                private final boolean[] lit = {false};
                private String firstIdWithTorch() {
                    try {
                        for (String id : camera.getCameraIdList()) {
                            if (Boolean.TRUE.equals(camera.getCameraCharacteristics(id)
                                    .get(android.hardware.camera2.CameraCharacteristics
                                            .FLASH_INFO_AVAILABLE))) {
                                return id;
                            }
                        }
                    } catch (Exception ignored) {}
                    return null;
                }
                public boolean isActive() { return lit[0]; }
                public void setEnabled(boolean enabled) {
                    String id = firstIdWithTorch();
                    if (id == null) return;
                    try {
                        camera.setTorchMode(id, enabled);
                        lit[0] = enabled;
                    } catch (Exception ignored) {}
                }
            });
        }

        mFeatures.put(FEATURE_LOCATION, new Feature() {
            public boolean isActive() {
                return Settings.Secure.getIntForUser(cr, Settings.Secure.LOCATION_MODE,
                        Settings.Secure.LOCATION_MODE_OFF, UserHandle.myUserId())
                        != Settings.Secure.LOCATION_MODE_OFF;
            }
            public void setEnabled(boolean enabled) {
                com.android.settingslib.Utils.updateLocationEnabled(mContext, enabled,
                        UserHandle.myUserId(), Settings.Secure.LOCATION_CHANGER_SYSTEM_SETTINGS);
            }
        });

        mFeatures.put(FEATURE_ROTATION, new Feature() {
            public boolean isActive() {
                return Settings.System.getIntForUser(cr,
                        Settings.System.ACCELEROMETER_ROTATION, 0, UserHandle.USER_CURRENT) == 1;
            }
            public void setEnabled(boolean enabled) {
                Settings.System.putIntForUser(cr,
                        Settings.System.ACCELEROMETER_ROTATION, enabled ? 1 : 0,
                        UserHandle.USER_CURRENT);
            }
        });

        PowerManager pm = mContext.getSystemService(PowerManager.class);
        if (pm != null) {
            mFeatures.put(FEATURE_BATTERY_SAVER, new Feature() {
                public boolean isActive() { return pm.isPowerSaveMode(); }
                public void setEnabled(boolean enabled) { pm.setPowerSaveModeEnabled(enabled); }
            });
            mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "GameSpace:caffeine");
        }

        NotificationManager nm = mContext.getSystemService(NotificationManager.class);
        if (nm != null) {
            mFeatures.put(FEATURE_ZEN, new Feature() {
                public boolean isActive() {
                    return nm.getCurrentInterruptionFilter()
                            != NotificationManager.INTERRUPTION_FILTER_ALL;
                }
                public void setEnabled(boolean enabled) {
                    nm.setInterruptionFilter(enabled
                            ? NotificationManager.INTERRUPTION_FILTER_NONE
                            : NotificationManager.INTERRUPTION_FILTER_ALL);
                }
            });
        }

        mFeatures.put(FEATURE_AOD, new Feature() {
            public boolean isActive() {
                return Settings.Secure.getIntForUser(cr, Settings.Secure.DOZE_ALWAYS_ON,
                        0, UserHandle.USER_CURRENT) == 1;
            }
            public void setEnabled(boolean enabled) {
                Settings.Secure.putIntForUser(cr, Settings.Secure.DOZE_ALWAYS_ON,
                        enabled ? 1 : 0, UserHandle.USER_CURRENT);
            }
        });

        NetworkPolicyManager npm = NetworkPolicyManager.from(mContext);
        if (npm != null) {
            mFeatures.put(FEATURE_DATA_SAVER, new Feature() {
                public boolean isActive() { return npm.getRestrictBackground(); }
                public void setEnabled(boolean enabled) { npm.setRestrictBackground(enabled); }
            });
        }

        mFeatures.put(FEATURE_AIRPLANE_MODE, new Feature() {
            public boolean isActive() {
                return Settings.Global.getInt(cr, Settings.Global.AIRPLANE_MODE_ON, 0) != 0;
            }
            public void setEnabled(boolean enabled) {
                Settings.Global.putInt(cr, Settings.Global.AIRPLANE_MODE_ON, enabled ? 1 : 0);
                mContext.sendBroadcastAsUser(
                        new Intent(Intent.ACTION_AIRPLANE_MODE_CHANGED)
                                .putExtra("state", enabled),
                        UserHandle.ALL);
            }
        });

        NfcAdapter nfc = NfcAdapter.getDefaultAdapter(mContext);
        if (nfc != null) {
            mFeatures.put(FEATURE_NFC, new Feature() {
                public boolean isActive() { return nfc.isEnabled(); }
                public void setEnabled(boolean enabled) {
                    if (enabled) nfc.enable(); else nfc.disable();
                }
            });
        }

        UiModeManager um = mContext.getSystemService(UiModeManager.class);
        if (um != null) {
            mFeatures.put(FEATURE_DARK_MODE, new Feature() {
                public boolean isActive() {
                    return (mContext.getResources().getConfiguration().uiMode
                            & Configuration.UI_MODE_NIGHT_MASK)
                            == Configuration.UI_MODE_NIGHT_YES;
                }
                public void setEnabled(boolean enabled) { um.setNightModeActivated(enabled); }
            });
        }

        ColorDisplayManager cdm = mContext.getSystemService(ColorDisplayManager.class);
        if (cdm != null) {
            mFeatures.put(FEATURE_NIGHT_LIGHT, new Feature() {
                public boolean isActive() {
                    return Settings.Secure.getIntForUser(cr, SETTING_NIGHT_DISPLAY,
                            0, UserHandle.USER_CURRENT) == 1;
                }
                public void setEnabled(boolean enabled) {
                    cdm.setNightDisplayActivated(enabled);
                }
            });
        }

        mFeatures.put(FEATURE_COLOR_INVERSION, new Feature() {
            public boolean isActive() {
                return Settings.Secure.getIntForUser(cr,
                        Settings.Secure.ACCESSIBILITY_DISPLAY_INVERSION_ENABLED,
                        0, UserHandle.USER_CURRENT) == 1;
            }
            public void setEnabled(boolean enabled) {
                Settings.Secure.putIntForUser(cr,
                        Settings.Secure.ACCESSIBILITY_DISPLAY_INVERSION_ENABLED,
                        enabled ? 1 : 0, UserHandle.USER_CURRENT);
            }
        });

        mFeatures.put(FEATURE_COLOR_CORRECTION, new Feature() {
            public boolean isActive() {
                return Settings.Secure.getIntForUser(cr,
                        Settings.Secure.ACCESSIBILITY_DISPLAY_DALTONIZER_ENABLED,
                        0, UserHandle.USER_CURRENT) == 1;
            }
            public void setEnabled(boolean enabled) {
                Settings.Secure.putIntForUser(cr,
                        Settings.Secure.ACCESSIBILITY_DISPLAY_DALTONIZER_ENABLED,
                        enabled ? 1 : 0, UserHandle.USER_CURRENT);
            }
        });

        mFeatures.put(FEATURE_REDUCE_BRIGHTNESS, new Feature() {
            public boolean isActive() {
                return Settings.Secure.getIntForUser(cr, SETTING_REDUCE_BRIGHT,
                        0, UserHandle.USER_CURRENT) == 1;
            }
            public void setEnabled(boolean enabled) {
                Settings.Secure.putIntForUser(cr, SETTING_REDUCE_BRIGHT,
                        enabled ? 1 : 0, UserHandle.USER_CURRENT);
            }
        });

        mFeatures.put(FEATURE_ONE_HANDED_MODE, new Feature() {
            public boolean isActive() {
                return Settings.Secure.getIntForUser(cr, SETTING_ONE_HANDED,
                        0, UserHandle.USER_CURRENT) == 1;
            }
            public void setEnabled(boolean enabled) {
                Settings.Secure.putIntForUser(cr, SETTING_ONE_HANDED,
                        enabled ? 1 : 0, UserHandle.USER_CURRENT);
            }
        });

        mFeatures.put(FEATURE_HEADS_UP, new Feature() {
            public boolean isActive() {
                return Settings.Global.getInt(cr,
                        Settings.Global.HEADS_UP_NOTIFICATIONS_ENABLED, 1) == 1;
            }
            public void setEnabled(boolean enabled) {
                Settings.Global.putInt(cr,
                        Settings.Global.HEADS_UP_NOTIFICATIONS_ENABLED, enabled ? 1 : 0);
            }
        });

        mFeatures.put(FEATURE_AUTO_SYNC, new Feature() {
            public boolean isActive() { return ContentResolver.getMasterSyncAutomatically(); }
            public void setEnabled(boolean enabled) {
                ContentResolver.setMasterSyncAutomatically(enabled);
            }
        });

        SensorPrivacyManager spm = mContext.getSystemService(SensorPrivacyManager.class);
        if (spm != null) {
            mFeatures.put(FEATURE_CAMERA_PRIVACY, new Feature() {
                public boolean isActive() {
                    return spm.isSensorPrivacyEnabled(SensorPrivacyManager.Sensors.CAMERA);
                }
                public void setEnabled(boolean enabled) {
                    spm.setSensorPrivacy(SensorPrivacyManager.Sensors.CAMERA, enabled);
                }
            });
            mFeatures.put(FEATURE_MIC_PRIVACY, new Feature() {
                public boolean isActive() {
                    return spm.isSensorPrivacyEnabled(SensorPrivacyManager.Sensors.MICROPHONE);
                }
                public void setEnabled(boolean enabled) {
                    spm.setSensorPrivacy(SensorPrivacyManager.Sensors.MICROPHONE, enabled);
                }
            });
        }

        mFeatures.put(FEATURE_CAFFEINE, new Feature() {
            public boolean isActive() {
                return mWakeLock != null && mWakeLock.isHeld();
            }
            public void setEnabled(boolean enabled) {
                if (mWakeLock == null) return;
                if (enabled && !mWakeLock.isHeld()) {
                    mWakeLock.acquire(CAFFEINE_DURATION_MS);
                } else if (!enabled && mWakeLock.isHeld()) {
                    mWakeLock.release();
                }
            }
        });

        mFeatures.put(FEATURE_DREAM, new Feature() {
            public boolean isActive() { return false; }
            public void setEnabled(boolean enabled) {
                try {
                    android.service.dreams.IDreamManager idm =
                            (android.service.dreams.IDreamManager)
                                    mContext.getSystemService(Context.DREAM_SERVICE);
                    if (idm == null) return;
                    if (idm.isDreaming()) idm.awaken(); else idm.dream();
                } catch (Exception ignored) {}
            }
            @Override public void toggle() throws Exception { setEnabled(true); }
        });

        mFeatures.put(FEATURE_SCREENSHOT, new Feature() {
            public boolean isActive() { return false; }
            public void setEnabled(boolean enabled) {
                if (!enabled) return;
                // Same path as ScreenUtils: internal ScreenshotHelper talks to SystemUI.
                mHandler.postDelayed(() -> {
                    try {
                        new com.android.internal.util.ScreenshotHelper(mContext).takeScreenshot(
                                WindowManager.TAKE_SCREENSHOT_FULLSCREEN,
                                WindowManager.ScreenshotSource.SCREENSHOT_OTHER,
                                mHandler, null);
                    } catch (Exception e) {
                        Log.e(TAG, "screenshot", e);
                    }
                }, 300);
            }
            @Override public void toggle() throws Exception { setEnabled(true); }
        });
    }

    private static boolean isUsbTethered(android.net.TetheringManager tm) {
        try {
            String[] usbRegexs = tm.getTetherableUsbRegexs();
            for (String iface : tm.getTetheredIfaces()) {
                for (String regex : usbRegexs) {
                    if (iface.matches(regex)) return true;
                }
            }
        } catch (Exception ignored) {}
        return false;
    }

    private PowerManager.WakeLock mWakeLock;

    // Observers -----------------------------------------------------------------

    private static final String[][] OBSERVED_SETTINGS = {
        {FEATURE_WIFI, Settings.Global.WIFI_ON, "global"},
        {FEATURE_AOD, Settings.Secure.DOZE_ALWAYS_ON, "secure"},
        {FEATURE_AIRPLANE_MODE, Settings.Global.AIRPLANE_MODE_ON, "global"},
        {FEATURE_ROTATION, Settings.System.ACCELEROMETER_ROTATION, "system"},
        {FEATURE_HEADS_UP, Settings.Global.HEADS_UP_NOTIFICATIONS_ENABLED, "global"},
        {FEATURE_COLOR_INVERSION, Settings.Secure.ACCESSIBILITY_DISPLAY_INVERSION_ENABLED, "secure"},
        {FEATURE_COLOR_CORRECTION, Settings.Secure.ACCESSIBILITY_DISPLAY_DALTONIZER_ENABLED, "secure"},
        {FEATURE_NIGHT_LIGHT, SETTING_NIGHT_DISPLAY, "secure"},
        {FEATURE_REDUCE_BRIGHTNESS, SETTING_REDUCE_BRIGHT, "secure"},
        {FEATURE_ONE_HANDED_MODE, SETTING_ONE_HANDED, "secure"},
    };

    private void registerObservers() {
        ContentResolver cr = mContext.getContentResolver();
        ContentObserver observer = new ContentObserver(mHandler) {
            @Override
            public void onChange(boolean selfChange) {
                // refresh all observed features cheaply
                for (String[] entry : OBSERVED_SETTINGS) {
                    notifySetting(entry[0], entry[1]);
                }
            }
        };
        for (String[] entry : OBSERVED_SETTINGS) {
            switch (entry[2]) {
                case "global":
                    cr.registerContentObserver(
                            Settings.Global.getUriFor(entry[1]), false, observer);
                    break;
                case "secure":
                    cr.registerContentObserver(
                            Settings.Secure.getUriFor(entry[1]), false, observer);
                    break;
                case "system":
                    cr.registerContentObserver(
                            Settings.System.getUriFor(entry[1]), false, observer);
                    break;
            }
        }

        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        filter.addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED);
        mContext.registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(intent.getAction())) {
                    notifyFeature(FEATURE_BLUETOOTH,
                            BluetoothAdapter.getDefaultAdapter() != null
                                    && BluetoothAdapter.getDefaultAdapter().isEnabled());
                } else {
                    notifyFeature(FEATURE_BATTERY_SAVER,
                            mContext.getSystemService(PowerManager.class).isPowerSaveMode());
                }
            }
        }, filter, Context.RECEIVER_NOT_EXPORTED);
    }
}
