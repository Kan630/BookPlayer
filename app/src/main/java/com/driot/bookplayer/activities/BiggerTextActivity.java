package com.driot.bookplayer.activities;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.driot.bookplayer.utils.MyTextChunk;
import com.driot.bookplayer.utils.MyTextChunkAdapter;
import com.driot.bookplayer.utils.TextOptions;

import java.util.ArrayList;
import java.util.HashMap;

import static com.driot.bookplayer.utils.Tonio2.getTextFileContentInArrayList;
import static com.driot.tonylib.KanLogger.myLog;
import static com.driot.tonylib.KanLogger.myLogE;

import com.driot.bookplayer.R;

/**
 * created by Antoine Driot -- antoine.driot.com -- on 21/08/21
 * * imported from Droit Positif (02/12/2020)
 */
public class BiggerTextActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private RecyclerView.LayoutManager layoutManager;
    private ArrayList<MyTextChunk> myTextChunkArrayList;
    private HashMap<Integer, Integer> map;

    private String file;
    private String typeStorage;

    private TextOptions textOptions;

    private boolean destroyedByFlip = false;

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        destroyedByFlip = true;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_biggertext);

        recyclerView = findViewById(R.id.rec);

        layoutManager = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(layoutManager);

        /*
        Toolbar myToolbar = findViewById(R.id.my_toolbar);
        setSupportActionBar(myToolbar);
        */

        setTitle(getIntent().getStringExtra("title"));
        file = getIntent().getStringExtra("file");
        typeStorage = getIntent().getStringExtra("typeStorage");

        textOptions = new TextOptions(this);
        loadRecyclerView();

        try {
            int zesize = myTextChunkArrayList.size();
        } catch (Exception e) {
            myLogE("Ca chie, fichier vide : " + e.getMessage());
        }

        createMap();
    }

    private void loadRecyclerView() {
        myTextChunkArrayList = getTextFileContentInArrayList(this, typeStorage, file,"log", textOptions.getCharSize());
        recyclerView.setAdapter(new MyTextChunkAdapter(myTextChunkArrayList));
        textOptions.setScrollPosition(this, file, recyclerView);
    }

    private void createMap() {
        map = new HashMap<>();
        for (int i = 0; i < myTextChunkArrayList.size(); i++) {
            int id = myTextChunkArrayList.get(i).getId(); // id of the model
            map.put(id, i); // i is the position of adapter
        }
    }

    @Override
    protected void onDestroy() {
        try {
            textOptions.saveScrollPosition(this, file, ((LinearLayoutManager) layoutManager).findFirstCompletelyVisibleItemPosition());
        } catch (Exception e) {
            myLogE("BiggerText.onDestroy  saveScrollPosition : " + e.getMessage());
        }
        if (!destroyedByFlip) {
            try {
                textOptions.saveHighlightedText(this, file, ""); // supprime highLightedWord si on quitte et que c'est pas un flip
            } catch (Exception e) {
                myLogE("BiggerText.onDestroy  saveHighlightedText : " + e.getMessage());
            }
        }
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        myLog("BiggerTextActivity back press");
    }

    /*
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.action_bar, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.action_tailleCharPlus) {
            textOptions.charSizePlus(this);
            loadRecyclerView();
            return true;
        } else if (itemId == R.id.action_tailleCharMoins) {
            textOptions.charSizeMoins(this);
            loadRecyclerView();
            return true;
        } else if (itemId == R.id.action_search) {
            searchWordInText();
            return true;
        } else if (itemId == R.id.action_debutTexte) {
            recyclerView.scrollToPosition(0);
            return true;
        }
        myLog("default : " + item.toString());
        return super.onOptionsItemSelected(item);
    }

    private void searchWordInText() {
        final EditText reponseEditText = new EditText(this);
        reponseEditText.setText("Article ");
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Search")
                .setMessage("Enter a word ?")
                .setView(reponseEditText)
                .setPositiveButton("Search", (dialog1, which) -> {
                    String reponse = "";
                    try {
                        reponse = String.valueOf(reponseEditText.getText());
                        reponse = reponse.trim();
                        searchWordInText2(reponse);
                    } catch (Exception e) {
                        if (!reponse.equals("")) {
                            myToastE("Error while searching :" + e.getMessage());
                            e.printStackTrace();
                        }
                    }
                })
                .setNegativeButton("Cancel", null)
                .create();
        dialog.show();
    }

    private void searchWordInText2(@Nullable String highlightedWord) {
        myLog("searchWord :" + highlightedWord);
        //saveScrollPosition();

        if (highlightedWord != null && highlightedWord.length() > 1) {
            textOptions.saveHighlightedText(this, file, highlightedWord);

            for (MyTextChunk m : myTextChunkArrayList) {
                if (m.contains(highlightedWord)) {
                    myLog("Found : " + m.getId() + " -- " + m.getText());
                    recyclerView.scrollToPosition(map.get(m.getId()) + 1);   //+1 sinon en plein milieu...
                    break;
                }
            }


        }
    }

     */

}

