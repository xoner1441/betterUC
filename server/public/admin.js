const ADMIN_STORAGE_KEY = "betteruc_admin_key";
const PANEL_SESSION_KEY = "betteruc-panel-session";
const FORUM_CHANGELOG_PREPARED_KEY = "betteruc_forum_changelog_prepared_version";
const FORUM_CHANGELOG_LIMIT = 10_000;

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
const createBackup = document.querySelector("#createBackup");
const backupStatus = document.querySelector("#backupStatus");
const databaseConnection = document.querySelector("#databaseConnection");
const databaseSize = document.querySelector("#databaseSize");
const databaseMigration = document.querySelector("#databaseMigration");
const cloudProfileCount = document.querySelector("#cloudProfileCount");
const cloudRevisionCount = document.querySelector("#cloudRevisionCount");
const cloudSyncEventCount = document.querySelector("#cloudSyncEventCount");
const cloudConflictCount = document.querySelector("#cloudConflictCount");
const cloudErrorCount = document.querySelector("#cloudErrorCount");
const statsProfileCount = document.querySelector("#statsProfileCount");
const webProfileCount = document.querySelector("#webProfileCount");
const auditEntryCount = document.querySelector("#auditEntryCount");
const featureFlagCount = document.querySelector("#featureFlagCount");
const disabledFeatureFlagCount = document.querySelector("#disabledFeatureFlagCount");
const databaseDetail = document.querySelector("#databaseDetail");
const featureFlagsStatus = document.querySelector("#featureFlagsStatus");
const featureFlagsList = document.querySelector("#featureFlagsList");
const featureFlagMessage = document.querySelector("#featureFlagMessage");
const forumChangelogVersion = document.querySelector("#forumChangelogVersion");
const forumChangelogSource = document.querySelector("#forumChangelogSource");
const forumChangelogCount = document.querySelector("#forumChangelogCount");
const forumChangelogPrepared = document.querySelector("#forumChangelogPrepared");
const forumChangelogMessage = document.querySelector("#forumChangelogMessage");
const copyForumChangelog = document.querySelector("#copyForumChangelog");

let adminKey = localStorage.getItem(ADMIN_STORAGE_KEY) || "";
let panelSession = localStorage.getItem(PANEL_SESSION_KEY) || "";
let accounts = [];
let expandedAccounts = new Set();
let cloudHistories = new Map();
let cloudHistoryLoading = new Set();
let cloudHistoryErrors = new Map();
let featureFlags = [];
let featureFlagsWritable = false;
let featureFlagSaving = new Set();
let forumChangelogReleases = [];
let forumChangelogLoading = false;

function setLoginMessage(text, type = "") {
  loginMessage.textContent = text;
  loginMessage.className = `form-message ${type}`;
}

function setCreateMessage(text, type = "") {
    createMessage.textContent = text;
    createMessage.className = `form-message ${type}`;
}

function setFeatureFlagMessage(text, type = "") {
  if (!featureFlagMessage) return;
  featureFlagMessage.textContent = text;
  featureFlagMessage.className = `form-message ${type}`;
}

function setForumChangelogMessage(text, type = "") {
  if (!forumChangelogMessage) return;
  forumChangelogMessage.textContent = text;
  forumChangelogMessage.className = `form-message ${type}`;
}

function forumChangelogItems(value) {
  return Array.isArray(value) ? value.filter(item => item && typeof item === "object") : [];
}

