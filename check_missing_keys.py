
import os
import re
import xml.etree.ElementTree as ET

def get_string_keys(file_path):
    keys = set()
    if not os.path.exists(file_path):
        return keys
    
    try:
        # Simple regex parsing to avoid XML namespace issues or malformed xml breaking everything
        # though standard xml parser is better if files are clean.
        # Let's try regex for robustness against slight format diffs not vital for keys.
        with open(file_path, 'r', encoding='utf-8') as f:
            content = f.read()
            # Match <string name="KEY">
            matches = re.findall(r'<string name="([^"]+)">', content)
            keys.update(matches)
            # Match <plurals name="KEY">
            matches_plurals = re.findall(r'<plurals name="([^"]+)">', content)
            keys.update(matches_plurals)
    except Exception as e:
        print(f"Error reading {file_path}: {e}")
    return keys

def main():
    base_path = 'app/src/main/res'
    default_values_path = os.path.join(base_path, 'values', 'strings.xml')
    
    default_keys = get_string_keys(default_values_path)
    print(f"Default strings count: {len(default_keys)}")

    locales = ['fr', 'es', 'it', 'de', 'pt', 'ru', 'zh', 'ar', 'hi']
    
    for locale in locales:
        locale_path = os.path.join(base_path, f'values-{locale}', 'strings.xml')
        locale_keys = get_string_keys(locale_path)
        missing = default_keys - locale_keys
        
        # Filter out translatable="false" if I had access to attributes, but current regex doesn't check.
        # However, usually translatable=false strings are not in other files anyway.
        # Let's just list them.
        
        if missing:
            print(f"Missing in {locale} ({len(missing)}):")
            for k in sorted(missing):
                print(f"  {k}")
        else:
            print(f"No missing keys in {locale}")

if __name__ == '__main__':
    main()
