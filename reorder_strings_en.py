"""
Reorder values/strings.xml (English only):
1. At top: all translatable="false" entries
2. Then: all single-word string values (no space, no newline in content)
3. Then: everything else (multi-word strings, plurals, CDATA, etc.)
"""
import re
import os

def extract_content(string_elem):
    """Get text content of a string element (between > and </string>). Handles CDATA."""
    if '<![CDATA[' in string_elem:
        m = re.search(r'<!\[CDATA\[(.*?)\]\]>', string_elem, re.DOTALL)
        if m:
            return m.group(1).strip()
    m = re.search(r'>\s*(.*?)\s*</string>', string_elem, re.DOTALL)
    if not m:
        return ''
    content = m.group(1).strip()
    # Strip surrounding quotes
    if (content.startswith('"') and content.endswith('"')) or (content.startswith("'") and content.endswith("'")):
        content = content[1:-1].strip()
    return content

def is_single_word(content):
    """True if content has no space and no newline (single word)."""
    if not content:
        return False
    return ' ' not in content and '\n' not in content

def main():
    path = os.path.join(os.path.dirname(__file__), 'app', 'src', 'main', 'res', 'values', 'strings.xml')
    with open(path, 'r', encoding='utf-8') as f:
        content = f.read()

    # Split: keep <resources> and </resources>, extract all string and plurals blocks
    parts = []
    rest = content

    # Find all <string>...</string> blocks (non-greedy, DOTALL for multiline)
    string_pattern = re.compile(r'(\s*<string\s+name="[^"]*"[^>]*>.*?</string>\s*)', re.DOTALL)
    string_blocks = string_pattern.findall(content)

    # Find all <plurals>...</plurals> blocks
    plurals_pattern = re.compile(r'(\s*<plurals\s+name="[^"]*"[^>]*>.*?</plurals>\s*)', re.DOTALL)
    plurals_blocks = plurals_pattern.findall(content)

    translatable_false = []
    single_words = []
    other_strings = []
    for blk in string_blocks:
        if 'translatable="false"' in blk:
            translatable_false.append(blk)
        else:
            text = extract_content(blk)
            if is_single_word(text):
                single_words.append(blk)
            else:
                other_strings.append(blk)

    # Build new content: remove all string and plurals from content, then re-insert in order
    # Remove string blocks
    new_content = string_pattern.sub('\n', content)
    # Remove plurals blocks
    new_content = plurals_pattern.sub('\n', new_content)
    # Collapse multiple newlines
    new_content = re.sub(r'\n{3,}', '\n\n', new_content)

    # Find insertion point: after <resources>\n
    if not new_content.strip().startswith('<resources'):
        print('Unexpected format')
        return
    idx = new_content.find('\n', new_content.find('<resources')) + 1
    head = new_content[:idx]
    tail = new_content[idx:]

    # Build body: section 1 (translatable=false), section 2 (single words), section 3 (rest + plurals)
    lines = []
    lines.append('    <!-- ********************************** -->')
    lines.append('    <!-- NO TRANSLATION (translatable=false) -->')
    lines.append('')
    for blk in translatable_false:
        lines.append(blk.rstrip())
    lines.append('')
    lines.append('    <!-- ********************************** -->')
    lines.append('    <!-- SINGLE WORDS -->')
    lines.append('')
    for blk in single_words:
        lines.append(blk.rstrip())
    lines.append('')
    lines.append('    <!-- ********************************** -->')
    lines.append('    <!-- OTHER (multi-word strings, plurals, etc.) -->')
    lines.append('')

    # For "rest": we need to put other_strings and plurals_blocks in their original order
    # So we need to preserve order of "other" items. Actually the user said "then below all translations that are single words" - so the rest is "everything else". So we can put other_strings first then plurals, or preserve original order. To preserve original order we'd need to track position. Simpler: put all other_strings then all plurals_blocks.
    for blk in other_strings:
        lines.append(blk.rstrip())
    for blk in plurals_blocks:
        lines.append(blk.rstrip())

    body = '\n'.join(lines)
    # Re-insert: replace the tail (everything after first newline after <resources>) with our body, but keep </resources> at the end
    if '</resources>' in tail:
        tail_before_close = tail.rsplit('</resources>', 1)[0]
        tail_close = '\n</resources>' + tail.rsplit('</resources>', 1)[1]
    else:
        tail_before_close = tail
        tail_close = '\n</resources>'
    # Actually we removed all strings/plurals and replaced with \n, so tail now has lots of blank lines and maybe some comments. We want to drop that and use our body, then </resources>.
    out = head + body + tail_close

    with open(path, 'w', encoding='utf-8') as f:
        f.write(out)
    print('Reordered:', path)
    print('  translatable=false:', len(translatable_false))
    print('  single words:', len(single_words))
    print('  other strings:', len(other_strings))
    print('  plurals:', len(plurals_blocks))

if __name__ == '__main__':
    main()
