const statusLabel = document.querySelector("#relayStatus");
const onlineInfo = document.querySelector("#onlineInfo");
const form = document.querySelector("#accessForm");
const message = document.querySelector("#accessMessage");
const tokenBox = document.querySelector("#tokenBox");
const generatedToken = document.querySelector("#generatedToken");
const copyToken = document.querySelector("#copyToken");
const panelLoginForm = document.querySelector("#panelLoginForm");
const panelDashboard = document.querySelector("#panelDashboard");
const panelMessage = document.querySelector("#panelMessage");
const panelUsername = document.querySelector("#panelUsername");
const panelRoleBadge = document.querySelector("#panelRoleBadge");
const panelStatusBadge = document.querySelector("#panelStatusBadge");
const panelStats = document.querySelector("#panelStats");
const panelAccountMeta = document.querySelector("#panelAccountMeta");
const panelHistory = document.querySelector("#panelHistory");
const panelUpdated = document.querySelector("#panelUpdated");
const panelRefresh = document.querySelector("#panelRefresh");
const panelAdmin = document.querySelector("#panelAdmin");
const panelLogout = document.querySelector("#panelLogout");
const downloadVersion = document.querySelector("#downloadVersion");
const downloadFile = document.querySelector("#downloadFile");
const downloadSize = document.querySelector("#downloadSize");
const downloadHint = document.querySelector("#downloadHint");
const latestDownloadButton = document.querySelector("#latestDownloadButton");
const latestDownloadButtonPanel = document.querySelector("#latestDownloadButtonPanel");
const PANEL_SESSION_KEY = "betteruc-panel-session";
const DOWNLOAD_VERSIONS = [
  {
    label: "26.2",
    minecraft: "26.2",
    target: "mc26.x",
    note: "Aktuelle UnicaCity-Version"
  },
  {
    label: "26.1.2",
    minecraft: "26.1.2",
    target: "mc26.x",
    note: "Fabric 26.x"
  },
  {
    label: "1.21.10",
    minecraft: "1.21.10",
    target: "mc1.21.10",
    note: "Legacy-Version"
  }
];
let selectedDownloadVersion = null;
let downloadModal = null;
let downloadReleaseCache = new Map();

async function refreshStatus() {
  try {
    const response = await fetch("/api/status", { cache: "no-store" });
    const data = await response.json();
    if (!data.ok) throw new Error("Status nicht erreichbar");
    if (statusLabel) statusLabel.textContent = `${data.relay.online} online | ${data.accounts} Accounts`;
    if (onlineInfo) onlineInfo.textContent = `Relay online: ${data.relay.online} verbunden`;
  } catch {
    if (statusLabel) statusLabel.textContent = "Relay wird vorbereitet";
    if (onlineInfo) onlineInfo.textContent = "Relay Status nicht erreichbar";
  }
}

function formatBytes(value) {
  const bytes = Number(value || 0);
  if (!Number.isFinite(bytes) || bytes <= 0) return "unbekannt";
  const units = ["B", "KB", "MB", "GB"];
  let size = bytes;
  let unit = 0;
  while (size >= 1024 && unit < units.length - 1) {
    size /= 1024;
    unit += 1;
  }
  return `${size.toLocaleString("de-DE", { maximumFractionDigits: unit === 0 ? 0 : 1 })} ${units[unit]}`;
}

async function refreshDownloadInfo() {
  if (!downloadVersion && !downloadFile && !downloadSize) return;
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), 6000);
  try {
    const response = await fetch("/api/releases/latest", {
      cache: "no-store",
      signal: controller.signal
    });
    const data = await response.json();
    if (!response.ok || !data.ok) throw new Error(data.error || "Release nicht erreichbar");

    const downloadUrl = data.downloadUrl || "/download/latest.jar";
    if (downloadVersion) downloadVersion.textContent = data.version ? `v${data.version}` : "aktuell";
    if (downloadFile) downloadFile.textContent = data.assetName || "betterUC.jar";
    if (downloadSize) downloadSize.textContent = formatBytes(data.assetSize);
    if (downloadHint) downloadHint.textContent = "Download bereit über betteruc.de.";
    if (latestDownloadButton) latestDownloadButton.href = downloadUrl;
    if (latestDownloadButtonPanel) latestDownloadButtonPanel.href = downloadUrl;
  } catch (error) {
    if (downloadVersion) downloadVersion.textContent = "aktuelle Version";
    if (downloadFile) downloadFile.textContent = "betterUC.jar";
    if (downloadSize) downloadSize.textContent = "beim Download";
    if (downloadHint) downloadHint.textContent = "Versionsdetails konnten gerade nicht geladen werden. Der Download-Link bleibt nutzbar.";
    if (latestDownloadButton) latestDownloadButton.href = "/download/latest.jar";
    if (latestDownloadButtonPanel) latestDownloadButtonPanel.href = "/download/latest.jar";
  } finally {
    clearTimeout(timeout);
  }
}

function initDownloadModal() {
  const downloadTriggers = [...document.querySelectorAll('a[href^="/download/latest.jar"]')];
  if (downloadTriggers.length === 0) return;
  ensureDownloadModal();
  downloadTriggers.forEach(trigger => {
    if (trigger.dataset.directDownload === "true") return;
    trigger.addEventListener("click", event => {
      event.preventDefault();
      openDownloadModal();
    });
  });
}

function ensureDownloadModal() {
  if (downloadModal) return downloadModal;
  const wrapper = document.createElement("div");
  wrapper.className = "download-modal";
  wrapper.hidden = true;
  wrapper.innerHTML = `
    <div class="download-modal-backdrop" data-download-close></div>
    <section class="download-dialog" role="dialog" aria-modal="true" aria-labelledby="downloadModalTitle">
      <header class="download-dialog-head">
        <span class="download-dialog-icon">bU</span>
        <h2 id="downloadModalTitle">betterUC herunterladen</h2>
        <button class="download-close" type="button" aria-label="Download-Fenster schließen" data-download-close>×</button>
      </header>
      <div class="download-dialog-body">
        <div class="download-divider"><span>Version wählen</span></div>
        <div class="download-select-block">
          <button class="download-select" type="button" id="downloadVersionToggle" aria-expanded="false">
            <span class="download-select-label">Select game version</span>
            <span class="download-chevron">⌄</span>
          </button>
          <div class="download-version-menu" id="downloadVersionMenu" hidden>
            <label class="download-search-label">
              <span>Version suchen</span>
              <input id="downloadVersionSearch" type="search" placeholder="Search game versions..." autocomplete="off">
            </label>
            <div class="download-version-list" id="downloadVersionList"></div>
          </div>
        </div>
        <div class="download-platform locked" aria-label="Platform Fabric">
          <span>Platform: Fabric</span>
          <small>locked</small>
        </div>
        <div class="download-result" id="downloadResult" hidden>
          <div class="download-result-mark">bU</div>
          <div>
            <strong id="downloadResultTitle">betterUC</strong>
            <span id="downloadResultMeta">Fabric</span>
          </div>
          <a class="button primary" id="downloadResultButton" href="/download/latest.jar" data-direct-download="true">Download</a>
        </div>
        <p class="download-modal-hint" id="downloadModalHint">Wähle deine Minecraft-Version aus. Danach erscheint der passende Download.</p>
      </div>
    </section>
  `;
  document.body.appendChild(wrapper);
  downloadModal = wrapper;

  wrapper.querySelectorAll("[data-download-close]").forEach(button => {
    button.addEventListener("click", closeDownloadModal);
  });
  wrapper.querySelector("#downloadVersionToggle")?.addEventListener("click", toggleDownloadVersionMenu);
  wrapper.querySelector("#downloadVersionSearch")?.addEventListener("input", renderDownloadVersionList);
  document.addEventListener("keydown", event => {
    if (event.key === "Escape" && !downloadModal.hidden) {
      closeDownloadModal();
    }
  });
  renderDownloadVersionList();
  return wrapper;
}

function openDownloadModal() {
  ensureDownloadModal();
  selectedDownloadVersion = null;
  updateDownloadModalState();
  downloadModal.hidden = false;
  document.body.classList.add("modal-open");
  setDownloadMenuOpen(false);
  downloadModal.querySelector("#downloadVersionToggle")?.focus();
}

function closeDownloadModal() {
  if (!downloadModal) return;
  downloadModal.hidden = true;
  document.body.classList.remove("modal-open");
}

function toggleDownloadVersionMenu() {
  const menu = downloadModal?.querySelector("#downloadVersionMenu");
  setDownloadMenuOpen(Boolean(menu?.hidden));
}

