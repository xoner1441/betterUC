# betterUC Clips – lokaler Prototyp (1.4.3)

## Beta 12: freiwilliges Teilen

- Nach dem Speichern `[Hochladen …]` anklicken oder unter Clips `Letzten Clip hochladen …`
  verwenden (`/buclip upload`). Nach einem Neustart wird der neueste `clip_*.mp4` im
  aktuellen Clip-Ordner gesucht. Alte Chat-Aktionen bleiben für die letzten 30 gespeicherten
  Clips dieser Spielsitzung verfügbar. Eine frei durchsuchbare lokale Clip-Bibliothek ist
  nicht Teil dieser Beta.
- Erst `Jetzt hochladen` startet den Upload. Vorher lokal ansehen und Ton/Stimmen/Chat prüfen.
  Vorbereitung, Übertragung und Freigabe laufen im Hintergrund; Fortschritt wird angezeigt.
  `Abbrechen`/Escape beendet den Upload, nicht die lokale Datei.
- Nach Erfolg Link kopieren oder Galerie öffnen. Die private Website-Galerie enthält Screenshots
  und Clips mit Filtern, Vorschau, Download, Ablauf und Löschen. Jeder mit dem Share-Link kann
  das einzelne Medium ansehen. Keine automatische Freigabe.
- Noch **nicht live konfiguriert**: Es fehlt der private R2-Bucket samt serverseitigen Schlüsseln.
  Bis dahin zeigt ein Upload einen verständlichen Einrichtungshinweis. Technische Einrichtung,
  Quoten und verbleibende Datenschutz-/Betriebsprüfungen: `server/CLIP-UPLOADS.md`.
- Lokal geprüft: H.264-Export → PNG-Vorschau + MD5, Progress-Streaming, Abbruch,
  PostgreSQL-Quoten/Besitzerrechte, MP4-Prüfung, Race beim Löschen, Lifecycle-Bereinigung
  sowie Browser-Galerie/Player auf Desktop und Mobile mit synthetischen Daten.
  Echter R2-PUT inklusive langer/unterbrochener Uploads und Ingame-Dialog noch separat testen.

Noch keine öffentliche Release-Funktion. Keine Änderung an Website, Bot oder Relay.
Aufnahme, Ton und Mikrofon sind standardmäßig aus und werden nicht über Cloud Sync aktiviert.
Bestehende Spielton-Einstellungen bleiben spielbezogen; kein automatischer Wechsel auf Systemton/Mikrofon.

## Umfang

- Windows x64; ab Beta 11 enthält die mitgelieferte FFmpeg-8.0-Bibliothek NVENC
  (NVIDIA), AMF (AMD) und QSV (Intel). Alle werden bis zum ersten erfolgreichen
  Probe-Encoding geprüft; ein NVIDIA-Fehler verhindert keinen AMD-Versuch.
  Kein CPU-Encoder als Fallback. Die Hardware benötigt einen passenden installierten
  Grafiktreiber. Kein separates Aufnahmeprogramm oder Laufzeit-Download.
  Ein kompletter Fehlschlag nennt den Status aller drei Hersteller; die technischen
  Einzelursachen und Runtime-Version stehen im Log. Radeon-Praxistest bleibt nötig.
- Auflösung separat wählbar: maximal 1280 × 720 oder 1920 × 1080; Bildrate separat
  30 oder 60 FPS als Ziel. Standard bleibt 1080p/60 FPS, auch für bestehende Configs.
  Seitenverhältnis beibehalten, kein Hochskalieren. Die Einstellungen bleiben lokal.
- Lokale H.264-MP4-Dateien; Zielbitrate passend zum Preset: 720p/30 = 8 Mbit/s,
  720p/60 = 12 Mbit/s, 1080p/30 = 16 Mbit/s, 1080p/60 = 25 Mbit/s.
  Optionaler Ton als AAC-LC
  (48 kHz, Stereo, 192 kbit/s).
- Tonquelle: Aus / Nur Minecraft / Gesamter Ausgabeton. Spielton erfasst nur den
  Minecraft-Prozessbaum inklusive Ingame-Voicechat. Systemton erfasst alle Apps
  auf dem gewählten Windows-Ausgabegerät (z. B. Headset), inklusive TeamSpeak,
  Discord, Browser und Benachrichtigungen. Kein zusätzliches Prozess-Loopback in
  diesem Modus: Minecraft wird nicht doppelt gemischt. Andere Ausgabegeräte sind
  nicht erfasst; Spiel und TeamSpeak müssen dasselbe Gerät verwenden.
