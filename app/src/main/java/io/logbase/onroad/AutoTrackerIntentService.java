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
import android.os.PowerManager;

import com.amazonaws.auth.CognitoCachingCredentialsProvider;
import com.amazonaws.mobileconnectors.s3.transfermanager.Transfer;
import com.amazonaws.mobileconnectors.s3.transfermanager.TransferManager;
import com.amazonaws.mobileconnectors.s3.transfermanager.Upload;
import com.amazonaws.regions.Regions;
import com.firebase.client.Firebase;
import com.flurry.android.FlurryAgent;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.GoogleApiClient.ConnectionCallbacks;
import com.google.android.gms.common.api.GoogleApiClient.OnConnectionFailedListener;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.gson.Gson;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.SortedMap;
import java.util.TreeMap;

import io.logbase.onroad.models.AccelerometerEvent;
import io.logbase.onroad.models.GyroscopeEvent;
import io.logbase.onroad.models.LinearAccelerometerEvent;
import io.logbase.onroad.models.LocationEvent;
import io.logbase.onroad.models.MagneticFieldEvent;
import io.logbase.onroad.models.OrientationEvent;
import io.logbase.onroad.utils.ZipUtils;

public class AutoTrackerIntentService extends IntentService implements
        ConnectionCallbacks, OnConnectionFailedListener, SensorEventListener, LocationListener {

    private static final String LOG_TAG = "OnRoad Auto Tracker";
    private static final int AUTO_FREQUENCY_MILLIS = 10 * 1000;
    private static final int FREQUENCY_MILLIS = 100;
    private static final long NOT_MOVING_ELAPSE_MILLIS =  15 * 1000;
    private static final float NOT_MOVING_AVG_SPEED = 1.6f;
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
    private Upload upload = null;
    private File uploadFile = null;
    private String userId = null;
    private String tripName = null;
    private static final boolean USE_FIREBASE = false;
    private static final String FIREBASE_URL = "https://glaring-torch-2138.firebaseio.com/events";
    private Firebase firebaseRef = null;
    Sensor linearAccelerometer = null;
    Sensor orientation = null;
    Sensor magneticField = null;
    private SensorEvent lastLinearAccelerometerEvent = null;
    private long lastLinearAccelerometerEventTime = 0;
    private SensorEvent lastOrientationEvent = null;
    private long lastOrientationEventTime = 0;
    private SensorEvent lastMagneticFieldEvent = null;
    private long lastMagneticFieldEventTime = 0;
    private android.os.PowerManager.WakeLock wakeLock = null;
    private static final String MY_FLURRY_APIKEY = "B7DM3GDQ5GKYSGJC96RY";
    private static final long FLURRY_EXPIRE_MILLIS = 5 * 1000;
    private static final String AUTO_MODE_STARTED = "AutoModeStarted";
    private static final String AUTO_MODE_ENDED = "AutoModeEnded";
    private static final String AUTO_MODE_EXITED = "AutoModeExited";
    private static final String RECORDING_START = "RecordingStart";
    private static final String RECORDING_END = "RecordingEnd";
    private static final String UPLOAD_IN_PROGRESS = "UploadInProgress";
    private static final String STARTED_UPLOAD = "StartedUpload";
    private static final String UPLOAD_COMPLETE = "UploadComplete";
    private static final String UPLOAD_ERROR = "UploadError";
    private static final String RECORD_ERROR = "RecordError";
    private static final String GOOGLE_API_DISCONNECTED = "GoogleAPIDisconnected";
    private static final boolean FLURRY_ENABLED = false;
    private static final boolean LOG_ENABLED = false;

    public AutoTrackerIntentService() {
        super("AutoTrackerIntentService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {

        if(LOG_ENABLED)
            Log.i(LOG_TAG, "Auto tracker service started.");

        SharedPreferences sharedPref = this.getSharedPreferences(getString(R.string.pref_file_key),
                Context.MODE_PRIVATE);
        userId = sharedPref.getString(getString(R.string.username_key), null);

        if(FLURRY_ENABLED) {
            FlurryAgent.setUserId(userId);
            FlurryAgent.init(this, MY_FLURRY_APIKEY);
            FlurryAgent.setContinueSessionMillis(FLURRY_EXPIRE_MILLIS);

            FlurryAgent.onStartSession(this);
            FlurryAgent.logEvent(AUTO_MODE_STARTED);
            FlurryAgent.onEndSession(this);
        }

        //GPS and Sensors init
        LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        accelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        gyroscope = mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        linearAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
        orientation = mSensorManager.getDefaultSensor(Sensor.TYPE_ORIENTATION);
        magneticField = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);

        //Check is GPS, sensors are available
        if( lm.isProviderEnabled(LocationManager.GPS_PROVIDER) && (accelerometer != null)
                && (gyroscope != null) ){

            //Acquire wake lock:
            PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "OnRoadWakelock");
            wakeLock.acquire();

            mGoogleApiClient = new GoogleApiClient.Builder(this)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .addApi(LocationServices.API)
                    .build();
            //GPS listener will be registered on connection
            mGoogleApiClient.connect();

            //Emit events to firebase
            if(USE_FIREBASE) {
                Firebase.setAndroidContext(this);
                firebaseRef = new Firebase(FIREBASE_URL);
            }

            while(runService) {

                //Sleep for a frequency
                try {
                    Thread.sleep(AUTO_FREQUENCY_MILLIS);
                } catch (Exception e) {
                    if(LOG_ENABLED)
                        Log.e(LOG_TAG, "Interrupted while sleeping : " + e);
                }

                //Start flurry session
                if(FLURRY_ENABLED)
                    FlurryAgent.onStartSession(this);

                if(isMoving())
                    try {
                        startRecording();
                    } catch (Exception e) {
                        if(LOG_ENABLED)
                            Log.e(LOG_TAG, "Exception while recording.");
                        if(FLURRY_ENABLED)
                            FlurryAgent.logEvent(RECORD_ERROR);
                    }
                else {
                    try {
                        ConnectivityManager cm = (ConnectivityManager) this.getSystemService(Context.CONNECTIVITY_SERVICE);
                        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
                        boolean networkAvailable = activeNetwork != null &&
                                activeNetwork.isConnectedOrConnecting();
                        if (networkAvailable)
                            uploadFiles();
                    } catch(Exception e) {
                        if(LOG_ENABLED)
                            Log.e(LOG_TAG, "Exception in upload loop.");
                        if(FLURRY_ENABLED)
                            FlurryAgent.logEvent(UPLOAD_ERROR);
                    }
                }
                //Read flag again
                String toggleMode = sharedPref.getString(getString(R.string.toggle_auto_mode_key), null);
                if ((toggleMode != null) && (toggleMode.equals(getString(R.string.toggle_auto_start_button)))) {
                    runService = false;
                }
                //Stop flurry session
                if(FLURRY_ENABLED)
                    FlurryAgent.onEndSession(this);
            }
            //After loop: service stopping
            LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, this);
            mGoogleApiClient.disconnect();
            if(LOG_ENABLED)
                Log.i(LOG_TAG, "Disconnecting Google API, stopping sensor tracker service.");
            if(FLURRY_ENABLED) {
                FlurryAgent.onStartSession(this);
                FlurryAgent.logEvent(AUTO_MODE_ENDED);
                FlurryAgent.onEndSession(this);
            }
            //Release wakelock
            wakeLock.release();
        } else {
            if(LOG_ENABLED)
                Log.i(LOG_TAG, "GPS or Sensors unavailable.");
            if(FLURRY_ENABLED) {
                FlurryAgent.onStartSession(this);
                FlurryAgent.logEvent(AUTO_MODE_EXITED);
                FlurryAgent.onEndSession(this);
            }
            //Broadcast to activity that the service stopped due to state issue
            Intent localIntent = new Intent(Constants.BROADCAST_ACTION)
                    .putExtra(Constants.SERVICE_STATUS, Constants.AUTO_TRACKER_STOP_STATUS);
            // Broadcasts the Intent to receivers in this app.
            LocalBroadcastManager.getInstance(this).sendBroadcast(localIntent);
        }
        if(LOG_ENABLED)
            Log.i(LOG_TAG, "Auto tracker service stopped.");
    }

    private void uploadFiles() {
        if (upload == null) {
            uploadAFile();
        } else if (upload.isDone()) {
            // Remove uploaded file and start next upload
            uploadFile.delete();
            upload = null;
            if(LOG_ENABLED)
                Log.i(LOG_TAG, "Upload completed. Removed last uploaded file");
            if(FLURRY_ENABLED)
                FlurryAgent.logEvent(UPLOAD_COMPLETE);
            uploadAFile();
        } else {
            //Check if any error while upload, then cancel the upload and restart.
            if(upload.getState().compareTo(Transfer.TransferState.Failed) == 0) {
                if(FLURRY_ENABLED)
                    FlurryAgent.logEvent(UPLOAD_ERROR);
                upload.abort();
                upload = null;
            } else {
                //DO nothing as upload is in progress
                if(LOG_ENABLED)
                    Log.i(LOG_TAG, "Upload in progress");
                if(FLURRY_ENABLED)
                    FlurryAgent.logEvent(UPLOAD_IN_PROGRESS);
            }
        }
    }

    private void uploadAFile() {
        File directory = null;
        File[] files = null;
        if (isExternalStorageWritable()) {
            directory = this.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
        } else {
            directory = getFilesDir();
        }
        if (directory.exists()) {
            //Check for unzipped files
            File[] unzippedFiles = directory.listFiles(new FilenameFilter() {
                @Override
                public boolean accept(File dir, String name) {
                    return name.toLowerCase().endsWith("_auto");
                }
            });
            //Zip the files and delete original
            if ( (unzippedFiles != null) && (unzippedFiles.length > 0) ) {
                if(LOG_ENABLED)
                    Log.i(LOG_TAG, "Found unzipped file(s), going to zip.");
                for(int i=0; i<unzippedFiles.length; i++) {
                    ZipUtils.zipFile(unzippedFiles[i], unzippedFiles[i].getPath() + ".zip");
                    unzippedFiles[i].delete();
                }
            }
            //Check for zip files
            files = directory.listFiles(new FilenameFilter() {
                @Override
                public boolean accept(File dir, String name) {
                    return name.toLowerCase().endsWith("_auto.zip");
                }
            });
        }

        if ( (files != null) && (files.length > 0) ) {
            CognitoCachingCredentialsProvider credentialsProvider = new CognitoCachingCredentialsProvider(
                    this,
                    "us-east-1:de6c43db-ed3e-4c40-9c03-f0ba710c669c",
                    Regions.US_EAST_1
            );
            TransferManager transferManager = new TransferManager(credentialsProvider);
            if(LOG_ENABLED)
                Log.i(LOG_TAG, "No. of files to upload: " + files.length);
            uploadFile = files[0];
            upload = transferManager.upload(Constants.S3_BUCKET_NAME, uploadFile.getName(), uploadFile);
            if(FLURRY_ENABLED)
                FlurryAgent.logEvent(STARTED_UPLOAD);
        } else {
            if(LOG_ENABLED)
                Log.i(LOG_TAG, "Nothing to upload");
        }
    }

    private void startRecording() throws Exception {
        if(LOG_ENABLED)
            Log.i(LOG_TAG, "Trip start detected!");
        if(FLURRY_ENABLED)
            FlurryAgent.logEvent(RECORDING_START);

        //reconnect for faster GPS updates
        mGoogleApiClient.disconnect();
        slowdownGPS = false;
        mGoogleApiClient.connect();

        boolean record = false;
        //Open file handle
        tripName = userId + "_trip_";
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss");
        String timestamp = sdf.format(new Date());
        tripName = tripName + timestamp + "_auto";
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
            if(LOG_ENABLED)
                Log.e(LOG_TAG, "Error opening file: " + e);
        }
        if(record) {
            //Register sensor listeners
            mSensorManager.registerListener(this, gyroscope, FREQUENCY_MILLIS * 1000);
            mSensorManager.registerListener(this, accelerometer, FREQUENCY_MILLIS * 1000);
            if(linearAccelerometer != null)
                mSensorManager.registerListener(this, linearAccelerometer, FREQUENCY_MILLIS * 1000);
            if(orientation != null)
                mSensorManager.registerListener(this, orientation, FREQUENCY_MILLIS * 1000);
            if(magneticField != null)
                mSensorManager.registerListener(this, magneticField, FREQUENCY_MILLIS * 1000);
        }
        while(record) {
            //Sleep for a frequency
            try {
                Thread.sleep(FREQUENCY_MILLIS);
            } catch (Exception e) {
                if(LOG_ENABLED)
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
            SharedPreferences sharedPref = this.getSharedPreferences(getString(R.string.pref_file_key),
                    Context.MODE_PRIVATE);
            String toggleMode = sharedPref.getString(getString(R.string.toggle_auto_mode_key), null);
            if ((toggleMode != null) && (toggleMode.equals(getString(R.string.toggle_auto_start_button)))) {
                record = false;
            }
            if((record)&&(!isMoving()))
                record = false;
        }

        lastAccelerometerEvent = null;
        lastGyroscopeEvent = null;

        //Close outputstream
        if(outputStream != null) {
            String filePath = file.getPath();
            if(LOG_ENABLED)
                Log.i(LOG_TAG, "Wrote file of space: " + file.length() + " for: " + filePath);
            try {
                ZipUtils.zipFile(file, filePath + ".zip");
                file.delete();
                outputStream.close();
            } catch (Exception e) {
                if(LOG_ENABLED)
                    Log.e(LOG_TAG, "Error closing file: " + e);
            }
            outputStream = null;
        }
        if(LOG_ENABLED)
            Log.i(LOG_TAG, "Trip ended!");

        //After loop: Unregister listeners, recording complete
        mSensorManager.unregisterListener(this);
        //Reconnect for slower GPS
        mGoogleApiClient.disconnect();
        slowdownGPS = true;
        mGoogleApiClient.connect();

        if(FLURRY_ENABLED)
            FlurryAgent.logEvent(RECORDING_END);
    }

    private boolean isMoving(){
        long timeElapsed = new Date().getTime() - lastLocationTime;
        float sumSpeed = 0;
        float avgSpeed = 0;
        if(speeds.size() > 0) {
            for (Long time : speeds.keySet())
                sumSpeed = sumSpeed + speeds.get(time);
            avgSpeed = sumSpeed / speeds.size();
        }
        if(timeElapsed > NOT_MOVING_ELAPSE_MILLIS) {
            if(LOG_ENABLED)
                Log.i(LOG_TAG, "Not moving, location unchanged.");
            return false;
        } else if (avgSpeed < NOT_MOVING_AVG_SPEED) {
            if(LOG_ENABLED)
                Log.i(LOG_TAG, "Not moving, avg speed is too low: " + avgSpeed);
            return false;
        } else {
            return true;
        }
    }

    @Override
    public void onConnected(Bundle connectionHint) {
        if(LOG_ENABLED)
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
        if(LOG_ENABLED)
            Log.i(LOG_TAG, "Google API connection suspended");
        if(FLURRY_ENABLED)
            FlurryAgent.logEvent(GOOGLE_API_DISCONNECTED);
        runService = false;
    }

    @Override
    public void onConnectionFailed(ConnectionResult result) {
        // This callback is important for handling errors that
        // may occur while attempting to connect with Google.
        //
        // More about this in the next section.
        if(LOG_ENABLED)
            Log.i(LOG_TAG, "Google API connection failed");
        if(FLURRY_ENABLED)
            FlurryAgent.logEvent(GOOGLE_API_DISCONNECTED);
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
        long ts = new Date().getTime();
        if(!FIXED_FREQ_WRITE) {
            LocationEvent le = new LocationEvent(Constants.LOCATION_EVENT_TYPE, ts, userId, tripName, lat, lon, speed);
            Gson gson = new Gson();
            String json = gson.toJson(le);
            writeToFile(json);
        } else {
            lastLocation = location;
            lastLocationTime = new Date().getTime();
        }
        //For motion detection
        //Get current ts
        long currentWindowStart = new Date().getTime() - NOT_MOVING_ELAPSE_MILLIS;
        if (speeds.size() > 0) {
            long firstKey = speeds.firstKey();
            if(firstKey < currentWindowStart){
                //Get a tail map
                if(LOG_ENABLED)
                    Log.i(LOG_TAG, "limiting speeds map withing window");
                SortedMap newSpeeds = speeds.tailMap(currentWindowStart);
                speeds = newSpeeds;
            }
        }
        if(location.getSpeed() < SPEED_NOISE_CUTOFF) {
            speeds.put(ts, location.getSpeed());
            if(LOG_ENABLED)
                Log.i(LOG_TAG, "Added speed: " + location.getSpeed() + "@" + location.getTime());
        }
    }

    private void writeToFile (String data) {
        if(LOG_ENABLED)
            Log.i(LOG_TAG, "Going to write: " + data);
        if(outputStream != null) {
            try {
                outputStream.write(data.getBytes());
                outputStream.write("\n".getBytes());
            } catch (Exception e) {
                if(LOG_ENABLED)
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
            else {
                if (LOG_ENABLED)
                    Log.e(LOG_TAG, "Directory not created");
            }
        } catch (IOException e) {
            if(LOG_ENABLED)
                Log.e(LOG_TAG, "Exception while creating file");
        }
        if (isFileCreated)
            return file;
        else
            return null;
    }

}
