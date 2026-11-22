package com.emdad.fuel;

import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintManager;
import android.provider.MediaStore;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private long lastBackPressTime = 0;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        webView = new WebView(this);
        setContentView(webView);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);

        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient());
        webView.addJavascriptInterface(new AndroidBridge(this), "AndroidBridge");

        webView.loadUrl("file:///android_asset/www/index.html");

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                webView.evaluateJavascript(
                        "(function(){ return window.handleAndroidBackPress ? window.handleAndroidBackPress() : false; })()",
                        value -> {
                            if ("true".equals(value)) {
                                return;
                            }
                            long now = System.currentTimeMillis();
                            if (now - lastBackPressTime < 2000) {
                                finish();
                            } else {
                                lastBackPressTime = now;
                                Toast.makeText(MainActivity.this, "اضغط رجوع مرة أخرى للخروج من إمداد", Toast.LENGTH_SHORT).show();
                            }
                        }
                );
            }
        });
    }

    public class AndroidBridge {
        private final Context context;

        AndroidBridge(Context ctx) {
            this.context = ctx;
        }

        @JavascriptInterface
        public void saveBase64File(String dataUrl, String fileName, String mimeType) {
            try {
                String cleanBase64 = dataUrl;
                int commaIdx = dataUrl.indexOf(',');
                if (commaIdx != -1) {
                    cleanBase64 = dataUrl.substring(commaIdx + 1);
                }
                byte[] bytes = Base64.decode(cleanBase64, Base64.DEFAULT);
                writeBytesToDownloads(bytes, fileName, mimeType);
                runOnUiThread(() ->
                        Toast.makeText(context, "تم حفظ الملف في التنزيلات: " + fileName, Toast.LENGTH_LONG).show()
                );
            } catch (Exception e) {
                runOnUiThread(() ->
                        Toast.makeText(context, "تعذر حفظ الملف: " + e.getMessage(), Toast.LENGTH_LONG).show()
                );
            }
        }

        @JavascriptInterface
        public void saveTextFile(String content, String fileName, String mimeType) {
            try {
                byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
                writeBytesToDownloads(bytes, fileName, mimeType);
                runOnUiThread(() ->
                        Toast.makeText(context, "تم تصدير الملف في التنزيلات: " + fileName, Toast.LENGTH_LONG).show()
                );
            } catch (Exception e) {
                runOnUiThread(() ->
                        Toast.makeText(context, "تعذر تصدير الملف: " + e.getMessage(), Toast.LENGTH_LONG).show()
                );
            }
        }

        @JavascriptInterface
        public void printPage() {
            runOnUiThread(() -> {
                PrintManager printManager = (PrintManager) getSystemService(Context.PRINT_SERVICE);
                if (printManager != null) {
                    PrintDocumentAdapter adapter = webView.createPrintDocumentAdapter("تقرير_إمداد_المعتمد");
                    PrintAttributes attributes = new PrintAttributes.Builder()
                            .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                            .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                            .build();
                    printManager.print("تقرير إمداد", adapter, attributes);
                }
            });
        }

        private void writeBytesToDownloads(byte[] bytes, String fileName, String mimeType) throws Exception {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentResolver resolver = context.getContentResolver();
                ContentValues values = new ContentValues();
                values.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
                values.put(MediaStore.Downloads.MIME_TYPE, mimeType);
                values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Emdad");
                Uri uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                if (uri == null) {
                    throw new Exception("فشل إنشاء مسار الملف");
                }
                try (OutputStream os = resolver.openOutputStream(uri)) {
                    if (os == null) throw new Exception("تعذر فتح الملف للكتابة");
                    os.write(bytes);
                }
            } else {
                File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Emdad");
                if (!dir.exists()) dir.mkdirs();
                File outFile = new File(dir, fileName);
                try (FileOutputStream fos = new FileOutputStream(outFile)) {
                    fos.write(bytes);
                }
            }
        }
    }
}