function buildForumChangelogSource(release) {
  if (!release) return "";
  const version = escapeHtml(release.version || "aktuell");
  const date = escapeHtml(release.date || "");
  const summary = forumChangelogItems(release.summary);
  const changes = Array.isArray(release.changes)
    ? release.changes.map(item => String(item || "").trim()).filter(Boolean)
    : [];
  const blocks = [
    `<h2>betterUC v${version}</h2>`,
    date ? `<p><strong>Veröffentlicht am:</strong> ${date}</p>` : ""
  ];

  if (summary.length > 0) {
    blocks.push("<h3>Highlights</h3>");
    blocks.push(`<ul>${summary.map(item => {
      const title = escapeHtml(item.title || "Neuerung");
      const description = escapeHtml(item.description || "");
      return `<li><strong>${title}</strong>${description ? ` – ${description}` : ""}</li>`;
    }).join("")}</ul>`);
  }

  if (changes.length > 0) {
    blocks.push("<h3>Änderungen</h3>");
    blocks.push(`<ul>${changes.map(change => `<li>${escapeHtml(change)}</li>`).join("")}</ul>`);
  }

  blocks.push(
    "<hr>",
    '<p><strong>Download:</strong> <a href="https://betteruc.de/download">betteruc.de/download</a><br>',
    '<strong>Vollständiger Changelog:</strong> <a href="https://betteruc.de/changelog">betteruc.de/changelog</a><br>',
    '<strong>GitHub:</strong> <a href="https://github.com/xoner1441/betterUC">betterUC auf GitHub</a></p>'
  );
  return blocks.filter(Boolean).join("\n");
}

function selectedForumChangelogRelease() {
  const version = forumChangelogVersion?.value || "";
  return forumChangelogReleases.find(release => String(release.version || "") === version) || null;
}

function updateForumChangelogPreparedLabel() {
  if (!forumChangelogPrepared) return;
  const preparedVersion = localStorage.getItem(FORUM_CHANGELOG_PREPARED_KEY) || "";
  const selectedVersion = selectedForumChangelogRelease()?.version || "";
  if (!preparedVersion) {
    forumChangelogPrepared.textContent = "Noch keine Version auf diesem Gerät vorbereitet.";
    return;
  }
  forumChangelogPrepared.textContent = preparedVersion === selectedVersion
    ? `v${preparedVersion} wurde auf diesem Gerät bereits kopiert.`
    : `Zuletzt vorbereitet: v${preparedVersion}`;
}

function updateForumChangelogCount() {
  if (!forumChangelogCount || !forumChangelogSource) return;
  const length = forumChangelogSource.value.length;
  forumChangelogCount.textContent = `${length.toLocaleString("de-DE")} / ${FORUM_CHANGELOG_LIMIT.toLocaleString("de-DE")} Zeichen`;
  forumChangelogCount.classList.toggle("is-warning", length > FORUM_CHANGELOG_LIMIT);
  if (copyForumChangelog) copyForumChangelog.disabled = length === 0 || length > FORUM_CHANGELOG_LIMIT;
}

function renderForumChangelogSource() {
  if (!forumChangelogSource) return;
  forumChangelogSource.value = buildForumChangelogSource(selectedForumChangelogRelease());
  updateForumChangelogPreparedLabel();
  updateForumChangelogCount();
  setForumChangelogMessage("");
}

async function loadForumChangelog() {
  if (!forumChangelogVersion || forumChangelogLoading) return;
  forumChangelogLoading = true;
  try {
    const response = await fetch("/data/changelog.json", { cache: "no-store" });
    const data = await response.json();
    if (!response.ok || Number(data.schema) !== 1) throw new Error("Changelog-Daten sind ungültig.");
    forumChangelogReleases = forumChangelogItems(data.releases);
    if (forumChangelogReleases.length === 0) throw new Error("Keine Releases im Changelog gefunden.");

    forumChangelogVersion.innerHTML = forumChangelogReleases
      .map(release => `<option value="${escapeAttr(release.version)}">v${escapeHtml(release.version)} · ${escapeHtml(release.date || "ohne Datum")}</option>`)
      .join("");
    const current = forumChangelogReleases.find(release => release.current) || forumChangelogReleases[0];
    forumChangelogVersion.value = String(current.version || "");
    forumChangelogVersion.disabled = false;
    renderForumChangelogSource();
  } catch (error) {
    forumChangelogReleases = [];
    forumChangelogVersion.innerHTML = "<option>Changelog nicht verfügbar</option>";
    forumChangelogVersion.disabled = true;
    if (forumChangelogSource) forumChangelogSource.value = "";
    updateForumChangelogCount();
    setForumChangelogMessage(error.message || "Changelog konnte nicht geladen werden.", "error");
  } finally {
    forumChangelogLoading = false;
  }
}