- Mikrofon unabhängig optional, auch ohne Ausgabe-/Spielton. Separate Geräteauswahl
  für Ein- und Ausgabe; zunächst „Audiogeräte laden / aktualisieren“, anschließend
  per Gerätebutton durchschalten. Stabile Geräte-IDs bleiben ausschließlich lokal.
  „Windows-Standard“ wird bei Aufnahmestart aufgelöst und während dieser Sitzung
  beibehalten. Nach Gerätewechsel/Neuverbinden „Tonaufnahme / Puffer neu starten“.
  Bei explizit fehlendem Gerät kein automatischer Fallback auf ein anderes Gerät.
- Systemton und Mikrofon verlangen beim Einschalten eine Bestätigung. Das Mikrofon
  erfasst Stimme und Umgebung unabhängig von TeamSpeak-/Discord-Mute/Push-to-Talk.
  Gesprächspartner vorher informieren und ihr Einverständnis einholen. Headset
  empfohlen: keine Echounterdrückung/Spracherkennung, kein eigenes Audio-Monitoring;
  Windows-„Dieses Gerät als Wiedergabequelle verwenden“ ausschalten, um Doppelton
  zu vermeiden. Lautsprecher können vom Mikrofon erneut aufgenommen werden.
- Separate Lautstärken 0–100 % für Spiel-/Ausgabeton und Mikrofon. Sie werden beim
  Export auf den gesamten ausgewählten Zeitraum angewendet (kein Neustart beim
  Schieben), als eine AAC-Stereo-Spur gemischt, laute Summen werden begrenzt.
  Bei hörbarer Übersteuerung Pegel senken. Keine getrennten editierbaren Tonspuren.
- Mikrofon ab Beta 9: kontinuierlicher Sample-Takt statt Einzelplatzierung nach
  möglicherweise schwankenden Windows-Paketzeitstempeln. Der erste Zeitstempel
  verbindet die Aufnahme mit der Videozeit; echte Paketverluste bleiben als Lücke
  erhalten. Dieser Mikrofon-Takt bleibt in Beta 10 unverändert.
  `/buclip` und beim Aufnahmeende das Log zeigen Geräteformat und reine Zähler für
  ungültige/geglättete Zeitstempel bzw. fehlende Samples (keine Sprachdaten).
- Spiel-/Ausgabeton ab Beta 10: ebenfalls kontinuierlicher Sample-Takt während
  aktiver Wiedergabe, statt jedes Paket anhand eines schwankenden/ungültigen
  Windows-Zeitstempels neu zu platzieren. Gilt für „Nur Minecraft“ und „Gesamter
  Ausgabeton“. Kein Wechsel des Geräts, keine Änderung der Audiofreigabe.
  Tatsächliche Wiedergabepausen ohne Loopback-Pakete werden separat erkannt und
  bleiben zeitlich erhalten. Bei ungültiger erster Zeitangabe wird nach Möglichkeit
  die Windows-Warteschlangenlänge berücksichtigt, statt einen ganzen Rückstau
  auf dieselbe Ankunftszeit zu legen. `/buclip`/Log enthalten jetzt auch Ausgabe-
  und Spielton-Zähler sowie Anzahl fortgesetzter Tonabschnitte.
- Spielton benötigt Windows Build 20348 oder neuer (üblicherweise Windows 11).
  Bei fehlender Unterstützung oder Audiofehlern bleibt das Video verfügbar;
  ein Popup weist darauf hin, `/buclip` nennt die Ursache. Die jeweils andere
  Tonquelle und Video laufen weiter. Es gibt keinen Mikrofon-/Systemton-Fallback.
- Freie Cliplänge von 5 bis 300 ganzen Sekunden, Standard 30. Zusätzliche Vorgaben:
  15/30/60/90/120/180/300 Sekunden. Schnitt am Keyframe;
  die tatsächliche Länge kann um eine Keyframe-Gruppe abweichen.
  Der komprimierte Video-Puffer ist auf höchstens 1 GiB und höchstens ein Achtel
  des maximalen Java-Heaps begrenzt. Reicht das bei der eingestellten Bitrate nicht
  für die gewünschte Länge, wird gekürzt und einmal pro Aufnahme-Sitzung gewarnt.
  Das Menü zeigt das Pufferlimit, `/buclip` zeigt zusätzlich das erreichte Limit.
