Fixture setup for instrumented tests (ImportBookTest, LoadManyBookTest)

Create folder: app/src/androidTest/assets/fixtures/

Add test files under one of these subfolders:
  fixtures/ebooks/       - epub, pdf (e.g. sample.epub)
  fixtures/single_files/ - mp3, m4a
  fixtures/m4b/          - m4b audiobooks
  fixtures/zip/          - zip archives
  fixtures/folders/      - folders with audio files (each subdir = one folder to import)

ImportBookTest has two modes (instrumentation arg -e MODE build|test):

  1) Build mode: -e MODE build
     Discovers all fixtures, imports each, records nb tracks + has cover img,
     writes LIST_TEST to app filesDir. Copy from logcat (=== LIST_TEST_CONTENT ===)
     or adb pull, save as app/src/androidTest/assets/LIST_TEST.

  2) Test mode: -e MODE test (default)
     Reads assets/LIST_TEST, runs each case: import, assert nb tracks + img, play one track.
     If LIST_TEST is missing, runs simple flow: first file + first folder.

LIST_TEST format (one case per line):
  LoadWay - filepath --- expected nb of tracks - expected img
  File - fixtures/m4b/sample.m4b --- 6 - true
  Folder - fixtures/folders/myaudio --- 18 - false

Note: fixtures/ is gitignored; add your own test files locally.
