"use strict";

const rosterRoot = document.getElementById("roster");
const statusElement = document.getElementById("roster-status");
const slotCount = document.getElementById("slot-count");
const updatedAt = document.getElementById("updated-at");
const rosterType = document.body.dataset.roster === "swat" ? "swat" : "police";
const rosterEndpoint = rosterType === "swat"
  ? "/api/teamspeak/swat-roster.json"
  : "/api/teamspeak/police-roster.json";

function element(tag, className, text) {
  const node = document.createElement(tag);
  if (className) node.className = className;
  if (text !== undefined) node.textContent = text;
  return node;
}

function memberCard(member) {
  const card = element("article", "member-card");
  const head = element("img", "member-head");
  head.src = member.headUrl;
  head.alt = `Minecraft-Kopf von ${member.username}`;
  head.width = 78;
  head.height = 78;
  head.loading = "lazy";

  const meta = element("div", "member-meta");
  meta.append(element("h3", "member-name", member.username));
  meta.append(element("p", "member-rank", member.rankName));
  const tags = element("div", "member-tags");
  tags.append(element("span", "member-tag", member.unit));
  if (rosterType !== "swat") {
    tags.append(element("span", "member-tag rank", `RANG ${member.rankNumber}`));
  }
  meta.append(tags);
  card.append(head, meta);
  return card;
}

function renderRoster(data) {
  rosterRoot.replaceChildren();
  for (const group of data.groups) {
    const section = element("section", "rank-section");
    section.dataset.group = group.key;
    const heading = element("h2", "rank-heading");
    heading.append(element("span", "", group.label));
    heading.append(element("span", "", String(group.members.length)));
    const grid = element("div", "member-grid");
    for (const member of group.members) grid.append(memberCard(member));
    section.append(heading, grid);
    rosterRoot.append(section);
  }

  slotCount.textContent = `${data.count} / ${data.slotLimit}`;
  updatedAt.textContent = data.generatedAt
    ? `Stand ${new Intl.DateTimeFormat("de-DE", {
      dateStyle: "medium",
      timeStyle: "short"
    }).format(new Date(data.generatedAt))}`
    : "Noch nicht synchronisiert";
  statusElement.hidden = true;
  rosterRoot.hidden = false;
  document.querySelector(".roster-shell").setAttribute("aria-busy", "false");
}

async function loadRoster() {
  try {
    const response = await fetch(rosterEndpoint, {
      headers: { Accept: "application/json" }
    });
    if (!response.ok) throw new Error(`HTTP ${response.status}`);
    const data = await response.json();
    if (!data.ok || !Array.isArray(data.groups)) throw new Error("Ungültige Serverantwort");
    renderRoster(data);
  } catch (error) {
    console.error("Polizeiliste konnte nicht geladen werden", error);
    statusElement.classList.add("error");
    statusElement.textContent = "Die Mitgliederübersicht ist gerade nicht erreichbar. Bitte später erneut versuchen.";
    document.querySelector(".roster-shell").setAttribute("aria-busy", "false");
  }
}

loadRoster();