function setDownloadMenuOpen(open) {
  const menu = downloadModal?.querySelector("#downloadVersionMenu");
  const toggle = downloadModal?.querySelector("#downloadVersionToggle");
  if (!menu || !toggle) return;
  menu.hidden = !open;
  toggle.setAttribute("aria-expanded", open ? "true" : "false");
  toggle.querySelector(".download-chevron").textContent = open ? "⌃" : "⌄";
  if (open) {
    const search = downloadModal.querySelector("#downloadVersionSearch");
    if (search) {
      search.value = "";
      renderDownloadVersionList();
      setTimeout(() => search.focus(), 0);
    }
  }
}

function renderDownloadVersionList() {
  const list = downloadModal?.querySelector("#downloadVersionList");
  if (!list) return;
  const query = downloadModal.querySelector("#downloadVersionSearch")?.value.trim().toLowerCase() || "";
  const versions = DOWNLOAD_VERSIONS.filter(version =>
    version.label.toLowerCase().includes(query)
      || version.note.toLowerCase().includes(query)
  );
  list.innerHTML = versions.map(version => `
    <button class="download-version-option" type="button" data-version="${escapeHtml(version.label)}">
      <strong>${escapeHtml(version.label)}</strong>
      <span>${escapeHtml(version.note)}</span>
    </button>
  `).join("") || `<p class="quiet">Keine passende Version gefunden.</p>`;
  list.querySelectorAll("[data-version]").forEach(button => {
    button.addEventListener("click", () => selectDownloadVersion(button.dataset.version));
  });
}

function selectDownloadVersion(label) {
  selectedDownloadVersion = DOWNLOAD_VERSIONS.find(version => version.label === label) || null;
  setDownloadMenuOpen(false);
  updateDownloadModalState();
  if (selectedDownloadVersion) {
    refreshSelectedDownloadRelease(selectedDownloadVersion);
  }
}

function updateDownloadModalState(releaseInfo = null) {
  if (!downloadModal) return;
  const label = downloadModal.querySelector(".download-select-label");
  const result = downloadModal.querySelector("#downloadResult");
  const title = downloadModal.querySelector("#downloadResultTitle");
  const meta = downloadModal.querySelector("#downloadResultMeta");
  const button = downloadModal.querySelector("#downloadResultButton");
  const hint = downloadModal.querySelector("#downloadModalHint");

  if (!selectedDownloadVersion) {
    if (label) label.textContent = "Select game version";
    if (result) result.hidden = true;
    if (hint) hint.textContent = "Wähle deine Minecraft-Version aus. Danach erscheint der passende Download.";
    return;
  }

  const downloadUrl = `/download/latest.jar?target=${encodeURIComponent(selectedDownloadVersion.target)}&mc=${encodeURIComponent(selectedDownloadVersion.minecraft)}`;
  if (label) label.textContent = `Game version: ${selectedDownloadVersion.label}`;
  if (result) result.hidden = false;
  if (button) button.href = releaseInfo?.downloadUrl || downloadUrl;
  if (title) title.textContent = releaseInfo?.assetName || `betterUC für ${selectedDownloadVersion.label}`;
  if (meta) {
    const size = releaseInfo?.assetSize ? ` | ${formatBytes(releaseInfo.assetSize)}` : "";
    meta.textContent = `[${selectedDownloadVersion.label}] Fabric${size}`;
  }
  if (hint) hint.textContent = releaseInfo ? "Download bereit über betteruc.de." : "Release-Daten werden geladen. Der Download ist trotzdem nutzbar.";
}

async function refreshSelectedDownloadRelease(version) {
  const cacheKey = `${version.target}:${version.minecraft}`;
  if (downloadReleaseCache.has(cacheKey)) {
    updateDownloadModalState(downloadReleaseCache.get(cacheKey));
    return;
  }
  try {
    const response = await fetch(`/api/releases/latest?target=${encodeURIComponent(version.target)}&mc=${encodeURIComponent(version.minecraft)}`, {
      cache: "no-store"
    });
    const data = await response.json();
    if (!response.ok || !data.ok) throw new Error(data.error || "Release nicht erreichbar");
    downloadReleaseCache.set(cacheKey, data);
    if (selectedDownloadVersion?.label === version.label) {
      updateDownloadModalState(data);
    }
  } catch {
    if (selectedDownloadVersion?.label === version.label) {
      updateDownloadModalState();
    }
  }
}

