package com.driot.bookplayer.librivox;

import java.util.Collections;
import java.util.List;

public class LibrivoxBooksResponse {
    public List<LibrivoxBook> books;

    public List<LibrivoxBook> asList() {
        return (books == null) ? Collections.emptyList() : books;
    }
}
