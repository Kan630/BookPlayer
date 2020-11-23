package com.driot.bookplayer.db;

import android.content.Context;
import android.net.Uri;

/**
 * created by Antoine Driot -- antoine.driot.com -- on 23/11/20
 */
public class Resource {
    private String type;
    private Folder folder;
    private ZikFile[] zikFiles;


    public Resource(FolderAttrib myFolder) {
        this.folder = new Folder();
        this.folder.setUri(myFolder.getsFolderUri());
        this.folder.setHash(myFolder.getsFolderHash());
        this.folder.setPath(myFolder.getsFolderPath());
        this.folder.setName(myFolder.getsFolderName());
    }
}
