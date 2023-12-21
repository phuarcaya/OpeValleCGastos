package com.example.opevallecgastos;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.appcompat.app.AppCompatActivity;


public class ViewPDFActivity extends AppCompatActivity {

    private WebView webView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_view_pdfactivity);
        webView = (WebView) findViewById(R.id.webPageViewPDF);

        webView.setWebViewClient(new WebViewClient());
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setSupportZoom(true);

        Intent intent = getIntent();
        String urlPDF = "";
        urlPDF = intent.getStringExtra("urlPDF");
        Log.e("urlPDF",urlPDF);
        webView.loadUrl("https://drive.google.com/viewerng/viewer?embedded=true&url=" + urlPDF);

        //webView.loadUrl("https://docs.google.com/gview?embedded=true&url="+urlPDF);

    }
}