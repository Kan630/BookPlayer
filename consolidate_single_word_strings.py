"""
Consolidate duplicate single-word string resources:
- For each string whose English value is a single word, canonical name = that value.
- If current name != value: remove duplicate (if entry with name=value exists) or rename (change name to value).
- Replace all refs: R.string.old_name -> R.string.canonical, @string/old_name -> @string/canonical.
- In Java, resource names with dots become underscores in R class (e.g. min. -> min_).
"""
import os
import re

def extract_content(string_elem):
    if '<![CDATA[' in string_elem:
        m = re.search(r'<!\[CDATA\[(.*?)\]\]>', string_elem, re.DOTALL)
        if m:
            return m.group(1).strip()
    m = re.search(r'>\s*(.*?)\s*</string>', string_elem, re.DOTALL)
    if not m:
        return ''
    content = m.group(1).strip()
    if (content.startswith('"') and content.endswith('"')) or (content.startswith("'") and content.endswith("'")):
        content = content[1:-1].strip()
    return content

def is_single_word(content):
    if not content:
        return False
    return ' ' not in content and '\n' not in content

def canonical_java_name(value):
    """R class converts dots to underscores in resource names."""
    return value.replace('.', '_')

def is_valid_resource_name(value):
    """Android resource names: must start with letter or underscore, then only [a-zA-Z0-9_.]."""
    if not value:
        return False
    return bool(re.match(r'^[a-zA-Z_][a-zA-Z0-9_.]*$', value))

def main():
    base = os.path.dirname(os.path.abspath(__file__))
    values_path = os.path.join(base, 'app', 'src', 'main', 'res', 'values', 'strings.xml')
    with open(values_path, 'r', encoding='utf-8') as f:
        content = f.read()

    string_pattern = re.compile(r'(\s*<string\s+name="([^"]+)"[^>]*>.*?</string>\s*)', re.DOTALL)
    blocks = string_pattern.findall(content)
    # (full_block, name)
    name_to_block = {}
    name_to_value = {}
    for full_block, name in blocks:
        name_to_block[name] = full_block
        name_to_value[name] = extract_content(full_block)

    # Skip translatable=false - we don't rename those
    rename_map = {}  # old_name -> canonical_name (the value)
    for name, value in name_to_value.items():
        if not is_single_word(value):
            continue
        if name == value:
            continue
        # translatable=false entries: skip (don't rename app_name etc.)
        if name in name_to_block and 'translatable="false"' in name_to_block[name]:
            continue
        # Canonical name = value (e.g. "Download")
        # Only include if value is a valid Android resource name (letters, digits, underscore, period)
        if not is_valid_resource_name(value):
            continue
        rename_map[name] = value

    # If canonical name already exists, we will REMOVE the redundant entry.
    # If canonical doesn't exist, we will RENAME (change name attribute to value).
    to_remove = []  # names to remove (canonical exists)
    to_rename = []  # (old_name, canonical_name) - change name attribute
    for old_name, canonical in rename_map.items():
        if canonical in name_to_value:
            to_remove.append(old_name)
        else:
            to_rename.append((old_name, canonical))

    print("Rename map (old -> canonical):", len(rename_map))
    for k in sorted(rename_map.keys()):
        print("  ", k, "->", rename_map[k])
    print("To remove (canonical exists):", len(to_remove))
    print("To rename (canonical new):", len(to_rename))

    # 1) Update all strings.xml files
    res_base = os.path.join(base, 'app', 'src', 'main', 'res')
    locale_dirs = ['values', 'values-es', 'values-it', 'values-de', 'values-pt', 'values-ru', 'values-zh', 'values-ar', 'values-hi', 'values-b+fr']
    for locale in locale_dirs:
        path = os.path.join(res_base, locale, 'strings.xml')
        if not os.path.isfile(path):
            continue
        with open(path, 'r', encoding='utf-8') as f:
            text = f.read()
        # Remove entries in to_remove
        for name in to_remove:
            pat = re.compile(r'\n?\s*<string name="' + re.escape(name) + r'"[^>]*>.*?</string>\s*', re.DOTALL)
            text = pat.sub('\n', text)
        # Rename entries in to_rename (change name="old" to name="canonical")
        for old_name, canonical in to_rename:
            pat = re.compile(r'(<string\s+)name="' + re.escape(old_name) + r'"')
            text = pat.sub(r'\1name="' + canonical + '"', text)
        text = re.sub(r'\n{4,}', '\n\n\n', text)
        with open(path, 'w', encoding='utf-8') as f:
            f.write(text)
        print("Updated:", path)

    # 2) Replace in Java/Kotlin: R.string.old_name -> R.string.canonical_java (one pass per file)
    main_src = os.path.join(base, 'app', 'src', 'main')
    for root, dirs, files in os.walk(main_src):
        dirs[:] = [d for d in dirs if d not in ('build', '.git')]
        for f in files:
            if not f.endswith(('.java', '.kt')):
                continue
            path = os.path.join(root, f)
            with open(path, 'r', encoding='utf-8') as fp:
                code = fp.read()
            new_code = code
            for old_name, canonical in rename_map.items():
                java_canonical = canonical_java_name(canonical)
                new_code = new_code.replace('R.string.' + old_name, 'R.string.' + java_canonical)
                new_code = new_code.replace('com.driot.bookplayer.R.string.' + old_name, 'com.driot.bookplayer.R.string.' + java_canonical)
            if new_code != code:
                with open(path, 'w', encoding='utf-8') as fp:
                    fp.write(new_code)
                print("  Java:", path)

    # 3) Replace in XML: @string/old_name -> @string/canonical (one pass per file)
    for root, dirs, files in os.walk(main_src):
        dirs[:] = [d for d in dirs if d not in ('build', '.git')]
        for f in files:
            if not f.endswith('.xml'):
                continue
            path = os.path.join(root, f)
            with open(path, 'r', encoding='utf-8') as fp:
                xml_text = fp.read()
            new_text = xml_text
            for old_name, canonical in rename_map.items():
                new_text = new_text.replace('@string/' + old_name, '@string/' + canonical)
            if new_text != xml_text:
                with open(path, 'w', encoding='utf-8') as fp:
                    fp.write(new_text)
                print("  XML:", path)

    print("Done.")

if __name__ == '__main__':
    main()
