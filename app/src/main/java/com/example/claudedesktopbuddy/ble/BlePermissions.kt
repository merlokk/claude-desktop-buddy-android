package com.example.claudedesktopbuddy.ble

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Runtime permissions required to advertise as a BLE peripheral.
 *
 * On Android 12 (API 31)+ advertising and serving GATT need [Manifest.permission.BLUETOOTH_ADVERTISE]
 * and [Manifest.permission.BLUETOOTH_CONNECT], which are requested at runtime. On older versions the
 * legacy `BLUETOOTH`/`BLUETOOTH_ADMIN` permissions are install-time only, so there is nothing to
 * request — hence an empty array.
 */
val blePermissions: Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        emptyArray()
    }

/** True when every runtime BLE permission required on this OS version is already granted. */
fun Context.hasBlePermissions(): Boolean =
    blePermissions.all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }
