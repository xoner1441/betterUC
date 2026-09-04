"use strict";
(async () => {
  const status = document.querySelector('#clipStatus');
  const id = location.pathname.match(/^\/c\/([a-zA-Z0-9_-]{32})$/)?.[1];
  try {
    if (!id) throw new Error('Ungültiger Clip-Link.');
    const response = await fetch(`/api/clips/${id}`, { cache:'no-store' });
    const data = await response.json();
    if (!response.ok || !data.ok) throw new Error(data.error || 'Clip ist nicht verfügbar.');
    document.querySelector('#clipTitle').textContent = data.originalName;
    document.title = `${data.originalName} · betterUC`;
    const player = document.querySelector('#clipPlayer');
    player.poster = `/api/clips/${id}/poster`;
    player.src = `/api/clips/${id}/play`;
    player.addEventListener('error', () => { status.hidden=false; status.textContent='Video konnte nicht geladen werden. Lade die Seite erneut oder versuche den Download.'; });
    document.querySelector('#clipMeta').textContent = `${Math.round(data.durationSeconds)} Sekunden · ${(data.byteSize/1048576).toFixed(1)} MB · Verfügbar bis ${new Date(data.expiresAt).toLocaleString('de-DE')}`;
    document.querySelector('#clipDownload').href = `/api/clips/${id}/download`;
    document.querySelector('#clipContent').hidden = false;
    status.hidden = true;
    document.querySelector('#clipCopy').addEventListener('click', async () => {
      const notice = document.querySelector('#clipCopyStatus');
      try { await navigator.clipboard.writeText(`${location.origin}/c/${id}`); notice.textContent='Link kopiert.'; }
      catch { notice.textContent='Bitte kopiere den Link aus der Adressleiste.'; }
    });
  } catch (error) { status.textContent=error.message; }
})();
