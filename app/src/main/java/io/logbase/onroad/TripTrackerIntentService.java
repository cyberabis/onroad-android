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
import android.os.Environment;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.firebase.client.Firebase;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.common.api.GoogleApiClient.ConnectionCallbacks;
import com.google.android.gms.common.api.GoogleApiClient.OnConnectionFailedListener;
import com.google.gson.Gson;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Date;

import io.logbase.onroad.models.AccelerometerEvent;
import io.logbase.onroad.models.GyroscopeEvent;
import io.logbase.onroad.models.LinearAccelerometerEvent;
import io.logbase.onroad.models.LocationEvent;
import io.logbase.onroad.models.MagneticFieldEvent;
import io.logbase.onroad.models.OrientationEvent;
import io.logbase.onroad.utils.ZipUtils;

public class TripTrackerIntentService extends IntentService implements
        ConnectionCallbacks, OnConnectionFailedListener, SensorEventListener, LocationListener {

    private static final String LOG_TAG = "OnRoad Trip Tracker";
    private static final int FREQUENCY_MILLIS = 100;
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
    private String userId = null;
    private String tripName = null;
    private static final boolean USE_FIREBASE = false;
    private static final String FIREBASE_URL = "https://glaring-torch-2138.firebaseio.com/events";
    private Firebase firebaseRef = null;
    private SensorEvent lastLinearAccelerometerEvent = null;
    private long lastLinearAccelerometerEventTime = 0;
    private SensorEvent lastOrientationEvent = null;
    private long lastOrientationEventTime = 0;
    private SensorEvent lastMagneticFieldEvent = null;
    private long lastMagneticFieldEventTime = 0;

    public TripTrackerIntentService() {
        super("TripTrackerIntentService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        Log.i(LOG_TAG, "Trip tracker service started.");

        //GPS and Sensors
        LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
        SensorManager mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        Sensor accelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        Sensor gyroscope = mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        Sensor linearAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
        Sensor orientation = mSensorManager.getDefaultSensor(Sensor.TYPE_ORIENTATION);
        Sensor magneticField = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);

        //Check is GPS, sensors are available
        if( lm.isProviderEnabled(LocationManager.GPS_PROVIDER) && (accelerometer != null)
                && (gyroscope != null) ){
            SharedPreferences sharedPref = this.getSharedPreferences(getString(R.string.pref_file_key),
                    Context.MODE_PRIVATE);
            userId = sharedPref.getString(getString(R.string.username_key), null);
            //Open file handle
            tripName = intent.getStringExtra(Constants.TRIP_NAME_EXTRA);
            try {
                //outputStream = openFileOutput(tripName, Context.MODE_APPEND);
                //Use external storage is available
                if(isExternalStorageWritable()){
                    file = getStorageFile(this, tripName);
                } else {
                    file = new File(this.getFilesDir(), tripName);
                }
                if (file != null) {
                    outputStream = new FileOutputStream(file);
                    runService = true;
                }
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
            if(linearAccelerometer != null)
                mSensorManager.registerListener(this, linearAccelerometer, FREQUENCY_MILLIS * 1000);
            if(orientation != null)
                mSensorManager.registerListener(this, orientation, FREQUENCY_MILLIS * 1000);
            if(magneticField != null)
                mSensorManager.registerListener(this, magneticField, FREQUENCY_MILLIS * 1000);

            //Emit events to firebase
            if(USE_FIREBASE) {
                Firebase.setAndroidContext(this);
                firebaseRef = new Firebase(FIREBASE_URL);
            }

            while(runService) {
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
                        long ts = lastLocationTime;
                        double speed = lastLocation.getSpeed();
                        LocationEvent le = new LocationEvent(Constants.LOCATION_EVENT_TYPE, ts, userId, tripName, lat, lon, speed);
                        Gson gson = new Gson();
                        String json = gson.toJson(le);
                        writeToFile(json);
                        lastLocation = null;
                    }
                    if(lastAccelerometerEvent != null) {
                        float x = lastAccelerometerEvent.values[0];
                        float y = lastAccelerometerEvent.values[1];
                        float z = lastAccelerometerEvent.values[2];
                        long ts = lastAccelerometerEventTime;
                        AccelerometerEvent ae = new AccelerometerEvent(Constants.ACCELEROMETER_EVENT_TYPE, ts, userId, tripName, x, y, z);
                        Gson gson = new Gson();
                        String json = gson.toJson(ae);
                        writeToFile(json);
                        lastAccelerometerEvent = null;
                    }
                    if(lastGyroscopeEvent != null) {
                        float x = lastGyroscopeEvent.values[0];
                        float y = lastGyroscopeEvent.values[1];
                        float z = lastGyroscopeEvent.values[2];
                        long ts = lastGyroscopeEventTime;
                        GyroscopeEvent ge = new GyroscopeEvent(Constants.GYROSCOPE_EVENT_TYPE, ts, userId, tripName, x, y, z);
                        Gson gson = new Gson();
                        String json = gson.toJson(ge);
                        writeToFile(json);
                        lastGyroscopeEvent = null;
                    }
                    if(lastLinearAccelerometerEvent != null) {
                        float x = lastLinearAccelerometerEvent.values[0];
                        float y = lastLinearAccelerometerEvent.values[1];
                        float z = lastLinearAccelerometerEvent.values[2];
                        long ts = lastLinearAccelerometerEventTime;
                        LinearAccelerometerEvent la = new LinearAccelerometerEvent(Constants.LINEAR_ACCELEROMETER_EVENT_TYPE,
                                ts, userId, tripName, x, y, z);
                        Gson gson = new Gson();
                        String json = gson.toJson(la);
                        writeToFile(json);
                        lastLinearAccelerometerEvent = null;
                    }
                    if(lastOrientationEvent != null) {
                        float azimuthAngle = lastOrientationEvent.values[0];
                        float pitchAngle = lastOrientationEvent.values[1];
                        float rollAngle = lastOrientationEvent.values[2];
                        long ts = lastLinearAccelerometerEventTime;
                        OrientationEvent oe = new OrientationEvent(Constants.ORIENTATION_EVENT_TYPE,
                                ts, userId, tripName, azimuthAngle, pitchAngle, rollAngle);
                        Gson gson = new Gson();
                        String json = gson.toJson(oe);
                        writeToFile(json);
                        lastOrientationEvent = null;
                    }
                    if(lastMagneticFieldEvent != null) {
                        float x = lastMagneticFieldEvent.values[0];
                        float y = lastMagneticFieldEvent.values[1];
                        float z = lastMagneticFieldEvent.values[2];
                        long ts = lastMagneticFieldEventTime;
                        MagneticFieldEvent mf = new MagneticFieldEvent(Constants.MAGNETIC_FIELD_EVENT_TYPE,
                                ts, userId, tripName, x, y, z);
                        Gson gson = new Gson();
                        String json = gson.toJson(mf);
                        writeToFile(json);
                        lastMagneticFieldEvent = null;
                    }
                }

                //Read flag again
                String toggleMode = sharedPref.getString(getString(R.string.toggle_trip_mode_key), null);
                if ((toggleMode != null) && (toggleMode.equals(getString(R.string.toggle_trip_start_button)))) {
                    runService = false;
                }
            }
            //After loop: Unregister listeners, recording complete
            LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, this);
            mSensorManager.unregisterListener(this);
            mGoogleApiClient.disconnect();
            //Close outputstream
            if(outputStream != null) {
                if(file != null) {
                    String filePath = file.getPath();
                    Log.i(LOG_TAG, "Wrote file of space: " + file.length() + " for: " + filePath);
                    ZipUtils.zipFile(file, filePath + ".zip");
                    file.delete();
                }
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
                    .putExtra(Constants.SERVICE_STATUS, Constants.TRIP_TRACKER_STOP_STATUS);
            // Broadcasts the Intent to receivers in this app.
            LocalBroadcastManager.getInstance(this).sendBroadcast(localIntent);
        }
        Log.i(LOG_TAG, "Trip tracker service stopped.");
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
        if(event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            if(!FIXED_FREQ_WRITE) {
                float x = event.values[0];
                float y = event.values[1];
                float z = event.values[2];
                long ts = new Date().getTime();
                AccelerometerEvent ae = new AccelerometerEvent(Constants.ACCELEROMETER_EVENT_TYPE, ts, userId, tripName, x, y, z);
                Gson gson = new Gson();
                String json = gson.toJson(ae);
                writeToFile(json);
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
                long ts = new Date().getTime();
                GyroscopeEvent ge = new GyroscopeEvent(Constants.GYROSCOPE_EVENT_TYPE, ts, userId, tripName, x, y, z);
                Gson gson = new Gson();
                String json = gson.toJson(ge);
                writeToFile(json);
            } else {
                lastGyroscopeEvent = event;
                lastGyroscopeEventTime = new Date().getTime();
            }
        }
        if(event.sensor.getType() == Sensor.TYPE_LINEAR_ACCELERATION) {
            if(!FIXED_FREQ_WRITE) {
                float x = event.values[0];
                float y = event.values[1];
                float z = event.values[2];
                long ts = new Date().getTime();
                LinearAccelerometerEvent la = new LinearAccelerometerEvent(Constants.LINEAR_ACCELEROMETER_EVENT_TYPE, ts, userId, tripName, x, y, z);
                Gson gson = new Gson();
                String json = gson.toJson(la);
                writeToFile(json);
            } else {
                lastLinearAccelerometerEvent = event;
                lastLinearAccelerometerEventTime = new Date().getTime();
            }
        }
        if(event.sensor.getType() == Sensor.TYPE_ORIENTATION) {
            if(!FIXED_FREQ_WRITE) {
                float azimuthAngle = event.values[0];
                float pitchAngle = event.values[1];
                float rollAngle = event.values[2];
                long ts = new Date().getTime();
                OrientationEvent oe = new OrientationEvent(Constants.ORIENTATION_EVENT_TYPE,
                        ts, userId, tripName, azimuthAngle, pitchAngle, rollAngle);
                Gson gson = new Gson();
                String json = gson.toJson(oe);
                writeToFile(json);
            } else {
                lastOrientationEvent = event;
                lastOrientationEventTime = new Date().getTime();
            }
        }
        if(event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
            if(!FIXED_FREQ_WRITE) {
                float x = event.values[0];
                float y = event.values[1];
                float z = event.values[2];
                long ts = new Date().getTime();
                MagneticFieldEvent mf = new MagneticFieldEvent(Constants.MAGNETIC_FIELD_EVENT_TYPE,
                        ts, userId, tripName, x, y, z);
                Gson gson = new Gson();
                String json = gson.toJson(mf);
                writeToFile(json);
            } else {
                lastMagneticFieldEvent = event;
                lastMagneticFieldEventTime = new Date().getTime();
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
        double speed = location.getSpeed();
        long ts = (new Date()).getTime();
        if(!FIXED_FREQ_WRITE) {
            LocationEvent le = new LocationEvent(Constants.LOCATION_EVENT_TYPE, ts, userId, tripName, lat, lon, speed);
            Gson gson = new Gson();
            String json = gson.toJson(le);
            writeToFile(json);
        } else {
            lastLocation = location;
            lastLocationTime = new Date().getTime();
        }
    }

    private void writeToFile(String data){
        Log.i(LOG_TAG, "Going to write: " + data);
        if(outputStream != null) {
            try {
                outputStream.write(data.getBytes());
                outputStream.write("\n".getBytes());
            } catch (Exception e) {
                Log.e(LOG_TAG, "Error while writing file: " + e);
            }
        }
        if(USE_FIREBASE)
            firebaseRef.push().setValue(data);
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
