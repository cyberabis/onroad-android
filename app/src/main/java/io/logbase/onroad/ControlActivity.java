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
import android.os.Environment;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.google.gson.Gson;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import io.logbase.onroad.models.MagneticFieldEvent;
import io.logbase.onroad.models.TrainingEvent;
import io.logbase.onroad.utils.ZipUtils;


public class ControlActivity extends ActionBarActivity implements RecognitionListener {

    private static final String LOG_TAG = "OnRoad Controls";
    private ToggleButton toggleSpeechRec;
    private ToggleButton toggleTraining;
    private SpeechRecognizer speech = null;
    private Intent recognizerIntent;
    private static final int SPEECH_REC_SLEEP = 1000;
    private String trainingFileName = null;
    private File trainingFile = null;
    private FileOutputStream trainingFileOS = null;
    private String userId;

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
        userId = sharedPref.getString(getString(R.string.username_key), null);

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

        //Hide keyboard from Scroll View
        getWindow().setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);

        //Training start / stop toggle
        toggleTraining = (ToggleButton) findViewById(R.id.toggleTraining);
        toggleTraining.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView,
                                         boolean isChecked) {
                if (isChecked) {
                    //Create a new training file
                    trainingFileName = userId + "_training_";
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss");
                    String timestamp = sdf.format(new Date());
                    trainingFileName = trainingFileName + timestamp;
                    Log.i(LOG_TAG, "Training file name: " + trainingFileName);
                    try {
                        if(isExternalStorageWritable()){
                            trainingFile = getStorageFile(trainingFileName);
                        } else {
                            trainingFile = new File(getFilesDir(), trainingFileName);
                        }
                        if (trainingFile != null) {
                            trainingFileOS = new FileOutputStream(trainingFile);
                        }
                    } catch (Exception e) {
                        trainingFileName = null;
                        Log.e(LOG_TAG, "Error opening file: " + e);
                        toggleTraining.setChecked(false);
                    }
                } else {
                    //Save and zip the file
                    Log.i(LOG_TAG, "Training file name reset to null from: " + trainingFileName);
                    //Close outputstream
                    if(trainingFileOS != null) {
                        String filePath = trainingFile.getPath();
                        Log.i(LOG_TAG, "Wrote training file of space: " + trainingFile.length() + " for: " + filePath);
                        try {
                            ZipUtils.zipFile(trainingFile, filePath + ".zip");
                            trainingFile.delete();
                            trainingFileOS.close();
                        } catch (Exception e) {
                            Log.e(LOG_TAG, "Error closing file: " + e);
                        }
                        trainingFileOS = null;
                    }
                    trainingFileName = null;
                }
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        //Speech Recognition
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
                    if(trainingFileOS != null) {
                        speech.startListening(recognizerIntent);
                        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                    } else {
                        alertForTrainingMode();
                        toggleSpeechRec.setChecked(false);
                    }
                } else {
                    speech.stopListening();
                    getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                }
            }
        });
    }

    private void alertForTrainingMode() {
        AlertDialog.Builder alertDialog = new AlertDialog.Builder(ControlActivity.this);
        alertDialog.setTitle(getString(R.string.app_name));
        alertDialog.setMessage(getString(R.string.training_not_started_alert));
        alertDialog.setNeutralButton("OK", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                // Do nothing
            }
        });
        alertDialog.show();
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

        //If training file is open, then zip it and close
        if(trainingFileOS != null) {
            String filePath = trainingFile.getPath();
            Log.i(LOG_TAG, "Wrote training file of space: " + trainingFile.length() + " for: " + filePath);
            try {
                ZipUtils.zipFile(trainingFile, filePath + ".zip");
                trainingFile.delete();
                trainingFileOS.close();
            } catch (Exception e) {
                Log.e(LOG_TAG, "Error closing file: " + e);
            }
            trainingFileOS = null;
        }
        trainingFileName = null;
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
        writeToTrainingFile("speed_bump");
    }

    public void pothole(View view) {
        writeToTrainingFile("pothole");
    }

    public void harshAcc(View view) {
        writeToTrainingFile("harsh_acceleration");
    }

    public void harshBrk(View view) {
        writeToTrainingFile("harsh_braking");
    }

    public void harshTurn(View view) {
        writeToTrainingFile("harsh_turn");
    }

    private void writeToTrainingFile (String data) {
        if(trainingFileOS != null) {
            try {
                long ts = new Date().getTime();
                TrainingEvent te = new TrainingEvent(Constants.TRAINING_EVENT_TYPE, ts, userId, null, data);
                Gson gson = new Gson();
                String json = gson.toJson(te);
                trainingFileOS.write(json.getBytes());
                trainingFileOS.write("\n".getBytes());
                Toast toast = Toast.makeText(this, data + " recorded", Toast.LENGTH_SHORT);
                //toast.setGravity(Gravity.TOP, 0, 100);
                toast.show();
            } catch (Exception e) {
                Log.e(LOG_TAG, "Error while writing training file: " + e);
            }
        } else {
            alertForTrainingMode();
        }
    }

    //For Speech recognition:
    @Override
    protected void onPause() {
        super.onPause();
        toggleSpeechRec.setChecked(false);
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
       if( (errorCode == SpeechRecognizer.ERROR_NO_MATCH)
                || (errorCode == SpeechRecognizer.ERROR_CLIENT) ){
            speech.cancel();
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
        TextView voiceMatch = (TextView) findViewById(R.id.voice_match);
        voiceMatch.setText(R.string.voice_match_none);
    }

    @Override
    public void onResults(Bundle results) {
        Log.i(LOG_TAG, "onResults");
        List<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        checkVoiceMatch(matches);
        //take a break and start again
        try {
            Thread.sleep(SPEECH_REC_SLEEP);
            Log.i(LOG_TAG, "Waiting for Recognition service to complete...");
        } catch (Exception e) {
            Log.e(LOG_TAG, "Interrupted while sleeping : " + e);
        }
        toggleSpeechRec.setChecked(true);
    }

    private void checkVoiceMatch(List<String> matches) {
        String match = null;
        //Pothole
        List<String> pothole = Arrays.asList("pothole");
        for(String s: matches){
            for(String p: pothole) {
                if(s.equals(p)) {
                    match = "pothole";
                    pothole(null);
                    break;
                }
            }
            if(match != null)
                break;
        }
        //Speed bump
        if(match == null) {
            List<String> speedBump = Arrays.asList("speed bump", "speed breaker");
            for(String s: matches){
                for(String b: speedBump) {
                    if(s.equals(b)) {
                        match = "speed bump";
                        speedBump(null);
                        break;
                    }
                }
                if(match != null)
                    break;
            }
        }
        //Acceleration
        if(match == null) {
            List<String> acceleration = Arrays.asList("acceleration");
            for(String s: matches){
                for(String a: acceleration) {
                    if(s.equals(a)) {
                        match = "acceleration";
                        harshAcc(null);
                        break;
                    }
                }
                if(match != null)
                    break;
            }
        }
        //Braking
        if(match == null) {
            List<String> braking = Arrays.asList("braking", "breaking");
            for(String s: matches){
                for(String b: braking) {
                    if(s.equals(b)) {
                        match = "braking";
                        harshBrk(null);
                        break;
                    }
                }
                if(match != null)
                    break;
            }
        }
        //Turn
        if(match == null) {
            List<String> turn = Arrays.asList("turn");
            for(String s: matches){
                for(String t: turn) {
                    if(s.equals(t)) {
                        match = "turn";
                        harshTurn(null);
                        break;
                    }
                }
                if(match != null)
                    break;
            }
        }
        if(match != null) {
            //Display the match
            Log.i(LOG_TAG, "Matched training command: " + match);
            TextView voiceMatch = (TextView) findViewById(R.id.voice_match);
            voiceMatch.setText(match);
        } else {
            //Only display the top match
            Log.i(LOG_TAG, "Best match: " + matches.get(0));
            TextView voiceMatch = (TextView) findViewById(R.id.voice_match);
            voiceMatch.setText(matches.get(0));
        }
    }

    @Override
    public void onRmsChanged(float rmsdB) {
        //Log.i(LOG_TAG, "onRmsChanged: " + rmsdB);
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

    private boolean isExternalStorageWritable() {
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state)) {
            return true;
        }
        return false;
    }

    private File getStorageFile(String name) {
        File file = new File(getExternalFilesDir(
                Environment.DIRECTORY_DOCUMENTS), name);
        boolean isFileCreated = false;
        try {
            if (file.createNewFile())
                isFileCreated = true;
            else {
                Log.e(LOG_TAG, "Directory not created");
            }
        } catch (IOException e) {
            Log.e(LOG_TAG, "Exception while creating file");
        }
        if (isFileCreated)
            return file;
        else
            return null;
    }
}
