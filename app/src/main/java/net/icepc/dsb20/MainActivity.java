package net.icepc.dsb20;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.util.concurrent.ExecutionException;

import android.app.Activity;
import android.content.Intent;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;

import com.googlecode.tesseract.android.TessBaseAPI;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

public class MainActivity extends Activity {
    public static final String PACKAGE_NAME = "net.icepc.dsb20";
    public static final String DATA_PATH = Environment.getExternalStorageDirectory().toString() + "/DSB20/";
    public static final String lang = "deu";
    protected static final String PHOTO_TAKEN = "photo_taken";
    private static final String TAG = "DSB20.java";

    protected Button _button;
    protected ImageView _image;
    protected EditText _field;
    protected String _path;
    protected boolean _taken;


    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.v(TAG, "First Log " + DATA_PATH + " WORKED");
        String[] paths = new String[]{DATA_PATH, DATA_PATH + "tessdata/"};
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        /*
        Implementation for creating a new directory on sd-card
         */

        for (String path : paths) {
            File dir = new File(path);
            if (!dir.exists()) {
                if (!dir.mkdirs()) {
                    Log.v(TAG, "ERROR: Creation of directory " + path + " on sdcard failed");
                    return;
                } else {
                    Log.v(TAG, "Created directory " + path + " on sdcard");
                }
            }

        }

        if (!(new File(DATA_PATH + "tessdata/" + lang + ".traineddata")).exists()) {
            try {

                AssetManager assetManager = getAssets();
                InputStream in = assetManager.open("tessdata/" + lang + ".traineddata");
                OutputStream out = new FileOutputStream(DATA_PATH
                        + "tessdata/" + lang + ".traineddata");

                // Transfer bytes from in to out
                byte[] buf = new byte[1024];
                int len;
                //while ((lenf = gin.read(buff)) > 0) {
                while ((len = in.read(buf)) > 0) {
                    out.write(buf, 0, len);
                }
                in.close();
                //gin.close();
                out.close();

                Log.v(TAG, "Copied " + lang + " traineddata");
            } catch (IOException e) {
                Log.e(TAG, "Was unable to copy " + lang + " traineddata " + e.toString());
            }
        }

        _image = (ImageView) findViewById(R.id.img1);
        _field = (EditText) findViewById(R.id.field);
        _button = (Button) findViewById(R.id.button);
        _button.setOnClickListener(new ButtonClickHandler());

        _path = DATA_PATH + "/ocr.jpg";

    /*
    Call ReturnWebsite and send Data to Server in ASyncTask
    */
        try {
            Log.e(TAG, "Website Text RECEIVED" + new ReturnWebsite().execute().get());
            _field.setText(new ReturnWebsite().execute().get());
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }
    }

    public class ButtonClickHandler implements View.OnClickListener {
        public void onClick(View view) {
            Log.v(TAG, "Starting Camera app");
            startCameraActivity();
        }
    }

    protected void startCameraActivity() {
        File file = new File(_path);
        Uri outputFileUri = Uri.fromFile(file);

        final Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        intent.putExtra(MediaStore.EXTRA_OUTPUT, outputFileUri);

        startActivityForResult(intent, 0);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {

        Log.i(TAG, "resultCode: " + resultCode);

        if (resultCode == -1) {
            onPhotoTaken();
        } else {
            Log.v(TAG, "User cancelled");
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putBoolean(MainActivity.PHOTO_TAKEN, _taken);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        Log.i(TAG, "onRestoreInstanceState()");
        if (savedInstanceState.getBoolean(MainActivity.PHOTO_TAKEN)) {
            onPhotoTaken();
        }
    }

    protected void onPhotoTaken() {
        _taken = true;

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = 4;

        Bitmap bitmap = BitmapFactory.decodeFile(_path, options);
        _image.setImageBitmap(bitmap);

        try {
            ExifInterface exif = new ExifInterface(_path);
            int exifOrientation = exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL);

            Log.v(TAG, "Orient: " + exifOrientation);

            int rotate = 0;

            switch (exifOrientation) {
                case ExifInterface.ORIENTATION_ROTATE_90:
                    rotate = 90;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    rotate = 180;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_270:
                    rotate = 270;
                    break;
            }

            Log.v(TAG, "Rotation: " + rotate);

            if (rotate != 0) {

                int w = bitmap.getWidth();
                int h = bitmap.getHeight();

                Matrix mtx = new Matrix();
                mtx.preRotate(rotate);

                bitmap = Bitmap.createBitmap(bitmap, 0, 0, w, h, mtx, false);
            }

            bitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true);

        } catch (IOException e) {
            Log.e(TAG, "Couldn't correct orientation: " + e.toString());
        }

        Log.v(TAG, "Before baseApi");

        TessBaseAPI baseApi = new TessBaseAPI();
        baseApi.setDebug(true);
        baseApi.init(DATA_PATH, lang);
        baseApi.setImage(bitmap);

        String recognizedText = baseApi.getUTF8Text();

        baseApi.end();

        Log.v(TAG, "OCRED TEXT: " + recognizedText);

        if ( lang.equalsIgnoreCase("eng") ) {
            recognizedText = recognizedText.replaceAll("[^a-zA-Z0-9]+", " ");
        }

        recognizedText = recognizedText.trim();

        if ( recognizedText.length() != 0 ) {
            _field.setText(_field.getText().toString().length() == 0 ? recognizedText : recognizedText);
            _field.setSelection(_field.getText().toString().length());
        }

    }

}

class ReturnWebsite extends AsyncTask<String,String,String>{
    public static final String URL_WEBSITE = "mobile.dsbcontrol.de";
    public static final String URL_PASSWORD = "eintracht";
    public static final String URL_USERNAME = "188261";

    @Override
    protected String doInBackground(String... params) {
        try {
            Document doc = Jsoup.connect(URL_WEBSITE)
                    .data("txtUserName",URL_USERNAME)
                    .data("txtPassword",URL_PASSWORD)
                    .post();
            return doc.toString();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }
}