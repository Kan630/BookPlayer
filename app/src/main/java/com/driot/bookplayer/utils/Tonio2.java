package com.driot.bookplayer.utils;

import android.content.Context;
import android.content.Intent;

import com.driot.bookplayer.activities.BiggerTextActivity;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.util.ArrayList;

import static android.content.Context.MODE_PRIVATE;
import static com.driot.tonylib.KanLogger.myLog;
import static com.driot.tonylib.KanLogger.myLogE;

/**
 * created by Antoine Driot -- antoine.driot.com -- on 27/11/20
 */
public class Tonio2 {

    /**
     * From Asset, get file text
     *
     * @param context
     * @param textFilePath
     * @return
     */


    public static String getTextFileInString(Context context, String textFilePath) {
        StringBuilder termsString = new StringBuilder();
        BufferedReader reader;
        try {
            reader = new BufferedReader(
                    new InputStreamReader(context.getAssets().open(textFilePath)));

            String str;
            while ((str = reader.readLine()) != null) {
                termsString.append(str);
                termsString.append('\n');
            }
            reader.close();
            return termsString.toString();

        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }


    public static ArrayList<MyFile> getFileInArrayList(Context c) {
        ArrayList<String> fileNameArrayList = new ArrayList<>();
        ArrayList<MyFile> myFileArrayList = new ArrayList<>();
        //listAssetFiles(c, "Jurisprudence", fileNameArrayList);
        listClassicFiles(c, "log", fileNameArrayList);
        if (fileNameArrayList.size() == 0) myLogE("Warning fileNameArrayList empty");
        for (String s : fileNameArrayList) {
            myFileArrayList.add(new MyFile(c, s));
        }
        return myFileArrayList;
    }

    public static ArrayList<MyTextChunk> getTextFileContentInArrayList(Context c, String typeStorage, String textFileName, String textFileFolder, int charSize) {
        ArrayList<MyTextChunk> arrayList = new ArrayList<>();
        BufferedReader reader;
        InputStream inputStream = null;
        myLog("opening file -" + textFileName + "- in folder -" + textFileFolder + "- with method -" + typeStorage + "-");
        try {

            //FROM ASSET FOLDER (BookPlayer/app/src/main/assets/)
            if (typeStorage.equals("asset")) {
                inputStream = c.getAssets().open(textFileName);

            //FROM USER FOLDER (usually data/data/com.driot.bookplayer/files/...)
            } else if (typeStorage.equals("classic")) {
                File dir = new File(c.getFilesDir(), textFileFolder);
                inputStream = new FileInputStream(new File(dir, textFileName));
                //inputStream = c.openFileInput(textFileName);

            } else {
                inputStream = null;
            }

            reader = new BufferedReader(
                    //new InputStreamReader(context.getAssets().open(textFilePath)));
                    //new InputStreamReader(context.openFileInput(textFilePath)));
                    new InputStreamReader(inputStream));

            String str;
            int i = 0;
            while ((str = reader.readLine()) != null) {
                //if (!(str.contains("redraw Seek Bar"))) { //TODO temp for log analysis
                    arrayList.add(new MyTextChunk(i, str, charSize));
                    i++;
                //}
            }
            reader.close();
            return arrayList;

        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static void loadBiggerText(Context context, String typeStorage, String file, String title) {
        Intent intent = new Intent(context, BiggerTextActivity.class);
        intent.putExtra("typeStorage", typeStorage);
        intent.putExtra("file", file);
        intent.putExtra("title", title);
        context.startActivity(intent);
    }

    //writeToFile(this, "salut", "loggg.txt");
    // usually write into data/data/com.driot.bookplayer/files/...)
    public static void writeToFile(Context context, String data, String fileName) {
        try {
            OutputStreamWriter outputStreamWriter = new OutputStreamWriter(context.openFileOutput(fileName, Context.MODE_PRIVATE));
            outputStreamWriter.write(data);
            outputStreamWriter.close();
        } catch (IOException e) {
            myLogE("File write failed: " + e.toString());
        }
    }

    public String readFromFile(Context context, String fileName) {

        String ret = "";

        try {
            InputStream inputStream = context.openFileInput(fileName);

            if ( inputStream != null ) {
                InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
                BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
                String receiveString = "";
                StringBuilder stringBuilder = new StringBuilder();

                while ( (receiveString = bufferedReader.readLine()) != null ) {
                    stringBuilder.append("\n").append(receiveString);
                }

                inputStream.close();
                ret = stringBuilder.toString();
            }
        }
        catch (FileNotFoundException e) {
            myLogE("File not found: " + e.toString());
        } catch (IOException e) {
            myLogE("Can not read file: " + e.toString());
        }

        return ret;
    }

    private static boolean listAssetFiles(Context c, String path, ArrayList<String> arrayList) {

        String[] list;
        try {
            list = c.getAssets().list(path);
            if (list.length > 0) {
                // This is a folder
                for (String file : list) {
                    if (!listAssetFiles(c, path + "/" + file, arrayList))
                        return false;
                    else {
                        // This is a file
                        arrayList.add(file);
                    }
                }
            }
        } catch (IOException e) {
            return false;
        }

        return true;
    }

    private static boolean listClassicFiles(Context c, String path, ArrayList<String> arrayList) {

        File dir = new File(c.getFilesDir(), path);
        for (File f : dir.listFiles()) {
            if (f.isFile()) {
                String name = f.getName();
                arrayList.add(name);
            }
        }

        return arrayList.size() > 0;

    }





}
