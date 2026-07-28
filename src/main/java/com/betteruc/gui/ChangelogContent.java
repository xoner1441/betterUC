package com.betteruc.gui;

import java.util.Arrays;

public final class ChangelogContent {

    private static final Page[] LATEST_PAGES = new Page[]{
            new Page(
                    "AKTUELLE \u00C4NDERUNGEN",
                    "HUD-Profile",
                    "Eigene HUD-Layouts lassen sich jetzt speichern und jederzeit wechseln.",
                    new String[]{
                            "Das bisherige HUD wird beim ersten Start automatisch als Profil Standard \u00FCbernommen.",
                            "Profile speichern Positionen, Gr\u00F6\u00DFen, Stile, Farben, Fonts, Gradients und Sichtbarkeit.",
                            "Eine aufklappbare Liste zeigt alle Profile und wechselt direkt zum ausgew\u00E4hlten Layout.",
                            "Profile k\u00F6nnen erstellt, dupliziert, umbenannt oder auf Standardwerte zur\u00FCckgesetzt werden.",
                            "Vor dem L\u00F6schen eines Profils ist eine zweite Best\u00E4tigung erforderlich.",
                            "HUD-Profile lassen sich als gepr\u00FCfte JSON-Datei exportieren und importieren.",
                            "Aktive HUD-Profile werden zusammen mit den Cloud-Einstellungen synchronisiert."
                    }
            ),
            new Page(
                    "AKTUELLE \u00C4NDERUNGEN",
                    "Bedienung & Health HUD",
                    "Das ClickGUI bleibt dort, wo du zuletzt gearbeitet hast.",
                    new String[]{
                            "Kategorie, Modul und Scrollposition werden beim Schlie\u00DFen gespeichert.",
                            "Das ClickGUI \u00F6ffnet wieder exakt im zuletzt verwendeten Modul.",
                            "Absorptionsherzen lassen sich im Health-Modul ein- oder ausblenden.",
                            "Die Farbe der Absorptionsherzen ist unabh\u00E4ngig einstellbar.",
                            "Das Health HUD bleibt mit und ohne Absorption sauber zentriert."
                    }
            ),
            new Page(
                    "AKTUELLE \u00C4NDERUNGEN",
                    "Updates & Kompatibilit\u00E4t",
                    "Updates und unterst\u00FCtzte Minecraft-Versionen werden transparenter.",
                    new String[]{
                            "Der Update-Bereich zeigt Suche, Download und vorbereitete Version als Live-Status.",
                            "Downloads werden weiterhin vor der Installation als passende betterUC-Jar gepr\u00FCft.",
                            "Minecraft 1.21.10, 26.1.2 und 26.2 werden weiterhin getrennt gebaut und gepr\u00FCft.",
                            "Ingame wird nur noch der aktuelle Changelog angezeigt.",
                            "Die vollst\u00E4ndige Versionshistorie ist auf betteruc.de/changelog verf\u00FCgbar."
                    }
            )
    };

    private ChangelogContent() {
    }

    public static Page[] latestPages() {
        return Arrays.copyOf(LATEST_PAGES, LATEST_PAGES.length);
    }

    public static Page[] allPages() {
        return latestPages();
    }

    public static Page[] clickGuiSections() {
        return latestPages();
    }

    public record Page(String eyebrow, String title, String description, String[] lines) {
    }
}