- Minecraft-Spielbild inklusive HUD, Chat und geöffneten Ingame-Menüs. Keine
  Desktop-Videoaufnahme, kein automatischer Upload, keine automatische Screenshot-Freigabe.
- Die benötigten DLLs sind in der JAR enthalten und werden lokal zum Laden entpackt.
  Keine ffmpeg.exe/ffprobe.exe, keine zusätzlichen Installationen oder Laufzeit-Downloads.
- Bei Fokusverlust, Weltwechsel, Änderung der Aufnahmeauflösung/Bildrate, Wechsel der Tonquellen/Geräte, Ausschalten oder
  Änderung der Pufferlänge wird der bisherige Replay-Puffer verworfen und neu gestartet.

## Im Spiel testen

1. Test-JAR bei geschlossenem Minecraft einsetzen (keine zweite betterUC-JAR daneben).
2. ClickGUI → Client → Clips (Beta) → Clip-Aufnahme AN.
   Für Ton „Tonquelle“ wählen, Mikrofon bei Bedarf separat einschalten und die
   Datenschutz-Hinweise bestätigen. Der Replay-Puffer startet dabei neu.
   Mit `/buclip` auf aktive Tonquellen und Fehler prüfen.
3. Minecraft-Steuerung → betterUC → „Clip speichern (letzte Sekunden)“ eine freie
   Taste zuweisen. Standardmäßig ist keine Taste belegt.
4. 30 Sekunden spielen und dann Hotkey oder `/buclip save` benutzen.
5. Ohne eigene Auswahl bleiben Dateien im bisherigen Instanzordner `betteruc-clips`.
   Unter Client → Clips (Beta) → Speicherort → „Speicherort wählen …“ den
   übergeordneten Ordner auswählen: z. B. `D:\Videos` ergibt `D:\Videos\buclips`.
   Die Windows-Ordnerauswahl nutzt das bereits mit Minecraft gelieferte TinyFD;
   keine zusätzliche Bibliothek oder Installation. Der Pfad bleibt ausschließlich
   in der lokalen Config und wird nicht per Cloud Sync übertragen.
   „Standardspeicherort verwenden“ kehrt zu `betteruc-clips` zurück. Bereits
   gespeicherte Dateien werden weder verschoben noch gelöscht. `/buclip folder`
   öffnet das aktuell eingestellte Ziel.
   `/buclip` zeigt Encoder, tatsächliche Auflösung, Ziel-FPS/-Bitrate, Durchsatz,
   Puffergröße und ausgelassene Bilder.
6. MP4 auf richtige Farben/Ausrichtung, flüssige Bewegung und Dauer prüfen.
   Einen eindeutig sichtbaren/hörbaren Vorgang aufnehmen (z. B. Schuss, Tür oder
   Inventarklick), auf Synchronität hören und in Minecraft stummgeschaltete
   Kategorien prüfen. Bei „Nur Minecraft“ und Mikrofon AUS mit separat laufendem
   Discord/Browser sicherstellen, dass deren Ton fehlt und kein Mikrofon geöffnet wird.
7. FPS/Frametimes in derselben Szene mit Aufnahme AUS und AN vergleichen, auch
   mit NRC, Shadern, offenem Chat, Vollbildwechsel und mehreren Clip-Speicherungen.
8. Fokusverlust, Disconnect/Reconnect, Größenwechsel, AUS/AN und kurze/lange Cliplängen
   testen. Der Puffer muss zurückgesetzt werden; Speicher darf nicht stetig wachsen.
9. Ohne unterstützten Encoder muss ein verständlicher Fehler erscheinen und das
   Spiel weiterlaufen. Nach Treiberproblemen über AUS/AN neu versuchen.
10. Himmel, Partikel und Straßenende/Nebel mit dem Spielbild vergleichen. Die
    Aufnahme kopiert jetzt RGB ohne Alpha-Blending; schwarze Flecken durch die
    vorherige Transparenz-Verrechnung dürfen in neuen Clips nicht mehr auftreten.
11. Ton AN/AUS, Audio-Ausgabegerät wechseln und schnelle Aufnahme-Neustarts
    testen. Bei Audiofehlern `/buclip` aufrufen; nach behobenem Problem den
    Puffer neu starten. Ohne Ton muss weiterhin speicherbar sein.
12. Unter „Aufnahmequalität“ alle vier Kombinationen aus 720p/1080p und 30/60 FPS
    speichern. Der Wechsel leert den Replay-Puffer, verändert aber keine bereits
    gespeicherten Clips. Auch während eines laufenden Exports umschalten: dessen
    Datei muss im ursprünglichen Format fertig werden. Ton-Synchronität bei beiden
    Bildraten prüfen; Einstellungen nach einem Spielneustart kontrollieren.
