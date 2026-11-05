package com.fidelio.sonyfield

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import android.view.TextureView

/**
 * Experimental Android Camera2 external-camera path.
 * Some Samsung devices do not expose UVC cameras to Camera2; USB enumeration
 * alone does not mean live capture is possible. No fake video fallback.
 */
class ExternalCamera(private val activity: Activity, private val view: TextureView, private val report: (String) -> Unit) {
    private val manager = activity.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private val thread = HandlerThread("SonyExternalCamera").apply { start() }
    private val handler = Handler(thread.looper)
    private var device: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var previewSurface: Surface? = null
    private var running = false
    private val callback = object : CameraManager.AvailabilityCallback() {
        override fun onCameraAvailable(cameraId: String) { if (running) handler.post { openIfExternal() } }
    }
    fun start() {
        running = true
        manager.registerAvailabilityCallback(callback, handler)
        view.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) { openIfExternal() }
            override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}
            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean { close(); return true }
            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
        }
        if (view.isAvailable) openIfExternal()
    }
    fun stop() {
        running = false
        manager.unregisterAvailabilityCallback(callback)
        close()
        thread.quitSafely()
    }
    private fun close() {
        session?.close(); session = null
        device?.close(); device = null
        previewSurface?.release(); previewSurface = null
    }
    private fun openIfExternal() {
        if (!running || !view.isAvailable || device != null) return
        if (activity.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            activity.runOnUiThread { report("Autoriza permiso de camara para probar UVC") }
            activity.requestPermissions(arrayOf(Manifest.permission.CAMERA), 101)
            return
        }
        try {
            val external = manager.cameraIdList.firstOrNull {
                manager.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) ==
                    CameraCharacteristics.LENS_FACING_EXTERNAL
            }
            if (external == null) {
                activity.runOnUiThread { report("USB: no aparece camara externa Camera2. Comprobar USB Streaming / compatibilidad UVC del Samsung") }
                return
            }
            activity.runOnUiThread { report("Camara USB externa detectada: $external; abriendo video...") }
            manager.openCamera(external, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    if (!running) { camera.close(); return }
                    device = camera
                    try {
                        val texture = view.surfaceTexture ?: return
                        val config = manager.getCameraCharacteristics(camera.id)
                            .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                        val sizes = config?.getOutputSizes(SurfaceTexture::class.java)
                        val selected = sizes?.filter { it.width <= 1920 && it.height <= 1080 }
                            ?.maxByOrNull { it.width * it.height } ?: sizes?.firstOrNull()
                        if (selected != null) texture.setDefaultBufferSize(selected.width, selected.height)
                        val surface = Surface(texture)
                        previewSurface = surface
                        camera.createCaptureSession(listOf(surface), object : CameraCaptureSession.StateCallback() {
                            override fun onConfigured(s: CameraCaptureSession) {
                                session = s
                                try {
                                    val request = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                                    request.addTarget(surface)
                                    s.setRepeatingRequest(request.build(), null, handler)
                                    activity.runOnUiThread { report("VIDEO USB EN DIRECTO: ${selected?.width}x${selected?.height}") }
                                } catch (e: Exception) { activity.runOnUiThread { report("Error vista previa: ${e.message}") } }
                            }
                            override fun onConfigureFailed(s: CameraCaptureSession) {
                                activity.runOnUiThread { report("La camara USB no admite la sesion de video solicitada") }
                            }
                        }, handler)
                    } catch (e: Exception) { activity.runOnUiThread { report("Error UVC: ${e.message}") } }
                }
                override fun onDisconnected(camera: CameraDevice) {
                    close()
                    activity.runOnUiThread { report("Camara USB desconectada") }
                }
                override fun onError(camera: CameraDevice, error: Int) {
                    close()
                    activity.runOnUiThread { report("Error Camera2 USB: $error") }
                }
            }, handler)
        } catch (e: Exception) {
            activity.runOnUiThread { report("Camera2: ${e.message}") }
        }
    }
}
