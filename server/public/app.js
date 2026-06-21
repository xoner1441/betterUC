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
refreshDownloadInfo();
if (panelLoginForm || panelDashboard) {
  fetchPanelSession();
}
setInterval(refreshStatus, 15000);