13. Neue Clip-Popups: Beim Aufnahmestart erscheint rechts eine türkisfarbene Karte
    für vier sichtbare Sekunden. Kein dauerhafter Text oben mittig. Fokuswechsel
    allein erzeugen keinen neuen Start-Hinweis. Beim Export bleibt „Clip wird
    gespeichert …“ sichtbar und wird durch die grüne Bestätigung mit tatsächlicher
    Länge, Auflösung, Bildrate und Tonstatus ersetzt. Bei Fehlern erscheint ein
    roter Hinweis (sechs sichtbare Sekunden), bei fehlendem Ton ein gelber.
    Keine zusätzlichen Hinweistöne oder automatischen Chatmeldungen; `/buclip`
    zeigt Details auf Anfrage und der Dateipfad steht im Log.
14. Gleichzeitige Reichensteuer-Warnung und Clip-Speicherung prüfen: Clip-Karten
    stehen mit Abstand darunter. Während einer Speicherung Aufnahme ausschalten
    oder Qualität ändern: Export und Ergebnis-Popup müssen erhalten bleiben.
    Menü/F1/Alt-Tab dürfen die Anzeigedauer der versteckten Karten nicht aufbrauchen.
15. Speicherort mit Leerzeichen/Umlauten wählen, speichern, Ordner öffnen und nach
    Spielneustart prüfen. Abbrechen ändert nichts. Die Auswahl prüft Schreibrechte
    mit einer kurzlebigen eigenen Testdatei; vorhandene Dateien bleiben unberührt.
    Ein nicht verfügbares Ziel oder eine Datei namens `buclips` muss als Fehler
    gemeldet werden, ohne auf einen anderen Ort auszuweichen. Laufende Exporte
    behalten ihr Ziel, neue Speicheranfragen benutzen den neuen Ort. Das Öffnen
    des externen Auswahldialogs kann wie normales Alt-Tab den Replay-Puffer leeren.
16. Cliplänge: Vorgaben bis 300 Sekunden durchschalten und z. B. 37 oder 137 Sekunden
    direkt eingeben. Erst „Cliplänge übernehmen“ aktiviert den Wert und startet
    den Replay-Puffer neu. Leere Eingabe, Text, Dezimalzahlen und Werte außerhalb
    5–300 müssen den Übernehmen-Button deaktivieren. Nach Neustart muss der eigene
    Wert erhalten bleiben. Nach ausreichend Aufnahmezeit lange Clips auf Dauer
    und Ton-Synchronität am Anfang und Ende prüfen. Bei kleinerem Java-Heap und
    hoher Bitrate kann der RAM-Schutz vor Erreichen der Ziellänge greifen.
17. Neue Audiofunktionen: Bestätigungsdialog jeweils abbrechen – Einstellung darf
    sich nicht ändern. Anschließend Ausgabe + Mikrofon bewusst einschalten,
    Headset/Mikrofon wählen, mit Zustimmung aller Beteiligten TeamSpeak-Stimmen
    und eigene Stimme testen. Keine doppelte Spieltonspur, Ton/Bild synchron.
    Auch Mikrofon allein, Spielton + Mikrofon und Systemton ohne Mikrofon testen.
18. Mit einer harmlosen Testphrase prüfen, dass TS-Mute/PTT nicht unser Mikrofon
    stummschaltet; der Menühinweis muss dies klar erklären. In der Mod Mikrofon
    ausschalten: der Replay-Puffer wird geleert, neue Clips enthalten keine Stimme.
    Beide Lautstärken prüfen, Export während Pegelwechsel behält seine alten Werte.
19. Mikrofon abziehen/Berechtigung verweigern: gelber Hinweis, Ausgabe/Video laufen
    weiter. Kein anderer Eingang wird ungefragt benutzt. Umgekehrt Ausgangsausfall
    prüfen. Neue Auswahl/Neuverbinden + Pufferneustart testen, auch schnelle Wechsel.
    Bei Alt-Tab, Welt verlassen und Aufnahme AUS enden beide Aufnahmen; kein
    Hintergrundmitschnitt. Die Windows-Mikrofonanzeige prüfen.
