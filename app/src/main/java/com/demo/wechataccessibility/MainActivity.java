package com.demo.wechataccessibility;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.provider.Settings;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.EditText;

public class MainActivity extends AppCompatActivity {
    private EditText editText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        editText = (EditText) findViewById(R.id.edit_text);
        findViewById(R.id.button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String s = editText.getText().toString();
                if (!s.isEmpty()) {
                    long timeOut = Long.valueOf(s);
                    saveTimeOut(timeOut);
                }
                Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                startActivity(intent);
            }
        });
    }

    private void saveTimeOut(final long timeOut) {
        if (timeOut > 0) {
            SharedPreferences.Editor editor = getSharedPreferences("timeOut", MODE_PRIVATE).edit();
            editor.putLong("timeOut", timeOut);
            editor.apply();
        }
    }
}
