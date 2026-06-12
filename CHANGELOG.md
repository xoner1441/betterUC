# Changelog

## [1.2.1] - 2026-06-12

### Neu
- **Chat-Customizations** fuer WPS/HQ-Nachrichten: Gesucht, getoetet, inhaftiert, veraendert und geloescht werden kompakter dargestellt.
- **Reinf-Customizations** fuer Fraktions- und Buendnisrufe mit Spieler, Ort und Entfernung.
- **Pay-Customization** fuer `/pay`: Geld senden und erhalten wird als kurze PAY-Nachricht angezeigt.
- Neuer Shortcut **`/abbuchen <betrag>`** als Ersatz fuer `/bank abbuchen <betrag>`.

### Geaendert
- WPS/HQ- und Reinf-Customizations sind im Client-Tab getrennt togglebar.
- Empfangene `/pay`-Spielernamen werden dunkelgruen dargestellt.
- betterUC-Hologramme koennen im ClickGUI ein- und ausgeschaltet werden.

### Versionierung
- Mod-Version auf **`1.2.1`** gesetzt.

## [1.1.8] - 2026-06-07

### Neu
- **Tablist-Badge-Overlay**: betterUC zeichnet das `bUC`-Badge jetzt separat hinter den fertigen Tablist-Namen.
- **Client-Kompatibilitaet**: Icons und Prefixe anderer Clients wie Unique, LabyMod oder Badlion bleiben sichtbar.

### Geaendert
- **Bargeld-HUD** aktualisiert sich jetzt bei Fraktionsbank-Einzahlungen und -Auszahlungen.
- Normale Mod-User bekommen kein Hologramm mehr, sondern nur noch das Tablist-Badge.
- Rollen wie Admin, Helper, Partner und VIP behalten Hologramm und farbiges Badge.
- Access-Code- und Relay-Felder speichern beim Wechseln im ClickGUI zuverlaessiger.

### Behoben
- Stats-Detailzeilen wie `Immobilien [Details]` werden sauberer aus automatischen `/stats`-Abfragen gefiltert.
- F-Bank-Zeilen mit `in die Fraktionsbank eingezahlt` werden nun korrekt erkannt.

### Versionierung
- Mod-Version auf **`1.1.8`** gesetzt.

## [1.1.7] - 2026-06-07

### Neu
- **Plant Timer** reagiert jetzt auf sichtbare Plantage-Nachrichten von allen Spielern.
- **Plant Timer** ist im ClickGUI als HUD-Modul togglebar.
- **Partner-Rolle** mit aqua Hologramm, aqua `bUC`-Tablist-Badge und Userpanel-Anzeige.

### Geändert
- Fraktionspings verlassen sich jetzt auf `/stats` und das Relay-System.
- Die alte `/memberinfo...`-Silent-Abfrage wurde entfernt.
- Admin bleibt die einzige Rolle mit Adminpanel-Rechten; Partner ist eine normale Anzeige-/Statusrolle.

### Versionierung
- Mod-Version auf **`1.1.7`** gesetzt.

## [1.1.6] - 2026-06-05

### Neu
- **Willkommensscreen** im Minecraft-Hauptmenü zeigt den Changelog nach einer neuen Mod-Version automatisch einmalig an.
- **Helper-Rolle** mit gelbem Hologramm und gelbem `bUC`-Tablist-Badge.
- Helper ist unter Admin einsortiert, hat aber keine zusätzlichen Website-Rechte.
- Website-Startseite und ClickGUI-Changelog wurden auf die aktuelle Version angepasst.
- **Discord-Bereich** in der ClickGUI öffnet oder kopiert den Community-Invite.
- **Discord-Bot erweitert**: Ticket-System mit Schließen/Löschen, GitHub-Update-Posts, `/updates` Commands und Rollen-Sync für Mod-User, VIP, Helper und Admin.

