package com.driot.bookplayer.activities;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.driot.bookplayer.R;

public class LibrivoxDetailActivity extends AppCompatActivity {

    private String identifier;
    private String title;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_librivox_detail);

        // Get data passed from intent
        identifier = getIntent().getStringExtra("identifier");
        title = getIntent().getStringExtra("title");

        TextView titleView = findViewById(R.id.textDetailTitle);
        TextView idView = findViewById(R.id.textDetailIdentifier);
        Button downloadButton = findViewById(R.id.buttonDownload);

        titleView.setText(title);
        idView.setText("ID: " + identifier);

        downloadButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String downloadUrl = "https://archive.org/download/" + identifier + "/" + identifier + "_64kb_mp3.zip";

                // ✅ Use your existing logic here:
                // Example: start your download service, or open browser
                startDownload(downloadUrl);
            }
        });
    }

    private void startDownload(String url) {
        // Example: open in browser
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        startActivity(intent);

        // TODO: Replace with your actual download handler
        // e.g., call your existing background service
    }
}
