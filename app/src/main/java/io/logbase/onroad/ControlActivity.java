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
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.util.Log;
import android.widget.ScrollView;
import android.widget.ToggleButton;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;


public class ControlActivity extends ActionBarActivity implements RecognitionListener {

    private static final String LOG_TAG = "OnRoad Controls";
    private ToggleButton toggleSpeechRec;
    private SpeechRecognizer speech = null;
    private Intent recognizerIntent;
    private static final int SPEECH_REC_SLEEP = 1000;

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
        String tripToggleMode = sharedPref.getString(getString(R.string.toggle_trip_mode_key), null);
        String autoToggleMode = sharedPref.getString(getString(R.string.toggle_auto_mode_key), null);
        String uploadStatus = sharedPref.getString(getString(R.string.upload_status_key), null);

        if ((tripToggleMode != null) && (tripToggleMode.equals(getString(R.string.toggle_trip_stop_button)))) {
            //implies data logging is in progress
            Button tripToggleButton = (Button) findViewById(R.id.toggle_trip);
            EditText editText = (EditText) findViewById(R.id.trip_name);
            editText.setText(sharedPref.getString(getString(R.string.trip_name), ""));
            editText.setEnabled(false);
            tripToggleButton.setText(getString(R.string.toggle_trip_stop_button));
        }
        if ((autoToggleMode != null) && (autoToggleMode.equals(getString(R.string.toggle_auto_stop_button)))) {
            //implies auto data logging is in progress
            Button autoToggleButton = (Button) findViewById(R.id.toggle_auto);
            autoToggleButton.setText(getString(R.string.toggle_auto_stop_button));
        }
        if(uploadStatus != null) {
            //implies uploading in progress
            Button syncButton = (Button) findViewById(R.id.sync_trip);
            syncButton.setText(R.string.upload_stop_button);
            syncButton.setEnabled(false);
        }

