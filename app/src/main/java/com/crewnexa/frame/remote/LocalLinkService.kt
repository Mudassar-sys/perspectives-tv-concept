package com.crewnexa.frame.remote

import android.app.Service
import android.content.Intent
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.IBinder

/**
 * The control channel between phone and frame.
 *
 * There are three ways to connect a phone to a panel and each one fails in a
 * different place, so the app uses all three in order rather than picking one:
 *
 *  1. Local network. The frame publishes itself over NSD and the phone talks to
 *     it directly. Round trip is a few milliseconds, and it keeps working when
 *     the internet is down but the router is fine. This is the common case and
 *     it is the one that makes the remote feel instant.
 *
 *  2. Cloud relay. The moment someone wants to change the frame in their hallway
 *     from the office, local discovery is useless. A relay covers that, and it
 *     also covers the guest network case where the phone and the frame are on
 *     the same router but isolated from each other, which is more common in
 *     apartments than people expect.
 *
 *  3. BLE. Slow and short range, but it is the only one that works before the
 *     frame has ever seen a network, so provisioning lives there.
 *
 * Picking one transport and shipping it is the single most common reason a
 * companion app gets bad reviews. It works in the office and fails in homes.
 */
class LocalLinkService : Service() {

    private lateinit var nsd: NsdManager
    private var registration: NsdManager.RegistrationListener? = null

    override fun onCreate() {
        super.onCreate()
        nsd = getSystemService(NsdManager::class.java)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val deviceId = intent?.getStringExtra(EXTRA_DEVICE_ID) ?: return START_NOT_STICKY
        val port = intent.getIntExtra(EXTRA_PORT, DEFAULT_PORT)
        publish(deviceId, port)
        return START_STICKY
    }

    private fun publish(deviceId: String, port: Int) {
        val info = NsdServiceInfo().apply {
            // Service name shows up in the phone app's device list, so it has to
            // be readable by a person, not a UUID.
            serviceName = "Frame ${deviceId.takeLast(4)}"
            serviceType = SERVICE_TYPE
            setPort(port)
            setAttribute("v", "1")
            setAttribute("id", deviceId)
        }

        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) = Unit
            override fun onRegistrationFailed(info: NsdServiceInfo, code: Int) = Unit
            override fun onServiceUnregistered(info: NsdServiceInfo) = Unit
            override fun onUnregistrationFailed(info: NsdServiceInfo, code: Int) = Unit
        }
        registration = listener
        nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener)
    }

    override fun onDestroy() {
        registration?.let { runCatching { nsd.unregisterService(it) } }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val SERVICE_TYPE = "_perspectives._tcp."
        const val DEFAULT_PORT = 8717
        const val EXTRA_DEVICE_ID = "device_id"
        const val EXTRA_PORT = "port"
    }
}
