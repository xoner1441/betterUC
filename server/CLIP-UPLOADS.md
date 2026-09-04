# Manuelle Clip-Uploads (Beta 12)

Status: implementiert, aber noch nicht live eingerichtet. Es wurde kein R2-Bucket angelegt
und kein produktiver Upload ausgelöst. Ohne R2-Konfiguration antworten Clip-Uploads mit
503; bestehende Screenshots und ihre Links bleiben unverändert nutzbar.

## Einrichten

1. Im eigenen Cloudflare-Konto R2 aktivieren und einen **privaten** Bucket anlegen,
   beispielsweise `betteruc-clips`. Eventuelle Kosten/Billing vorher prüfen.
   Weder `r2.dev` noch eine öffentliche Custom Domain für den Bucket aktivieren.
2. Einen separaten R2-API-Token mit Object Read & Write ausschließlich für diesen Bucket
   erstellen. Account-ID, Bucket-Name, Access-Key-ID und Secret-Key in den vier
   `CLIP_R2_*`-Variablen der Serverumgebung hinterlegen (siehe `.env.example`).
   Keine Schlüssel in Git, Mod/JAR, Chat oder Screenshots eintragen.
3. Server-Abhängigkeiten mit `npm ci --omit=dev` installieren. Der bestehende
   Datenbank-Migrationslauf legt über `012_clip_uploads.sql` die Metadaten an.
   Vor Deployment Datenbank wie üblich sichern; vorhandene Migrationen nicht ändern.
4. `PUBLIC_BASE_URL=https://betteruc.de` und den bestehenden HTTPS-Proxy beibehalten.
   Nur kleine JSON-Anfragen laufen durch den Relay; das MP4 geht per signiertem PUT
   direkt vom Java-Client zu R2. Kein großer Proxy-Upload und kein Browser-CORS nötig.
5. R2-Lifecycle-Regeln als zusätzliche Ausfallsicherung anlegen:
   `clips/staging/` nach 1 Tag löschen; `clips/ready/` und `clips/posters/` nach 7 Tagen.
   Bei abweichender `CLIP_TTL_SECONDS` die beiden letzteren Regeln entsprechend anpassen
   (auf ganze Tage aufrunden, niemals kürzer als die konfigurierte Freigabe).
   Zusätzlich ein Kosten-/Speicherbudget im Cloudflare-Konto kontrollieren.
6. Server neu starten und **erst dann** mit einem harmlosen eigenen Testclip testen:
   Mod-Upload bestätigen → Fortschritt → Link `/c/<id>` → abspielen, vor-/zurückspulen,
   herunterladen → im angemeldeten Userpanel filtern und löschen → Link nicht verfügbar.
   Auch Abbruch, abgelaufene Sitzung, WLAN-Unterbrechung und Limitüberschreitung testen.
   Mit einem zweiten Konto prüfen, dass dessen Galerie/Löschrechte getrennt sind.

## Grenzen und Datenschutz

- Kein automatischer Upload: Klick auf `[Hochladen …]`, `/buclip upload` oder den
  Clips-Menübutton öffnet erst die Bestätigung mit Ton-/Stimmenhinweis.
- Private Galerie unter `/panel`, gemeinsame Ansicht für Screenshots und Clips.
  Ein einzelner zufälliger Share-Link ist **nicht passwortgeschützt**: jeder mit dem
  Link kann das Medium abrufen und weitergeben. Keine öffentliche Galerie/Indexierung.
- MP4: H.264, optional AAC, maximal 1080p und 300 Sekunden (2 Sekunden Mux-Toleranz).
  Die Serverprüfung prüft MP4-Struktur/Codec-Metadaten, **nicht jeden Frame**, und ersetzt
  keinen Malware-Scanner oder Moderationsprozess. Keine Server-Transkodierung.
- Standard: 1 GiB pro Clip; 5 reservierte Uploads und 3 GiB pro Account innerhalb
  der letzten 24 Stunden; 100 GiB global reservierte logische Clipgröße.
  Abgebrochene/fehlgeschlagene Versuche zählen zur Tagesquote, um Missbrauch zu begrenzen.
  Staging plus fertige Kopie können vorübergehend doppelt Speicher belegen; die globale
  Quote ist daher kein exaktes Cloudflare-Kostenlimit. Vorschaubilder/Requests kommen hinzu.
- Die Reservierung beginnt eine einstündige Upload-Erlaubnis und standardmäßig 7 Tage
  Freigabe. Signed PUT bindet Größe, Content-Type, MD5 und `If-None-Match: *`.
  Erst nach Prüfung wird eine getrennte, unveränderbare Auslieferungskopie freigegeben.
  MD5 dient hier nur Übertragungsintegrität, nicht Passwortschutz/Authentifizierung.
- Standardbereinigung jede Minute, nur ein Lauf gleichzeitig. Fehlgeschlagene Löschungen
  werden erneut versucht. Staging bleibt bis 5 Minuten nach Grant-Ablauf stehen, damit
  eine noch gültige signierte PUT-URL kein bereits freigegebenes Video verändern kann.
- Löschen sperrt den betterUC-Link sofort. Bereits ausgegebene R2-GET-URLs sind bis zu
  30 Minuten gültig; solange R2-Löschen nicht erfolgreich war, können sie noch funktionieren.
  Bereits heruntergeladene Kopien können nicht zurückgerufen werden. Bei Storageausfällen
  kann die physische Löschung verzögert erfolgen; Lifecycle ist die zweite Sicherung.
- Upload-Metadaten (Account, Dateiname, Größe, Dauer, Ablauf, Status) liegen in PostgreSQL;
  MP4 und Vorschaubild in R2. Keine Zugangsdaten werden an R2 weitergereicht: nur signierte
  objektspezifische Anfragen. Die lokalen Originaldateien werden nie gelöscht.
- Vor Live-Freigabe Cloudflare-Vertrag, gewünschte Speicherregion/Jurisdiktion und die
  Datenschutzhinweise durch den Betreiber prüfen. Technische Texte sind keine Rechtsprüfung.

## Lokale Prüfung

`npm ci` und `npm run test:clips` im `server`-Verzeichnis: isoliertes PGlite/PostgreSQL,
synthetische Daten, Fake-Object-Store, keine produktiven Credentials und keine R2-Requests.
Der echte synthetische MP4-Test nutzt optional die zuvor von Gradle erzeugte Testdatei.
PGlite testet SQL/Transaktionen, ersetzt aber keinen Last-/Mehrprozess-Test auf Live-PostgreSQL.

Quellen: [R2 Signed URLs](https://developers.cloudflare.com/r2/api/s3/presigned-urls/),
[S3-Kompatibilität](https://developers.cloudflare.com/r2/api/s3/api/),
[Lifecycle](https://developers.cloudflare.com/r2/buckets/object-lifecycles/).
