package io.logbase.onroad;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

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

        while(runService) {

            //Read sensor data


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
