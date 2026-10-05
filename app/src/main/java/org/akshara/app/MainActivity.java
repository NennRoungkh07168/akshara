package org.akshara.app;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.provider.Settings;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.core.content.FileProvider;
import androidx.webkit.WebViewAssetLoader;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Akshara: hosts the offline web app from app assets in a WebView,
 * served from https://appassets.androidplatform.net so storage and
 * network requests behave like a normal website.
 * Adds camera support for the Scan tab: photo capture through the
 * file chooser, and live camera (getUserMedia) through runtime permission.
 */
public class MainActivity extends Activity {

    private static final String START_URL =
            "https://appassets.androidplatform.net/assets/www/index.html";
    private static final int REQ_FILE = 1;
    private static final int REQ_CAMERA_FOR_WEB = 2;
    private static final int REQ_CAMERA_FOR_CHOOSER = 3;

    private WebView web;
    private ValueCallback<Uri[]> fileCallback;
    private boolean chooserWantsCapture;
    private Uri photoUri;
    private PermissionRequest pendingWebPermission;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        web = new WebView(this);
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(true);

        final WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return loader.shouldInterceptRequest(request.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if ("appassets.androidplatform.net".equals(uri.getHost())) return false;
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, uri));
                } catch (ActivityNotFoundException ignored) { }
                return true;
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback,
                                             FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                chooserWantsCapture = params.isCaptureEnabled();
                if (hasCamera()) {
                    openChooser();
                } else {
                    requestPermissions(new String[]{Manifest.permission.CAMERA}, REQ_CAMERA_FOR_CHOOSER);
                }
                return true;
            }

            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                runOnUiThread(() -> {
                    boolean wantsVideo = false;
                    for (String r : request.getResources()) {
                        if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(r)) wantsVideo = true;
                    }
                    if (!wantsVideo) {
                        request.deny();
                        return;
                    }
                    if (hasCamera()) {
                        request.grant(new String[]{PermissionRequest.RESOURCE_VIDEO_CAPTURE});
                    } else {
                        pendingWebPermission = request;
                        requestPermissions(new String[]{Manifest.permission.CAMERA}, REQ_CAMERA_FOR_WEB);
                    }
                });
            }
        });

        web.addJavascriptInterface(new AndroidBridge(), "AksharaAndroid");

        if (savedInstanceState != null) {
            web.restoreState(savedInstanceState);
        } else {
            web.loadUrl(START_URL);
        }
    }

    /** Lets the web app open keyboard settings and the keyboard picker. */
    public class AndroidBridge {
        @JavascriptInterface
        public void openKeyboardSettings() {
            runOnUiThread(() -> {
                try {
                    startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS));
                } catch (ActivityNotFoundException ignored) { }
            });
        }

        @JavascriptInterface
        public void showKeyboardPicker() {
            runOnUiThread(() -> {
                InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                if (imm != null) imm.showInputMethodPicker();
            });
        }

        /** Fetches text from Aksharamukha's public API only (no other hosts). Runs on the bridge thread. */
        @JavascriptInterface
        public String httpGet(String address) {
            HttpURLConnection c = null;
            try {
                URL url = new URL(address);
                String host = url.getHost();
                if (!"https".equals(url.getProtocol()) || !(host.equals("aksharamukha-plugin.appspot.com")
                        || host.equals("aksharamukha.appspot.com"))) {
                    return "ERROR: host not allowed";
                }
                c = (HttpURLConnection) url.openConnection();
                c.setConnectTimeout(10000);
                c.setReadTimeout(20000);
                int code = c.getResponseCode();
                InputStream in = code < 400 ? c.getInputStream() : c.getErrorStream();
                if (in == null) return "ERROR: HTTP " + code;
                StringBuilder sb = new StringBuilder();
                try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                    String line;
                    boolean first = true;
                    while ((line = r.readLine()) != null) {
                        if (!first) sb.append('\n');
                        sb.append(line);
                        first = false;
                    }
                }
                return code < 400 ? sb.toString() : "ERROR: HTTP " + code;
            } catch (Exception e) {
                return "ERROR: " + e.getMessage();
            } finally {
                if (c != null) c.disconnect();
            }
        }

        @JavascriptInterface
        public boolean isKeyboardEnabled() {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm == null) return false;
            for (InputMethodInfo info : imm.getEnabledInputMethodList()) {
                if (getPackageName().equals(info.getPackageName())) return true;
            }
            return false;
        }
    }

    private boolean hasCamera() {
        return checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    /** Offers the camera (if allowed) plus the gallery / files picker. */
    private void openChooser() {
        Intent pick = new Intent(Intent.ACTION_GET_CONTENT);
        pick.addCategory(Intent.CATEGORY_OPENABLE);
        pick.setType("image/*");

        Intent camera = null;
        photoUri = null;
        if (hasCamera()) {
            try {
                File dir = new File(getCacheDir(), "photos");
                if (!dir.exists()) dir.mkdirs();
                File photo = File.createTempFile("scan_", ".jpg", dir);
                photoUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", photo);
                camera = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                camera.putExtra(MediaStore.EXTRA_OUTPUT, photoUri);
                camera.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (IOException e) {
                camera = null;
                photoUri = null;
            }
        }

        try {
            if (chooserWantsCapture && camera != null) {
                startActivityForResult(camera, REQ_FILE);
            } else {
                Intent chooser = Intent.createChooser(pick, "Choose or take a photo");
                if (camera != null) chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Intent[]{camera});
                startActivityForResult(chooser, REQ_FILE);
            }
        } catch (ActivityNotFoundException e) {
            if (fileCallback != null) fileCallback.onReceiveValue(null);
            fileCallback = null;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_FILE || fileCallback == null) return;
        Uri[] result = null;
        if (resultCode == RESULT_OK) {
            if (data != null && data.getData() != null) {
                result = new Uri[]{data.getData()};
            } else if (photoUri != null) {
                result = new Uri[]{photoUri};
            }
        }
        fileCallback.onReceiveValue(result);
        fileCallback = null;
        photoUri = null;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        boolean granted = results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED;
        if (requestCode == REQ_CAMERA_FOR_WEB && pendingWebPermission != null) {
            if (granted) {
                pendingWebPermission.grant(new String[]{PermissionRequest.RESOURCE_VIDEO_CAPTURE});
            } else {
                pendingWebPermission.deny();
            }
            pendingWebPermission = null;
        } else if (requestCode == REQ_CAMERA_FOR_CHOOSER && fileCallback != null) {
            // Without camera permission the picker still opens for gallery photos.
            openChooser();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        web.saveState(outState);
    }

    @Override
    public void onBackPressed() {
        if (web.canGoBack()) {
            web.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
