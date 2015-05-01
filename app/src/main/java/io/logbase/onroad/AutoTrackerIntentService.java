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
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.GoogleApiClient.ConnectionCallbacks;
import com.google.android.gms.common.api.GoogleApiClient.OnConnectionFailedListener;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.SortedMap;
import java.util.TreeMap;

public class AutoTrackerIntentService extends IntentService implements
        ConnectionCallbacks, OnConnectionFailedListener, SensorEventListener, LocationListener {

    private static final String LOG_TAG = "OnRoad Sensor Tracker";
    private static final int AUTO_FREQUENCY_MILLIS = 10 * 1000;
    private static final int FREQUENCY_MILLIS = 100;
    private static final long NOT_MOVING_ELAPSE_MILLIS = 5 * 60 * 1000;
    private static final float NOT_MOVING_AVG_SPEED = 6;
    private static final float SPEED_NOISE_CUTOFF = 55.55f;
    private GoogleApiClient mGoogleApiClient;
    private static boolean runService = true;
    private Location lastLocation = null;
    private SensorEvent lastAccelerometerEvent = null;
    private SensorEvent lastGyroscopeEvent = null;
    private long lastAccelerometerEventTime = 0;
    private long lastGyroscopeEventTime = 0;
    private long lastLocationTime = 0;
    private static final boolean FIXED_FREQ_WRITE = true;
    private File file = null;
    private FileOutputStream outputStream = null;
    private SensorManager mSensorManager = null;
    private Sensor accelerometer = null;
    private Sensor gyroscope = null;
    private boolean slowdownGPS = true;
    private SortedMap<Long, Float> speeds = new TreeMap<Long, Float>();

    public AutoTrackerIntentService() {
        super("AutoTrackerIntentService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        Log.i(LOG_TAG, "Auto tracker service started.");

        //GPS and Sensors init
        LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        accelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        gyroscope = mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);

        //Check is GPS, sensors are available
        if( lm.isProviderEnabled(LocationManager.GPS_PROVIDER) && (accelerometer != null)
                && (gyroscope != null) ){

            mGoogleApiClient = new GoogleApiClient.Builder(this)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .addApi(LocationServices.API)
                    .build();
            //GPS listener will be registered on connection
            mGoogleApiClient.connect();

            while(runService) {
                //Sleep for a frequency
                try {
                    Thread.sleep(AUTO_FREQUENCY_MILLIS);
                } catch (Exception e) {
                    Log.e(LOG_TAG, "Interrupted while sleeping : " + e);
                }
                if(isMoving())
                    startRecording();
                else {
                    ConnectivityManager cm = (ConnectivityManager) this.getSystemService(Context.CONNECTIVITY_SERVICE);
                    NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
                    boolean networkAvailable = activeNetwork != null &&
                            activeNetwork.isConnectedOrConnecting();
                    if(networkAvailable)
                        uploadFiles();
                }
                //Read flag again
                SharedPreferences sharedPref = this.getSharedPreferences(getString(R.string.pref_file_key),
                        Context.MODE_PRIVATE);
                String toggleMode = sharedPref.getString(getString(R.string.toggle_auto_mode_key), null);
                if ((toggleMode != null) && (toggleMode.equals(getString(R.string.toggle_auto_start_button)))) {
                    runService = false;
                }
            }
            //After loop: service stopping
            LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, this);
            mGoogleApiClient.disconnect();
            Log.i(LOG_TAG, "Disconnecting Google API, stopping sensor tracker service.");
        } else {
            Log.i(LOG_TAG, "GPS or Sensors unavailable.");
            //Broadcast to activity that the service stopped due to state issue
            Intent localIntent = new Intent(Constants.BROADCAST_ACTION)
                    .putExtra(Constants.SERVICE_STATUS, Constants.AUTO_TRACKER_STOP_STATUS);
            // Broadcasts the Intent to receivers in this app.
            LocalBroadcastManager.getInstance(this).sendBroadcast(localIntent);
        }
        Log.i(LOG_TAG, "Auto tracker service stopped.");
    }

    private void uploadFiles() {
        //TODO upload files
    }

    private void startRecording(){
        Log.i(LOG_TAG, "Trip start detected!");

        //reconnect for faster GPS updates
        mGoogleApiClient.disconnect();
        slowdownGPS = false;
        mGoogleApiClient.connect();

        boolean record = false;
        //Open file handle
        String tripName = "auto_trip_";
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss");
        String timestamp = sdf.format(new Date());
        tripName = tripName + timestamp;
        try {
            if(isExternalStorageWritable()){
                file = getStorageFile(this, tripName);
            } else {
                file = new File(this.getFilesDir(), tripName);
            }
            if (file != null) {
                outputStream = new FileOutputStream(file);
                record = true;
            }
        } catch (Exception e) {
            Log.e(LOG_TAG, "Error opening file: " + e);
        }
        if(record) {
            //Register sensor listeners
            mSensorManager.registerListener(this, gyroscope, FREQUENCY_MILLIS * 1000);
            mSensorManager.registerListener(this, accelerometer, FREQUENCY_MILLIS * 1000);
        }
        while(record) {
            //Sleep for a frequency
            try {
                Thread.sleep(FREQUENCY_MILLIS);
            } catch (Exception e) {
                Log.e(LOG_TAG, "Interrupted while sleeping : " + e);
            }

            //if fixedFrequencyWrite, write to file.
            if(FIXED_FREQ_WRITE) {
                if(lastLocation != null) {
                    double lat = lastLocation.getLatitude();
                    double lon = lastLocation.getLongitude();
                    //long ts = lastLocation.getTime();
                    long ts = lastLocationTime;
                    Log.i(LOG_TAG, "Location values: " + ts + "|" + lastLocation.getLatitude() + "|"
                            + lastLocation.getLongitude() + "|" + lastLocation.getSpeed());
                    writeToFile("loc|" + String.valueOf(ts) + "|" + String.valueOf(lat) + "|" + String.valueOf(lon)  + "|"
                            + String.valueOf(lastLocation.getSpeed()));
                    lastLocation = null;
                }
                if(lastAccelerometerEvent != null) {
                    float x = lastAccelerometerEvent.values[0];
                    float y = lastAccelerometerEvent.values[1];
                    float z = lastAccelerometerEvent.values[2];
                    //long ts = lastAccelerometerEvent.timestamp;
                    long ts = lastAccelerometerEventTime;
                    Log.i(LOG_TAG, "Accelerometer values: " + ts + "|" + x + "|" + y + "|" + z);
                    writeToFile("acc|" + String.valueOf(ts) + "|" + String.valueOf(x) + "|"
                            + String.valueOf(y) + "|" + String.valueOf(z));
                    lastAccelerometerEvent = null;
                }
                if(lastGyroscopeEvent != null) {
                    float x = lastGyroscopeEvent.values[0];
                    float y = lastGyroscopeEvent.values[1];
                    float z = lastGyroscopeEvent.values[2];
                    //long ts = lastGyroscopeEvent.timestamp;
                    long ts = lastGyroscopeEventTime;
                    Log.i(LOG_TAG, "Gyroscope values: " + ts + "|" + x + "|" + y + "|" + z);
                    writeToFile("gyr|" + String.valueOf(ts) + "|" + String.valueOf(x) + "|"
                            + String.valueOf(y) + "|" + String.valueOf(z));
                    lastGyroscopeEvent = null;
                }
            }
            if(!isMoving())
                record = false;
        }
        //After loop: Unregister listeners, recording complete
        mSensorManager.unregisterListener(this);
        lastAccelerometerEvent = null;
        lastGyroscopeEvent = null;
        //Close outputstream
        if(outputStream != null) {
            if(file != null)
                Log.i(LOG_TAG, "Wrote file of space: " + file.length());
            try {
                outputStream.close();
            } catch (Exception e) {
                Log.e(LOG_TAG, "Error closing file: " + e);
            }
            outputStream = null;
        }
        Log.i(LOG_TAG, "Trip ended!");

        //Reconnect for slower GPS
        mGoogleApiClient.disconnect();
        slowdownGPS = true;
        mGoogleApiClient.connect();
    }

    private boolean isMoving(){
        long timeElapsed = new Date().getTime() - lastLocationTime;
        float sumSpeed = 0;
        for(Long time: speeds.keySet())
            sumSpeed = sumSpeed + speeds.get(time);
        float avgSpeed = sumSpeed / speeds.size();
        if(timeElapsed > NOT_MOVING_ELAPSE_MILLIS)
            return false;
        else if (avgSpeed < NOT_MOVING_AVG_SPEED)
            return false;
        else
            return true;
    }

    @Override
    public void onConnected(Bundle connectionHint) {
        Log.i(LOG_TAG, "Google API connected");
        LocationRequest mLocationRequest = new LocationRequest();
        if(slowdownGPS) {
            mLocationRequest.setInterval(AUTO_FREQUENCY_MILLIS);
            mLocationRequest.setFastestInterval(AUTO_FREQUENCY_MILLIS);
        } else {
            mLocationRequest.setInterval(FREQUENCY_MILLIS);
            mLocationRequest.setFastestInterval(FREQUENCY_MILLIS);
        }
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
        if(event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            if(!FIXED_FREQ_WRITE) {
                float x = event.values[0];
                float y = event.values[1];
                float z = event.values[2];
                //long ts = event.timestamp;
                long ts = new Date().getTime();
                Log.i(LOG_TAG, "Accelerometer values: " + ts + "|" + x + "|" + y + "|" + z);
                writeToFile("acc|" + String.valueOf(ts) + "|" + String.valueOf(x) + "|"
                        + String.valueOf(y) + "|" + String.valueOf(z));
            } else {
                lastAccelerometerEvent = event;
                lastAccelerometerEventTime = new Date().getTime();
            }
        }
        if(event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
            if(!FIXED_FREQ_WRITE) {
                float x = event.values[0];
                float y = event.values[1];
                float z = event.values[2];
                //long ts = event.timestamp;
                long ts = new Date().getTime();
                Log.i(LOG_TAG, "Gyroscope values: " + ts + "|" + x + "|" + y + "|" + z);
                writeToFile("gyr|" + String.valueOf(ts) + "|" + String.valueOf(x) + "|"
                        + String.valueOf(y) + "|" + String.valueOf(z));
            } else {
                lastGyroscopeEvent = event;
                lastGyroscopeEventTime = new Date().getTime();
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        //Do nothing for now
    }

    @Override
    public void onLocationChanged(Location location) {
        double lat = location.getLatitude();
        double lon = location.getLongitude();
        //long ts = location.getTime();
        long ts = new Date().getTime();
        if(!FIXED_FREQ_WRITE) {
            Log.i(LOG_TAG, "Location values: " + ts + "|" + location.getLatitude() + "|" + location.getLongitude()
                    + "|" + location.getSpeed());
            writeToFile("loc|" + String.valueOf(ts) + "|" + String.valueOf(lat) + "|" + String.valueOf(lon) + "|"
                    + String.valueOf(location.getSpeed()));
        } else {
            lastLocation = location;
            lastLocationTime = new Date().getTime();
        }
        //Get current ts
        long currentWindowStart = new Date().getTime() - NOT_MOVING_ELAPSE_MILLIS;
        long firstKey = speeds.firstKey();
        if(firstKey < currentWindowStart){
            //Get a tail map
            Log.i(LOG_TAG, "limiting speeds map withing window");
            SortedMap newSpeeds = speeds.tailMap(currentWindowStart);
            speeds = newSpeeds;
        }
        if(location.getSpeed() < SPEED_NOISE_CUTOFF) {
            speeds.put(ts, location.getSpeed());
            Log.i(LOG_TAG, "Added speed: " + location.getSpeed() + "@" + location.getTime());
        }
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

    private boolean isExternalStorageWritable() {
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state)) {
            return true;
        }
        return false;
    }

    private File getStorageFile(Context context, String name) {
        File file = new File(context.getExternalFilesDir(
                Environment.DIRECTORY_DOCUMENTS), name);
        boolean isFileCreated = false;
        try {
            if (file.createNewFile())
                isFileCreated = true;
            else
                Log.e(LOG_TAG, "Directory not created");
        } catch (IOException e) {
            Log.e(LOG_TAG, "Exception while creating file");
        }
        if (isFileCreated)
            return file;
        else
            return null;
    }
}
