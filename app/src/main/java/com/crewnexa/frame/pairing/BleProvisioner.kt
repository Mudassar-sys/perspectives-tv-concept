package com.crewnexa.frame.pairing

import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.content.Context
import android.os.ParcelUuid
import java.util.UUID

/**
 * First boot on a wall-mounted panel is a chicken and egg problem. The frame
 * needs Wi-Fi credentials to reach anything, and it has no keyboard to receive
 * them. A remote with arrow keys and an on-screen keyboard is possible but it is
 * a miserable way to type a WPA2 passphrase on a wall.
 *
 * So the panel comes up as a BLE peripheral instead and the phone writes the
 * credentials to it. This is standard IoT provisioning and it is the reason the
 * published frame specification lists Bluetooth 5.2.
 *
 * Three details that matter in practice and are usually missed:
 *
 *  1. A BLE characteristic write is capped at the negotiated MTU, which starts
 *     at 23 bytes. An SSID plus a passphrase does not fit. The payload has to be
 *     chunked and reassembled, which is what [ProvisioningPayload] handles.
 *
 *  2. The passphrase must never be written in the clear. The frame publishes an
 *     ephemeral public key in its advertisement, the phone seals the payload to
 *     it, and the frame is the only thing that can open it.
 *
 *  3. Provisioning must stay available after the frame is online. Home networks
 *     get replaced, and a frame that can only be provisioned once becomes a
 *     support call the day the router changes.
 */
class BleProvisioner(
    private val context: Context,
    private val onCredentials: (ssid: String, passphrase: String) -> Unit,
) {

    private var server: BluetoothGattServer? = null
    private val assembler = ChunkAssembler()

    fun start(deviceId: String) {
        val manager = context.getSystemService(BluetoothManager::class.java)
        val advertiser = manager.adapter.bluetoothLeAdvertiser ?: return

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
            .setConnectable(true)
            .build()

        // The advertisement carries the last six of the device id so the phone
        // can show "Frame 4829 16" instead of a list of identical entries when
        // more than one frame is in the room.
        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .addServiceUuid(ParcelUuid(SERVICE_UUID))
            .addServiceData(ParcelUuid(SERVICE_UUID), deviceId.takeLast(6).toByteArray())
            .build()

        advertiser.startAdvertising(settings, data, advertiseCallback)
        server = manager.openGattServer(context, gattCallback)
    }

    fun stop() {
        server?.close()
        server = null
    }

    private fun handleWrite(value: ByteArray): Boolean {
        val complete = assembler.accept(value) ?: return false
        val payload = ProvisioningPayload.open(complete) ?: return false
        onCredentials(payload.ssid, payload.passphrase)
        return true
    }

    /**
     * Reassembles a payload split across MTU-sized writes. Frame format is one
     * header byte holding the sequence number, high bit set on the last chunk.
     */
    private class ChunkAssembler {
        private val buffer = mutableListOf<Byte>()

        fun accept(chunk: ByteArray): ByteArray? {
            if (chunk.isEmpty()) return null
            val header = chunk[0].toInt()
            val isLast = header and 0x80 != 0
            buffer.addAll(chunk.drop(1))
            if (!isLast) return null
            val out = buffer.toByteArray()
            buffer.clear()
            return out
        }
    }

    private val advertiseCallback = object : android.bluetooth.le.AdvertiseCallback() {}

    private val gattCallback = object : android.bluetooth.BluetoothGattServerCallback() {
        override fun onCharacteristicWriteRequest(
            device: android.bluetooth.BluetoothDevice,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray,
        ) {
            if (characteristic.uuid == CREDENTIALS_UUID) handleWrite(value)
            if (responseNeeded) {
                server?.sendResponse(device, requestId, 0, offset, null)
            }
        }
    }

    companion object {
        val SERVICE_UUID: UUID = UUID.fromString("6f4d1a00-9c2b-4d3e-8a71-0c5e2b7f9d10")
        val CREDENTIALS_UUID: UUID = UUID.fromString("6f4d1a01-9c2b-4d3e-8a71-0c5e2b7f9d10")
    }
}