async function copyText(value) {
  if (navigator.clipboard?.writeText) {
    await navigator.clipboard.writeText(value);
    return;
  }
  const helper = document.createElement("textarea");
  helper.value = value;
  helper.style.position = "fixed";
  helper.style.opacity = "0";
  document.body.appendChild(helper);
  helper.select();
  const copied = document.execCommand("copy");
  helper.remove();
  if (!copied) throw new Error("Kopieren wurde vom Browser blockiert.");
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
  loadForumChangelog().catch(error => setForumChangelogMessage(error.message, "error"));
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

function formatBytes(value) {
  const size = Number(value);
  if (!Number.isFinite(size) || size <= 0) return "0 KB";
  if (size < 1024 * 1024) return `${Math.ceil(size / 1024)} KB`;
  return `${(size / (1024 * 1024)).toFixed(1)} MB`;
}

function moneyLabel(value) {
  if (value === null || value === undefined) return "nicht getrackt";
  return `${Number(value).toLocaleString("de-DE")}$`;
}

function numberLabel(value, suffix = "") {
  if (value === null || value === undefined || value === "") return "nicht getrackt";
  return `${Number(value).toLocaleString("de-DE")}${suffix}`;
}

function textLabel(value) {
  return value ? String(value) : "nicht getrackt";
}

function renderBackupStatus(backups) {
  if (!backupStatus) return;
  const entries = Array.isArray(backups) ? backups : [];
  const postgres = entries.find(backup => backup.type === "postgres");
  const jsonBackup = entries.find(backup => backup.type === "json");
  if (postgres) {
    backupStatus.textContent = `DB-Backup: ${formatDate(postgres.createdAt)} (${formatBytes(postgres.size)})`;
    backupStatus.title = jsonBackup
      ? `JSON-Spiegel: ${formatDate(jsonBackup.createdAt)} (${formatBytes(jsonBackup.size)})`
      : "Noch kein JSON-Spiegel vorhanden";
    return;
  }
  backupStatus.textContent = jsonBackup
    ? `Nur JSON-Backup: ${formatDate(jsonBackup.createdAt)}`
    : "Noch kein Backup";
  backupStatus.title = "";
}

function renderFeatureFlags() {
  if (!featureFlagsList || !featureFlagsStatus) return;
  const disabledCount = featureFlags.filter(flag => !flag.enabled).length;
  featureFlagsStatus.textContent = featureFlagsWritable
    ? (disabledCount > 0 ? `${disabledCount} deaktiviert` : "alles aktiv")
    : "nur lesbar";
  featureFlagsStatus.className = `status-pill ${disabledCount > 0 ? "revoked" : "active"}`;

  if (featureFlags.length === 0) {
    featureFlagsList.innerHTML = `<p class="quiet">Keine Feature-Schalter vorhanden.</p>`;
    return;
  }

  featureFlagsList.innerHTML = featureFlags.map(flag => {
    const saving = featureFlagSaving.has(flag.key);
    return `
      <label class="feature-flag-card ${flag.enabled ? "is-enabled" : "is-disabled"}">
        <span class="feature-flag-copy">
          <strong>${escapeHtml(flag.label || flag.key)}</strong>
          <span>${escapeHtml(flag.description || "")}</span>
          <small>${flag.updatedAt ? `Geändert ${escapeHtml(formatDate(flag.updatedAt))}` : "Standard aktiv"}</small>
        </span>
        <input class="feature-flag-toggle" type="checkbox" data-key="${escapeAttr(flag.key)}"
          ${flag.enabled ? "checked" : ""} ${!featureFlagsWritable || saving ? "disabled" : ""}>
        <span class="feature-switch" aria-hidden="true"></span>
      </label>
    `;
  }).join("");
}

async function loadFeatureFlags() {
  try {
    const data = await api("/api/admin/features");
    featureFlags = Array.isArray(data.features) ? data.features : [];
    featureFlagsWritable = Boolean(data.writable);
    renderFeatureFlags();
  } catch (error) {
    featureFlags = [];
    featureFlagsWritable = false;
    renderFeatureFlags();
    setFeatureFlagMessage(error.message || "Feature-Schalter konnten nicht geladen werden.", "error");
  }
}

async function updateFeatureFlag(key, enabled) {
  if (!key || featureFlagSaving.has(key)) return;
  featureFlagSaving.add(key);
  setFeatureFlagMessage("Änderung wird gespeichert ...");
  renderFeatureFlags();
  try {
    const data = await api(`/api/admin/features/${encodeURIComponent(key)}`, {
      method: "PATCH",
      body: JSON.stringify({ enabled })
    });
    const updated = data.feature;
    featureFlags = featureFlags.map(flag => flag.key === key ? updated : flag);
    setFeatureFlagMessage(
      `${updated.label || key} ist jetzt ${updated.enabled ? "aktiv" : "deaktiviert"}.`,
      "success"
    );
  } catch (error) {
    setFeatureFlagMessage(error.message || "Änderung konnte nicht gespeichert werden.", "error");
    await loadFeatureFlags();
  } finally {
    featureFlagSaving.delete(key);
    renderFeatureFlags();
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

function cloudHistoryActionLabel(entry) {
  if (entry.action === "restore") {
    return `Wiederhergestellt aus Rev. ${entry.restoredFromRevision ?? "-"}`;
  }
  if (entry.action === "reset") return "Stand vor Reset";
  return "Gespeichert";
}

function cloudSyncEventLabel(type) {
  if (type === "download") return "Cloud-Profil geladen";
  if (type === "upload") return "Cloud-Profil gespeichert";
  if (type === "conflict") return "Versionskonflikt";
  if (type === "error") return "Sync-Fehler";
  return "noch keine Aktivität";
}

function cloudHistoryHtml(account, hasCloud) {
  if (cloudHistoryLoading.has(account.id)) {
    return `<p class="quiet">Cloud-Verlauf wird geladen.</p>`;
  }
  if (cloudHistoryErrors.has(account.id)) {
    return `<p class="form-message error">${escapeHtml(cloudHistoryErrors.get(account.id))}</p>`;
  }
  const history = cloudHistories.get(account.id);
  if (!history) {
    return `<p class="quiet">Verlauf wird beim Öffnen der Details geladen.</p>`;
  }
  if (history.length === 0) {
    return `<p class="quiet">Noch keine gespeicherten Cloud-Revisionen vorhanden.</p>`;
  }
  return `
    <div class="cloud-history-list">
      ${history.map(entry => {
        const isCurrent = hasCloud && Number(account.cloudSettings?.revision) === Number(entry.revision);
        return `
          <article class="cloud-history-entry">
            <div>
              <strong>Rev. ${escapeHtml(entry.revision)}</strong>
              <span>${escapeHtml(cloudHistoryActionLabel(entry))}</span>
              <small>${escapeHtml(formatDate(entry.recordedAt))} · ${escapeHtml(entry.updatedByVersion || "Version unbekannt")} · Schema v${escapeHtml(entry.schemaVersion)}</small>
            </div>
            <button class="button secondary restore-cloud-settings" type="button" data-history-id="${escapeAttr(entry.id)}" ${isCurrent ? "disabled" : ""}>
              ${isCurrent ? "Aktuell" : "Wiederherstellen"}
            </button>
          </article>
        `;
      }).join("")}
    </div>
  `;
}

function accountDetailHtml(account) {
  const stats = account.stats || {};
  const cloud = account.cloudSettings || null;
  const hasCloud = Boolean(cloud && (cloud.exists ?? cloud.revision !== null));
  const history = Array.isArray(account.statsHistory) ? account.statsHistory.slice(0, 6) : [];
  const meta = [
    ["Online", account.online ? "Ja" : "Nein"],
    ["Verbunden seit", formatDate(account.connectedAt)],
    ["Letzter Kontakt", formatDate(account.lastSeenAt)],
    ["Letzter Weblogin", formatDate(account.lastPanelLoginAt)],
    ["Server", account.lastServer || "nicht erkannt"],
    ["Channel", account.lastChannel || "nicht erkannt"],
    ["Version", account.lastVersion || "nicht erkannt"]
  ];
  const webProfile = [
    ["Web-Login", account.hasWebPassword ? "eingerichtet" : "nicht eingerichtet"],
    ["Passwort gesetzt", formatDate(account.webPasswordSetAt)],
    ["Letzter Weblogin", formatDate(account.lastPanelLoginAt)],
    ["Zuletzt entfernt", formatDate(account.webPasswordClearedAt)],
    ["Sessions abgemeldet", formatDate(account.webSessionsInvalidAfter)]
  ];
  const cloudProfile = [
    ["Cloud-Profil", hasCloud ? "eingerichtet" : "nicht vorhanden"],
    ["Revision", hasCloud ? String(cloud.revision) : "-"],
    ["Letzter Sync", hasCloud ? formatDate(cloud.updatedAt) : "nie"],
    ["Mod-Version", cloud?.updatedByVersion || "-"],
    ["Schema", hasCloud ? `v${cloud.schemaVersion}` : "-"],
    ["Gespeicherte Stände", String(cloud?.historyCount || 0)],
    ["Letzte Aktivität", cloudSyncEventLabel(cloud?.lastEventType)],
    ["Aktivität am", formatDate(cloud?.lastEventAt)],
    ["Client-Version", cloud?.lastEventModVersion || "-"],
    ["Konflikte (24h)", String(cloud?.conflicts24h || 0)],
    ["Fehler (24h)", String(cloud?.errors24h || 0)]
  ];
  const statCards = [
    ["Bank", moneyLabel(stats.bankMoney)],
    ["Bargeld", moneyLabel(stats.cashMoney)],
    ["Häuser", textLabel(stats.houses)],
    ["Treuebonus", numberLabel(stats.loyaltyBonus, " Punkte")],
    ["Spielzeit", numberLabel(stats.playTimeHours, " Stunden")],
    ["Votepoints", numberLabel(stats.votepoints)],
    ["Warns", textLabel(stats.warns)],
    ["Fraktion", textLabel(stats.factionDisplay || account.faction)]
  ];
  const historyHtml = history.length === 0
    ? `<p class="quiet">Noch kein Stats-Verlauf vorhanden.</p>`
    : history.map(entry => `
      <article class="history-entry">
        <span>${formatDate(entry.at)}</span>
        <strong>${escapeHtml(moneyLabel(entry.bankMoney))} Bank | ${escapeHtml(moneyLabel(entry.cashMoney))} Bargeld</strong>
        <small>${escapeHtml(entry.factionDisplay || "Fraktion nicht erkannt")} | ${escapeHtml(textLabel(entry.warns))}</small>
      </article>
    `).join("");

  return `
    <tr class="admin-detail-row" data-id="${account.id}">
      <td colspan="7">
        <div class="admin-detail-grid">
          <div>
            <h4>Accountdetails</h4>
            <div class="account-meta-grid">
              ${meta.map(([label, value]) => `
                <article class="account-meta-card">
                  <span>${escapeHtml(label)}</span>
                  <strong>${escapeHtml(value)}</strong>
                </article>
              `).join("")}
            </div>
          </div>
          <div>
            <h4>Webprofil</h4>
            <div class="account-meta-grid">
              ${webProfile.map(([label, value]) => `
                <article class="account-meta-card">
                  <span>${escapeHtml(label)}</span>
                  <strong>${escapeHtml(value)}</strong>
                </article>
              `).join("")}
            </div>
            <div class="web-profile-panel">
              <label>
                Neues Webpasswort
                <input class="row-input web-password-input" type="password" autocomplete="new-password" placeholder="mind. 6 Zeichen">
              </label>
              <div class="admin-detail-actions">
                <button class="button secondary set-web-password" type="button">Passwort setzen</button>
                <button class="button secondary logout-web" type="button">Websession abmelden</button>
                <button class="button secondary danger clear-web-password" type="button" ${account.hasWebPassword ? "" : "disabled"}>Weblogin entfernen</button>
              </div>
              <p class="quiet">Wenn du den Weblogin entfernst, kann sich der Spieler erst nach neuem Passwort wieder anmelden.</p>
            </div>
          </div>
          <div>
            <h4>Stats</h4>
            <div class="account-meta-grid">
              ${statCards.map(([label, value]) => `
                <article class="account-meta-card">
                  <span>${escapeHtml(label)}</span>
                  <strong>${escapeHtml(value)}</strong>
                </article>
              `).join("")}
            </div>
          </div>
          <div>
            <h4>Cloud-Profil</h4>
            <div class="account-meta-grid">
              ${cloudProfile.map(([label, value]) => `
                <article class="account-meta-card">
                  <span>${escapeHtml(label)}</span>
                  <strong>${escapeHtml(value)}</strong>
                </article>
              `).join("")}
            </div>
            ${cloud?.lastEventDetail
              ? `<p class="quiet">Letzte Sync-Meldung: ${escapeHtml(cloud.lastEventDetail)}</p>`
              : ""}
            <div class="admin-detail-actions cloud-profile-actions">
              <button class="button secondary danger reset-cloud-settings" type="button" ${hasCloud ? "" : "disabled"}>Cloud-Profil zurücksetzen</button>
            </div>
            <p class="quiet">Dabei werden nur die aktiven Mod-Einstellungen gelöscht. Der Verlauf, Account, Stats und Access-Code bleiben erhalten.</p>
            <div class="cloud-history-panel">
              <h5>Cloud-Verlauf</h5>
              ${cloudHistoryHtml(account, hasCloud)}
            </div>
          </div>
          <div>
            <h4>Verlauf</h4>
            <div class="history-list compact">${historyHtml}</div>
          </div>
        </div>
      </td>
    </tr>
  `;
}

function renderAccounts() {
  const query = accountSearch.value.trim();
  const filtered = accounts.filter(account => accountMatches(account, query));
  if (filtered.length === 0) {
    accountsTable.innerHTML = `<tr><td colspan="7">Keine Accounts gefunden.</td></tr>`;
    return;
  }

  accountsTable.innerHTML = filtered.map(account => {
    const expanded = expandedAccounts.has(account.id);
    return `
    <tr data-id="${account.id}" class="${expanded ? "is-expanded" : ""}">
      <td>
        <div class="admin-player-name">
          <strong>${escapeHtml(account.minecraftName || "Unbenannt")}</strong>
          <span class="online-dot ${account.online ? "online" : "offline"}" title="${account.online ? "Online" : "Offline"}" aria-label="${account.online ? "Online" : "Offline"}"></span>
        </div>
        <span>${escapeHtml(account.minecraftUuid || "keine UUID")}</span>
      </td>
      <td>
        <input class="row-input faction-input" value="${escapeAttr(account.faction || "")}" maxlength="48" placeholder="Fraktion">
      </td>
      <td>
        <select class="row-input role-input">
          <option value="user" ${account.role === "user" ? "selected" : ""}>Spieler</option>
          <option value="helper" ${account.role === "helper" ? "selected" : ""}>Helper</option>
          <option value="partner" ${account.role === "partner" ? "selected" : ""}>Partner</option>
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
          <button class="button secondary toggle-details" type="button">${expanded ? "Zuklappen" : "Details"}</button>
          <button class="button secondary save-account" type="button">Speichern</button>
          <button class="button secondary reset-code" type="button">Code neu</button>
          ${account.status === "revoked"
            ? `<button class="button secondary activate-account" type="button">Aktivieren</button>`
            : `<button class="button secondary revoke-account" type="button">Sperren</button>`}
          <button class="button secondary danger delete-account" type="button">Löschen</button>
        </div>
      </td>
    </tr>
    ${expanded ? accountDetailHtml(account) : ""}
  `;
  }).join("");
}

function updateTotals(totals) {
  document.querySelector("#totalAccounts").textContent = totals.accounts ?? 0;
  document.querySelector("#activeAccounts").textContent = totals.active ?? 0;
  document.querySelector("#revokedAccounts").textContent = totals.revoked ?? 0;
  document.querySelector("#onlineAccounts").textContent = totals.online ?? 0;
  document.querySelector("#helperAccounts").textContent = totals.helper ?? 0;
  document.querySelector("#partnerAccounts").textContent = totals.partner ?? 0;
  document.querySelector("#vipAccounts").textContent = totals.vip ?? 0;
  document.querySelector("#adminAccounts").textContent = totals.admin ?? 0;
}

function renderDatabaseStatus(data, error = null) {
  const database = data?.database || {};
  const counts = database.counts || {};
  const connected = Boolean(database.connected) && !error;
  databaseConnection.textContent = connected ? "PostgreSQL online" : "nicht verfügbar";
  databaseConnection.className = `status-pill ${connected ? "active" : "revoked"}`;
  databaseSize.textContent = connected ? formatBytes(database.sizeBytes) : "-";
  databaseMigration.textContent = connected
    ? (database.pendingMigrations?.length ? `${database.pendingMigrations.length} offen` : "aktuell")
    : "-";
  cloudProfileCount.textContent = counts.cloudProfiles ?? "-";
  cloudRevisionCount.textContent = counts.cloudRevisions ?? "-";
  cloudSyncEventCount.textContent = counts.cloudSyncEvents24h ?? "-";
  cloudConflictCount.textContent = counts.cloudConflicts24h ?? "-";
  cloudErrorCount.textContent = counts.cloudErrors24h ?? "-";
  statsProfileCount.textContent = counts.statsProfiles ?? "-";
  webProfileCount.textContent = counts.webProfiles ?? "-";
  auditEntryCount.textContent = counts.auditEntries ?? "-";
  featureFlagCount.textContent = counts.featureFlags ?? "-";
  disabledFeatureFlagCount.textContent = counts.disabledFeatureFlags ?? "-";
  if (error) {
    databaseDetail.textContent = error.message || "Datenbankstatus konnte nicht geladen werden.";
    return;
  }
  if (!connected) {
    databaseDetail.textContent = `Persistenz: ${data?.persistence || "unbekannt"}. Cloud-Synchronisierung ist derzeit nicht verfügbar.`;
    return;
  }
  const latest = database.latestMigration?.version || "keine";
  databaseDetail.textContent = `${database.databaseName || "betteruc"} · PostgreSQL ${database.serverVersion || ""} · letzte Migration ${latest}`;
}

async function loadAccounts() {
  const data = await api("/api/admin/accounts");
  accounts = data.accounts || [];
  const ids = new Set(accounts.map(account => account.id));
  expandedAccounts = new Set([...expandedAccounts].filter(id => ids.has(id)));
  cloudHistories = new Map([...cloudHistories].filter(([id]) => ids.has(id)));
  cloudHistoryErrors = new Map([...cloudHistoryErrors].filter(([id]) => ids.has(id)));
  updateTotals(data.totals || {});
  renderBackupStatus(data.backups || []);
  renderAccounts();
  try {
    renderDatabaseStatus(await api("/api/admin/database"));
  } catch (error) {
    renderDatabaseStatus(null, error);
  }
  await loadFeatureFlags();
}

async function loadCloudHistory(id) {
  cloudHistoryLoading.add(id);
  cloudHistoryErrors.delete(id);
  renderAccounts();
  try {
    const data = await api(`/api/admin/accounts/${encodeURIComponent(id)}/cloud-history`);
    cloudHistories.set(id, Array.isArray(data.history) ? data.history : []);
  } catch (error) {
    cloudHistoryErrors.set(id, error.message || "Cloud-Verlauf konnte nicht geladen werden.");
  } finally {
    cloudHistoryLoading.delete(id);
    renderAccounts();
  }
}

async function restoreCloudRevision(id, historyId) {
  await api(`/api/admin/accounts/${encodeURIComponent(id)}/restore-cloud`, {
    method: "POST",
    body: JSON.stringify({ historyId })
  });
  await loadAccounts();
  await loadCloudHistory(id);
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

async function setAccountWebPassword(id, password) {
  await api(`/api/admin/accounts/${encodeURIComponent(id)}/web-password`, {
    method: "POST",
    body: JSON.stringify({ password })
  });
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

createBackup?.addEventListener("click", async () => {
  try {
    backupStatus.textContent = "Backup wird erstellt ...";
    const data = await api("/api/admin/backups", {
      method: "POST",
      body: "{}"
    });
    renderBackupStatus(data.backups || [data.backup]);
    setCreateMessage("Backup wurde erstellt.", "success");
  } catch (error) {
    setCreateMessage(error.message, "error");
  }
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

featureFlagsList?.addEventListener("change", event => {
  const input = event.target.closest(".feature-flag-toggle");
  if (!input) return;
  updateFeatureFlag(input.dataset.key || "", input.checked);
});

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

forumChangelogVersion?.addEventListener("change", renderForumChangelogSource);
forumChangelogSource?.addEventListener("input", updateForumChangelogCount);
copyForumChangelog?.addEventListener("click", async () => {
  const source = forumChangelogSource?.value || "";
  const release = selectedForumChangelogRelease();
  if (!source || !release) return;
  if (source.length > FORUM_CHANGELOG_LIMIT) {
    setForumChangelogMessage("Der Quellcode überschreitet das Forenlimit von 10.000 Zeichen.", "error");
    return;
  }
  try {
    await copyText(source);
    localStorage.setItem(FORUM_CHANGELOG_PREPARED_KEY, String(release.version || ""));
    updateForumChangelogPreparedLabel();
    setForumChangelogMessage(`Changelog v${release.version} wurde als HTML-Quellcode kopiert.`, "success");
  } catch (error) {
    setForumChangelogMessage(error.message || "Changelog konnte nicht kopiert werden.", "error");
  }
});

accountsTable.addEventListener("click", async event => {
  const button = event.target.closest("button");
  if (!button) return;
  const id = rowAccountId(button);
  if (!id) return;

  try {
    if (button.classList.contains("toggle-details")) {
      const opening = !expandedAccounts.has(id);
      if (expandedAccounts.has(id)) {
        expandedAccounts.delete(id);
        cloudHistories.delete(id);
        cloudHistoryErrors.delete(id);
      } else {
        expandedAccounts.add(id);
      }
      renderAccounts();
      if (opening && !cloudHistories.has(id) && !cloudHistoryLoading.has(id)) {
        await loadCloudHistory(id);
      }
      return;
    }
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
    if (button.classList.contains("set-web-password")) {
      const panel = button.closest(".web-profile-panel");
      const password = (panel?.querySelector(".web-password-input")?.value || "").trim();
      if (password.length < 6 || password.length > 72) {
        setCreateMessage("Webpasswort muss 6 bis 72 Zeichen lang sein.", "error");
        return;
      }
      await setAccountWebPassword(id, password);
      setCreateMessage("Webpasswort wurde gesetzt.", "success");
      return;
    }
    if (button.classList.contains("clear-web-password")) {
      const row = button.closest("tr");
      const name = row?.previousElementSibling?.querySelector("td strong")?.textContent?.trim() || "diesen Account";
      if (!confirm(`Weblogin fuer ${name} entfernen? Der Spieler kann sich danach erst mit neuem Passwort wieder anmelden.`)) return;
      await runAccountAction(id, "clear-web-password");
      setCreateMessage("Weblogin wurde entfernt.", "success");
      return;
    }
    if (button.classList.contains("logout-web")) {
      await runAccountAction(id, "logout-web");
      setCreateMessage("Websession wurde abgemeldet.", "success");
      return;
    }
    if (button.classList.contains("reset-cloud-settings")) {
      const detailRow = button.closest("tr");
      const accountRow = detailRow?.previousElementSibling;
      const name = accountRow?.querySelector("td strong")?.textContent?.trim() || "diesen Account";
      if (!confirm(`Cloud-Profil von ${name} zurücksetzen? Der bisherige Stand bleibt im Verlauf wiederherstellbar.`)) return;
      await runAccountAction(id, "reset-cloud");
      await loadCloudHistory(id);
      setCreateMessage("Cloud-Profil wurde zurückgesetzt.", "success");
      return;
    }
    if (button.classList.contains("restore-cloud-settings")) {
      const historyId = Number(button.dataset.historyId);
      const detailRow = button.closest("tr");
      const accountRow = detailRow?.previousElementSibling;
      const name = accountRow?.querySelector("td strong")?.textContent?.trim() || "diesen Account";
      const revision = button.closest(".cloud-history-entry")?.querySelector("strong")?.textContent || "diesen Stand";
      if (!confirm(`${revision} für ${name} wiederherstellen? Dadurch wird eine neue Cloud-Revision erstellt.`)) return;
      await restoreCloudRevision(id, historyId);
      setCreateMessage("Cloud-Einstellungen wurden wiederhergestellt.", "success");
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
