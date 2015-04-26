package io.logbase.onroad;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Location;
import android.location.LocationManager;
import android.provider.Settings;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationServices;

public class SensorTrackerIntentService extends IntentService {

    private static final String LOG_TAG = "OnRoad Sensor Tracker";

    public SensorTrackerIntentService() {
        super("SensorTrackerIntentService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        Log.i(LOG_TAG, "Sensor tracker service started.");

        //Flags
        boolean runService = true;
        long frequency = 1000;

        //Check what sensors available

        GoogleApiClient mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addApi(LocationServices.API)
                .build();
        Location mLastLocation = null;
        LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
        if(!lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            runService = false;
            //Broadcast to activity that the service stopped
            Intent localIntent = new Intent(Constants.BROADCAST_ACTION)
                            .putExtra(Constants.SERVICE_STATUS, Constants.STOP_STATUS);
            // Broadcasts the Intent to receivers in this app.
            LocalBroadcastManager.getInstance(this).sendBroadcast(localIntent);
        }

        while(runService) {

            //Read sensor data and write to file
            //GPS
            mLastLocation = LocationServices.FusedLocationApi.getLastLocation(
                    mGoogleApiClient);
            Log.i(LOG_TAG, "Location : " + mLastLocation);
            if (mLastLocation != null) {
                Log.i(LOG_TAG, "Lat: " + mLastLocation.getLatitude());
                Log.i(LOG_TAG, "Long: " + mLastLocation.getLongitude());
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

        Log.i(LOG_TAG, "Sensor tracker service stopped.");
    }

}