20. Mikrofon-Robotereffekt: zuerst Tonquelle AUS + Mikrofon AN testen, danach
    Ausgabeton + Mikrofon. Headset verwenden, Mikrofon-Mithören deaktivieren.
    Bei weiterem Effekt einen kurzen Testclip und `/buclip`-Diagnose prüfen.
    Der Zeitstempel-Fix ist synthetisch reproduziert/getestet; der gemischte
    Nutzerclip allein beweist nicht, ob Treiberjitter oder eine zweite verzögerte
    Kopie der Stimme die konkrete Ursache ist. Keine automatische Echoentfernung.
21. Ausgabeton-Robotereffekt: betroffenen Tester mit Beta 10 erneut aufnehmen
    lassen, zunächst Mikrofon AUS. „Nur Minecraft“ und „Gesamter Ausgabeton“ testen,
    dann Mikrofon wieder dazunehmen. Zwischendurch einige Sekunden keinen Ton
    abspielen und danach einen hör-/sichtbaren Vorgang auslösen: keine abgeschnittene
    Pause oder A/V-Verschiebung. Bei weiterem Effekt Clip plus `/buclip`-Diagnose
    anfordern. Der eingereichte gemischte MP4-Clip allein beweist keinen konkreten
    Treiberfehler; die Einzelplatzierungs-Schwachstelle ist separat reproduziert.

Den Pufferstand zeigen das Clips-Menü und `/buclip`. Popups erscheinen nur bei
Ereignissen, nicht dauerhaft während der Aufnahme. Bei Last werden Frames ausgelassen, mit
korrekter zeitlicher Dauer gespeichert, nicht künstlich als echte 30/60 FPS ausgegeben.
Der Aufnahmeweg braucht zusätzliche GPU-Kopie, CPU-Farbkonvertierung und Upload
zum Hardware-Encoder. Er ist **noch nicht Zero-Copy**. Ein schneller isolierter
Encoder-Test sagt daher nichts Verlässliches über den FPS-Verlust im Spiel aus.

Speichergrenzen: drei rohe 1080p-Pufferpaare (CPU/GPU, zusammen ca. 50 MB),
komprimierter Ring maximal 1 GiB beziehungsweise ein Achtel des Java-Maximalheaps,
bei kurzen/niedrig aufgelösten Clips ein kleineres Bitratenbudget; zusätzlich
Encoder-Speicher. Ein laufender
Export hält seine Paketliste fest; bis zu ein weiterer Ring kann parallel entstehen.
Optionaler PCM-Tonpuffer: maximal ca. 58,6 MB pro Quelle für 300 Sekunden plus fünf Sekunden
Reserve, mit Ausgabe + Mikrofon bis ca. 117,2 MB. Beim Export bleiben die ausgewählten PCM-Blöcke erhalten, während sich
der laufende Puffer erneuert. AAC liest sie blockweise (normalerweise 4 KiB pro
Lesepuffer, bis zu drei beim Mischen) ohne weitere vollständige PCM-Kopie; hinzu kommen die AAC-Pakete.
Die AAC-Kodierung läuft
nur beim Speichern auf dem Export-Thread, nicht auf dem Render-Thread.
Gespeicherte Clips bleiben auf der Festplatte, bis der Nutzer sie löscht.

## Entwicklerprüfungen

Normal: `gradlew.bat test -PcopyToMinecraftMods=false`

Mit echtem Hardware-Encoder und verstecktem GPU-Test, ausschließlich künstliche Pixel/Tonsamples:
`gradlew.bat test -PclipHardwareTest -PcopyToMinecraftMods=false`

Der Hardwaretest erzeugt `build/clip-test/synthetic-{720,1080}p{30,60}.mp4` und
je einen Keyframe-basierten Ausschnitt. Er prüft alle vier Presets auf H.264,
Auflösung, Bildrate, Orientierung, Zahl der decodierten Frames und Zeitstempel
einschließlich ausgelassener Frames.
Der GPU-Test verwendet dieselben Shader und den Farbschreibzustand der Aufnahme:
Er reproduziert die schwarzen/dunklen Pixel mit dem alten Alpha-Blending und
prüft die korrigierte RGB-Kopie bei Alpha 0/64/128/255 inklusive Verkleinerung.

Zusätzlich echte Windows-Prozess-Audiotests (spielen kurze leise Testtöne):
`gradlew.bat test -PclipHardwareTest -PclipAudioHardwareTest -PcopyToMinecraftMods=false`

