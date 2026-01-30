"""Sort single-word string entries in values/strings.xml alphabetically by value (case-insensitive)."""
import re
import os

base = os.path.dirname(os.path.abspath(__file__))
path = os.path.join(base, 'app', 'src', 'main', 'res', 'values', 'strings.xml')

with open(path, 'r', encoding='utf-8') as f:
    content = f.read()

# Find the SINGLE WORDS section
start_marker = '    <!-- SINGLE WORDS -->'
end_marker = '    <!-- OTHER (multi-word'
i0 = content.find(start_marker)
i1 = content.find(end_marker)
if i0 == -1 or i1 == -1:
    print("Section not found")
    exit(1)

before = content[:i0 + len(start_marker)]
after = content[i1:]
section = content[i0 + len(start_marker):i1]

# Parse <string name="...">...</string> (may span or have inconsistent indent)
pattern = re.compile(r'\s*<string\s+name="([^"]+)"[^>]*>(.*?)</string>\s*', re.DOTALL)
entries = []
for m in pattern.finditer(section):
    name, value = m.group(1), m.group(2).strip()
    # Strip surrounding quotes from value for sort key
    sort_val = value
    if (sort_val.startswith('"') and sort_val.endswith('"')) or (sort_val.startswith("'") and sort_val.endswith("'")):
        sort_val = sort_val[1:-1].strip()
    sort_key = sort_val.lower()
    entries.append((sort_key, name, value))

entries.sort(key=lambda x: x[0])

# Rebuild section with consistent indent (4 spaces)
lines = ['\n\n']
for _, name, value in entries:
    lines.append('    <string name="' + name + '">' + value + '</string>\n')
lines.append('\n    ')

new_section = ''.join(lines)
new_content = before + new_section + after

with open(path, 'w', encoding='utf-8') as f:
    f.write(new_content)
print("Sorted", len(entries), "single-word strings alphabetically by value.")