function setMessage(text, type) {
  if (!message) return;
  message.textContent = text;
  message.className = `form-message ${type || ""}`;
}

function setPanelMessage(text, type) {
  if (!panelMessage) return;
  panelMessage.textContent = text;
  panelMessage.className = `form-message ${type || ""}`;
}

form?.addEventListener("submit", async event => {
  event.preventDefault();
  setMessage("Access Code wird erstellt ...", "");
  tokenBox.hidden = true;

  const payload = {
    minecraftName: document.querySelector("#minecraftName").value.trim(),
    faction: document.querySelector("#faction").value.trim()
  };

  try {
    const response = await fetch("/api/access", {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify(payload)
    });
    const data = await response.json();
    if (!response.ok || !data.ok) {
      throw new Error(data.error || "Access Code konnte nicht erstellt werden.");
    }

    generatedToken.textContent = data.accessCode;
    tokenBox.hidden = false;
    setMessage("Access Code erstellt. Bitte direkt kopieren.", "success");
    await refreshStatus();
  } catch (error) {
    setMessage(error.message, "error");
  }
});

copyToken?.addEventListener("click", async () => {
  const token = generatedToken.textContent.trim();
  if (!token) return;
  await navigator.clipboard.writeText(token);
  setMessage("Access Code kopiert.", "success");
});

function moneyLabel(value) {
  if (value === null || value === undefined) return "Noch nicht getrackt";
  return `${Number(value).toLocaleString("de-DE")}$`;
}

function numberLabel(value, suffix = "") {
  if (value === null || value === undefined || value === "") return "Noch nicht getrackt";
  return `${Number(value).toLocaleString("de-DE")}${suffix}`;
}

function textLabel(value) {
  return value ? String(value) : "Noch nicht getrackt";
}

function dateLabel(value, fallback = "Noch keine Stats empfangen") {
  if (!value) return fallback;
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return fallback;
  return `Aktualisiert: ${date.toLocaleString("de-DE")}`;
}

function plainDateLabel(value, fallback = "nie") {
  if (!value) return fallback;
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return fallback;
  return date.toLocaleString("de-DE");
}

function escapeHtml(value) {
  return String(value || "").replace(/[&<>"']/g, char => ({
    "&": "&amp;",
    "<": "&lt;",
    ">": "&gt;",
    '"': "&quot;",
    "'": "&#039;"
  })[char]);
}

function roleLabel(role) {
  if (role === "admin") return "Admin";
  if (role === "helper") return "Helper";
  if (role === "partner") return "Partner";
  if (role === "vip") return "VIP";
  return "Spieler";
}

function roleClass(role) {
  if (role === "admin") return "role-badge role-admin";
  if (role === "helper") return "role-badge role-helper";
  if (role === "partner") return "role-badge role-partner";
  if (role === "vip") return "role-badge role-vip";
  return "role-badge role-user";
}

function accountStatusLabel(user) {
  if (user.status === "revoked") return "gesperrt";
  return user.online ? "online" : "aktiv";
}

function renderMetaCards(user) {
  if (!panelAccountMeta) return;
  const meta = [
    ["Online", user.online ? "Ja" : "Nein"],
    ["Verbunden seit", plainDateLabel(user.connectedAt)],
    ["Letzter Kontakt", plainDateLabel(user.lastSeenAt)],
    ["Letzter Weblogin", plainDateLabel(user.lastPanelLoginAt)],
    ["Server", user.lastServer || "nicht erkannt"],
    ["Version", user.lastVersion || "nicht erkannt"],
    ["Access Code", user.tokenPrefix ? `${user.tokenPrefix}...` : "nicht erkannt"],
    ["Web-Login", user.hasWebPassword ? "eingerichtet" : "nicht eingerichtet"]
  ];

  panelAccountMeta.innerHTML = meta.map(([label, value]) => `
    <article class="account-meta-card">
      <span>${escapeHtml(label)}</span>
      <strong>${escapeHtml(value)}</strong>
    </article>
  `).join("");
}

function renderHistory(history) {
  if (!panelHistory) return;
  const entries = Array.isArray(history) ? history.slice(0, 6) : [];
  if (entries.length === 0) {
    panelHistory.innerHTML = `<p class="quiet">Noch kein Verlauf vorhanden.</p>`;
    return;
  }

  panelHistory.innerHTML = entries.map(entry => `
    <article class="history-entry">
      <span>${escapeHtml(plainDateLabel(entry.at))}</span>
      <strong>${escapeHtml(moneyLabel(entry.bankMoney))} Bank | ${escapeHtml(moneyLabel(entry.cashMoney))} Bargeld</strong>
      <small>${escapeHtml(entry.factionDisplay || "Fraktion nicht erkannt")} | ${escapeHtml(textLabel(entry.warns))}</small>
    </article>
  `).join("");
}