`synthetic-av-sync-{30,60}fps-{3,300}s.mp4` prüfen AAC-Stereo,
gleiche Startzeiten/Dauer und die Position eines Tons nach einem Keyframe-Schnitt
bei ausgelassenen Videobildern. Die langen Fälle prüfen zusätzlich einen Tonmarker
am Ende der fünfminütigen Aufnahme; der Keyframe-Schnitt macht die exportierte
Datei geringfügig kürzer als 300 Sekunden. Unit-Tests prüfen die vollen fünf
Minuten im Video-/Audiopuffer, eigene Längen und Begrenzung bei RAM-Druck. Der Windows-
Test erfasst nur den Ton des Test-Java-Prozesses und prüft dessen Zeitstempel.
Ein weiterer nativer Test prüft zwei Tonimpulse mit geschlossener Wiedergabequelle
und 900 ms Pause dazwischen. Kein Mikrofon-/Systemton-Mitschnitt in diesen Tests.
Das ersetzt nicht den abschließenden A/V-Synchronitätstest in Minecraft/NRC.
Zusätzlich `synthetic-av-sync-60fps-{3,300}s-mixed.mp4`: zwei unabhängige synthetische
Quellen mit versetzten Paketen/Markern werden gemischt und nach AAC/MP4-Decodierung
auf Zeitlage geprüft. Keine echten Gespräche/Mikrofondaten im automatischen Test.
Unit-Tests prüfen Quellenauswahl (kein Doppelton), Clamping/Pegel, Zeitlücken,
Abschalten während Geräteaktivierung, unabhängige Quellenausfälle und lokale
Consent-/Geräteeinstellungen. Hardwaretests lesen die Geräteliste und initialisieren
das Standardgeräte-Format, ohne eine Aufnahme zu starten oder Mikrofonpakete zu lesen.
Die Mikrofon-Takttests reproduzieren zerstückelten Ton mit dem alten Verfahren,
prüfen bytegenau lückenlose PCM-Ausgabe mit schwankenden/ungültigen Zeitstempeln,
gebündelte Paketzustellung, echte Verluste, Zählerrücksetzung und fünf Minuten
ohne Rundungslücken. Die Loopback-Takttests ergänzen Stereo-Paketjitter, ungültige
Zeitstempel bei Rückstau, Wiedergabepausen mit stehendem Gerätezähler, lange
Geräterücksetzungen und verzögerte Worker-Ausführung ohne falsche Stille.
Alle AAC/MP4-Synchronitätstests verwenden nun auch Loopback-Paketjitter und
ausgelassene stille Pakete; gemischte Tests zusätzlich den stabilisierten Mikrofontakt.

Test-JAR ohne Installation:
`gradlew.bat build "-PmodArchiveClassifier=mc26.x-clips-beta11" -PcopyToMinecraftMods=false`

### Beta 11: AMD-/Runtime-Test

- Radeon RX 7600: Aufnahme aktivieren, `/buclip` prüfen. Im Log muss
  `Hardware-Encoder bereit: h264_amf (AMD)` stehen. Ein vorheriger NVIDIA-Fehler
  im Log ist bei einem AMD-PC normal und darf die Aufnahme nicht stoppen.
- Zuerst 1080p/60 FPS mit 30 Sekunden prüfen: flüssiges Bild, richtige Farben,
  keine schwarzen Elemente, Ton synchron. Anschließend längeren Clip und
  Spiel-/Systemton mit Mikrofon wie in Beta 10 prüfen. Audio-Taktung unverändert.
- Auch NVIDIA und nach Möglichkeit Intel testen, da die gemeinsamen nativen
  FFmpeg-Bibliotheken ausgetauscht wurden. AMD ist lokal ohne Radeon nur auf
  Encoder-Vorhandensein und gültige Optionen prüfbar, nicht auf echte Aufnahme.
- Native Tests verlangen alle drei Encoder, die festgelegte LGPL-Runtime,
  passende ABI und genau zehn DLLs (fünf Bibliotheken + fünf JNI-Brücken).
  Auswahltests simulieren einen NVIDIA-Fehler mit anschließend erfolgreichem AMD,
  Intel-Fallback und vollständige Fehler. Keine CPU-Ausweichaufnahme.
- Die Build-Abhängigkeit wird über URL + SHA-256 festgelegt und jedes Mal geprüft.
  `--offline` ist nach dem ersten Download möglich. Keine veränderliche latest-URL.

Vor einer öffentlichen Verteilung stehen Hardware-/Ingame-Tests und ein vollständiger
Redistributionscheck der nativen Abhängigkeiten an (siehe CLIPS-THIRD-PARTY.md).
