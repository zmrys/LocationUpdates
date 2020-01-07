package com.jacknephilim.locationupdates

import android.app.*
import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.*
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.gms.location.*

/**
 * A bound and started service that is promoted to a foreground service when location updates have
 * been requested and all clients unbind.
 *
 * For apps running in the background on "O" devices, location is computed only once every 10
 * minutes and delivered batched every 30 minutes. This restriction applies even to apps
 * targeting "N" or lower which are run on "O" devices.
 *
 * This sample show how to use a long-running service for location updates. When an activity is
 * bound to this service, frequent location updates are permitted. When the activity is removed
 * from the foreground, the service promotes itself to a foreground service, and location updates
 * continue. When the activity comes back to the foreground, the foreground service stops, and the
 * notification assocaited with that service is removed.
 */

class LocationUpdateService : Service() {
    companion object {
        private const val PACKAGE_NAME = "com.jacknephilim.locationupdates"
        private val TAG = LocationUpdateService::class.java.simpleName
        /**
         * The name of the channel for notifications.
         */
        private const val CHANNEL_ID = "channel_01"
        const val ACTION_BROADCAST = "$PACKAGE_NAME.broadcast"
        const val EXTRA_LOCATION = "$PACKAGE_NAME.location"
        private const val EXTRA_STARTED_FROM_NOTIFICATION =
            "$PACKAGE_NAME.started_from_notification"
        /**
         * The desired interval for location updates. Inexact. Updates may be more or less frequent.
         */
        private const val UPDATE_INTERVAL_IN_MILLISECONDS: Long = 10000
        /**
         * The fastest rate for active location updates. Updates will never be more frequent
         * than this value.
         */
        private const val FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS =
            UPDATE_INTERVAL_IN_MILLISECONDS / 2
        /**
         * The identifier for the notification displayed for the foreground service.
         */
        private const val NOTIFICATION_ID: Int = 12345678
    }

    private val binder = LocationBinder()
    /**
     * Used to check whether the bound activity has really gone away and not unbound as part of an
     * orientation change. We create a foreground service notification only if the former takes
     * place.
     */
    private var changingConfiguration: Boolean = false
    private val notificationManager: NotificationManager? = null
    /**
     * Provides access to the Fused Location Provider API.
     */
    private var fusedLocationClient: FusedLocationProviderClient? = null

    /**
     * Callback for changes in location.
     */
    private var locationCallback: LocationCallback? = null

    private var serviceHandler: Handler? = null

