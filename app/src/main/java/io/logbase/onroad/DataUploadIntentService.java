package io.logbase.onroad;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.os.Environment;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.amazonaws.auth.CognitoCachingCredentialsProvider;
import com.amazonaws.mobileconnectors.s3.transfermanager.TransferManager;
import com.amazonaws.mobileconnectors.s3.transfermanager.Upload;
import com.amazonaws.regions.Regions;

import java.io.File;
import java.io.FilenameFilter;


public class DataUploadIntentService extends IntentService {

    private static final String LOG_TAG = "OnRoad Upload Tracker";
    private CognitoCachingCredentialsProvider credentialsProvider = null;
    private static final int UPLOAD_SLEEP_FREQ = 5000;

    public DataUploadIntentService() {
        super("DataUploadIntentService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        Log.i(LOG_TAG, "Data upload service started.");
        // Initialize the Amazon Cognito credentials provider
        credentialsProvider = new CognitoCachingCredentialsProvider(
                this,
                "us-east-1:de6c43db-ed3e-4c40-9c03-f0ba710c669c",
                Regions.US_EAST_1
        );
        TransferManager transferManager = new TransferManager(credentialsProvider);

        File directory = null;
        File[] files = null;
        if(isExternalStorageWritable()) {
            directory = this.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
        } else {
            directory = getFilesDir();
        }
        if(directory.exists()) {
            files = directory.listFiles(new FilenameFilter() {
                @Override
                public boolean accept(File dir, String name) {
                    return name.toLowerCase().endsWith(".zip");
                }
            });
        }
        if (files != null) {
            Log.i(LOG_TAG, "No. of files to upload: " + files.length);
            for(int i=0; i<files.length; i++) {
                Log.i(LOG_TAG, " Uploading file name: " + files[i].getName());
                //TODO compress and upload file
                Upload upload = transferManager.upload(Constants.S3_BUCKET_NAME, files[i].getName(), files[i]);
                //If upload complete, remove the file.
                while(!upload.isDone()) {
                    try {
                        Thread.sleep(UPLOAD_SLEEP_FREQ);
                        Log.i(LOG_TAG, "Waiting for upload to complete...");
                    } catch (Exception e) {
                        Log.e(LOG_TAG, "Interrupted while sleeping : " + e);
                    }
                }
                files[i].delete();
            }
            Log.i(LOG_TAG, "Data upload completed.");
        } else
            Log.i(LOG_TAG, "Nothing to upload");

        //Broadcast to enable button.
        Intent localIntent = new Intent(Constants.BROADCAST_ACTION)
                .putExtra(Constants.SERVICE_STATUS, Constants.DATA_UPLOAD_DONE_STATUS);
        // Broadcasts the Intent to receivers in this app.
        LocalBroadcastManager.getInstance(this).sendBroadcast(localIntent);
    }

    private boolean isExternalStorageWritable() {
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state)) {
            return true;
        }
        return false;
    }

}
