package com.tivisync;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class SetupActivity extends Activity {

    private EditText etUrl;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

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

        TextView sub = new TextView(this);
        sub.setText("Enter your TiviSync server URL including this device's name:");
        sub.setTextSize(14);
        sub.setTextColor(0xFFAAAAAA);
        sub.setPadding(0, 0, 0, 20);
        layout.addView(sub);

        etUrl = new EditText(this);
        etUrl.setHint("http://192.168.x.x:5005/devicename");
        etUrl.setTextColor(0xFFFFFFFF);
        etUrl.setHintTextColor(0xFF666666);
        etUrl.setBackgroundColor(0xFF2D2D44);
        etUrl.setPadding(20, 20, 20, 20);
        etUrl.setTextSize(15);
        layout.addView(etUrl);

        TextView note = new TextView(this);
        note.setText("The device name must match this device's backup subfolder on your share (e.g. office, familyroom, bedroom).");
        note.setTextSize(12);
        note.setTextColor(0xFF888888);
        note.setPadding(0, 12, 0, 0);
        layout.addView(note);

        Button btn = new Button(this);
        btn.setText("Save");
        btn.setTextSize(16);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
        p.setMargins(0, 40, 0, 0);
        btn.setLayoutParams(p);
        btn.setOnClickListener(v -> save());
        layout.addView(btn);

        scroll.addView(layout);
        setContentView(scroll);
        getWindow().getDecorView().setBackgroundColor(0xFF1A1A2E);
    }

    private void save() {
        String url = etUrl.getText().toString().trim();
        if (url.isEmpty()) {
            Toast.makeText(this, "Enter server URL", Toast.LENGTH_SHORT).show();
            return;
        }
        getSharedPreferences("tivisync_prefs", MODE_PRIVATE)
            .edit()
            .putString("server_url", url)
            .putBoolean("setup_done", true)
            .apply();
        Toast.makeText(this, "Saved!", Toast.LENGTH_SHORT).show();
        finish();
    }
}
