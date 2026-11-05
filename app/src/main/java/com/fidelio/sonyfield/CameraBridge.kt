package com.fidelio.sonyfield

import android.content.Context
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbManager

/**
 * Read-only USB diagnostics. Does NOT claim to provide Sony live view.
 * Camera2 does not expose all USB video/PTP devices on all Android phones.
 */
class CameraBridge(private val context: Context) {
    fun usbStatus(): String {
        val manager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        val devices = manager.deviceList.values.toList()
        if (devices.isEmpty()) {
            return "USB: no se detecta dispositivo. Comprobar OTG, cable de datos y modo USB de Sony."
        }
        return devices.joinToString("\n") { device ->
            val sony = device.vendorId == 0x054c
            val interfaces = (0 until device.interfaceCount).joinToString("; ") { index ->
                val iface = device.getInterface(index)
                val kind = when (iface.interfaceClass) {
                    UsbConstants.USB_CLASS_STILL_IMAGE -> "PTP/imagen"
                    UsbConstants.USB_CLASS_VIDEO -> "UVC/video"
                    UsbConstants.USB_CLASS_VENDOR_SPEC -> "Sony/propietario"
                    else -> "clase ${iface.interfaceClass}"
                }
                "IF${index}=${kind} alt=${iface.alternateSetting} endpoints=${iface.endpointCount}"
            }
            val identity = if (sony) "Sony" else "USB"
            "${identity} VID=%04X PID=%04X permiso=%s | %s".format(
                device.vendorId, device.productId,
                if (manager.hasPermission(device)) "SI" else "NO",
                interfaces.ifBlank { "sin interfaces" }
            )
        }
    }

    fun track(x: Float, y: Float): String =
        "Tracking remoto pendiente: ninguna orden enviada a la Sony."
}
