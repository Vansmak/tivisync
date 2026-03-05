package com.tivisync;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.StrictMode;
import android.util.Log;
import androidx.core.content.FileProvider;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class MainActivity extends Activity {

    private static final String TAG = "TiviSync";
    private static final String TIVIMATE_PACKAGE = "ar.tvplayer.tv";
    static final String PREF_SERVER_URL = "server_url";
    static final String PREF_LAST_RESTORED = "last_restored";
    static final String PREF_SETUP_DONE = "setup_done";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder().permitAll().build());

        SharedPreferences prefs = getSharedPreferences("tivisync_prefs", MODE_PRIVATE);

        if (!prefs.getBoolean(PREF_SETUP_DONE, false)) {
            startActivity(new Intent(this, SetupActivity.class));
            finish();
            return;
        }

        try {
            String serverUrl = prefs.getString(PREF_SERVER_URL, "");
            String lastRestored = prefs.getString(PREF_LAST_RESTORED, "");
            String tiviVersion = getTiviMateVersion();

            String syncUrl = serverUrl + "?version=" + tiviVersion + "&last=" + lastRestored + "&type=tmb";
            URL url = new URL(syncUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setInstanceFollowRedirects(false);

            int status = conn.getResponseCode();

            if (status == 302 || status == 301) {
                Intent launch = getPackageManager().getLaunchIntentForPackage(TIVIMATE_PACKAGE);
                if (launch != null) startActivity(launch);
            } else if (status == 200) {
                String disposition = conn.getHeaderField("Content-Disposition");
                String filename = "tivisync_backup.tmb";
                if (disposition != null && disposition.contains("filename=")) {
                    filename = disposition.replaceAll(".*filename=", "").replace("\"", "").trim();
                }

                File localFile = new File(getCacheDir(), filename);
                try (InputStream in = conn.getInputStream();
                     FileOutputStream out = new FileOutputStream(localFile)) {
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
                }

                prefs.edit().putString(PREF_LAST_RESTORED, filename).apply();

                Uri fileUri = FileProvider.getUriForFile(this, getPackageName() + ".provider", localFile);
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setDataAndType(fileUri, "application/octet-stream");
                intent.setPackage(TIVIMATE_PACKAGE);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            }
        } catch (Exception e) {
            Log.e(TAG, "Sync error: " + e.getMessage(), e);
        }

        finish();
    }

    private String getTiviMateVersion() {
        try {
            return getPackageManager().getPackageInfo(TIVIMATE_PACKAGE, 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return "5.2.0";
        }
    }
}
