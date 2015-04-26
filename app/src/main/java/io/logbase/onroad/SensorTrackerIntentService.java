package io.logbase.onroad;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.common.api.GoogleApiClient.ConnectionCallbacks;
import com.google.android.gms.common.api.GoogleApiClient.OnConnectionFailedListener;

import java.io.File;
import java.io.FileOutputStream;

public class SensorTrackerIntentService extends IntentService implements
        ConnectionCallbacks, OnConnectionFailedListener, SensorEventListener, LocationListener {

    private static final String LOG_TAG = "OnRoad Sensor Tracker";
    private static final int FREQUENCY_MILLIS = 1000;
    private GoogleApiClient mGoogleApiClient;
    private static boolean runService = true;
    private FileOutputStream outputStream = null;

    public SensorTrackerIntentService() {
        super("SensorTrackerIntentService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        Log.i(LOG_TAG, "Sensor tracker service started.");

        //GPS and Sensors
        LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
        SensorManager mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        Sensor accelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        Sensor gyroscope = mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);

        //Check is GPS, sensors are available
        if( lm.isProviderEnabled(LocationManager.GPS_PROVIDER) && (accelerometer != null)
                && (gyroscope != null) ){
            runService = true;
            //Open file handle
            String tripName = intent.getStringExtra(Constants.TRIP_NAME_EXTRA);
            try {
                outputStream = openFileOutput(tripName, Context.MODE_APPEND);
            } catch (Exception e) {
                Log.e(LOG_TAG, "Error opening file: " + e);
            }

            mGoogleApiClient = new GoogleApiClient.Builder(this)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .addApi(LocationServices.API)
                    .build();
            //GPS listener will be registered on connection
            mGoogleApiClient.connect();
            //Register sensor listeners
            mSensorManager.registerListener(this, gyroscope, FREQUENCY_MILLIS * 1000);
            mSensorManager.registerListener(this, accelerometer, FREQUENCY_MILLIS * 1000);

            while(runService) {
                //Sleep for a frequency
                try {
                    Thread.sleep(FREQUENCY_MILLIS);
                } catch (Exception e) {
                    Log.e(LOG_TAG, "Interrupted while sleeping : " + e);
                }
                //Read flag again
                SharedPreferences sharedPref = this.getSharedPreferences(getString(R.string.pref_file_key),
                        Context.MODE_PRIVATE);
                String toggleMode = sharedPref.getString(getString(R.string.toggle_mode_key), null);
                if ((toggleMode != null) && (toggleMode.equals(getString(R.string.toggle_trip_start_button)))) {
                    runService = false;
                }
            }
            //Unregister listeners, recording complete
            LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, this);
            mSensorManager.unregisterListener(this);
            mGoogleApiClient.disconnect();
            //Close outputstream
            if(outputStream != null) {
                File file = new File(this.getFilesDir(), tripName);
                Log.i(LOG_TAG, "Wrote file of space: " + file.length());
                try {
                    outputStream.close();
                } catch (Exception e) {
                    Log.e(LOG_TAG, "Error closing file: " + e);
                }
            }
            Log.i(LOG_TAG, "Disconnecting Google API, stopping sensor tracker service.");
        } else {
            Log.i(LOG_TAG, "GPS or Sensors unavailable.");
            //Broadcast to activity that the service stopped due to state issue
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
        LocationRequest mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(FREQUENCY_MILLIS);
        mLocationRequest.setFastestInterval(FREQUENCY_MILLIS);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient,
                mLocationRequest, this);
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

    @Override
    public void onSensorChanged(SensorEvent event) {
        //TODO write event data as JSON string
        if(event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            float x = event.values[0];
            float y = event.values[1];
            float z = event.values[2];
            Log.i(LOG_TAG, "Accelerometer values: " + x + "|" + y + "|" + z);
        }
        if(event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
            float x = event.values[0];
            float y = event.values[1];
            float z = event.values[2];
            Log.i(LOG_TAG, "Gyroscope values: " + x + "|" + y + "|" + z);
        }
        writeToFile("TEST");
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        //Do nothing for now
    }

    @Override
    public void onLocationChanged(Location location) {
        //TODO write event data as JSON string
        Log.i(LOG_TAG, "Location values: " + location.getLatitude() + "|" + location.getLongitude());
        writeToFile("TEST");
    }

    private void writeToFile(String data){
        if(outputStream != null) {
            try {
                outputStream.write(data.getBytes());
                outputStream.write("\n".getBytes());
            } catch (Exception e) {
                Log.e(LOG_TAG, "Error while writing file: " + e);
            }
        }
    }
}
