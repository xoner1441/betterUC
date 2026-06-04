const ADMIN_STORAGE_KEY = "betteruc_admin_key";
const PANEL_SESSION_KEY = "betteruc-panel-session";

const loginPanel = document.querySelector("#loginPanel");
const adminPanel = document.querySelector("#adminPanel");
const loginForm = document.querySelector("#adminLogin");
const adminKeyInput = document.querySelector("#adminKey");
const loginMessage = document.querySelector("#loginMessage");
const accountsTable = document.querySelector("#accountsTable");
const accountSearch = document.querySelector("#accountSearch");
const accountRoleFilter = document.querySelector("#accountRoleFilter");
const accountStatusFilter = document.querySelector("#accountStatusFilter");
const accountFactionFilter = document.querySelector("#accountFactionFilter");
const createForm = document.querySelector("#createAccount");
const createMessage = document.querySelector("#createMessage");
const adminTokenBox = document.querySelector("#adminTokenBox");
const adminGeneratedToken = document.querySelector("#adminGeneratedToken");
const copyAdminToken = document.querySelector("#copyAdminToken");

let adminKey = localStorage.getItem(ADMIN_STORAGE_KEY) || "";
let panelSession = localStorage.getItem(PANEL_SESSION_KEY) || "";
let accounts = [];

function setLoginMessage(text, type = "") {
  loginMessage.textContent = text;
  loginMessage.className = `form-message ${type}`;
}

function setCreateMessage(text, type = "") {
  createMessage.textContent = text;
  createMessage.className = `form-message ${type}`;
}

function headers() {
  const result = { "content-type": "application/json" };
  if (panelSession) result["x-betteruc-session"] = panelSession;
  if (adminKey) result["x-betteruc-admin"] = adminKey;
  return result;
}

async function api(path, options = {}) {
  const response = await fetch(path, {
    ...options,
    headers: {
      ...headers(),
      ...(options.headers || {})
    }
  });
  const data = await response.json();
  if (!response.ok || !data.ok) {
    throw new Error(data.error || "Anfrage fehlgeschlagen.");
  }
  return data;
}

function showAdmin() {
  loginPanel.hidden = true;
  adminPanel.hidden = false;
}

function showLogin() {
  adminPanel.hidden = true;
  loginPanel.hidden = false;
}

function formatDate(value) {
  if (!value) return "nie";
  try {
    return new Intl.DateTimeFormat("de-DE", {
      dateStyle: "short",
      timeStyle: "short"
    }).format(new Date(value));
  } catch {
    return value;
  }
}

function accountMatches(account, query) {
  const role = accountRoleFilter?.value || "";
  const status = accountStatusFilter?.value || "";
  const faction = accountFactionFilter?.value.trim().toLowerCase() || "";

  if (role && account.role !== role) return false;
  if (status === "online" && !account.online) return false;
  if (status === "offline" && account.online) return false;
  if ((status === "active" || status === "revoked") && account.status !== status) return false;
  const factionHaystack = `${account.faction || ""} ${account.factionDisplay || ""}`.toLowerCase();
  if (faction && !factionHaystack.includes(faction)) return false;
  if (!query) return true;

  const haystack = [
    account.minecraftName,
    account.minecraftUuid,
    account.faction,
    account.role,
    account.tokenPrefix,
    account.lastServer,
    account.lastVersion,
    account.status
  ].join(" ").toLowerCase();
  return haystack.includes(query.toLowerCase());
}

function renderAccounts() {
  const query = accountSearch.value.trim();
  const filtered = accounts.filter(account => accountMatches(account, query));
  if (filtered.length === 0) {
    accountsTable.innerHTML = `<tr><td colspan="7">Keine Accounts gefunden.</td></tr>`;
    return;
  }

  accountsTable.innerHTML = filtered.map(account => `
    <tr data-id="${account.id}">
      <td>
        <strong>${escapeHtml(account.minecraftName || "Unbenannt")}</strong>
        <span>${escapeHtml(account.minecraftUuid || "keine UUID")} ${account.online ? "online" : ""}</span>
      </td>
      <td>
        <input class="row-input faction-input" value="${escapeAttr(account.faction || "")}" maxlength="48" placeholder="Fraktion">
      </td>
      <td>
        <select class="row-input role-input">
          <option value="user" ${account.role === "user" ? "selected" : ""}>Spieler</option>
          <option value="vip" ${account.role === "vip" ? "selected" : ""}>VIP</option>
          <option value="admin" ${account.role === "admin" ? "selected" : ""}>Admin</option>
        </select>
      </td>
      <td><span class="status-pill ${account.status === "revoked" ? "revoked" : "active"}">${account.status === "revoked" ? "gesperrt" : "aktiv"}</span></td>
      <td><code>${escapeHtml(account.tokenPrefix || "-")}</code></td>
      <td>
        <span>${formatDate(account.lastSeenAt)}</span>
        <small>${escapeHtml(account.lastVersion || "")}</small>
      </td>
      <td>
        <div class="row-actions">
          <button class="button secondary save-account" type="button">Speichern</button>
          <button class="button secondary reset-code" type="button">Code neu</button>
          ${account.status === "revoked"
            ? `<button class="button secondary activate-account" type="button">Aktivieren</button>`
            : `<button class="button secondary revoke-account" type="button">Sperren</button>`}
          <button class="button secondary danger delete-account" type="button">Löschen</button>
        </div>
      </td>
    </tr>
  `).join("");
}

