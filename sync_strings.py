import re
import os

english_path = r'c:\Users\adrio\StudioProjects\BookPlayer\app\src\main\res\values\strings.xml'
french_path = r'c:\Users\adrio\StudioProjects\BookPlayer\app\src\main\res\values-b+fr\strings.xml'
target_path = r'c:\Users\adrio\StudioProjects\BookPlayer\app\src\main\res\values-b+fr\strings.xml'

# Manual translations for missing keys
missing_translations = {
    "Bookshelves": "Bibliothèques",
    "Librivox": "LibriVox",
    "MB_taken_by_linked_audios": " Mo : audios liés",
    "Storage": "Stockage",
    "action_reset_to_original": "↩️ RàZ original",
    "calculating_storage": "Calcul du stockage…",
    "copied": "copié",
    "device_storage_memory": "Mémoire de l'appareil",
    "linked": "lié",
    "loading_voice_3pt": "Chargement voix…",
    "mass_import_found_candidates_click": "%1$d candidats trouvés, cliquez pour ouvrir.",
    "mass_import_scan_complete": "Scan import de masse terminé.",
    "mass_import_scanning_title": "Scan import de masse...",
    "mass_import_selected_summary": "%1$d candidats sélectionnés pour import (%2$s)",
    "massiveimport_title": "Import de masse",
    "no_ebooks_found_bookshelf": "Aucun ebook trouvé \npour la bibliothèque [%1$s] \navec la langue [%2$s]",
    "option_mass_import_display_storage_bar": "Afficher la barre de stockage",
    "option_podcast_open_specific_view_subtitle2": "Note : Vous pouvez basculer entre ces vues en tapotant ou double-tapotant l'image de couverture.",
    "others": "autres",
    "power_management_exempt": "App exemptée des optimisations batterie (bien)",
    "power_management_subject": "App sujette aux optimisations batterie (risque d'arrêt)",
    "radio_thanks": "Merci à radio-browser.info pour leur répertoire gratuit de radios",
    "sd_card_storage": "Stockage Carte SD",
    "select_at_least_one_item": "Veuillez sélectionner au moins un élément.",
    "storage_device": "Stockage appareil",
    "storage_legend_new_books": "nouveaux livres",
    "storage_sd_card": "Stockage carte SD",
    "tracks_count": {
        "one": "%d piste",
        "other": "%d pistes"
    }
}

# 1. Parse existing French translations
french_map = {} # key -> text
french_plurals = {} # key -> { quantity -> text }

try:
    with open(french_path, 'r', encoding='utf-8') as f:
        content = f.read()
        
        # Single strings: <string name="KEY" ...>VALUE</string>
        # Be careful with multiline values and nested tags like <font>
        # We use re.DOTALL to match across lines
        for m in re.finditer(r'<string name="([^"]+)"[^>]*>(.*?)</string>', content, re.DOTALL):
            key = m.group(1)
            val = m.group(2)
            french_map[key] = val
            
        # Plurals
        # Capture the whole plural block
        for m in re.finditer(r'<plurals name="([^"]+)">\s*(.*?)\s*</plurals>', content, re.DOTALL):
            key = m.group(1)
            body = m.group(2)
            items = {}
            for item_m in re.finditer(r'<item quantity="([^"]+)">\s*(.*?)\s*</item>', body, re.DOTALL):
                qty = item_m.group(1)
                txt = item_m.group(2)
                items[qty] = txt
            french_plurals[key] = items

except FileNotFoundError:
    print("French file not found, creating new.")

# 2. Process English file line by line
new_content = []
with open(english_path, 'r', encoding='utf-8') as f:
    lines = f.readlines()

plural_current_key = None
plural_map_buffer = None

for line in lines:
    line_stripped = line.strip()
    
    # Check for <string name="...">
    # Regex to capture the tag parts: (whitespace)(<string name="KEY")(attrs)(>)(CONTENT)(</string>)
    string_match = re.search(r'^(\s*)<string name="([^"]+)"(.*?)>(.*?)</string>', line, re.DOTALL)
    
    # Check for <plurals name="..."> (start)
    plural_start_match = re.search(r'^(\s*)<plurals name="([^"]+)">', line)
    
    # Check for </plurals> (end)
    plural_end_match = re.search(r'^(\s*)</plurals>', line)

    # Check for <item quantity="..."> (inside plural)
    plural_item_match = re.search(r'^(\s*)<item quantity="([^"]+)">', line)

    if string_match:
        indent = string_match.group(1)
        key = string_match.group(2)
        attrs = string_match.group(3)
        en_content = string_match.group(4)
        
        # Decide content
        fr_content = en_content
        
        if key in french_map:
            fr_content = french_map[key]
        elif key in missing_translations:
            fr_content = missing_translations[key]
        
        # Reconstruct line
        new_line = f'{indent}<string name="{key}"{attrs}>{fr_content}</string>\n'
        new_content.append(new_line)
        
    elif plural_start_match:
        plural_current_key = plural_start_match.group(2)
        # We print the start tag as is (layout)
        new_content.append(line)
        
    elif plural_end_match:
        plural_current_key = None
        new_content.append(line)
        
    elif plural_current_key and plural_item_match:
        # Inside a plural block
        indent = plural_item_match.group(1)
        qty = plural_item_match.group(2)
        
        # Extract content (removing tags)
        # Regex for item line: indent <item quantity="qty">CONTENT</item>
        # Assumption: item is on one line for now (matches English file style)
        item_full_match = re.search(r'<item quantity="[^"]+">(.*?)</item>', line)
        
        fr_item_content = "???"
        if item_full_match:
             en_item_content = item_full_match.group(1)
             fr_item_content = en_item_content # Default fallback
        
        # Look up
        if plural_current_key in french_plurals and qty in french_plurals[plural_current_key]:
            fr_item_content = french_plurals[plural_current_key][qty]
        elif plural_current_key in missing_translations and isinstance(missing_translations[plural_current_key], dict):
             if qty in missing_translations[plural_current_key]:
                 fr_item_content = missing_translations[plural_current_key][qty]
        
        new_line = f'{indent}<item quantity="{qty}">{fr_item_content}</item>\n'
        new_content.append(new_line)

    else:
        # Comments, empty lines, resources tags, etc. -> Copy exact English line
        new_content.append(line)

# 3. Write output
with open(target_path, 'w', encoding='utf-8') as f:
    f.writelines(new_content)

print("Done. French file synchronized.")
