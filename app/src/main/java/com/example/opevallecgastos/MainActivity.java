package com.example.opevallecgastos;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.provider.MediaStore;
import android.util.Log;
import android.view.KeyEvent;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class MainActivity extends AppCompatActivity {
    private WebView webView;
    private String urlWebApp = "";
    private static String file_type = "*/*";
    //private static String file_type = "image/jpeg, image/png, application/pdf";
    private String cam_file_data = null;
    private ValueCallback<Uri> file_data;
    private ValueCallback<Uri[]> file_path;
    private final static int file_req_code = 1;

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);
        if (Build.VERSION.SDK_INT >= 21) {
            Uri[] results = null;

            /*-- if file request cancelled; exited camera. we need to send null value to make future attempts workable --*/
            if (resultCode == Activity.RESULT_CANCELED) {
                file_path.onReceiveValue(null);
                return;
            }

            /*-- continue if response is positive --*/
            if (resultCode == Activity.RESULT_OK) {
                if (null == file_path) {
                    return;
                }
                ClipData clipData;
                String stringData;

                try {
                    clipData = intent.getClipData();
                    stringData = intent.getDataString();
                } catch (Exception e) {
                    clipData = null;
                    stringData = null;
                }
                if (clipData == null && stringData == null && cam_file_data != null) {
                    results = new Uri[]{Uri.parse(cam_file_data)};
                } else {
                    if (clipData != null) {
                        final int numSelectedFiles = clipData.getItemCount();
                        results = new Uri[numSelectedFiles];
                        for (int i = 0; i < clipData.getItemCount(); i++) {
                            results[i] = clipData.getItemAt(i).getUri();
                        }
                    } else {
                        try {
                            Bitmap cam_photo = (Bitmap) intent.getExtras().get("data");
                            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                            cam_photo.compress(Bitmap.CompressFormat.JPEG, 100, bytes);
                            stringData = MediaStore.Images.Media.insertImage(this.getContentResolver(), cam_photo, null, null);
                        } catch (Exception ignored) {
                        }

                        results = new Uri[]{Uri.parse(stringData)};
                    }
                }
            }

            file_path.onReceiveValue(results);
            file_path = null;
        } else {
            if (requestCode == file_req_code) {
                if (null == file_data) return;
                Uri result = intent == null || resultCode != RESULT_OK ? null : intent.getData();
                file_data.onReceiveValue(result);
                file_data = null;
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        urlWebApp = getResources().getString(R.string.urlWebApp);
        webView = (WebView) findViewById(R.id.webPage);
        ConnectivityManager cm = (ConnectivityManager) this.getSystemService(Context.CONNECTIVITY_SERVICE);
        boolean isConnected = isNetworkAvaliable(MainActivity.this);

        if (isConnected) {
            webView.getSettings().setJavaScriptEnabled(true);
            webView.getSettings().setDomStorageEnabled(true);
            webView.getSettings().setSupportZoom(true);
            webView.getSettings().setBuiltInZoomControls(true);
            webView.getSettings().setDisplayZoomControls(false);
            webView.setOverScrollMode(WebView.OVER_SCROLL_NEVER);
            webView.clearHistory();

//            String pdf = "https://tistoragedatalake.blob.core.windows.net/aldisenosee/20523146158-01-F001-144.pdf";
//            webView.loadUrl("https://drive.google.com/viewerng/viewer?embedded=true&url=" + pdf);

            webView.loadUrl(urlWebApp);
            webView.setWebViewClient(new WebViewClient());
            webView.setWebChromeClient(new WebChromeClient());
            webView.setWebChromeClient(new WebChromeClient() {

                /*-- handling input[type="file"] requests for android API 21+ --*/
                public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {

                    if (file_permission() && Build.VERSION.SDK_INT >= 21) {
                        file_path = filePathCallback;
                        Intent takePictureIntent = null;
                        Intent takeVideoIntent = null;

                        boolean includeVideo = false;
                        boolean includePhoto = false;

                        /*-- checking the accept parameter to determine which intent(s) to include --*/

                        paramCheck:
                        for (String acceptTypes : fileChooserParams.getAcceptTypes()) {
                            String[] splitTypes = acceptTypes.split(", ?+");
                            /*-- although it's an array, it still seems to be the whole value; split it out into chunks so that we can detect multiple values --*/
                            for (String acceptType : splitTypes) {
                                switch (acceptType) {
                                    case "*/*":
                                        includePhoto = true;
                                        includeVideo = true;
                                        break paramCheck;
                                    case "image/*":
                                        includePhoto = true;
                                        break;
                                    case "video/*":
                                        includeVideo = true;
                                        break;
                                }
                            }
                        }

                        if (fileChooserParams.getAcceptTypes().length == 0) {
                            /*-- no `accept` parameter was specified, allow both photo and video --*/
                            includePhoto = true;
                            includeVideo = true;
                        }

                        if (includePhoto) {
                            takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                            if (takePictureIntent.resolveActivity(MainActivity.this.getPackageManager()) != null) {
                                File photoFile = null;
                                try {
                                    photoFile = create_image();
                                    takePictureIntent.putExtra("PhotoPath", cam_file_data);
                                } catch (IOException ex) {
                                    Log.e("TAG", "Image file creation failed", ex);
                                }
                                if (photoFile != null) {
                                    cam_file_data = "file:" + photoFile.getAbsolutePath();
                                    takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(photoFile));
                                } else {
                                    cam_file_data = null;
                                    takePictureIntent = null;
                                }
                            }
                        }

                        if (includeVideo) {
                            takeVideoIntent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
                            if (takeVideoIntent.resolveActivity(MainActivity.this.getPackageManager()) != null) {
                                File videoFile = null;
                                try {
                                    videoFile = create_video();
                                } catch (IOException ex) {
                                    Log.e("TAG", "Video file creation failed", ex);
                                }
                                if (videoFile != null) {
                                    cam_file_data = "file:" + videoFile.getAbsolutePath();
                                    takeVideoIntent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(videoFile));
                                } else {
                                    cam_file_data = null;
                                    takeVideoIntent = null;
                                }
                            }
                        }

                        Intent contentSelectionIntent = new Intent(Intent.ACTION_GET_CONTENT);
                        contentSelectionIntent.addCategory(Intent.CATEGORY_OPENABLE);
                        contentSelectionIntent.setType(file_type);


                        Intent[] intentArray;
                        if (takePictureIntent != null && takeVideoIntent != null) {
                            intentArray = new Intent[]{takePictureIntent, takeVideoIntent};
                        } else if (takePictureIntent != null) {
                            intentArray = new Intent[]{takePictureIntent};
                        } else if (takeVideoIntent != null) {
                            intentArray = new Intent[]{takeVideoIntent};
                        } else {
                            intentArray = new Intent[0];
                        }

                        Intent chooserIntent = new Intent(Intent.ACTION_CHOOSER);
                        chooserIntent.putExtra(Intent.EXTRA_INTENT, contentSelectionIntent);
                        chooserIntent.putExtra(Intent.EXTRA_TITLE, "File chooser");
                        chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, intentArray);
                        startActivityForResult(chooserIntent, file_req_code);
                        return true;
                    } else {
                        return false;
                    }
                }
            });

            webView.setWebViewClient(new WebViewClient() {
                @Override
                public boolean shouldOverrideUrlLoading(WebView view, String url) {
                    if (url != null) {
                        //Log.e("urlaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", url);

                        String urlPathPDF = getResources().getString(R.string.urlPathPDF);
                        if (url.contains(urlPathPDF)) {
                            Intent intent = new Intent(MainActivity.this, ViewPDFActivity.class);
                            //String pdf = "https://tistoragedatalake.blob.core.windows.net/aldisenosee/20523146158-01-F001-144.pdf";
                            intent.putExtra("urlPDF", url);
                            startActivity(intent);
                        } else {
                            webView.loadUrl(url);
                        }
                    } else {
                        return false;
                    }

                    return true;
                }

                @Override
                public void onPageFinished(WebView view, String url) {
                    super.onPageFinished(view, url);
                }

                @Override
                public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                    Toast.makeText(MainActivity.this, "Error: " + description, Toast.LENGTH_SHORT).show();
                }

            });

        } else {
            Toast.makeText(MainActivity.this, "Error de red!. Por favor, revise su conexión a internet y vuelva a ingresar.", Toast.LENGTH_SHORT).show();
            Handler handler = new Handler();
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    finish();
                }
            }, 1000);
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    public boolean file_permission() {
        if (Build.VERSION.SDK_INT >= 23 && (ContextCompat.checkSelfPermission(this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)) {
            ActivityCompat.requestPermissions(MainActivity.this, new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.CAMERA}, 1);
            return false;
        } else {
            return true;
        }
    }

    private File create_image() throws IOException {
        @SuppressLint("SimpleDateFormat") String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = "img_" + timeStamp + "_";
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        return File.createTempFile(imageFileName, ".jpg", storageDir);
    }

    private File create_video() throws IOException {
        @SuppressLint("SimpleDateFormat")
        String file_name = new SimpleDateFormat("yyyy_mm_ss").format(new Date());
        String new_name = "file_" + file_name + "_";
        File sd_directory = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        return File.createTempFile(new_name, ".3gp", sd_directory);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // Check if the key event was the Back button and if there's history
        if ((keyCode == KeyEvent.KEYCODE_BACK) && webView.canGoBack()) {
            webView.goBack();
            return true;
        }
        // If it wasn't the Back key or there's no web page history, bubble up to the default
        // system behavior (probably exit the activity)
        return super.onKeyDown(keyCode, event);
    }

    public static boolean isNetworkAvaliable(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        boolean result = false;
        if (activeNetwork != null) {
            if (activeNetwork.getType() == ConnectivityManager.TYPE_WIFI || activeNetwork.getType() == ConnectivityManager.TYPE_MOBILE) {
                result = true;
            }
        }
        return result;
    }

}