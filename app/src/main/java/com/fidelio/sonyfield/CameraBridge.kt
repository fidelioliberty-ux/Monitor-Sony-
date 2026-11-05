package com.fidelio.sonyfield
import android.content.Context
import android.hardware.usb.UsbManager
class CameraBridge(private val context: Context) {
    fun usbStatus(): String {
        val manager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        return "USB detectados: ${manager.deviceList.size}; protocolo Sony pendiente"
    }
    fun track(x: Float, y: Float): String = "Touch Tracking real no implementado; ninguna orden enviada a la camara."
}