        //Speech Recognition
        //TODO Probably need to move this to resume method?
        toggleSpeechRec = (ToggleButton) findViewById(R.id.toggleSpeechRec);
        speech = SpeechRecognizer.createSpeechRecognizer(this);
        speech.setRecognitionListener(this);
        recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "en");
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, this.getPackageName());
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_WEB_SEARCH);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);

        toggleSpeechRec.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView,
                                         boolean isChecked) {
                if (isChecked) {
                    speech.startListening(recognizerIntent);
                    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                } else {
                    speech.stopListening();
                    getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                }
            }
        });

        //Hide keyboard from Scroll View
        getWindow().setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
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
        String tripToggleMode = sharedPref.getString(getString(R.string.toggle_trip_mode_key), null);
        String autoToggleMode = sharedPref.getString(getString(R.string.toggle_auto_mode_key), null);
        String uploadStatus = sharedPref.getString(getString(R.string.upload_status_key), null);
        SharedPreferences.Editor editor = sharedPref.edit();
        if(tripToggleMode!= null)
            editor.remove(getString(R.string.toggle_trip_mode_key));
        if(autoToggleMode!= null)
            editor.remove(getString(R.string.toggle_auto_mode_key));
        if(uploadStatus != null)
            editor.remove(getString(R.string.upload_status_key));
    }

    public void toggleTrip(View view) {
        SharedPreferences sharedPref = this.getSharedPreferences(getString(R.string.pref_file_key),
                Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        String toggleMode = sharedPref.getString(getString(R.string.toggle_trip_mode_key), null);
        Button toggleButton = (Button) findViewById(R.id.toggle_trip);
        EditText editText = (EditText) findViewById(R.id.trip_name);

        Log.i(LOG_TAG, "Trip Toggle mode: " + toggleMode);

        if ((toggleMode != null) && (toggleMode.equals(getString(R.string.toggle_trip_stop_button)))) {
            //Stop, next state is start
            editor.putString(getString(R.string.toggle_trip_mode_key), getString(R.string.toggle_trip_start_button));
            editor.remove(getString(R.string.trip_name));
            editor.commit();
            editText.setEnabled(true);
            toggleButton.setText(getString(R.string.toggle_trip_start_button));
            editText.setText("");
        } else {
            String autoMode = sharedPref.getString(getString(R.string.toggle_auto_mode_key), null);
            //if auto mode running, cannot start trip.
            if( (autoMode != null) && (autoMode.equals(getString(R.string.toggle_auto_stop_button))) ){
                Log.i(LOG_TAG, "Cannot start trip as auto mode is running.");
            } else {
                //Start, next state is stop
                //Save toggle mode
                editor.putString(getString(R.string.toggle_trip_mode_key), getString(R.string.toggle_trip_stop_button));
                //Save trip name in shared pref
                editor.putString(getString(R.string.trip_name), editText.getText().toString());
                editor.commit();
                editText.setEnabled(false);
                toggleButton.setText(getString(R.string.toggle_trip_stop_button));
                Intent tripTrackerIntent = new Intent(this, TripTrackerIntentService.class);
                String userId = sharedPref.getString(getString(R.string.username_key), null);
                String tripName = userId + "_trip_";
                if ((editText.getText() != null) && (!editText.getText().toString().equals("")))
                    tripName = tripName + editText.getText().toString() + "_";
                SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss");
                String timestamp = sdf.format(new Date());
                tripName = tripName + timestamp;
                Log.i(LOG_TAG, "Trip Name: " + tripName);
                tripTrackerIntent.putExtra(Constants.TRIP_NAME_EXTRA, tripName);
                startService(tripTrackerIntent);
            }
        }
    }

    public void toggleAuto(View view) {
        SharedPreferences sharedPref = this.getSharedPreferences(getString(R.string.pref_file_key),
                Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        String toggleMode = sharedPref.getString(getString(R.string.auto_mode_key), null);
        Button toggleButton = (Button) findViewById(R.id.toggle_auto);

        Log.i(LOG_TAG, "Auto Toggle mode: " + toggleMode);

        if ((toggleMode != null) && (toggleMode.equals(getString(R.string.toggle_auto_stop_button)))) {
            //Stop, next state is start
            editor.putString(getString(R.string.toggle_auto_mode_key), getString(R.string.toggle_auto_start_button));
            editor.commit();
            toggleButton.setText(getString(R.string.toggle_auto_start_button));
        } else {
            //if tripmode running cannot start auto
            String tripMode = sharedPref.getString(getString(R.string.toggle_trip_mode_key), null);
            if( (tripMode != null) && (tripMode.equals(getString(R.string.toggle_trip_stop_button))) ) {
                Log.i(LOG_TAG, "Cannot start auto as trip is running");
            } else {
                //Start, next state is stop
                //Save toggle mode
                editor.putString(getString(R.string.toggle_auto_mode_key), getString(R.string.toggle_auto_stop_button));
                editor.commit();
                toggleButton.setText(getString(R.string.toggle_auto_stop_button));
                //Start AutoIntentService
                Intent autoTrackerIntent = new Intent(this, AutoTrackerIntentService.class);
                startService(autoTrackerIntent);
            }
        }
    }

    public class StatusReceiver extends BroadcastReceiver {

        private static final String LOG_TAG = "OnRoad Status Receiver";

        // Called when the BroadcastReceiver gets an Intent it's registered to receive
        public void onReceive(Context context, Intent intent) {
            String status = intent.getExtras().getString(Constants.SERVICE_STATUS);
            Log.i(LOG_TAG, "Received status: " + status);
            if((status != null)&&(status.equals(Constants.TRIP_TRACKER_STOP_STATUS))){
                SharedPreferences sharedPref = context.getSharedPreferences(getString(R.string.pref_file_key),
                        Context.MODE_PRIVATE);
                Button toggleButton = (Button) findViewById(R.id.toggle_trip);
                EditText editText = (EditText) findViewById(R.id.trip_name);
                SharedPreferences.Editor editor = sharedPref.edit();
                editor.putString(getString(R.string.toggle_trip_mode_key), getString(R.string.toggle_trip_start_button));
                editor.commit();
                editText.setEnabled(true);
                toggleButton.setText(getString(R.string.toggle_trip_start_button));
                AlertDialog.Builder alertDialog = new AlertDialog.Builder(ControlActivity.this);
                alertDialog.setTitle(getString(R.string.app_name));
                alertDialog.setMessage(getString(R.string.trip_alert));
                alertDialog.setNeutralButton("OK", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        // Do nothing
                    }
                });
                alertDialog.show();
            } else if((status != null)&&(status.equals(Constants.DATA_UPLOAD_DONE_STATUS))){
                SharedPreferences sharedPref = context.getSharedPreferences(getString(R.string.pref_file_key),
                        Context.MODE_PRIVATE);
                SharedPreferences.Editor editor = sharedPref.edit();
                editor.remove(getString(R.string.upload_status_key));
                editor.commit();
                Button syncButton = (Button) findViewById(R.id.sync_trip);
                syncButton.setText(R.string.upload_start_button);
                syncButton.setEnabled(true);
            } else if((status != null)&&(status.equals(Constants.AUTO_TRACKER_STOP_STATUS))){
                SharedPreferences sharedPref = context.getSharedPreferences(getString(R.string.pref_file_key),
                        Context.MODE_PRIVATE);
                Button toggleButton = (Button) findViewById(R.id.toggle_auto);
                SharedPreferences.Editor editor = sharedPref.edit();
                editor.putString(getString(R.string.toggle_auto_mode_key), getString(R.string.toggle_auto_start_button));
                editor.commit();
                toggleButton.setText(getString(R.string.toggle_auto_start_button));
                AlertDialog.Builder alertDialog = new AlertDialog.Builder(ControlActivity.this);
                alertDialog.setTitle(getString(R.string.app_name));
                alertDialog.setMessage(getString(R.string.auto_alert));
                alertDialog.setNeutralButton("OK", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        // Do nothing
                    }
                });
                alertDialog.show();
            }
        }
    }

    public void upload(View view) {
        boolean uploadReady = true;
        //Check if trip is in progress, and internet is available
        SharedPreferences sharedPref = this.getSharedPreferences(getString(R.string.pref_file_key),
                Context.MODE_PRIVATE);
        String toggleMode = sharedPref.getString(getString(R.string.toggle_trip_mode_key), null);
        if ((toggleMode != null) && (toggleMode.equals(getString(R.string.toggle_trip_stop_button))))
            uploadReady = false;
        //not ready for upload if auto mode running
        String autoMode = sharedPref.getString(getString(R.string.toggle_auto_mode_key), null);
        if( (autoMode != null) && (autoMode.equals(getString(R.string.toggle_auto_stop_button))) )
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
            syncButton.setText(R.string.upload_stop_button);
            syncButton.setEnabled(false);
            //Set state in shared pref
            SharedPreferences.Editor editor = sharedPref.edit();
            editor.putString(getString(R.string.upload_status_key), getString(R.string.upload_start_button));
            editor.commit();
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

    public void speedBump(View view) {
        //TODO
        SharedPreferences sharedPref = this.getSharedPreferences(getString(R.string.pref_file_key),
                Context.MODE_PRIVATE);
        String tripMode = sharedPref.getString(getString(R.string.toggle_trip_mode_key), null);
        if( (tripMode != null) && (tripMode.equals(getString(R.string.toggle_trip_stop_button))) ) {

        } else {
            Log.i(LOG_TAG, "Cannot start auto as trip is not running");
        }
    }

    public void pothole(View view) {
        //TODO
    }

    public void harshAcc(View view) {
        //TODO
    }

    public void harshBrk(View view) {
        //TODO
    }

    public void harshTurn(View view) {
        //TODO
    }

    //For Speech recognition:
    @Override
    protected void onPause() {
        super.onPause();
        if (speech != null) {
            speech.destroy();
            Log.i(LOG_TAG, "Destroyed speech");
        }
    }

    @Override
    public void onBeginningOfSpeech() {
        Log.i(LOG_TAG, "onBeginningOfSpeech");
    }

    @Override
    public void onBufferReceived(byte[] buffer) {
        Log.i(LOG_TAG, "onBufferReceived: " + buffer);
    }

    @Override
    public void onEndOfSpeech() {
        Log.i(LOG_TAG, "onEndOfSpeech");
        toggleSpeechRec.setChecked(false);
    }

    @Override
    public void onError(int errorCode) {
        String errorMessage = getErrorText(errorCode);
        Log.d(LOG_TAG, "FAILED " + errorMessage);
        toggleSpeechRec.setChecked(false);
        if(errorCode == SpeechRecognizer.ERROR_CLIENT){
            //take a break and start again
            try {
                Thread.sleep(SPEECH_REC_SLEEP);
                Log.i(LOG_TAG, "Waiting for Recognition service to complete...");
            } catch (Exception e) {
                Log.e(LOG_TAG, "Interrupted while sleeping : " + e);
            }
            toggleSpeechRec.setChecked(true);
        }
    }

    @Override
    public void onEvent(int arg0, Bundle arg1) {
        Log.i(LOG_TAG, "onEvent");
    }

    @Override
    public void onPartialResults(Bundle arg0) {
        Log.i(LOG_TAG, "onPartialResults");
    }

    @Override
    public void onReadyForSpeech(Bundle arg0) {
        Log.i(LOG_TAG, "onReadyForSpeech");
    }

    @Override
    public void onResults(Bundle results) {
        Log.i(LOG_TAG, "onResults");
        ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        String text = "";
        for (String result : matches)
            text += result + "\n";
        Log.i(LOG_TAG, "Returned text: " + text);
        //Matched
        //TODO Process and continue listening
        //take a break and start again
        try {
            Thread.sleep(SPEECH_REC_SLEEP);
            Log.i(LOG_TAG, "Waiting for Recognition service to complete...");
        } catch (Exception e) {
            Log.e(LOG_TAG, "Interrupted while sleeping : " + e);
        }
        toggleSpeechRec.setChecked(true);

    }

    @Override
    public void onRmsChanged(float rmsdB) {
        Log.i(LOG_TAG, "onRmsChanged: " + rmsdB);
    }

    public static String getErrorText(int errorCode) {
        String message;
        switch (errorCode) {
            case SpeechRecognizer.ERROR_AUDIO:
                message = "Audio recording error";
                break;
            case SpeechRecognizer.ERROR_CLIENT:
                message = "Client side error";
                break;
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                message = "Insufficient permissions";
                break;
            case SpeechRecognizer.ERROR_NETWORK:
                message = "Network error";
                break;
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                message = "Network timeout";
                break;
            case SpeechRecognizer.ERROR_NO_MATCH:
                message = "No match";
                break;
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                message = "RecognitionService busy";
                break;
            case SpeechRecognizer.ERROR_SERVER:
                message = "error from server";
                break;
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                message = "No speech input";
                break;
            default:
                message = "Didn't understand, please try again.";
                break;
        }
        return message;
    }

}
