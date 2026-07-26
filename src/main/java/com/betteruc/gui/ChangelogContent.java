package com.betteruc.gui;

import java.util.Arrays;

public final class ChangelogContent {

    private static final Page[] LATEST_PAGES = new Page[]{
            new Page(
                    "NEU IN 1.3.0",
                    "Bank & PayDay",
                    "Wichtige Bankinformationen fallen rechtzeitig auf.",
                    new String[]{
                            "Der neue Reichensteuer-Alert warnt 5 Minuten vor dem PayDay.",
                            "Die Warnung erscheint, wenn mehr als 100.000$ auf der Bank liegen.",
                            "Ein Slide-in, ein Chat-Hinweis und ein optionaler Ton machen den Alert sichtbar.",
                            "Veraltete Bankwerte werden vor der Pruefung automatisch aktualisiert.",
                            "Alert und Warnton lassen sich im Bank-Modul getrennt aktivieren."
                    }
            ),
            new Page(
                    "NEU IN 1.2.9",
                    "Automationen & HUDs",
                    "Mehr Kontrolle über Job-Helfer und wichtige Anzeigen.",
                    new String[]{
                            "Lieferant, Fischer, Winzer, Gärtner und Müllmann sind einzeln schaltbar.",
                            "Automationsschalter werden im Cloud-Profil synchronisiert.",
                            "Der Mülleimer-Filter markiert ausgewählte Fundstücke direkt im Inventar.",
                            "Das Ammo HUD erkennt Waffenprofile, Nachladen und niedrige Munition zuverlässiger.",
                            "Fremde Actionbar-Texte überschreiben die Munitionsanzeige nicht mehr."
                    }
            ),
            new Page(
                    "NEU IN 1.2.8",
                    "Cloud & Stabilität",
                    "Deine Einstellungen bleiben sicher, aktuell und auf deinen Geräten verfügbar.",
                    new String[]{
                            "Cloud-Profile synchronisieren HUD-, Chat-, Ping- und Komforteinstellungen.",
                            "Beim Serverbeitritt wird zuerst dein aktuelles Cloud-Profil geladen.",
                            "Revisionsschutz verhindert unbemerkte Überschreibungen durch mehrere Clients.",
                            "Frühere Cloud-Stände können im Adminpanel wiederhergestellt werden.",
                            "Render- und Cache-Optimierungen reduzieren Lagspikes in HUDs, Chat und Tablist.",
                            "Globale Feature-Schalter können Funktionen bei Problemen serverseitig pausieren."
                    }
            ),
            new Page(
                    "NEU IN 1.2.8",
                    "HUD & Bedienung",
                    "Mehr Kontrolle über Darstellung, Positionierung und tägliche Abläufe.",
                    new String[]{
                            "Potion HUD besitzt eine eigene Farbe für Text und moderne Akzentstriche.",
                            "HUD-Positionen, Größen, Farben, Stile, Präfixe und Fonts werden synchronisiert.",
                            "Hotkeys können Commands sofort senden oder nur im Chat vorbereiten.",
                            "Gameplay-HUDs pausieren unter 26.x hinter Inventaren und Einstellungsfenstern.",
                            "Der Cloud-Sync-Bereich kann laden, hochladen oder die Automatik pausieren."
                    }
            ),
            new Page(
                    "NEU IN 1.2.8",
                    "Kommunikation",
                    "betterUC verbindet Mod-User, Fraktionen und das Team übersichtlicher.",
                    new String[]{
                            "/buc <nachricht> schreibt in den globalen betterUC Mod-Chat.",
                            "Der Globalchat kann live mit dem moderierten Discord-Kanal verbunden werden.",
                            "Pings können global, an die eigene Fraktion oder an den Staat gesendet werden.",
                            "Polizei, FBI und Rettungsdienst teilen sich den Ping-Kanal Staat.",
                            "Admins können wichtige Mitteilungen mit /bubroadcast senden.",
                            "Discord-Tickets enthalten Mod-Daten, Zuständigkeit, Abschlussgrund und Transkript."
                    }
            ),
            new Page(
                    "NEU IN 1.2.8",
                    "Jobs & Commands",
                    "Neue Helfer nehmen wiederkehrende Schritte ab, ohne die Kontrolle zu verstecken.",
                    new String[]{
                            "Müllmann liest Glas, Metall, Abfall und Holz direkt aus dem Scoreboard.",
                            "/muellarea richtet die vier globalen Müllsortierbereiche ein.",
                            "Winzer leert alle Trauben-Fenster bis zur geforderten Anzahl.",
                            "Gärtner gibt Blumen ab und sammelt verwelkte Büsche.",
                            "Fischer sucht Schwärme, fängt Fisch und gibt ihn am Steg ab.",
                            "/vm <spieler> sendet /asu <spieler> Versuchter Mord."
                    }
            )
    };

