package com.tivisync;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class SetupActivity extends Activity {

    private EditText etHost, etShare, etUser, etPass;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Build UI programmatically - no XML layout needed
        ScrollView scroll = new ScrollView(this);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(80, 60, 80, 60);

        TextView title = new TextView(this);
        title.setText("TiviSync Setup");
        title.setTextSize(28);
        title.setTextColor(0xFFFFFFFF);
        title.setPadding(0, 0, 0, 40);
        layout.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Enter your SMB share details. This is saved securely and only needed once.");
        subtitle.setTextSize(14);
        subtitle.setTextColor(0xFFAAAAAA);
        subtitle.setPadding(0, 0, 0, 40);
        layout.addView(subtitle);

        etHost = addField(layout, "Server IP (e.g. 192.168.1.100)");
        etShare = addField(layout, "Share name (e.g. USBShare)");
        etUser = addField(layout, "Username");
        etPass = addField(layout, "Password");
        etPass.setInputType(android.text.InputType.TYPE_CLASS_TEXT |
                android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);

        Button btnSave = new Button(this);
        btnSave.setText("Save & Test Connection");
        btnSave.setTextSize(16);
        btnSave.setPadding(0, 30, 0, 30);
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        btnParams.setMargins(0, 40, 0, 0);
        btnSave.setLayoutParams(btnParams);
        btnSave.setOnClickListener(v -> saveAndTest());
        layout.addView(btnSave);

        scroll.addView(layout);
        setContentView(scroll);

        // Dark background
        getWindow().getDecorView().setBackgroundColor(0xFF1A1A2E);
    }

    private EditText addField(LinearLayout parent, String hint) {
        TextView label = new TextView(this);
        label.setText(hint);
        label.setTextColor(0xFFCCCCCC);
        label.setTextSize(13);
        label.setPadding(0, 20, 0, 6);
        parent.addView(label);

        EditText et = new EditText(this);
        et.setHint(hint);
        et.setTextColor(0xFFFFFFFF);
        et.setHintTextColor(0xFF666666);
        et.setBackgroundColor(0xFF2D2D44);
        et.setPadding(20, 20, 20, 20);
        et.setTextSize(15);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        et.setLayoutParams(params);
        parent.addView(et);
        return et;
    }

    private void saveAndTest() {
        String host = etHost.getText().toString().trim();
        String share = etShare.getText().toString().trim();
        String user = etUser.getText().toString().trim();
        String pass = etPass.getText().toString();

        if (host.isEmpty() || share.isEmpty() || user.isEmpty()) {
            Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            SharedPreferences encPrefs = getEncryptedPrefs();
            encPrefs.edit()
                    .putString(MainActivity.PREF_SMB_HOST, host)
                    .putString(MainActivity.PREF_SMB_SHARE, share)
                    .putString(MainActivity.PREF_SMB_USER, user)
                    .putString(MainActivity.PREF_SMB_PASS, pass)
                    .putBoolean(MainActivity.PREF_SETUP_DONE, true)
                    .apply();

            Toast.makeText(this, "Saved! TiviSync will check for backups on next device wake.", Toast.LENGTH_LONG).show();
            finish();

        } catch (Exception e) {
            Toast.makeText(this, "Error saving: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private SharedPreferences getEncryptedPrefs() throws Exception {
        androidx.security.crypto.MasterKey masterKey =
                new androidx.security.crypto.MasterKey.Builder(this)
                        .setKeyScheme(androidx.security.crypto.MasterKey.KeyScheme.AES256_GCM)
                        .build();
        return androidx.security.crypto.EncryptedSharedPreferences.create(
                this,
                "tivisync_secure_prefs",
                masterKey,
                androidx.security.crypto.EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                androidx.security.crypto.EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        );
    }
}
