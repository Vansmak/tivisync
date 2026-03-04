package com.tivisync;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.StrictMode;
import android.util.Log;

import androidx.core.content.FileProvider;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import android.content.SharedPreferences;

import jcifs.CIFSContext;
import jcifs.config.PropertyConfiguration;
import jcifs.context.BaseContext;
import jcifs.smb.NtlmPasswordAuthenticator;
import jcifs.smb.SmbFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

public class MainActivity extends Activity {

    private static final String TAG = "TiviSync";
    private static final String TIVIMATE_PACKAGE = "ar.tvplayer.tv";
    private static final String PREFS_FILE = "tivisync_secure_prefs";

    // Preference keys
    static final String PREF_SMB_HOST = "smb_host";
    static final String PREF_SMB_SHARE = "smb_share";
    static final String PREF_SMB_USER = "smb_user";
    static final String PREF_SMB_PASS = "smb_pass";
    static final String PREF_LAST_RESTORED = "last_restored_filename";
    static final String PREF_SETUP_DONE = "setup_done";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);

        try {
            SharedPreferences prefs = getEncryptedPrefs();

            if (!prefs.getBoolean(PREF_SETUP_DONE, false)) {
                // First run — show setup screen
                Intent setup = new Intent(this, SetupActivity.class);
                startActivity(setup);
                finish();
                return;
            }

            // Already configured — run the sync check
            String host = prefs.getString(PREF_SMB_HOST, "");
            String share = prefs.getString(PREF_SMB_SHARE, "");
            String user = prefs.getString(PREF_SMB_USER, "");
            String pass = prefs.getString(PREF_SMB_PASS, "");

            runSync(prefs, host, share, user, pass);

        } catch (Exception e) {
            Log.e(TAG, "Startup error: " + e.getMessage(), e);
        }

        finish();
    }

    void runSync(SharedPreferences prefs, String host, String share, String user, String pass) {
        String tiviVersion = getTiviMateVersion();
        if (tiviVersion == null) {
            Log.d(TAG, "TiViMate not installed, exiting");
            return;
        }

        String versionPrefix = "v" + tiviVersion.replace(".", "")
                .substring(0, Math.min(3, tiviVersion.replace(".", "").length()));

        Log.d(TAG, "TiViMate version: " + tiviVersion + ", looking for prefix: " + versionPrefix);

        try {
            Properties props = new Properties();
            props.setProperty("jcifs.smb.client.minVersion", "SMB202");
            props.setProperty("jcifs.smb.client.maxVersion", "SMB311");
            CIFSContext baseContext = new BaseContext(new PropertyConfiguration(props));
            NtlmPasswordAuthenticator auth = new NtlmPasswordAuthenticator("", user, pass);
            CIFSContext context = baseContext.withCredentials(auth);

            String smbUrl = "smb://" + host + "/" + share + "/";
            SmbFile shareDir = new SmbFile(smbUrl, context);

            SmbFile[] files = shareDir.listFiles();
            if (files == null || files.length == 0) {
                Log.d(TAG, "No files found on share");
                return;
            }

            List<SmbFile> tmbFiles = new ArrayList<>();
            for (SmbFile f : files) {
                String name = f.getName();
                if (name.endsWith(".tmb") && name.contains(versionPrefix)) {
                    tmbFiles.add(f);
                }
            }

            if (tmbFiles.isEmpty()) {
                Log.d(TAG, "No matching .tmb files for version " + versionPrefix);
                return;
            }

            Collections.sort(tmbFiles, (a, b) -> {
                try { return b.getName().compareTo(a.getName()); }
                catch (Exception e) { return 0; }
            });

            SmbFile newestFile = tmbFiles.get(0);
            String newestName = newestFile.getName();

            String lastRestored = prefs.getString(PREF_LAST_RESTORED, "");
            if (newestName.equals(lastRestored)) {
                Log.d(TAG, "Already restored " + newestName + ", nothing to do");
                return;
            }

            Log.d(TAG, "New backup found: " + newestName);

            File localFile = new File(getCacheDir(), newestName);
            try (InputStream in = newestFile.getInputStream();
                 FileOutputStream out = new FileOutputStream(localFile)) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
            }

            prefs.edit().putString(PREF_LAST_RESTORED, newestName).apply();

            Uri fileUri = FileProvider.getUriForFile(this,
                    getPackageName() + ".provider", localFile);

            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(fileUri, "application/octet-stream");
            intent.setPackage(TIVIMATE_PACKAGE);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            startActivity(intent);

        } catch (Exception e) {
            Log.e(TAG, "Sync error: " + e.getMessage(), e);
        }
    }

    SharedPreferences getEncryptedPrefs() throws Exception {
        MasterKey masterKey = new MasterKey.Builder(this)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build();
        return EncryptedSharedPreferences.create(
                this,
                PREFS_FILE,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        );
    }

    private String getTiviMateVersion() {
        try {
            return getPackageManager()
                    .getPackageInfo(TIVIMATE_PACKAGE, 0)
                    .versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }
}