    /**
     * The current location.
     */
    private var location: Location? = null
    /**
     * Contains parameters used by [com.google.android.gms.location.FusedLocationProviderApi].
     */
    private var locationRequest: LocationRequest? = null

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult?) {
                super.onLocationResult(locationResult)
                locationResult?.lastLocation?.apply {
                    onNewLocation(this)
                }
            }
        }
        createLocationRequest()
        getLastLocation()
        val handlerThread = HandlerThread(TAG)
        handlerThread.start()
        serviceHandler = Handler(handlerThread.looper)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "Service started.")
        val startedFromNotification =
            intent?.getBooleanExtra(EXTRA_STARTED_FROM_NOTIFICATION, false)!!
        // We got here because the user decided to remove location updates from the notification.
        if (startedFromNotification) {
            removeLocationUpdates()
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        // Called when a client (MainActivity in case of this sample) comes to the foreground
        // and binds with this service.
        // The service should cease to be a foreground service
        // when that happens.
        Log.i(TAG, "in onBind()")
        stopForeground(true)
        changingConfiguration = false
        return binder
    }

    override fun onRebind(intent: Intent?) {
        // Called when a client (MainActivity in case of this sample) returns to the foreground
        // and binds once again with this service. The service should cease to be a foreground
        // service when that happens.
        Log.i(TAG, "in onRebind()")
        stopForeground(true)
        changingConfiguration = false
        super.onRebind(intent)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Log.i(TAG, "Last client unbound from service")
        // Called when the last client (MainActivity in case of this sample) unbinds from this
        // service. If this method is called due to a configuration change in MainActivity, we
        // do nothing. Otherwise, we make this service a foreground service.
        if (!changingConfiguration && Utils.requestingLocationUpdates(this)) {
            Log.i(TAG, "Starting foreground service")
            startForeground(NOTIFICATION_ID, getNotification())
        }
        return true // Ensures onRebind() is called when a client re-binds.
    }

    override fun onDestroy() {
        serviceHandler?.removeCallbacksAndMessages(null)
    }

    inner class LocationBinder : Binder() {
        val locationUpdateService: LocationUpdateService
            get() = this@LocationUpdateService
    }


    private fun createLocationRequest() {
        locationRequest = LocationRequest()
        locationRequest?.apply {
            interval = UPDATE_INTERVAL_IN_MILLISECONDS
            fastestInterval = FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }
    }

    private fun getLastLocation() {
        try {
            fusedLocationClient?.lastLocation?.addOnCompleteListener {
                if (it.isSuccessful) {
                    it.result?.apply {
                        location = this
                    }
                } else {
                    Log.w(TAG, "Failed to get location .")
                }
            }
        } catch (se: SecurityException) {
            Log.e(TAG, "Lost location permission.$se")
        }

    }

    private fun onNewLocation(l: Location) {
        Log.i(TAG, "New Location $l")
        location = l
        // Notify anyone listening for broadcasts about the new location.
        val intent = Intent(ACTION_BROADCAST)
        intent.putExtra(EXTRA_LOCATION, l)
        LocalBroadcastManager.getInstance(applicationContext).sendBroadcast(intent)
        // Update notification content if running as a foreground service.
        if (serviceIsRunningInForeground()) {
            notificationManager?.notify(NOTIFICATION_ID, getNotification())
        }
    }

    private fun getNotification(): Notification {
        val text = Utils.getLocationText(location)
        val intent = createIntent(LocationUpdateService::class.java).apply {
            putExtra(EXTRA_STARTED_FROM_NOTIFICATION, true)
        }
        val serviceIntent = servicePendingIntent(intent, PendingIntent.FLAG_UPDATE_CURRENT)
        val activityIntent = activityPendingIntent(MainActivity::class.java, 0)

        val data = EzNotificationData(
            channelId = CHANNEL_ID,
            title = Utils.getLocationTitle(this)!!,
            text = text!!,
            smallIcon = R.mipmap.ic_launcher,
            onActivityAction = NotificationAction(
                R.mipmap.ic_launcher,
                getString(R.string.launch_activity),
                activityIntent
            ),
            onServiceAction = NotificationAction(
                R.drawable.ic_cancel_24dp,
                getString(R.string.remove_location_updates),
                serviceIntent
            ),
            ongoing = true,
            priority = Notification.PRIORITY_HIGH,
            ticker = text,
            setWhen = System.currentTimeMillis()
        )
        return EzNotification(this, data).getNotification()
    }

    private fun serviceIsRunningInForeground(): Boolean {
        val manager = this.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        for (service in manager.getRunningServices(Int.MAX_VALUE)) {
            if (javaClass.name == service.service.className) {
                if (service.foreground) {
                    return true
                }
            }
        }
        return false
    }

    /**
     * Removes location updates. Note that in this sample we merely log the
     * {@link SecurityException}.
     */
    public fun removeLocationUpdates() {
        Log.i(TAG, "Removing location updates")
        try {
            fusedLocationClient?.removeLocationUpdates(locationCallback)
            Utils.setRequestingLocationUpdates(this, false)
            stopSelf()
        } catch (unlikely: SecurityException) {
            Utils.setRequestingLocationUpdates(this, true)
            Log.e(TAG, "Lost location permission. Could not remove updates. $unlikely")
        }
    }
    /**
     * Makes a request for location updates. Note that in this sample we merely log the
     * {@link SecurityException}.
     */
    public fun requestLocationUpdates() {
        Log.i(TAG, "Requesting location updates")
        Utils.setRequestingLocationUpdates(this, true)
        startService(createIntent(LocationUpdateService::class.java))
        try {
            fusedLocationClient?.requestLocationUpdates(locationRequest,
                locationCallback, Looper.myLooper())

        }catch (unlikely:SecurityException){
            Utils.setRequestingLocationUpdates(this, false)
            Log.e(TAG, "Lost location permission. Could not request updates. $unlikely")
        }
    }


}