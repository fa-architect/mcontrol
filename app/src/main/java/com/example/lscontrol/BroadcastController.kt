package com.example.lscontrol

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertisingSet
import android.bluetooth.le.AdvertisingSetCallback
import android.bluetooth.le.AdvertisingSetParameters
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid

/**
 * 2チャンネル独立ブロードキャスト送信クラス。
 * CH1=伸縮、CH2=振動。startAdvertisingSet()で同時送信。
 * 値はPixel 6a HCIログ（2026-09-24）から取得。
 */
@SuppressLint("MissingPermission")
class BroadcastController(ctx: Context, private val onStatus: (String) -> Unit) {

    companion object {
        private const val MFR_ID = 0x00FF
        private val SVC = ParcelUuid.fromString("0000ae8f-0000-1000-8000-00805f9b34fb")
        private const val PFX = "6db643ce97fe427c"

        /** index 0=停止, 1〜9=パターン */
        val CH1 = listOf(
            "d5964c","d41f5d","d7846f","d60d7e",
            "d1b20a","d03b1b","d3a029","d22938","dddec0","dc57d1"
        ).map { hex(PFX + it) }

        val CH2 = listOf(
            "a5113f","a4982e","a7031c","a68a0d",
            "a13579","a0bc68","a3275a","a2ae4b","ad59b3","acd0a2"
        ).map { hex(PFX + it) }

        val ALL_STOP = hex(PFX + "e5157d")

        private fun hex(s: String) = ByteArray(s.length / 2) {
            s.substring(it * 2, it * 2 + 2).toInt(16).toByte()
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private val adapter = (ctx.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    private val adv get() = adapter?.bluetoothLeAdvertiser
    val isBluetoothOn get() = adapter?.isEnabled == true

    private inner class AdvHandle(val label: String) : AdvertisingSetCallback() {
        var set: AdvertisingSet? = null
        override fun onAdvertisingSetStarted(s: AdvertisingSet?, txPower: Int, status: Int) {
            handler.post { if (status == ADVERTISE_SUCCESS) set = s else onStatus("$label 送信失敗 ($status)") }
        }
        override fun onAdvertisingSetStopped(s: AdvertisingSet?) {
            handler.post { if (set == s) set = null }
        }
    }

    private var h1: AdvHandle? = null
    private var h2: AdvHandle? = null
    private var hStop: AdvHandle? = null

    private fun params() = AdvertisingSetParameters.Builder()
        .setLegacyMode(true).setConnectable(true).setScannable(true)
        .setInterval(AdvertisingSetParameters.INTERVAL_MIN)
        .setTxPowerLevel(AdvertisingSetParameters.TX_POWER_HIGH)
        .build()

    private fun data(payload: ByteArray) = AdvertiseData.Builder()
        .setIncludeDeviceName(false).setIncludeTxPowerLevel(false)
        .addManufacturerData(MFR_ID, payload).addServiceUuid(SVC)
        .build()

    fun sendCh1(payload: ByteArray) {
        val existing = h1
        if (existing?.set != null) { existing.set!!.setAdvertisingData(data(payload)); return }
        cancelH1()
        val h = AdvHandle("伸縮").also { h1 = it }
        adv?.startAdvertisingSet(params(), data(payload), null, null, null, 0, 0, h)
    }

    fun sendCh2(payload: ByteArray) {
        val existing = h2
        if (existing?.set != null) { existing.set!!.setAdvertisingData(data(payload)); return }
        cancelH2()
        val h = AdvHandle("振動").also { h2 = it }
        adv?.startAdvertisingSet(params(), data(payload), null, null, null, 0, 0, h)
    }

    private fun cancelH1() { h1?.let { adv?.stopAdvertisingSet(it) }; h1 = null }
    private fun cancelH2() { h2?.let { adv?.stopAdvertisingSet(it) }; h2 = null }

    fun stopCh1() {
        h1?.set?.setAdvertisingData(data(CH1[0]))
        handler.postDelayed({ cancelH1() }, 400)
    }

    fun stopCh2() {
        h2?.set?.setAdvertisingData(data(CH2[0]))
        handler.postDelayed({ cancelH2() }, 400)
    }

    fun stopAll() {
        cancelH1(); cancelH2()
        hStop?.let { adv?.stopAdvertisingSet(it) }
        val h = AdvHandle("全停止").also { hStop = it }
        adv?.startAdvertisingSet(params(), data(ALL_STOP), null, null, null, 80, 0, h)
        handler.postDelayed({ hStop?.let { adv?.stopAdvertisingSet(it) }; hStop = null }, 900)
    }
}
