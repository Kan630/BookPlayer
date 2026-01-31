import re
import os

english_path = r'c:\Users\adrio\StudioProjects\BookPlayer\app\src\main\res\values\strings.xml'
french_path = r'c:\Users\adrio\StudioProjects\BookPlayer\app\src\main\res\values-b+fr\strings.xml'

# Recovered multiline translations from previous valid state
# Using raw strings or escaping backslashes to ensure \n and \' serve their purpose in Android XML
recovered_data = {
    "DeleteLogs_AskConfirm": "Vous êtes sur le point de supprimer tous les journaux.\\nÊtes-vous sûr ?",
    "ModifyFolder_AskDelete": "Vous êtes sur le point de supprimer ce dossier et toutes les pistes audio.\\nÊtes-vous sûr ?",
    "ModifyFolder_AskReset": "Vous êtes sur le point de réinitialiser la progression pour ce dossier.\\nÊtes-vous sûr ?",
    "ModifyZikFile_AskDelete": "Vous êtes sur le point de supprimer cette piste audio.\\nÊtes-vous sûr ?",
    
    # CDATA sections do NOT need apostrophe escaping
    "help_text_general": """<![CDATA[
Lorsque vous ouvrez l'application, vous voyez une liste de <font color="#90D8E8">dossiers</font> — cela représente vos Livres, Leçons ou Podcasts.
<br><br>
Un <font color="#81C784">appui long</font> sur un <font color="#90D8E8">dossier</font> ouvre l'écran "Modifier le dossier" (renommer / supprimer / réinitialiser, etc.).
<br>
Un simple appui ouvre la liste des <font color="#BA68C8">pistes</font>.
<br><br>
Un <font color="#81C784">appui long</font> sur une <font color="#BA68C8">piste</font> ouvre l'écran "Modifier la piste".
<br>
Un simple appui lance la lecture.
]]>""",
    "help_text_manual_import": """<![CDATA[
Lorsque vous <font color="#81C784"><b>importez manuellement</b></font> un livre, vous commencez par sélectionner un <font color="#64B5F6">fichier</font> ou un <font color="#64B5F6">dossier</font>.
<br><br>
BookPlayer prend en charge presque tous les formats audio, lit les pistes son de vidéos et peut extraire les chapitres depuis des fichiers ZIP ou M4B.
<br><br>
<b>Option 1 : <font color="#FFCC80">Copie</font></b> <i>(par défaut)</i> — Le fichier est <b>copié</b> dans la <font color="#90D8E8">mémoire privée</font> de BookPlayer.
<br>
<b>Avantages :</b>
<ul>
<li>Aucune permission nécessaire</li>
<li>Protégé contre les suppressions accidentelles</li>
</ul>
<br>
<b>Option 2 : <font color="#FFCC80">Sans copie</font></b> — Seul le chemin du fichier est enregistré. La lecture se fait directement depuis la <i>mémoire partagée</i>.
<br>
<b>Avantage :</b> économise de l'espace interne.
<br><br>
<b>Remarque :</b> les fichiers ZIP et les M4B découpés sont toujours copiés.
<br><br>
Activez <font color="#F48FB1"><b>la suppression automatique</b></font> dans le menu Options pour supprimer le fichier source après l'import.
]]>""",
    "help_librivox_text": """<![CDATA[
<font color="#BA68C8"><b>Importer des livres audio du domaine public</b></font>
<br><br>
Parcourez le <font color="#90D8E8"><b>catalogue Librivox</b></font> en sélectionnant une <font color="#81C784">langue</font>.
<br><br>
Appuyez sur un livre pour le télécharger — l'audio sera <font color="#FFCC80"><b>copié</b></font> sur votre appareil et ajouté à votre bibliothèque.
<br><br>
Vous pouvez <font color="#81C784"><b>commencer l'écoute immédiatement</b></font>.
]]>""",
    "help_podcast_text": """<![CDATA[
<font color="#BA68C8"><b>Découvrir des podcasts</b></font>
<br><br>
Explorez une large sélection de <font color="#90D8E8">podcasts</font> dans de nombreuses <font color="#81C784">langues</font>.
<br><br>
Téléchargez n'importe quel <font color="#FFCC80">épisode</font> en un seul clic.
<br><br>
Ajoutez des émissions à vos <font color="#EF9A9A">favoris</font> pour un accès rapide, et activez <font color="#81C784">le téléchargement automatique</font> pour récupérer les nouveaux épisodes.
<br><br>
Les vérifications se font régulièrement, notamment au démarrage de BookPlayer.
]]>""",
    "help_url_text": """<![CDATA[
<font color="#BA68C8"><b>Importer via un lien</b></font>
<br><br>
Collez une URL pointant directement vers un fichier audio, ZIP ou M4B.
<br><br>
Appuyez sur <font color="#FFCC80">Importer</font> et BookPlayer téléchargera et intégrera le contenu.
<br><br>
Tout est géré automatiquement — extraction, ajout et disponibilité à la lecture.
]]>""",
    "help_storage_text": """<![CDATA[
<font color="#BA68C8"><b>Où sont stockés les fichiers</b></font>
<br><br>
Par défaut, BookPlayer utilise la <font color="#81C784">carte SD</font> si elle est disponible, pour économiser de l'espace.
<br><br>
Pour basculer vers le stockage interne, modifiez le paramètre dans le menu <font color="#64B5F6">Options</font>.
]]>""",
    "help_memory_cleaning_text": """<![CDATA[
<font color="#BA68C8"><b>Gérer le stockage interne</b></font>
<br><br>
Un écran dédié affiche les fichiers copiés dans l'<font color="#90D8E8">espace privé</font> de BookPlayer.
<br><br>
Voyez quels éléments occupent le plus de place et <font color="#EF9A9A">supprimez</font> ceux dont vous n'avez plus besoin.
<br><br>
<font color="#F44336">Remarque :</font> ceci ne concerne que :
<ul>
<li>Les importations ZIP</li>
<li>Les importations manuelles avec <font color="#81C784">[copie]</font> activée</li>
</ul>
Le contenu en mémoire partagée n'est pas affiché ici.
]]>""",
    "help_tellme_text": """<![CDATA[
Vous avez un problème ou avez trouvé un bug ?
<br><br>
Merci de <font color="#81C784">me le signaler</font> afin que je puisse améliorer l'application !
<br><br>
Utilisez l'option <font color="#90D8E8">"Contacter le développeur"</font> dans le menu.
]]>""",
    "help_permission_text": """<![CDATA[
BookPlayer fonctionne <font color="#81C784">même sans autorisation</font>.
<br><br>
Mais pour profiter de toutes les fonctionnalités :
<ul>
<li><b>Lecture audio</b> → accès au stockage externe</li>
<li><b>Microphone</b> → activation de l'analyseur visuel audio</li>
</ul>
Refuser les autorisations ne casse rien — BookPlayer s'adapte.
]]>""",
    "help_forum_text": """<![CDATA[
<font color="#BA68C8"><b>Rejoignez la communauté BookPlayer</b></font>
<br><br>
Un nouveau <font color="#90D8E8">forum</font> a été lancé comme petite expérimentation (<i>juin 2025</i>).
<br><br>
Il est encore peu actif pour le moment, mais vous pouvez vous inscrire pour :
<ul>
<li><font color="#FFCC80">Signaler des bugs</font></li>
<li><font color="#FFCC80">Suggérer des fonctionnalités</font></li>
<li><font color="#FFCC80">Partager des astuces et des livres audio</font></li>
</ul>
Tous les messages sont lisibles publiquement. Connectez-vous pour participer.
]]>""",
    "help_tts_text": """<![CDATA[
<font color="#BA68C8"><b>Écouter du texte avec des voix synthétiques</b></font>
<br><br>
Les ebooks ou <font color="#90D8E8">documents texte</font> peuvent aussi être chargés comme <font color="#BA68C8">livres audio</font>.
<br><br>
BookPlayer utilisera les <font color="#81C784">voix intégrées de l'appareil</font> pour lire le texte à voix haute.
<br><br>
C'est une <font color="#FFCC80">fonctionnalité relativement nouvelle</font> — merci d'être indulgent et de partager vos retours.
]]>""",
    "help_radio_text": """<![CDATA[Parcourez les stations par <font color="#81C784">pays</font>, <font color="#FFCC80">popularité</font> ou autre, ou <font color="#64B5F6">cherchez</font> une station par nom.
    <br><br>
    Tapez sur une station pour <font color="#81C784"><b>lancer la lecture</b></font>.
    <br><br>
    Utilisez <font color="#EF9A9A">le cœur</font> pour ajouter aux <font color="#BA68C8">favoris</font>.]]>""",

    # Non-CDATA strings - Need escaping for apostrophes
    "option_delete_source_file_subtitle": "<i>Après l\\'importation d\\'un livre audio dans BookPlayer, le fichier ou dossier source peut être supprimé automatiquement.\\n\\nNote : cela n\\'est possible que pour les fichiers zip ou si l\\'option ci-dessus est cochée (copie des fichiers dans la mémoire interne de BookPlayer).</i>",
    "option_copy_file_subtitle": "Avantages : \\n\\n* Pas besoin de permission [Lire l\\'audio]\\n\\n* Aucun risque de perdre le livre audio ni la progression si le fichier source est supprimé/déplacé.\\n\\n\\nInconvénients :\\n\\n* Nécessite plus de mémoire si vous ne supprimez pas le fichier source.",
    "permission_read_write_denied": "L\\'autorisation [Lire l\\'audio] a été refusée.\\n\\n\\nCliquez sur [Infos sur l\\'app] pour activer les autorisations manuellement.\\n\\n\\nOu\\n\\n\\nCliquez sur [Options] pour activer l\\'option [Copier le fichier en interne] de l\\'app.\\n\\n<i>Ainsi, vos fichiers audio seront copiés dans la mémoire interne de Bookplayer et vous n\\'aurez plus besoin d\\'autorisation pour les lire.</i>\\n",
    "permission_read_denied_short_text_on_load": "L\\'autorisation [Musique et audio] a précédemment été refusée.\\n\\n\\nVous devrez cliquer sur [Infos sur l\\'app] et activer cette autorisation manuellement.\\n\\n\\n<i>Elle est nécessaire si vous souhaitez lire directement l\\'audio depuis le stockage général du smartphone (option de copie désactivée).</i>\\n",
    "DeleteImages_AskConfirm": "\"Vous êtes sur le point de supprimer toutes les images.\\n       Êtes-vous sûr ?\"",
    
    "option_reset_for_power_user_explain_text": "\\nLa réinitialisation pour utilisateur avancé restaure les valeurs d\\'usine, mais garde quelques réglages spécifiques :\\n\\nImportation :\\n\\n* [ne pas copier]\\n\\n* [\"Ouvrir avec\" pour tout type de fichier]\\n\\nComportement de lecture :\\n\\n* [ne pas ouvrir la vue de lecture dédiée]\\n\\n* [continuer la lecture audio en quittant l\\'application]\\n",
    "option_reset_app_explain_text": "\\nParfois, l\\'application peut se bloquer, par exemple après un crash pendant une importation, empêchant toute autre importation ou annulation.\\nDans ce cas, appuyez sur ce bouton pour annuler et réinitialiser les importations en cours.\\n",
    "option_tts_chunk_size_explain_text": "\\nLa taille du bloc détermine combien de texte est envoyé au moteur TTS à la fois. L\\'augmenter peut donner une voix plus fluide et naturelle.\\n",
    "option_tts_highlight_delay_explain_text": "\\nVous pouvez ajuster le délai de surbrillance pour mieux synchroniser l\\'audio avec les mots affichés.\\n",
    "option_automotive_on_explain_text": "\\nAutoriser Android Auto à se connecter à BookPlayer, afficher le catalogue et contrôler la lecture.\\n",
    "option_automotive_auto_resume_on_car_connect_explain_text": "\\nAutoriser Android Auto à reprendre automatiquement la lecture lors de la connexion à la voiture, si l\\'audio était déjà en cours.\\n",
    "option_automotive_let_car_autoplay_explain_text": "\\nAutoriser Android Auto à démarrer automatiquement la lecture lors de la connexion à la voiture, même si rien ne jouait.\\n",
    "ChangeTrackOrder_Text": "Faites glisser les boîtes.\\n\\n\\nMaintenez une boîte à gauche pour la sélectionner, puis faites-la glisser à la position voulue.",
    "m4b_error_non_standard_chapter_format": "\"\"\"\\n                        Ce fichier M4B utilise un format de chapitres non standard.\\n                        BookPlayer ne peut découper que les fichiers M4B qui possèdent des chapitres texte intégrés.\\n\\n                        Pour l\\'instant, BookPlayer importera le M4B original comme un seul fichier audio.\"\"\"",
    "m4b_error_too_large_or_incompatible_structure": "\"\"\"\\n                        Ce fichier M4B est trop volumineux ou utilise une structure incompatible\\n                        avec le système de mémoire d\\'Android.\\n\\n                        BookPlayer prend actuellement en charge uniquement les fichiers M4B standards.\\n\\n                        Astuces :\\n                        - Essayez de convertir le M4B avec un outil externe (ffmpeg, mp4box...),\\n                        - Ou séparez-le sur un ordinateur et importez les chapitres individuellement.\\n\\n                        Pour l\\'instant, BookPlayer importera le M4B original comme un seul fichier audio.\"\"\"",
    
    # Missing help titles
    "help_title": "Manuel BookPlayer",
    "help_title_manual_import": "Import manuel :",
    "help_librivox_title": "Import Librivox :",
    "help_podcast_title": "Import Podcast :",
    "help_url_title": "Import par URL :",
    "help_radio_title": "Radios du Monde",
    "help_memory_cleaning_title": "Nettoyage mémoire :",
    "help_storage_title": "Emplacement de stockage :",
    "help_permission_title": "Autorisations :",
    "help_tellme_title": "Contacter le développeur :",
    "help_forum_title": "Forum :",
    "help_tts_title": "Synthèse vocale :",
}

