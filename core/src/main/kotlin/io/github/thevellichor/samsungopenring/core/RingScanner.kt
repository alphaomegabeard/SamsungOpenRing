package io.github.thevellichor.samsungopenring.core

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.util.Log

internal object RingScanner {

    private const val TAG = "OpenRing.Scanner"

    // Samsung's own Ring Manager/protocol documentation identifies the device by a
    // name containing "Ring". Android may report a Galaxy Ring as LE or DUAL and
    // cached names are not guaranteed to be exactly "Galaxy Ring (...)".
    private val RING_PATTERN = Regex("Ring", RegexOption.IGNORE_CASE)

    fun findBondedRing(context: Context): BluetoothDevice? {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = manager?.adapter

        if (adapter == null) {
            Log.e(TAG, "No Bluetooth adapter")
            return null
        }

        if (!adapter.isEnabled) {
            Log.e(TAG, "Bluetooth is disabled")
            return null
        }

        // Prefer a ring that Samsung's official app already has connected. This is
        // ideal for our second-GATT-client use case and avoids relying solely on the
        // bonded-device cache.
        try {
            val connectedRing = manager.getConnectedDevices(BluetoothProfile.GATT)
                .firstOrNull { device -> device.name?.contains(RING_PATTERN) == true }
            if (connectedRing != null) {
                Log.d(TAG, "Found connected ring: ${connectedRing.name} (${connectedRing.address}), type=${connectedRing.type}")
                return connectedRing
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Could not inspect connected GATT devices: ${e.message}")
        }

        // Fallback to the existing bond created by Samsung Ring Manager. Do NOT
        // require DEVICE_TYPE_LE: Android can legitimately expose BLE peripherals as
        // DEVICE_TYPE_DUAL depending on cached SDP/GATT information and OS version.
        val bonded = try {
            adapter.bondedDevices
        } catch (e: SecurityException) {
            Log.e(TAG, "Cannot inspect bonded devices: ${e.message}")
            return null
        }

        val ring = bonded?.firstOrNull { device ->
            device.name?.contains(RING_PATTERN) == true
        }

        if (ring != null) {
            Log.d(TAG, "Found bonded ring: ${ring.name} (${ring.address}), type=${ring.type}")
        } else {
            val visible = bonded.orEmpty().joinToString { d ->
                "${d.name ?: "<unnamed>"}[type=${d.type}]"
            }
            Log.d(TAG, "No Ring-named device found in ${bonded?.size ?: 0} bonded devices: $visible")
        }

        return ring
    }

    fun isBluetoothEnabled(context: Context): Boolean {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        return manager?.adapter?.isEnabled == true
    }
}
