package com.driot.bookplayer.objects;


//POJO (Plain Old Java Object) => interface projection

public class ZikFileSummary {
    public String path;
    public String folderName;
    public Double percentdone;
    public int idFolder;
    public String sourceLocation;


    public ZikFileSummary(String path, String folderName, Double percentdone, int idFolder, String sourceLocation) {
        this.path = path;
        this.folderName = folderName;
        this.percentdone = percentdone;
        this.idFolder = idFolder;
        this.sourceLocation = sourceLocation;
    }
}