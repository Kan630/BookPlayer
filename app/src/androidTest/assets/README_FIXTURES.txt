Fixture setup for instrumented tests (ImportBookTest, LoadManyBookTest)

LoadManyBookTest (and ClearAndLoadManyBookTest, which extends it) reads fixtures directly off
device storage - NOT from this assets/ folder. Put real files on the device itself (SD card or
internal storage), under a top-level "fixtures" folder:

  <storage-root>/fixtures/zip/           - zip archives
  <storage-root>/fixtures/ebooks/        - epub, pdf, fb2... (searched recursively)
  <storage-root>/fixtures/folders/       - folders with audio files (each subdir = one book)
  <storage-root>/fixtures/m4b/           - m4b audiobooks
  <storage-root>/fixtures/single_files/  - mp3, m4a

LoadManyBookTest auto-discovers this: it checks primary storage first, then every volume
reported by getExternalFilesDirs() (SD card included), and uses the first "fixtures" dir it
finds - no adb push, no rebuild/reinstall needed to update fixture content, just add/remove
files on the device directly. Deliberately NOT bundled into this androidTest assets/ folder:
that would mean repackaging a large, ever-growing set of real book files into the APK on every
single build just to pick up a fixture change.

Listing what's inside "fixtures" (as opposed to reading a file/folder whose exact path is
already known) needs MANAGE_EXTERNAL_STORAGE on Android 11+ to do via plain java.io.File - and
on this project's Samsung A16 test device, Knox disables that permission for this app entirely
(the toggle is greyed out in Settings > All files access, and even `adb shell appops set
MANAGE_EXTERNAL_STORAGE allow` silently doesn't stick). So instead, listing goes through
MediaStore.Files (content://media/external/file, queried by path prefix) - the officially
sanctioned scoped-storage-compliant way to discover what exists under a folder, indexes every
file type (not just media), and needs no special permission beyond what the app already
declares. It relies on the OS having already scanned the fixtures folder at least once - if you
add new fixture files and LoadManyBookTest doesn't see them, trigger a rescan (e.g. via the
device's Files app "scan" action, or unmount/remount the SD card) before rerunning.

Actually reading a specific already-known file/folder path (not listing) works fine without any
extra permission - confirmed via `adb shell run-as <pkg> stat <path>` / `cat <path>` on the A16.
That's why File-type fixtures (zip/ebooks/m4b/single_files) are read directly with no copy step.
Folder-type fixtures are the one exception: importing "a folder" means the app's own import
logic does its own real File.listFiles() scan over the picked folder to find its tracks, which
hits the same listing restriction - so each folder fixture's files (already discovered via
MediaStore above) get mirrored into the app's own cache dir first, which has no such
restriction. This is a small, fast, purely on-device copy of just that one book's files, not a
rebuild/reinstall step.

If no "fixtures" folder is found on any volume, LoadManyBookTest fails loudly with an
AssertionError naming what it checked - it does NOT silently pass having imported nothing.

---

ImportBookTest is a separate, standalone test (not part of OrderedInstrumentedTestSuite) that
still uses the OLD approach below - fixtures bundled into this assets/ folder and copied out of
the APK at runtime. It hasn't been migrated to the real-storage approach above yet.

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
