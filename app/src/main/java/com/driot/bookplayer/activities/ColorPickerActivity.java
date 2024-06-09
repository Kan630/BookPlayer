package com.driot.bookplayer.activities;

import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import androidx.appcompat.app.AppCompatActivity;

import com.driot.bookplayer.R;

import yuku.ambilwarna.AmbilWarnaDialog;

public class ColorPickerActivity extends AppCompatActivity {

    private View viewColor1;
    private View viewColor2;
    private int currentColor1 = Color.WHITE;
    private int currentColor2 = Color.WHITE;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_color_picker);

        Button buttonPickColor1 = findViewById(R.id.button_pick_color1);
        viewColor1 = findViewById(R.id.view_color1);

        Button buttonPickColor2 = findViewById(R.id.button_pick_color2);
        viewColor2 = findViewById(R.id.view_color2);

        buttonPickColor1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openColorPickerDialog(true);
            }
        });

        buttonPickColor2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openColorPickerDialog(false);
            }
        });
    }

    private void openColorPickerDialog(final boolean isPrimary) {
        int initialColor = isPrimary ? currentColor1 : currentColor2;

        AmbilWarnaDialog colorPickerDialog = new AmbilWarnaDialog(this, initialColor, new AmbilWarnaDialog.OnAmbilWarnaListener() {
            @Override
            public void onOk(AmbilWarnaDialog dialog, int color) {
                if (isPrimary) {
                    currentColor1 = color;
                    viewColor1.setBackgroundColor(currentColor1);
                } else {
                    currentColor2 = color;
                    viewColor2.setBackgroundColor(currentColor2);
                }
            }

            @Override
            public void onCancel(AmbilWarnaDialog dialog) {
                // Do nothing
            }
        });

        colorPickerDialog.show();
    }
}