function renderPanel(user) {
  if (!panelDashboard || !panelStats) return;
  panelLoginForm.hidden = true;
  panelDashboard.hidden = false;
  panelUsername.textContent = user.minecraftName || "-";
  if (panelRoleBadge) {
    panelRoleBadge.textContent = roleLabel(user.role);
    panelRoleBadge.className = roleClass(user.role);
  }
  if (panelStatusBadge) {
    const active = user.status !== "revoked";
    panelStatusBadge.textContent = accountStatusLabel(user);
    panelStatusBadge.className = `status-pill ${active ? "active" : "revoked"}`;
  }
  if (panelAdmin) {
    const isAdmin = user.role === "admin";
    panelAdmin.hidden = !isAdmin;
    panelAdmin.style.display = isAdmin ? "" : "none";
  }

  const stats = user.stats || {};
  const factionDisplay = stats.factionDisplay || user.faction || user.factionDisplay || "";
  const cards = [
    ["Bank Geld", moneyLabel(stats.bankMoney)],
    ["Bargeld", moneyLabel(stats.cashMoney)],
    ["Häuser", textLabel(stats.houses)],
    ["Treuebonus", numberLabel(stats.loyaltyBonus, " Punkte")],
    ["Spielzeit", numberLabel(stats.playTimeHours, " Stunden")],
    ["Votepoints", numberLabel(stats.votepoints)],
    ["Warns", textLabel(stats.warns)],
    ["Fraktion", textLabel(factionDisplay)]
  ];

  panelStats.innerHTML = cards.map(([label, value]) => `
    <article class="panel-stat-card">
      <span>${escapeHtml(label)}</span>
      <strong>${escapeHtml(value)}</strong>
    </article>
  `).join("");
  renderMetaCards(user);
  renderHistory(user.statsHistory);
  panelUpdated.textContent = dateLabel(user.lastStatsAt || stats.updatedAt);
}

function showPanelLogin(messageText = "") {
  if (!panelLoginForm || !panelDashboard) return;
  panelLoginForm.hidden = false;
  panelDashboard.hidden = true;
  if (messageText) setPanelMessage(messageText, "");
}

async function fetchPanelSession() {
  const sessionToken = localStorage.getItem(PANEL_SESSION_KEY);
  if (!sessionToken) {
    showPanelLogin();
    return;
  }

  try {
    const response = await fetch("/api/user/me", {
      cache: "no-store",
      headers: { "x-betteruc-session": sessionToken }
    });
    const data = await response.json();
    if (!response.ok || !data.ok) throw new Error(data.error || "Login abgelaufen.");
    renderPanel(data.user);
  } catch (error) {
    localStorage.removeItem(PANEL_SESSION_KEY);
    showPanelLogin(error.message);
  }
}

panelLoginForm?.addEventListener("submit", async event => {
  event.preventDefault();
  setPanelMessage("Login wird geprüft ...", "");

  const payload = {
    minecraftName: document.querySelector("#panelMinecraftName").value.trim(),
    password: document.querySelector("#panelPassword").value
  };

  try {
    const response = await fetch("/api/user/login", {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify(payload)
    });
    const data = await response.json();
    if (!response.ok || !data.ok) throw new Error(data.error || "Login fehlgeschlagen.");
    localStorage.setItem(PANEL_SESSION_KEY, data.sessionToken);
    setPanelMessage("", "");
    renderPanel(data.user);
  } catch (error) {
    setPanelMessage(error.message, "error");
  }
});

panelRefresh?.addEventListener("click", fetchPanelSession);

panelAdmin?.addEventListener("click", () => {
  window.location.href = "/admin";
});

panelLogout?.addEventListener("click", () => {
  localStorage.removeItem(PANEL_SESSION_KEY);
  showPanelLogin("Du bist ausgeloggt.");
});

refreshStatus();
initDownloadModal();
refreshDownloadInfo();
if (panelLoginForm || panelDashboard) {
  fetchPanelSession();
}
setInterval(refreshStatus, 15000);
