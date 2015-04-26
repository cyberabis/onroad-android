package io.logbase.onroad;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.provider.Settings;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.common.api.GoogleApiClient.ConnectionCallbacks;
import com.google.android.gms.common.api.GoogleApiClient.OnConnectionFailedListener;

public class SensorTrackerIntentService extends IntentService implements
        ConnectionCallbacks, OnConnectionFailedListener {

    private static final String LOG_TAG = "OnRoad Sensor Tracker";
    private GoogleApiClient mGoogleApiClient;
    private static boolean runService = true;

    public SensorTrackerIntentService() {
        super("SensorTrackerIntentService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        Log.i(LOG_TAG, "Sensor tracker service started.");
        runService = true;
        //Check is GPS is available
        LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
        if(lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            mGoogleApiClient = new GoogleApiClient.Builder(this)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .addApi(LocationServices.API)
                    .build();
            mGoogleApiClient.connect();
            long frequency = 1000;
            Location mLastLocation = null;
            while(runService) {
                //Read sensor data and write to file
                //GPS
                mLastLocation = LocationServices.FusedLocationApi.getLastLocation(
                        mGoogleApiClient);
                Log.i(LOG_TAG, "Location : " + mLastLocation);
                if (mLastLocation != null) {
                    //TODO
                    //Save
                }
                //Sleep for a frequency
                try {
                    Thread.sleep(frequency);
                } catch (Exception e) {
                }
                //Read flag again
                SharedPreferences sharedPref = this.getSharedPreferences(getString(R.string.pref_file_key),
                        Context.MODE_PRIVATE);
                String toggleMode = sharedPref.getString(getString(R.string.toggle_mode_key), null);
                if ((toggleMode != null) && (toggleMode.equals(getString(R.string.toggle_trip_start_button)))) {
                    runService = false;
                }
            }
            mGoogleApiClient.disconnect();
            Log.i(LOG_TAG, "Disconnecting Google API, stopping sensor tracker service.");
        } else {
            //Broadcast to activity that the service stopped
            Intent localIntent = new Intent(Constants.BROADCAST_ACTION)
                    .putExtra(Constants.SERVICE_STATUS, Constants.STOP_STATUS);
            // Broadcasts the Intent to receivers in this app.
            LocalBroadcastManager.getInstance(this).sendBroadcast(localIntent);
        }
        Log.i(LOG_TAG, "Sensor tracker service stopped.");
    }

    @Override
    public void onConnected(Bundle connectionHint) {
        Log.i(LOG_TAG, "Google API connected");
        runService = true;
    }

    @Override
    public void onConnectionSuspended(int cause) {
        // The connection has been interrupted.
        // Disable any UI components that depend on Google APIs
        // until onConnected() is called.
        Log.i(LOG_TAG, "Google API connection suspended");
        runService = false;
    }

    @Override
    public void onConnectionFailed(ConnectionResult result) {
        // This callback is important for handling errors that
        // may occur while attempting to connect with Google.
        //
        // More about this in the next section.
        Log.i(LOG_TAG, "Google API connection failed");
        runService = false;
    }

}
