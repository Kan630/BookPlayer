Fixture setup for instrumented tests (ImportBookTest, LoadManyBookTest)

Create folder: app/src/androidTest/assets/fixtures/

Add test files under one of these subfolders:
  fixtures/ebooks/       - epub, pdf (e.g. sample.epub)
  fixtures/single_files/ - mp3, m4a
  fixtures/m4b/          - m4b audiobooks
  fixtures/zip/          - zip archives
  fixtures/folders/      - folders with audio files

For ImportBookTest, add at least one file under ebooks/ or single_files/.

Note: fixtures/ is gitignored; add your own test files locally.