# Missing translations dict
missing_translations = {
    "Bookshelves": "Bibliothèques",
    "Librivox": "LibriVox",
    "MB_taken_by_linked_audios": " Mo : audios liés",
    "Storage": "Stockage",
    "action_reset_to_original": "↩️ RàZ original",
    "calculating_storage": "Calcul du stockage…",
    "copied": "copié",
    "device_storage_memory": "Mémoire de l\\'appareil",
    "linked": "lié",
    "loading_voice_3pt": "Chargement voix…",
    "mass_import_found_candidates_click": "%1$d candidats trouvés, cliquez pour ouvrir.",
    "mass_import_scan_complete": "Scan import de masse terminé.",
    "mass_import_scanning_title": "Scan import de masse...",
    "mass_import_selected_summary": "%1$d candidats sélectionnés pour import (%2$s)",
    "massiveimport_title": "Import de masse",
    "no_ebooks_found_bookshelf": "Aucun ebook trouvé \\npour la bibliothèque [%1$s] \\navec la langue [%2$s]",
    "option_mass_import_display_storage_bar": "Afficher la barre de stockage",
    "option_podcast_open_specific_view_subtitle2": "Note : Vous pouvez basculer entre ces vues en tapotant ou double-tapotant l\\'image de couverture.",
    "others": "autres",
    "power_management_exempt": "App exemptée des optimisations batterie (bien)",
    "power_management_subject": "App sujette aux optimisations batterie (risque d\\'arrêt)",
    "radio_thanks": "Merci à radio-browser.info pour leur répertoire gratuit de radios",
    "sd_card_storage": "Stockage Carte SD",
    "select_at_least_one_item": "Veuillez sélectionner au moins un élément.",
    "storage_device": "Stockage appareil",
    "storage_legend_new_books": "nouveaux livres",
    "storage_sd_card": "Stockage carte SD",
}

