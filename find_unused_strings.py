"""
Find string resources that are never referenced in the codebase.
References: @string/key in XML, R.string.key in Java/Kotlin, R.plurals.key for plurals.
Resource names can contain [a-zA-Z0-9_.] - we must include period so e.g. "min." is detected.
"""
import os
import re

def get_string_keys(file_path):
    keys = set()
    if not os.path.exists(file_path):
        return keys
    with open(file_path, 'r', encoding='utf-8') as f:
        content = f.read()
        matches = re.findall(r'<string name="([^"]+)">', content)
        keys.update(matches)
        matches_plurals = re.findall(r'<plurals name="([^"]+)">', content)
        keys.update(matches_plurals)
    return keys

def find_referenced_strings(project_root):
    referenced = set()
    # Android resource names: [a-zA-Z0-9_.] - include period for keys like "min."
    xml_pattern = re.compile(r'@string/([a-zA-Z0-9_.]+)')
    java_string_pattern = re.compile(r'R\.string\.([a-zA-Z0-9_.]+)')
    java_plurals_pattern = re.compile(r'R\.plurals\.([a-zA-Z0-9_.]+)')
    search_root = os.path.join(project_root, 'app', 'src', 'main')
    if not os.path.isdir(search_root):
        search_root = project_root
    for root, dirs, files in os.walk(search_root):
        dirs[:] = [d for d in dirs if d not in ('build', '.git')]
        for f in files:
            path = os.path.join(root, f)
            try:
                with open(path, 'r', encoding='utf-8', errors='ignore') as fp:
                    content = fp.read()
            except Exception:
                continue
            if f.endswith('.xml'):
                for m in xml_pattern.finditer(content):
                    referenced.add(m.group(1))
            if f.endswith(('.java', '.kt')):
                for m in java_string_pattern.finditer(content):
                    referenced.add(m.group(1))
                for m in java_plurals_pattern.finditer(content):
                    referenced.add(m.group(1))  # plurals used via R.plurals.xxx
    return referenced

def main():
    project_root = os.path.dirname(os.path.abspath(__file__))
    default_strings = os.path.join(project_root, 'app', 'src', 'main', 'res', 'values', 'strings.xml')
    defined_keys = get_string_keys(default_strings)
    referenced_keys = find_referenced_strings(project_root)
    unused = defined_keys - referenced_keys
    unused_sorted = sorted(unused)
    print(f"Defined: {len(defined_keys)}, Referenced: {len(referenced_keys)}, Unused: {len(unused_sorted)}")
    print("Unused string/plurals keys:")
    for k in unused_sorted:
        print(k)

if __name__ == '__main__':
    main()
