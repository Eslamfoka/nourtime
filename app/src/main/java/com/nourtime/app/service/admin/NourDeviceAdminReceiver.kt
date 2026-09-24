package com.nourtime.app.service.admin

import android.app.admin.DeviceAdminReceiver

/** Lets the app lock the screen and blocks uninstalling while active. Protection logic comes in step 5. */
class NourDeviceAdminReceiver : DeviceAdminReceiver()
