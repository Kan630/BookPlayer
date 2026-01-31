import re

french_path = r'c:\Users\adrio\StudioProjects\BookPlayer\app\src\main\res\values-b+fr\strings.xml'
key_to_check = "help_text_general"

with open(french_path, 'r', encoding='utf-8') as f:
    content = f.read()

print(f"Content length: {len(content)}")

# Test regex
pattern = r'<string name="([^"]+)"[^>]*>(.*?)</string>'
matches = list(re.finditer(pattern, content, re.DOTALL))
print(f"Total matches: {len(matches)}")

found = False
for m in matches:
    if m.group(1) == key_to_check:
        print(f"Found key: {key_to_check}")
        print(f"Value start: {m.group(2)[:50]}...")
        found = True
        break

if not found:
    print(f"Key {key_to_check} NOT found via regex.")
    # Check if string exists at all
    if key_to_check in content:
        print(f"Key literal '{key_to_check}' IS in file. Regex failed.")
        # Print snippet
        idx = content.find(key_to_check)
        print(f"Snippet: {content[idx-20:idx+100]}")
