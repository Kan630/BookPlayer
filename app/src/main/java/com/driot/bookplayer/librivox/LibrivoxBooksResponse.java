package com.driot.bookplayer.librivox;

import androidx.annotation.Keep;

import java.util.Collections;
import java.util.List;

@Keep
public class LibrivoxBooksResponse {
    public List<LibrivoxBook> books;

    public List<LibrivoxBook> asList() {
        return (books == null) ? Collections.emptyList() : books;
    }
}
