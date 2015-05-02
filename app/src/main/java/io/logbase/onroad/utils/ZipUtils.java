package io.logbase.onroad.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import android.util.Log;
/**
 * Created by abishek on 02/05/15.
 */
public class ZipUtils {

    private static final String LOG_TAG = "OnRoad ZipUtils";

    public static void zipFile(File inputFile, String zipFilePath) {
        try {
            // Wrap a FileOutputStream around a ZipOutputStream
            // to store the zip stream to a file. Note that this is
            // not absolutely necessary
            FileOutputStream fileOutputStream = new FileOutputStream(zipFilePath);
            ZipOutputStream zipOutputStream = new ZipOutputStream(fileOutputStream);
            // a ZipEntry represents a file entry in the zip archive
            // We name the ZipEntry after the original file's name
            ZipEntry zipEntry = new ZipEntry(inputFile.getName());
            zipOutputStream.putNextEntry(zipEntry);
            FileInputStream fileInputStream = new FileInputStream(inputFile);
            byte[] buf = new byte[1024];
            int bytesRead;
            // Read the input file by chucks of 1024 bytes
            // and write the read bytes to the zip stream
            while ((bytesRead = fileInputStream.read(buf)) > 0) {
                zipOutputStream.write(buf, 0, bytesRead);
            }
            // close ZipEntry to store the stream to the file
            zipOutputStream.closeEntry();
            zipOutputStream.close();
            fileOutputStream.close();
            Log.i(LOG_TAG, "Regular file :" + inputFile.getCanonicalPath()+" is zipped to :" +zipFilePath);
        } catch (IOException e) {
            Log.e(LOG_TAG, "Error zipping file: " + e);
        }
    }
}
