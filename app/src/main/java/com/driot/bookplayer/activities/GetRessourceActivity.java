package com.driot.bookplayer.activities;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import androidx.annotation.Nullable;

import com.driot.bookplayer.R;

/**
 * created by Antoine Driot -- antoine.driot.com -- on 08/11/20
 */
class GetRessourceActivity extends Activity {

    private static final int OPEN_ZIP_FILE_REQUEST_CODE = 24;
    private static final int OPEN_FOLDER_REQUEST_CODE = 25;


    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_getressource);

        Button bOpenFolder = findViewById(R.id.bOpenFolder);
        Button bOpenZipFile = findViewById(R.id.bOpenZipFile);

        bOpenZipFile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                startActivityForResult(intent, OPEN_ZIP_FILE_REQUEST_CODE);
            }
        });
        bOpenFolder.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                startActivityForResult(intent, OPEN_FOLDER_REQUEST_CODE);
            }
        });

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == OPEN_ZIP_FILE_REQUEST_CODE) {
            // check is a zip file
            //check contains media file
        } else if (requestCode == OPEN_FOLDER_REQUEST_CODE) {
            // check is a folder file
            //check contains media file
        }
    }
}
