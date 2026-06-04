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
const panelUpdated = document.querySelector("#panelUpdated");
const panelRefresh = document.querySelector("#panelRefresh");
const panelAdmin = document.querySelector("#panelAdmin");
const panelLogout = document.querySelector("#panelLogout");
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

function dateLabel(value) {
  if (!value) return "Noch keine Stats empfangen";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "Noch keine Stats empfangen";
  return `Aktualisiert: ${date.toLocaleString("de-DE")}`;
}

function roleLabel(role) {
  if (role === "admin") return "Admin";
  if (role === "vip") return "VIP";
  return "Spieler";
}

function roleClass(role) {
  if (role === "admin") return "role-badge role-admin";
  if (role === "vip") return "role-badge role-vip";
  return "role-badge role-user";
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
    panelStatusBadge.textContent = active ? "aktiv" : "gesperrt";
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
      <span>${label}</span>
      <strong>${value}</strong>
    </article>
  `).join("");
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
if (panelLoginForm || panelDashboard) {
  fetchPanelSession();
}
setInterval(refreshStatus, 15000);