### Geändert
- Die Feature- und Commandlisten zeigen nur noch Funktionen, die aktuell aktiv sind.
- Pingrad, Ping-Cooldown, Soundauswahl und Reichweitenlimit bleiben als aktuelle Ping-Features hervorgehoben.
- Server-Deploy kann den Discord-Bot automatisch mitstarten, wenn `DISCORD_BOT_TOKEN` und `DISCORD_GUILD_ID` gesetzt sind.

### Entfernt
- Veraltete Client-Commands **`/seinzahlen`** und **`/scall`** entfernt.
- Alte Website-Karten und Changelog-Einträge zu entfernten Commands entfernt.

### Versionierung
- Mod-Version auf **`1.1.6`** gesetzt.

## [1.0.2] - 2026-05-03

### Neu
- **Fullbright** als togglebares Feature im Settings-HUD.
- **FPS-HUD** mit Toggle und frei einstellbarer Position.
- **Payday-HUD** auf Basis von `/stats` (`Payday: x/60 Minuten`).
- **CookDrug-Timer-HUD**:
  - Start bei `[CookDrug] Das Pseudoephedrin aus der Medizin muss nun einige Minuten kochen.`
  - 9-Minuten-Timer
  - Sofortiges Beenden/Reset bei `[CookDrug] Die Kristalle sind fertig gekocht.`
- **Ammo-HUD** aus der orangenen Munitionseinblendung (Ammo + Waffenname).
- **Kontostand-HUD (Bank)**:
  - Live-Update aus Kontoauszug-Chatzeilen
  - Toggle + X/Y-Position im Settings-HUD
  - Persistenter letzter Kontostand (bleibt nach Rejoin/Restart erhalten)
  - Anzeige mit Tausendertrennpunkten (z. B. `88.375$`)
- Neuer Custom-Command **`/eigenbedarf`**:
  - ersetzt die manuelle Nutzung von `/dbank get <Droge> <Menge> <Reinheit>`
  - 2 konfigurierbare Felder im Settings-HUD (Droge, Menge, Reinheit 0-3)
  - unterstuetzt: `Pulver`, `Kraeuter`, `Kristalle`, `Wundertuete`
  - Ausfuehrung ueber `/eigenbedarf` (beide Felder) oder `/eigenbedarf 1|2`
- **Auto-Marker fuer Fahrzeuge**:
  - Marker/Hologramm beim Aussteigen (mit Render-Range)
  - Marker verschwindet beim erneuten Einsteigen.

### Geaendert
- Settings-HUD strukturell verbessert (Seitenaufteilung), damit Optionen nicht mehr aus dem Bildschirm laufen.
- HUD-Darstellung fuer ToggleSprint/FPS/Payday auf transparente Textdarstellung umgestellt (kein dunkler Block mehr).
- Payday-Logik erweitert:
  - `/stats` beim ersten Join zur Initialisierung
  - Minuten laufen lokal weiter
  - Reset bei Payday-Header im Chat.
- AFK-Integration:
  - Payday pausiert bei `Du bist nun im AFK-Modus`
  - laeuft weiter bei `Du bist nun nicht mehr im AFK-Modus`
  - zusaetzlicher stiller `/stats`-Refresh nach AFK-Ende.
- Auto-Reload (`/memberinfoall kartell` + `/blacklist`) pausiert im AFK-Modus und laeuft danach wieder weiter.

### Behoben
- FPS-HUD zeigte teils keine korrekten FPS an.
- Fullbright wirkte nicht vollstaendig: Schattendarstellung/Ambient-Anteile wurden nachgezogen.
- `memberinfoall`-Ausgabe:
  - **Auto-Refresh bleibt unterdrueckt**
  - **manueller `/memberinfoall kartell` bleibt sichtbar**.
- Car-Marker-/Render-Implementierung fuer die vorhandene Fabric/Yarn-API stabilisiert.

### Versionierung
- Mod-Version auf **`1.0.2`** gesetzt.