    private static final Page[] FEATURE_PAGES = new Page[]{
            new Page(
                    "FEATURE-ÜBERSICHT",
                    "HUD & Design",
                    "Alle wichtigen Informationen dort, wo du sie brauchst.",
                    new String[]{
                            "Health, FPS, Payday, Ammo, Bank, Bargeld, Potion und Sprint sind einzeln einstellbar.",
                            "Hack-, Plant-, Dealer- und Produktions-Timer unterstützen wiederkehrende Abläufe.",
                            "HUDs lassen sich in der Vorschau verschieben, skalieren und miteinander ausrichten.",
                            "Modern, Transparent, Cartoon und Custom stehen als HUD-Stile bereit.",
                            "Farben, Farbverläufe, Präfixe und Custom Fonts können pro HUD gewählt werden."
                    }
            ),
            new Page(
                    "FEATURE-ÜBERSICHT",
                    "Ping-System",
                    "Private Markierungen für koordinierte Mod-User.",
                    new String[]{
                            "Das Pingrad bietet Normal-, Gefahr- und Sammeln-Pings.",
                            "Reichweite, Größe, Farben und Sound lassen sich anpassen.",
                            "Global-, Fraktions- und Staatspings trennen die Empfänger sauber.",
                            "Cooldown und Reichweitenprüfung verhindern Spam und unnötige Verarbeitung.",
                            "Pings werden blockiert, wenn Kommunikationsgeräte fehlen oder das Handy aus ist."
                    }
            ),
            new Page(
                    "COMMAND-LISTE",
                    "betterUC Commands",
                    "Die aktuell registrierten Kurzbefehle und Mod-Funktionen.",
                    new String[]{
                            "/register <passwort> richtet den Login für das Web-Userpanel ein.",
                            "/betterucupdate installiert die neueste passende Mod-Version.",
                            "/abbuchen <betrag> und /überweisen <spieler> <summe> <grund> kürzen Bankbefehle ab.",
                            "/vm <spieler> meldet Versuchter Mord über /asu.",
                            "/adropdrink startet die automatische Getränkeabgabe.",
                            "/blset, /setbl, /modbl und /blinfo verwalten Blacklist-Einträge.",
                            "/buc <nachricht> öffnet den globalen Mod-Chat.",
                            "/buonline zeigt Helpern und Admins verbundene Mod-User.",
                            "/bubroadcast <nachricht> sendet als Admin eine Ankündigung."
                    }
            )
    };

    private ChangelogContent() {
    }

    public static Page[] latestPages() {
        return Arrays.copyOf(LATEST_PAGES, LATEST_PAGES.length);
    }

    public static Page[] allPages() {
        Page[] pages = Arrays.copyOf(LATEST_PAGES, LATEST_PAGES.length + FEATURE_PAGES.length);
        System.arraycopy(FEATURE_PAGES, 0, pages, LATEST_PAGES.length, FEATURE_PAGES.length);
        return pages;
    }

    public static Page[] clickGuiSections() {
        return latestPages();
    }

    public record Page(String eyebrow, String title, String description, String[] lines) {
    }
}
