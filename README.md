# SonyFieldMonitor

Android prototype for Sony A7S III and Samsung Galaxy A55 5G.

## Connection mode: Sony Remote Shooting (USB-C)

Use MENU > Setup > USB > USB Connection Mode > Remote Shooting on Sony A7S III firmware 6.01.

**Current state: USB diagnostic only, not a working live-view monitor.** The USB PTP remote shooting/live-view transport is not implemented yet. A successful Android build does not prove that the camera image works.

Press **USB PTP** to see attached device vendor/product IDs, interface types and permission state. Sony USB vendor ID is 054C. If a Sony device is listed, record the VID/PID and interface types for the next implementation stage.

The current Camera2 UVC prototype is deliberately not automatically started because Sony Remote Shooting is not equivalent to USB Streaming/UVC. Touch tracking is also not implemented. Horizon calibration works from phone accelerometer; peaking and false color need a real captured image to work.

Official technical reference: https://support.d-imaging.sony.co.jp/app/cameraremotecommand/en/index.html
