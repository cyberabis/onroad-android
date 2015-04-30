package io.logbase.onroad;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.Date;


public class ControlActivity extends ActionBarActivity {

    private static final String LOG_TAG = "OnRoad Controls";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_control);
        IntentFilter mStatusIntentFilter = new IntentFilter(Constants.BROADCAST_ACTION);
        // Instantiates a new DownloadStateReceiver
        StatusReceiver mStatusReceiver = new StatusReceiver();
        // Registers the DownloadStateReceiver and its intent filters
        LocalBroadcastManager.getInstance(this).registerReceiver(
                mStatusReceiver,
                mStatusIntentFilter);
        //Restore state
        SharedPreferences sharedPref = this.getSharedPreferences(getString(R.string.pref_file_key),
                Context.MODE_PRIVATE);
        String toggleMode = sharedPref.getString(getString(R.string.toggle_mode_key), null);
        Button toggleButton = (Button) findViewById(R.id.toggle_trip);
        EditText editText = (EditText) findViewById(R.id.trip_name);
        if ((toggleMode != null) && (toggleMode.equals(getString(R.string.toggle_trip_stop_button)))) {
            //implies data logging is in progress
            //Get the trip name
            editText.setText(sharedPref.getString(getString(R.string.trip_name), ""));
            editText.setEnabled(false);
            toggleButton.setText(getString(R.string.toggle_trip_stop_button));
        }

    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_control, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        Log.i(LOG_TAG, "Destroying OnRoad App...");

        //Reset state keys
        SharedPreferences sharedPref = this.getSharedPreferences(getString(R.string.pref_file_key),
                Context.MODE_PRIVATE);
        String toggleMode = sharedPref.getString(getString(R.string.toggle_mode_key), null);
        if(toggleMode!= null) {
            SharedPreferences.Editor editor = sharedPref.edit();
            editor.remove(getString(R.string.toggle_mode_key));
        }
    }

    public void toggleTrip(View view) {
        SharedPreferences sharedPref = this.getSharedPreferences(getString(R.string.pref_file_key),
                Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        String toggleMode = sharedPref.getString(getString(R.string.toggle_mode_key), null);
        Button toggleButton = (Button) findViewById(R.id.toggle_trip);
        EditText editText = (EditText) findViewById(R.id.trip_name);

        Log.i(LOG_TAG, "Toggle mode: " + toggleMode);

        if ((toggleMode != null) && (toggleMode.equals(getString(R.string.toggle_trip_stop_button)))) {
            //Stop, next state is start
            editor.putString(getString(R.string.toggle_mode_key), getString(R.string.toggle_trip_start_button));
            editor.remove(getString(R.string.trip_name));
            editor.commit();
            editText.setEnabled(true);
            toggleButton.setText(getString(R.string.toggle_trip_start_button));
            editText.setText("");
        } else {
            //Start, next state is stop
            //Save toggle mode
            editor.putString(getString(R.string.toggle_mode_key), getString(R.string.toggle_trip_stop_button));
            //Save trip name in shared pref
            editor.putString(getString(R.string.trip_name), editText.getText().toString());
            editor.commit();
            editText.setEnabled(false);
            toggleButton.setText(getString(R.string.toggle_trip_stop_button));
            Intent sensorTrackerIntent = new Intent(this, SensorTrackerIntentService.class);
            String tripName = "events_";
            if ((editText.getText() != null) && (!editText.getText().toString().equals("")))
                tripName = tripName + editText.getText().toString() + "_";
            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss");
            String timestamp = sdf.format(new Date());
            tripName = tripName + timestamp;
            Log.i(LOG_TAG, "Trip Name: " + tripName);
            sensorTrackerIntent.putExtra(Constants.TRIP_NAME_EXTRA, tripName);
            startService(sensorTrackerIntent);
        }
    }

    public class StatusReceiver extends BroadcastReceiver {

        private static final String LOG_TAG = "OnRoad Status Receiver";

        // Called when the BroadcastReceiver gets an Intent it's registered to receive
        public void onReceive(Context context, Intent intent) {
            String status = intent.getExtras().getString(Constants.SERVICE_STATUS);
            Log.i(LOG_TAG, "Received status: " + status);
            if((status != null)&&(status.equals(Constants.SENSOR_TRACKER_STOP_STATUS))){
                SharedPreferences sharedPref = context.getSharedPreferences(getString(R.string.pref_file_key),
                        Context.MODE_PRIVATE);
                Button toggleButton = (Button) findViewById(R.id.toggle_trip);
                EditText editText = (EditText) findViewById(R.id.trip_name);
                SharedPreferences.Editor editor = sharedPref.edit();
                editor.putString(getString(R.string.toggle_mode_key), getString(R.string.toggle_trip_start_button));
                editor.commit();
                editText.setEnabled(true);
                toggleButton.setText(getString(R.string.toggle_trip_start_button));
                AlertDialog.Builder alertDialog = new AlertDialog.Builder(ControlActivity.this);
                alertDialog.setTitle(getString(R.string.app_name));
                alertDialog.setMessage(getString(R.string.sensor_alert));
                alertDialog.setNeutralButton("OK", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        // Do nothing
                    }
                });
                alertDialog.show();
            } else if((status != null)&&(status.equals(Constants.DATA_UPLOAD_DONE_STATUS))){
                Button syncButton = (Button) findViewById(R.id.sync_trip);
                syncButton.setText(R.string.sync_button);
                syncButton.setEnabled(true);
            }
        }
    }

    public void upload(View view) {
        boolean uploadReady = true;
        //Check if trip is in progress, and internet is available
        SharedPreferences sharedPref = this.getSharedPreferences(getString(R.string.pref_file_key),
                Context.MODE_PRIVATE);
        String toggleMode = sharedPref.getString(getString(R.string.toggle_mode_key), null);
        if ((toggleMode != null) && (toggleMode.equals(getString(R.string.toggle_trip_stop_button))))
            uploadReady = false;
        if(uploadReady) {
            ConnectivityManager cm = (ConnectivityManager) this.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
            uploadReady = activeNetwork != null &&
                    activeNetwork.isConnectedOrConnecting();
        }
        if(uploadReady) {
            //Start new background thread
            //Change button msg and enabled state until upload completes
            Log.i(LOG_TAG, "Going to start upload");
            Intent dataUploadIntent = new Intent(this, DataUploadIntentService.class);
            startService(dataUploadIntent);
            Button syncButton = (Button) findViewById(R.id.sync_trip);
            syncButton.setText(R.string.sync_progress_msg);
            syncButton.setEnabled(false);
        } else {
            Log.i(LOG_TAG, "Not ready for upload");
            AlertDialog.Builder alertDialog = new AlertDialog.Builder(ControlActivity.this);
            alertDialog.setTitle(getString(R.string.app_name));
            alertDialog.setMessage(getString(R.string.upload_alert));
            alertDialog.setNeutralButton("OK", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    // Do nothing
                }
            });
            alertDialog.show();
        }
    }

}
