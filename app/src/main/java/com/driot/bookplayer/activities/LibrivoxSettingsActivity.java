package com.driot.bookplayer.activities;

import android.os.Bundle;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;

import com.driot.bookplayer.R;
import com.driot.bookplayer.global.Option;
import com.driot.bookplayer.global.Var;
import com.driot.bookplayer.helpers.InsetHelper;
import com.driot.bookplayer.utils.log.LoggingActivity;

public class LibrivoxSettingsActivity extends LoggingActivity {

    EditText et_librivox_api_nb_results;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_librivox_settings);
        InsetHelper.apply(this);


        et_librivox_api_nb_results = findViewById(R.id.et_podcast_index_org_api_nb_results);
        et_librivox_api_nb_results.setText(String.valueOf(Option.getLibrivoxApiNbResults()));

        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN); // Avoid keyboard on opening
    }

    private void saveEditTextValues() {
        if (et_librivox_api_nb_results != null ) {
            int value6 = clampInt(et_librivox_api_nb_results, Var.LIBRIVOX_API_MIN_RESULTS, Var.LIBRIVOX_API_MAX_RESULTS, Option.DEFAULT_LIBRIVOX_API_NB_RESULTS,
                    () -> myLongToast(getString(R.string.minimum_number_of_results_for_) + " " + getString(R.string.librivox) + " " + getString(R.string.too_low)),
                    () -> myLongToast(getString(R.string.maximum_number_of_results_for_) + " " + getString(R.string.librivox) + " " + getString(R.string.too_high)));
            Option.setLibrivoxApiNbResults(value6);
        }

    }


    @Override
    protected void onDestroy() {
        saveEditTextValues();
        super.onDestroy();
    }

    public static int clampInt(EditText et, int min, int max, int def, Runnable onTooLow, Runnable onTooHigh) {
        if (et == null) return def;
        String str = et.getText().toString().trim();
        int val;
        try {
            val = Integer.parseInt(str);
        } catch (NumberFormatException e) {
            return def;
        }

        if (val < min) {
            if (onTooLow != null) onTooLow.run();
            return min;
        } else if (val > max) {
            if (onTooHigh != null) onTooHigh.run();
            return max;
        }
        return val;
    }

}