function updateTotals(totals) {
  document.querySelector("#totalAccounts").textContent = totals.accounts ?? 0;
  document.querySelector("#activeAccounts").textContent = totals.active ?? 0;
  document.querySelector("#revokedAccounts").textContent = totals.revoked ?? 0;
  document.querySelector("#onlineAccounts").textContent = totals.online ?? 0;
}

async function loadAccounts() {
  const data = await api("/api/admin/accounts");
  accounts = data.accounts || [];
  updateTotals(data.totals || {});
  renderAccounts();
}

function rowAccountId(target) {
  return target.closest("tr")?.dataset.id || "";
}

async function updateAccount(id, body) {
  await api(`/api/admin/accounts/${encodeURIComponent(id)}`, {
    method: "PATCH",
    body: JSON.stringify(body)
  });
  await loadAccounts();
}

async function runAccountAction(id, action) {
  const data = await api(`/api/admin/accounts/${encodeURIComponent(id)}/${action}`, {
    method: "POST",
    body: "{}"
  });
  if (data.accessCode) {
    adminGeneratedToken.textContent = data.accessCode;
    adminTokenBox.hidden = false;
    setCreateMessage("Neuer Code wurde generiert. Direkt kopieren.", "success");
  }
  await loadAccounts();
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

function escapeAttr(value) {
  return escapeHtml(value).replace(/`/g, "&#096;");
}

loginForm.addEventListener("submit", async event => {
  event.preventDefault();
  adminKey = adminKeyInput.value.trim();
  if (!adminKey) {
    setLoginMessage("Bitte Admin-Key eintragen.", "error");
    return;
  }
  try {
    panelSession = "";
    await loadAccounts();
    localStorage.setItem(ADMIN_STORAGE_KEY, adminKey);
    showAdmin();
    setLoginMessage("");
  } catch (error) {
    setLoginMessage(error.message, "error");
  }
});

document.querySelector("#refreshAccounts").addEventListener("click", () => {
  loadAccounts().catch(error => setCreateMessage(error.message, "error"));
});

document.querySelector("#logoutAdmin").addEventListener("click", () => {
  localStorage.removeItem(ADMIN_STORAGE_KEY);
  localStorage.removeItem(PANEL_SESSION_KEY);
  adminKey = "";
  panelSession = "";
  showLogin();
});

accountSearch.addEventListener("input", renderAccounts);
accountRoleFilter?.addEventListener("change", renderAccounts);
accountStatusFilter?.addEventListener("change", renderAccounts);
accountFactionFilter?.addEventListener("input", renderAccounts);

createForm.addEventListener("submit", async event => {
  event.preventDefault();
  setCreateMessage("Code wird erstellt ...");
  adminTokenBox.hidden = true;
  try {
    const data = await api("/api/admin/accounts", {
      method: "POST",
      body: JSON.stringify({
        minecraftName: document.querySelector("#newMinecraftName").value.trim(),
        faction: document.querySelector("#newFaction").value.trim(),
        role: document.querySelector("#newRole").value
      })
    });
    adminGeneratedToken.textContent = data.accessCode;
    adminTokenBox.hidden = false;
    createForm.reset();
    setCreateMessage("Access Code erstellt. Direkt kopieren.", "success");
    await loadAccounts();
  } catch (error) {
    setCreateMessage(error.message, "error");
  }
});

copyAdminToken.addEventListener("click", async () => {
  const token = adminGeneratedToken.textContent.trim();
  if (!token) return;
  await navigator.clipboard.writeText(token);
  setCreateMessage("Code kopiert.", "success");
});

accountsTable.addEventListener("click", async event => {
  const button = event.target.closest("button");
  if (!button) return;
  const id = rowAccountId(button);
  if (!id) return;

  try {
    if (button.classList.contains("save-account")) {
      const row = button.closest("tr");
      await updateAccount(id, {
        faction: row.querySelector(".faction-input").value.trim(),
        role: row.querySelector(".role-input").value
      });
      return;
    }
    if (button.classList.contains("reset-code")) {
      await runAccountAction(id, "reset-code");
      return;
    }
    if (button.classList.contains("revoke-account")) {
      await runAccountAction(id, "revoke");
      return;
    }
    if (button.classList.contains("activate-account")) {
      await runAccountAction(id, "activate");
      return;
    }
    if (button.classList.contains("delete-account")) {
      const row = button.closest("tr");
      const name = row?.querySelector("td strong")?.textContent?.trim() || "diesen Account";
      if (!confirm(`${name} wirklich komplett löschen? Diese Aktion entfernt den Account dauerhaft.`)) return;
      await runAccountAction(id, "delete");
      setCreateMessage("Account wurde gelöscht.", "success");
    }
  } catch (error) {
    setCreateMessage(error.message, "error");
  }
});

async function bootstrapAdmin() {
  if (panelSession) {
    try {
      await loadAccounts();
      showAdmin();
      return;
    } catch {
      panelSession = "";
      localStorage.removeItem(PANEL_SESSION_KEY);
    }
  }

  if (adminKey) {
    adminKeyInput.value = adminKey;
    try {
      await loadAccounts();
      showAdmin();
      return;
    } catch {
      localStorage.removeItem(ADMIN_STORAGE_KEY);
      adminKey = "";
    }
  }

  showLogin();
}

bootstrapAdmin();
