package com.jacknephilim.locationupdates

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.preference.PreferenceManager
import kotlinx.android.synthetic.main.activity_main.*
import org.jetbrains.anko.design.indefiniteSnackbar
import org.jetbrains.anko.toast

class MainActivity : AppCompatActivity(), SharedPreferences.OnSharedPreferenceChangeListener {
    private val TAG = MainActivity::class.java.simpleName
    // The BroadcastReceiver used to listen from broadcasts from the service.
    private lateinit var receiver: MyReceiver
    // A reference to the service used to get location updates.
    private var service: LocationUpdateService? = null
    // Used in checking for runtime permissions.
    private val REQUEST_PERMISSIONS_REQUEST_CODE: Int = 34
    // Tracks the bound state of the service.
    private var bound: Boolean = false


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        receiver = MyReceiver()
        setContentView(R.layout.activity_main)
        // Check that the user hasn't revoked permissions by going to Settings.
        if (Utils.requestingLocationUpdates(this)) {
            if (!checkPermissions()) {
                requestPermissions()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        PreferenceManager
            .getDefaultSharedPreferences(this)
            .registerOnSharedPreferenceChangeListener(this)
        request_location_updates_button.setOnClickListener {
            if (!checkPermissions()) {
                requestPermissions()
            } else {
                service?.requestLocationUpdates()
            }
        }
        remove_location_updates_button.setOnClickListener {
            service?.removeLocationUpdates()
        }
        // Restore the state of the buttons when the activity (re)launches.
        setButtonsState(Utils.requestingLocationUpdates(this))

        // Bind to the service. If the service is in foreground mode, this signals to the service
        // that since this activity is in the foreground, the service can exit foreground mode.
        bindService(
            createIntent(LocationUpdateService::class.java), serviceConnection,
            Context.BIND_AUTO_CREATE
        )

    }

    override fun onResume() {
        super.onResume()
        LocalBroadcastManager.getInstance(this).registerReceiver(
            receiver,
            IntentFilter(LocationUpdateService.ACTION_BROADCAST)
        )
    }

    override fun onPause() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(receiver)
        super.onPause()
    }

    override fun onStop() {
        if(bound){
            // Unbind from the service. This signals to the service that this activity is no longer
            // in the foreground, and the service can respond by promoting itself to a foreground
            // service.
            unbindService(serviceConnection)
            bound = false
        }
        PreferenceManager.getDefaultSharedPreferences(this)
            .unregisterOnSharedPreferenceChangeListener(this)
        super.onStop()
    }

    private fun requestPermissions() {
        val shouldProvideRationale = ActivityCompat.shouldShowRequestPermissionRationale(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
        if (shouldProvideRationale) {
            activity_main.indefiniteSnackbar(R.string.permission_rationale).setAction(R.string.ok) {
                ActivityCompat.requestPermissions(
                    this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                    REQUEST_PERMISSIONS_REQUEST_CODE
                )
            }

        } else {
            Log.i(TAG, "Requesting permission")
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                REQUEST_PERMISSIONS_REQUEST_CODE
            )
        }
    }

    private fun checkPermissions() =
        Utils.hasPermissions(this, Manifest.permission.ACCESS_FINE_LOCATION)

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_PERMISSIONS_REQUEST_CODE) {
            when {
                grantResults.contains(PackageManager.PERMISSION_GRANTED) -> {
                    service?.requestLocationUpdates()
                }
                grantResults.count() <= 0 -> {
                    Log.i(TAG, "User interaction was cancelled.")
                }
                else -> {
                    // Permission denied.
                    Log.i(TAG, "Permission denied.")
                    activity_main.indefiniteSnackbar(R.string.permission_denied_explanation)
                        .setAction(R.string.settings) {
                            Intent().apply {
                                action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                                data = Uri.fromParts("package", BuildConfig.APPLICATION_ID, null)
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                startActivity(this)
                            }
                        }
                }
            }
        }
    }


    // Monitors the state of the connection to the service.
    private val serviceConnection: ServiceConnection = object :
        ServiceConnection {
        override fun onServiceConnected(name: ComponentName, serviceBinder: IBinder) {
            val binder = serviceBinder as LocationUpdateService.LocationBinder
            service = binder.locationUpdateService
            bound = true
        }

        override fun onServiceDisconnected(name: ComponentName) {
            service = null
            bound = false
        }
    }


    private class MyReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val location =
                intent?.getParcelableExtra<Location>(LocationUpdateService.EXTRA_LOCATION)
            location?.apply {
                context?.toast(Utils.getLocationText(location)!!)
            }
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        // Update the buttons state depending on whether location updates are being requested.
        if (key == Utils.KEY_REQUESTING_LOCATION_UPDATES) {
            setButtonsState(
                sharedPreferences!!.getBoolean(
                    Utils.KEY_REQUESTING_LOCATION_UPDATES,
                    false
                )
            )
        }
    }

    private fun setButtonsState(requestingLocationUpdates: Boolean) {
        if (requestingLocationUpdates) {
            request_location_updates_button.isEnabled = false
            remove_location_updates_button.isEnabled = true
        } else {
            request_location_updates_button.isEnabled = true
            remove_location_updates_button.isEnabled = false
        }
    }
}
