package com.betteruc.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class ChangelogScreen extends Screen {

    private static final int BUTTON_W = 170;
    private static final int BUTTON_H = 20;

    private static final Page[] PAGES = new Page[]{
            new Page(
                    "Highlights",
                    "betterUC 1.2.3",
                    new String[]{
                            "/adropdrink gibt Lieferanten-Getraenke automatisch im 2,5s-Takt ab",
                            "Auto-Dropdrink stoppt, wenn du nicht in der Kneipe bist",
                            "Fischer-Automation sucht, faengt und gibt Fisch am Steg ab",
                            "Lieferjunge-Scoreboard wird genauer ausgelesen",
                            "WPS/HQ-Chatformat wirkt jetzt ruhiger und einheitlicher",
                            "Labels wie gesucht, getoetet, inhaftiert und veraendert sind klein und nicht mehr fett",
                            "Formatierte Chatzeilen nutzen nun den Trenner ◆",
                            "Lange Fahndungsgruende bleiben kompakt in einer Grund-Zeile",
                            "Zeit, Wanteds und Wanted-Aenderungen stehen separat darunter",
                            "Drogenabgabe 5/10/15g wird automatisch zu DA 5g/10g/15g",
                            "Plus-Abstaende in Gruenden werden automatisch geglaettet",
                            "WPS/HQ-Nachrichten koennen im Chat kompakter und uebersichtlicher dargestellt werden",
                            "Reinf- und Verstaerkungsrufe haben eine eigene Chat-Customization mit Spieler, Ort und Entfernung",
                            "Reinf- und WPS/HQ-Customizations sind im Client-Tab getrennt schaltbar",
                            "/pay-Nachrichten werden als kurze PAY-Zeilen angezeigt",
                            "Empfangene /pay-Betraege sind dunkelgruen markiert",
                            "betterUC-Hologramme koennen im ClickGUI ein- und ausgeschaltet werden",
                            "Neuer Shortcut: /abbuchen <betrag> sendet /bank abbuchen <betrag>",
                            "/buonline zeigt Helpern und Admins online Mod-User mit Fraktion und Version",
                            "bUC-Tablist-Badge wird als eigenes Overlay hinter den fertigen Namen gezeichnet",
                            "Andere Client-Icons wie Unique, LabyMod oder Badlion bleiben in der Tablist sichtbar",
                            "HUD-Farbverlauf ist jetzt pro HUD einzeln einstellbar",
                            "Auto-Updater lädt neue Release-JARs herunter und ersetzt sie nach dem Schließen des Spiels",
                            "Bargeld-HUD erkennt Fraktionsbank-Einzahlungen und -Auszahlungen",
                            "Access-Code und Relay-Felder speichern zuverlaessiger im ClickGUI",
                            "Stats-Filter entfernt Detailzeilen wie Immobilien sauberer",
                            "Normale Mod-User bekommen nur noch ein Tablist-Badge, Rollen behalten ihr Hologramm",
                            "Plant Timer reagiert jetzt auf sichtbare Plantage-Nachrichten von allen Spielern",
                            "Plant Timer ist im HUD-Tab togglebar",
                            "Fraktionspings nutzen /stats und das Relay-System ohne /memberinfo-Silent-Abfrage",
                            "Unique Client wird erkannt und betterUC-Hologramme werden automatisch höher gestapelt",
                            "Willkommensscreen zeigt neue Features nach einem Update einmalig im Hauptmenü",
                            "Helper-Rolle mit gelbem Hologramm und bUC-Tablist-Badge",
                            "Helper ist unter Admin einsortiert, hat aber keine Website-Zusatzrechte",
                            "Partner-Rolle mit aqua Hologramm und aqua bUC-Tablist-Badge",
                            "Partner wird im Userpanel angezeigt, bekommt aber keine Adminpanel-Rechte",
                            "VIP-Rolle mit dunkellila Hologramm und bUC-Tablist-Badge",
                            "Veraltete /seinzahlen- und /scall-Commands entfernt",
                            "Website, ClickGUI und Changelog zeigen nur noch aktive Features",
                            "Userpanel zeigt Rolle, Status und getrackte Account-Daten",
                            "Adminpanel kann Accounts nach Rolle, Status, Online-Zustand und Fraktion filtern",
                            "Verbindungsseite im ClickGUI wurde klarer sortiert",
                            "Website-Bereiche für Access, Userpanel und Adminpanel sind getrennt",
                            "Potion-HUD im Inventar-Stil mit Icon, Name und Dauer",
                            "CookDrug-Timer entfernt, weil der Server nun einen eigenen Timer hat",
                            "Plant-Timer für eigene Plantagen mit Wasser- und Düngerzeiten",
                            "Payday-, Bank-, Bargeld-, Ammo-, FPS-, Herz- und ToggleSprint-HUDs",
                            "HUD-Positionen und Größen können in der HUD Vorschau angepasst werden",
                            "Auto-Stats Join direkt im Settings-HUD schaltbar",
                            "Build kopiert die Mod-JAR automatisch in den Minecraft-Mods-Ordner",
                            "Update Notify zeigt neue betterUC-Versionen direkt im Chat und kann Updates automatisch vorbereiten",
                            "Ping Relay sendet private Markierungen an andere betterUC-Nutzer",
                            "Pingrad mit Normal-, Gefahr- und Sammeln-Pings",
                            "Eigene Pingfarben, Ping-Soundauswahl und Cooldown gegen Spam",
                            "Discord-Bereich in der ClickGUI öffnet oder kopiert den Community-Invite",
                            "Discord-Bot mit Online-Liste, Tickets, Update-Posts und Account-Linking"
                    }
            ),
            new Page(
                    "HUDs & Komfort",
                    "Spieler-Features",
                    new String[]{
                            "FPS-HUD für schnelle Performance-Übersicht",
                            "Payday-HUD mit AFK-Erkennung",
                            "Bank-HUD erkennt neue Kontostände aus dem Chat",
                            "Bargeld-HUD liest /stats, Bargeldbestände, Auszahlungen, Einzahlungen und +/- Beträge",
                            "Ammo-HUD für Waffenanzeige",
                            "Potion-HUD zeigt aktive Effekte wie im Inventar",
                            "Health-HUD für kompakte Lebensanzeige",
                            "ToggleSprint-HUD und integrierter ToggleSprint",
                            "Frei verschiebbare HUD-Elemente per Drag & Drop",
                            "Farbwahl für mehrere HUD-Anzeigen"
                    }
            ),
            new Page(
                    "Ping System",
                    "Private Markierungen",
                    new String[]{
                            "Pingrad öffnet sich beim Halten der Ping-Taste",
                            "Normal-, Gefahr- und Sammeln-Pings sind auswählbar",
                            "Pings können global oder nur an die eigene Fraktion gesendet werden",
                            "Pings werden nur in der eingestellten Reichweite erkannt",
                            "Eigene Farben pro Pingtyp",
                            "Ping-Sounds sind auswählbar und können deaktiviert werden",
                            "Cooldown schützt vor Spam",
                            "Pings werden blockiert, wenn Kommunikationsgeräte fehlen oder das Handy aus ist"
                    }
            ),
            new Page(
                    "Tools & Commands",
                    "Alltag im Spiel",
                    new String[]{
                            "Hotkey Commands: eigene Befehle auf Tasten legen",
                            "/car find Koordinaten werden automatisch ins Navi übernommen",
                            "Chat-Zeitstempel und größere Chat-Historie",
                            "/adropdrink sendet /dropdrink automatisch anhand des Lieferjunge-Scoreboards",
                            "Fischer-Jobhinweise starten /findschwarm, /catchfish und /dropfish automatisch",
                            "Ping-Taste ist in den Minecraft-Keybinds frei einstellbar",
                            "/register <passwort> verbindet deinen Ingame-Account mit dem Userpanel",
                            "/buonline zeigt Helpern und Admins online Mod-User"
                    }
            ),
            new Page(
                    "Fraktions-Tools",
                    "Optional für Orga-Spieler",
                    new String[]{
                            "Blacklist-Gründe können im Menü gepflegt werden",
                            "/blset und /setbl setzen Blacklist-Einträge mit Vorlagen",
                            "/modbl erweitert bestehende Blacklist-Einträge",
                            "/blinfo zeigt gespeicherte Infos zu einem Blacklist-Spieler",
                            "/setrp setzt RP-Stufen für Blacklist-Einträge",
                            "Normale /blacklist-Ausgabe bleibt serverseitig sichtbar"
                    }
            )
    };

    private final Screen parent;
    private final boolean welcomeMode;
    private int pageIndex = 0;

    public ChangelogScreen(Screen parent) {
        this(parent, false);
    }

    public ChangelogScreen(Screen parent, boolean welcomeMode) {
        super(Component.literal(welcomeMode ? "betterUC Willkommen" : "betterUC Features"));
        this.parent = parent;
        this.welcomeMode = welcomeMode;
    }

    @Override
    protected void init() {
        int centerX = width / 2;
        int navY = height - 54;

        Button previousButton = Button.builder(Component.literal("<"), b -> {
            if (pageIndex > 0) {
                pageIndex--;
                refreshWidgets();
            }
        }).bounds(centerX - BUTTON_W / 2 - 28, navY, 24, BUTTON_H).build();
        previousButton.active = pageIndex > 0;
        addRenderableWidget(previousButton);

        Button nextButton = Button.builder(Component.literal(">"), b -> {
            if (pageIndex < PAGES.length - 1) {
                pageIndex++;
                refreshWidgets();
            }
        }).bounds(centerX + BUTTON_W / 2 + 4, navY, 24, BUTTON_H).build();
        nextButton.active = pageIndex < PAGES.length - 1;
        addRenderableWidget(nextButton);

        String closeLabel = welcomeMode ? "Verstanden" : "Zurück";
        addRenderableWidget(Button.builder(Component.literal(closeLabel), b -> {
            if (minecraft != null) {
                minecraft.gui.setScreen(parent);
            }
        }).bounds(centerX - BUTTON_W / 2, height - 30, BUTTON_W, BUTTON_H).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        if (welcomeMode && parent != null) {
            parent.extractRenderState(context, mouseX, mouseY, delta);
        } else {
            context.fill(0, 0, width, height, 0xD0000000);
        }

        context.fill(0, 0, width, height, welcomeMode ? 0x88000000 : 0x66000000);

        int panelWidth = Math.min(590, width - 40);
        int panelHeight = Math.min(300, height - 92);
        int panelX = width / 2 - panelWidth / 2;
        int panelY = Math.max(20, height / 2 - panelHeight / 2 - 8);

        context.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0xF010151C);
        drawBorder(context, panelX, panelY, panelWidth, panelHeight, 0xFF38BDF8);

        super.extractRenderState(context, mouseX, mouseY, delta);

        Page page = PAGES[pageIndex];
        String heading = welcomeMode ? "Willkommen bei betterUC" : "betterUC Changelog & Features";
        context.centeredText(font, Component.literal(heading), width / 2, panelY + 12, 0xFFFFFFFF);
        context.centeredText(font, Component.literal(page.title), width / 2, panelY + 30, 0xFF38BDF8);
        context.centeredText(font, Component.literal(page.subtitle), width / 2, panelY + 44, 0xFFBBBBBB);
        context.centeredText(
                font,
                Component.literal("Seite " + (pageIndex + 1) + "/" + PAGES.length),
                width / 2,
                height - 49,
                0xFFAAAAAA
        );

        int x = panelX + 22;
        int y = panelY + 68;
        int maxLineWidth = panelWidth - 44;
        int bottomY = panelY + panelHeight - 14;
        for (String line : page.lines) {
            if (y > bottomY) break;
            y = drawWrappedBullet(context, line, x, y, maxLineWidth);
        }
    }

    private int drawWrappedBullet(GuiGraphicsExtractor context, String line, int x, int y, int maxWidth) {
        String bullet = "- ";
        String remaining = line;
        boolean firstLine = true;
        int currentY = y;
        while (!remaining.isEmpty()) {
            String prefix = firstLine ? bullet : "  ";
            int availableWidth = maxWidth - font.width(prefix);
            String part = takeFittingText(remaining, availableWidth);
            context.text(font, Component.literal(prefix + part), x, currentY, 0xFFE6E6E6);
            remaining = remaining.substring(part.length()).trim();
            currentY += 12;
            firstLine = false;
        }
        return currentY + 2;
    }

    private String takeFittingText(String text, int maxWidth) {
        if (font.width(text) <= maxWidth) return text;

        int lastSpace = -1;
        for (int i = 1; i <= text.length(); i++) {
            char c = text.charAt(i - 1);
            if (Character.isWhitespace(c)) {
                lastSpace = i - 1;
            }
            String candidate = text.substring(0, i).trim();
            if (font.width(candidate) > maxWidth) {
                if (lastSpace > 0) {
                    return text.substring(0, lastSpace).trim();
                }
                return text.substring(0, Math.max(1, i - 1)).trim();
            }
        }
        return text;
    }

    private void refreshWidgets() {
        clearWidgets();
        init();
    }

    private void drawBorder(GuiGraphicsExtractor context, int x, int y, int w, int h, int color) {
        context.fill(x, y, x + w, y + 1, color);
        context.fill(x, y + h - 1, x + w, y + h, color);
        context.fill(x, y, x + 1, y + h, color);
        context.fill(x + w - 1, y, x + w, y + h, color);
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.gui.setScreen(parent);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private record Page(String title, String subtitle, String[] lines) {
    }
}
