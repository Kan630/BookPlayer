import re
import os

def extract_keys(file_path):
    keys = set()
    try:
        with open(file_path, 'r', encoding='utf-8') as f:
            content = f.read()
            # Extract string keys
            matches = re.findall(r'<string name="([^"]+)"', content)
            keys.update(matches)
            # Extract plurals keys
            matches_plurals = re.findall(r'<plurals name="([^"]+)"', content)
            keys.update(matches_plurals)
    except FileNotFoundError:
        print(f"File not found: {file_path}")
    return keys

english_path = r'c:\Users\adrio\StudioProjects\BookPlayer\app\src\main\res\values\strings.xml'
french_path = r'c:\Users\adrio\StudioProjects\BookPlayer\app\src\main\res\values-b+fr\strings.xml'

english_keys = extract_keys(english_path)
french_keys = extract_keys(french_path)

missing_in_french = english_keys - french_keys
extra_in_french = french_keys - english_keys

print(f"Missing in French ({len(missing_in_french)}):")
for k in sorted(missing_in_french):
    print(k)

print(f"\nExtra in French (will be removed) ({len(extra_in_french)}):")
for k in sorted(extra_in_french):
    print(k)