# 1. Parse existing French file to try to find existing good translations
french_map = {}
if os.path.exists(french_path):
    try:
        with open(french_path, 'r', encoding='utf-8') as f:
            content = f.read()
            # Capture content including CDATA or whatever
            for m in re.finditer(r'<string name="([^"]+)"[^>]*>(.*?)</string>', content, re.DOTALL):
                key = m.group(1)
                val = m.group(2)
                french_map[key] = val
    except Exception as e:
        print(f"Error reading French file: {e}")

# 2. Process English file
with open(english_path, 'r', encoding='utf-8') as f:
    english_content = f.read()

def replacer(match):
    start_tag = match.group(1)
    key = match.group(2)
    attrs = match.group(3)
    en_content = match.group(4)
    end_tag = match.group(5)
    
    fr_content = en_content # Default fallback
    
    if key in recovered_data:
        fr_content = recovered_data[key]
    elif key in missing_translations:
        fr_content = missing_translations[key]
    elif key in french_map and "Help" not in key and "help" not in key:
        # Prevent using English text if french_map oddly has English (due to bad syncs)
        # But for now we trust it for single lines
        fr_content = french_map[key]
    
    return f'{start_tag}{fr_content}{end_tag}'

pattern = r'(<string name="([^"]+)"(.*?)>)(.*?)(</string>)'
new_content = re.sub(pattern, replacer, english_content, flags=re.DOTALL)

# Plurals
plurals_map = {
    "audio_files_count": {"one": "%d fichier audio", "other": "%d fichiers audio"},
    "subfolders_count": {"one": "%d sous-dossier", "other": "%d sous-dossiers"},
    "tracks_count": {"one": "%d piste", "other": "%d pistes"}
}

def plural_replacer(match):
    full_block = match.group(0)
    key = match.group(1)
    body = match.group(2)
    if key in plurals_map:
        def item_replacer(m):
             qty = m.group(1)
             if qty in plurals_map[key]:
                 return f'<item quantity="{qty}">{plurals_map[key][qty]}</item>'
             return m.group(0)
        new_body = re.sub(r'<item quantity="([^"]+)">.*?</item>', item_replacer, body)
        return f'<plurals name="{key}">{new_body}</plurals>'
    return full_block

new_content = re.sub(r'<plurals name="([^"]+)">\s*(.*?)\s*</plurals>', plural_replacer, new_content, flags=re.DOTALL)

with open(french_path, 'w', encoding='utf-8') as f:
    f.write(new_content)

print("Recovery and Sync complete.")
