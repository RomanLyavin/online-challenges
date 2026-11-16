(function () {
  "use strict";

  const state = {
    data: null,
    manifest: null,
    challengeFilter: "ALL",
    challengeQuery: "",
    confirmationQuery: "",
    theme: null
  };

  const $ = (selector) => document.querySelector(selector);
  const $$ = (selector) => Array.from(document.querySelectorAll(selector));

  const STATUS = {
    ACTIVE: { label: "Идёт", className: "ACTIVE" },
    UPCOMING: { label: "Скоро", className: "UPCOMING" },
    FINISHED: { label: "Завершён", className: "FINISHED" }
  };

  const MODE = {
    DAILY: "Каждый день",
    INTERVAL: "Каждые N дней",
    TOTAL: "Всего за период"
  };

  function escapeHtml(value) {
    return String(value == null ? "" : value)
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;")
      .replace(/'/g, "&#39;");
  }

  function safeUrl(value) {
    const raw = String(value == null ? "" : value).trim();
    if (!raw) return "";
    if (/^https?:\/\//i.test(raw)) return raw;
    if (/^(\.{0,2}\/)?[A-Za-z0-9._~\-/%]+$/.test(raw)) return raw;
    return "";
  }

  function text(value, fallback) {
    const raw = String(value == null ? "" : value).trim();
    return raw || (fallback || "");
  }

  function num(value, fallback) {
    const parsed = Number(value);
    return Number.isFinite(parsed) ? parsed : (fallback || 0);
  }

  function formatNumber(value) {
    const parsed = num(value);
    return new Intl.NumberFormat("ru-RU", { maximumFractionDigits: 2 }).format(parsed);
  }

  function formatBytes(value) {
    const bytes = num(value);
    if (bytes <= 0) return "";
    const units = ["Б", "КБ", "МБ", "ГБ"];
    let size = bytes;
    let index = 0;
    while (size >= 1024 && index < units.length - 1) {
      size /= 1024;
      index += 1;
    }
    const fractionDigits = index === 0 ? 0 : 1;
    const formatted = new Intl.NumberFormat("ru-RU", {
      minimumFractionDigits: fractionDigits,
      maximumFractionDigits: fractionDigits
    }).format(size);
    return formatted + " " + units[index];
  }

  function formatDate(value) {
    if (!value) return "—";
    const date = new Date(String(value) + "T00:00:00");
    if (Number.isNaN(date.getTime())) return String(value);
    return new Intl.DateTimeFormat("ru-RU", { day: "numeric", month: "long", year: "numeric" }).format(date);
  }

  function formatShortDate(value) {
    if (!value) return "—";
    const date = new Date(String(value) + "T00:00:00");
    if (Number.isNaN(date.getTime())) return String(value);
    return new Intl.DateTimeFormat("ru-RU", { day: "numeric", month: "short" }).format(date).replace(".", "");
  }

  function formatDateTime(value) {
    if (!value) return "";
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return String(value);
    return new Intl.DateTimeFormat("ru-RU", {
      day: "numeric", month: "long", year: "numeric", hour: "2-digit", minute: "2-digit"
    }).format(date);
  }

  function dateRange(start, end) {
    if (!start && !end) return "Даты не указаны";
    if (!end || start === end) return formatDate(start);
    return formatShortDate(start) + " — " + formatDate(end);
  }

  function plural(count, one, few, many) {
    const value = Math.abs(count) % 100;
    const last = value % 10;
    if (value > 10 && value < 20) return many;
    if (last > 1 && last < 5) return few;
    if (last === 1) return one;
    return many;
  }

  function initials(name) {
    const parts = text(name, "Участник").split(/\s+/).filter(Boolean).slice(0, 2);
    return parts.map((part) => part.charAt(0).toUpperCase()).join("") || "Я";
  }

  function avatar(name, url, extraClass) {
    const safe = safeUrl(url);
    const image = safe
      ? '<img src="' + escapeHtml(safe) + '" alt="" loading="lazy" onerror="this.remove()">'
      : "";
    return '<span class="avatar ' + escapeHtml(extraClass || "") + '">' + escapeHtml(initials(name)) + image + "</span>";
  }

  function medalEmoji(place) {
    if (Number(place) === 1) return "🥇";
    if (Number(place) === 2) return "🥈";
    if (Number(place) === 3) return "🥉";
    return "";
  }

  function statusOf(challenge) {
    const key = String(challenge && challenge.status || "").toUpperCase();
    return STATUS[key] || { label: text(challenge && challenge.statusLabel, "Статус"), className: "FINISHED" };
  }

  function proofTypeLabel(type) {
    return String(type || "").toUpperCase() === "VIDEO" ? "видео" : "фото";
  }

  function userByCode(code) {
    if (!state.data || !Array.isArray(state.data.users)) return null;
    return state.data.users.find((user) => user && user.code === code) || null;
  }

  function emptyState(emoji, title, message, action) {
    return '<div class="empty">' +
      '<span class="empty-emoji" aria-hidden="true">' + escapeHtml(emoji) + "</span>" +
      "<h3>" + escapeHtml(title) + "</h3>" +
      "<p>" + escapeHtml(message) + "</p>" +
      (action || "") +
      "</div>";
  }

  function renderSummary() {
    const data = state.data || {};
    const totals = data.totals || {};
    const users = Array.isArray(data.users) ? data.users : [];
    const challenges = Array.isArray(data.challenges) ? data.challenges : [];
    const manifestTotal = state.manifest && Number(state.manifest.total);
    const confirmations = Number.isFinite(manifestTotal) && manifestTotal > 0
      ? manifestTotal
      : num(totals.confirmations);

    $("#stat-users").textContent = String(num(totals.users, users.length));
    $("#stat-challenges").textContent = String(num(totals.challenges, challenges.length));
    $("#stat-confirmations").textContent = String(confirmations);

    const generated = data.generatedAt ? "Обновлено " + formatDateTime(data.generatedAt) : "";
    $("#generated-note").textContent = generated
      ? generated + " · данные синхронизированы с GitHub"
      : "Данных пока нет — подключите приложение и выполните синхронизацию.";

    const repoUrl = safeUrl(data.repoUrl) || "https://github.com/RomanLyavin/online-challenges";
    const apkUrl = repoUrl.replace(/\/+$/, "") + "/releases/latest";
    ["#repo-link", "#repo-link-2", "#footer-repo"].forEach((selector) => {
      const element = $(selector);
      if (element) element.href = repoUrl;
    });
    ["#apk-link", "#apk-link-2"].forEach((selector) => {
      const element = $(selector);
      if (element) element.href = apkUrl;
    });
  }

  function renderLeaderboard() {
    const panel = $("#leaderboard-panel");
    if (!panel) return;
    const users = state.data && Array.isArray(state.data.users) ? state.data.users : [];
    if (!users.length) {
      panel.innerHTML = emptyState(
        "🏅",
        "Рейтинг пока пуст",
        "Он появится после первой синхронизации реальных участников. Демо-данные приложения на сайт не попадают.",
        '<a class="button button-primary" href="https://github.com/RomanLyavin/online-challenges/releases/latest" target="_blank" rel="noopener">Скачать приложение</a>'
      );
      return;
    }

    const rows = users.map((user, index) => {
      const rank = index + 1;
      const rankClass = rank <= 3 ? " top-" + rank : "";
      const medals = [];
      if (num(user.gold) > 0) medals.push('<span class="medal-pill">🥇 ' + num(user.gold) + "</span>");
      if (num(user.silver) > 0) medals.push('<span class="medal-pill">🥈 ' + num(user.silver) + "</span>");
      if (num(user.bronze) > 0) medals.push('<span class="medal-pill">🥉 ' + num(user.bronze) + "</span>");
      return "<tr>" +
        '<td class="rank' + rankClass + '">#' + rank + "</td>" +
        '<td><div class="person">' + avatar(user.name, user.avatarUrl) +
        '<div><div class="person-name">' + escapeHtml(text(user.name, "Участник")) + "</div>" +
        '<div class="person-code">' + escapeHtml(text(user.code, "")) + "</div></div></div></td>" +
        '<td class="points-cell">' + num(user.points) + " <small>из " + num(user.maxPoints) + "</small></td>" +
        '<td class="medals-col"><div class="medal-counts">' + (medals.length ? medals.join("") : '<span class="medal-pill">—</span>') + "</div></td>" +
        "</tr>";
    }).join("");

    panel.innerHTML = '<div class="leader-table-wrap"><table class="leaderboard">' +
      "<thead><tr><th>#</th><th>Участник</th><th>Баллы</th><th class=\"medals-col\">Медали</th></tr></thead>" +
      "<tbody>" + rows + "</tbody></table></div>";
  }

  function goalPill(goal) {
    const proof = String(goal.proofType || "").toUpperCase();
    const proofLabel = proof === "VIDEO" ? "видео" : proof === "ANY" ? "фото/видео" : "фото";
    return '<span class="goal-pill">' +
      '<span aria-hidden="true">' + escapeHtml(text(goal.emoji, "🎯")) + "</span>" +
      "<span><b>" + escapeHtml(text(goal.name, "Цель")) + "</b> " +
      escapeHtml(formatNumber(goal.target)) + " " + escapeHtml(text(goal.unit, "")) + "</span>" +
      '<span class="proof">' + escapeHtml(proofLabel) + "</span>" +
      "</span>";
  }

  function podiumHtml(challenge) {
    const podium = Array.isArray(challenge.podium) ? challenge.podium.slice(0, 3) : [];
    if (!podium.length) return "";
    const items = podium.map((entry) => {
      const place = num(entry.place, 0);
      const medal = text(entry.medal, medalEmoji(place));
      return '<div class="podium-item place-' + place + '">' +
        '<span class="podium-medal" aria-hidden="true">' + escapeHtml(medal || "🏅") + "</span>" +
        '<div class="podium-body"><div class="podium-name">' + escapeHtml(text(entry.name, "Участник")) + "</div>" +
        '<div class="podium-points">' + num(entry.points) + " " + plural(num(entry.points), "балл", "балла", "баллов") + " · " + place + " место</div></div>" +
        "</div>";
    }).join("");
    return '<div class="podium">' + items + "</div>";
  }

  function participantsHtml(challenge) {
    const participants = Array.isArray(challenge.participants) ? challenge.participants : [];
    if (!participants.length) {
      return '<div class="empty">Пока нет участников.</div>';
    }
    const rows = participants.map((participant, index) => {
      const place = num(participant.place, index + 1);
      const percent = Math.min(100, Math.max(0, num(participant.progressPercent)));
      return "<tr>" +
        '<td class="place">' + (place <= 3 && participant.medal ? escapeHtml(participant.medal) : "#" + place) + "</td>" +
        '<td><div class="person">' + avatar(participant.name, participant.avatarUrl) +
        '<div><div class="person-name">' + escapeHtml(text(participant.name, "Участник")) + "</div>" +
        '<div class="bar" aria-hidden="true"><span style="width:' + percent + '%"></span></div></div></div></td>' +
        '<td class="num">' + num(participant.points) + " / " + num(participant.maxPoints) + "</td>" +
        "</tr>";
    }).join("");
    return '<table class="participant-table"><tbody>' + rows + "</tbody></table>";
  }

  function recentHtml(challenge) {
    const recent = Array.isArray(challenge.recent) ? challenge.recent.slice(0, 6) : [];
    if (!recent.length) {
      return '<div class="empty">Подтверждённых отметок пока нет.</div>';
    }
    return '<div class="recent-list">' + recent.map((entry) => {
      const results = Array.isArray(entry.results) ? entry.results : [];
      const goals = results.map((result) => {
        const className = result.completed ? "ok" : "partial";
        return '<div class="' + className + '">' + escapeHtml(text(result.emoji, "•")) + " " +
          escapeHtml(text(result.goal, "Цель")) + ": " + escapeHtml(formatNumber(result.actual)) + " / " +
          escapeHtml(formatNumber(result.target)) + " " + escapeHtml(text(result.unit, "")) + "</div>";
      }).join("");
      const proofs = results.flatMap((result) => Array.isArray(result.proofs) ? result.proofs : []);
      const proofLinks = proofs.map((proof) => {
        const url = safeUrl(proof.url);
        if (!url) return "";
        const isVideo = String(proof.type || "").toUpperCase() === "VIDEO";
        return '<a class="proof-link" href="' + escapeHtml(url) + '" target="_blank" rel="noopener">' +
          (isVideo ? "🎬 Видео" : "📷 Фото") + "</a>";
      }).join("");
      return '<div class="recent-item">' +
        '<div class="recent-top"><span class="recent-name">' + escapeHtml(text(entry.name, "Участник")) + "</span>" +
        '<span class="recent-date">' + escapeHtml(formatShortDate(entry.date)) + "</span></div>" +
        '<div class="recent-goals">' + goals + "</div>" +
        (proofLinks ? '<div class="recent-proofs">' + proofLinks + "</div>" : "") +
        "</div>";
    }).join("") + "</div>";
  }

  function challengeCard(challenge) {
    const status = statusOf(challenge);
    const goals = Array.isArray(challenge.goals) ? challenge.goals : [];
    const participantCount = num(challenge.participantCount);
    const modeLabel = text(challenge.modeLabel, MODE[String(challenge.mode || "").toUpperCase()] || "Челлендж");
    const interval = String(challenge.mode || "").toUpperCase() === "INTERVAL" && num(challenge.intervalDays) > 1
      ? " · каждые " + num(challenge.intervalDays) + " дн."
      : "";
    return '<article class="challenge-card" data-status="' + escapeHtml(status.className) + '">' +
      '<div class="challenge-head"><div>' +
      '<h3 class="challenge-title">' + escapeHtml(text(challenge.title, "Челлендж")) + "</h3>" +
      (challenge.description ? '<p class="challenge-description">' + escapeHtml(challenge.description) + "</p>" : "") +
      '<div class="challenge-meta">' +
      '<span class="tag">📅 ' + escapeHtml(dateRange(challenge.startDate, challenge.endDate)) + "</span>" +
      '<span class="tag">🎯 ' + escapeHtml(modeLabel + interval) + "</span>" +
      '<span class="tag">👥 ' + participantCount + " " + plural(participantCount, "участник", "участника", "участников") + "</span>" +
      "</div></div>" +
      '<span class="status-pill ' + escapeHtml(status.className) + '">' + escapeHtml(status.label) + "</span>" +
      "</div>" +
      podiumHtml(challenge) +
      '<div class="challenge-body">' +
      (goals.length ? '<div class="goal-list">' + goals.map(goalPill).join("") + "</div>" : "") +
      '<div class="challenge-columns">' +
      '<div class="subpanel"><div class="subpanel-head"><span>Участники и баллы</span><span>' + participantCount + "</span></div>" +
      participantsHtml(challenge) + "</div>" +
      '<div class="subpanel"><div class="subpanel-head"><span>Последние отметки</span><span>с подтверждением</span></div>' +
      recentHtml(challenge) + "</div>" +
      "</div></div></article>";
  }

  function renderChallenges() {
    const panel = $("#challenges-panel");
    if (!panel) return;
    const challenges = state.data && Array.isArray(state.data.challenges) ? state.data.challenges : [];
    const query = state.challengeQuery.trim().toLowerCase();
    const filtered = challenges.filter((challenge) => {
      const status = String(challenge.status || "").toUpperCase();
      if (state.challengeFilter !== "ALL" && status !== state.challengeFilter) return false;
      if (!query) return true;
      const haystack = [challenge.title, challenge.description, challenge.statusLabel].join(" ").toLowerCase();
      return haystack.includes(query);
    });

    if (!challenges.length) {
      panel.innerHTML = emptyState(
        "🏁",
        "Челленджей пока нет",
        "Создайте челлендж в приложении и синхронизируйте его с GitHub — он появится здесь вместе с результатами.",
        '<a class="button button-soft" href="https://github.com/RomanLyavin/online-challenges#readme" target="_blank" rel="noopener">Как подключить</a>'
      );
      return;
    }
    if (!filtered.length) {
      panel.innerHTML = emptyState("🔎", "Ничего не найдено", "Попробуйте другой статус или поисковый запрос.");
      return;
    }
    panel.innerHTML = '<div class="challenge-list">' + filtered.map(challengeCard).join("") + "</div>";
  }

  function confirmationCard(file) {
    const isVideo = String(file.type || "").toUpperCase() === "VIDEO";
    const url = safeUrl(file.url) || safeUrl(file.rawUrl);
    const title = text(file.goalName, "Цель");
    const challenge = text(file.challengeTitle, "Челлендж");
    const thumbnail = isVideo
      ? '<span aria-hidden="true">🎬</span>'
      : '<span aria-hidden="true">📷</span>';
    const openLink = url
      ? '<a class="conf-open" href="' + escapeHtml(url) + '" target="_blank" rel="noopener">Открыть ' + escapeHtml(proofTypeLabel(file.type)) + " →</a>"
      : '<span class="conf-sub">Файл недоступен</span>';
    const size = formatBytes(file.size);
    return '<article class="conf-card">' +
      '<a class="conf-thumb" ' + (url ? 'href="' + escapeHtml(url) + '" target="_blank" rel="noopener"' : "") + ">" +
      (isVideo || !url
        ? thumbnail
        : '<img src="' + escapeHtml(url) + '" alt="' + escapeHtml(title + " — " + challenge) + '" loading="lazy" onerror="this.parentNode.innerHTML=\'📷\'">') +
      "</a>" +
      '<div class="conf-info">' +
      '<div class="conf-title">' + escapeHtml(title) + "</div>" +
      '<div class="conf-sub">' + escapeHtml(challenge) + "</div>" +
      '<div class="conf-sub">' + escapeHtml(formatDate(file.date)) + (size ? " · " + escapeHtml(size) : "") + "</div>" +
      '<div class="conf-actions"><span class="tag">' + (isVideo ? "🎬 Видео" : "📷 Фото") + "</span>" + openLink + "</div>" +
      "</div></article>";
  }

  function renderConfirmations() {
    const panel = $("#confirmations-panel");
    const counter = $("#confirmation-counter");
    if (!panel) return;
    const files = state.manifest && Array.isArray(state.manifest.files) ? state.manifest.files : [];
    const query = state.confirmationQuery.trim().toLowerCase();
    const filtered = files.filter((file) => {
      if (!query) return true;
      const haystack = [file.userName, file.userCode, file.challengeTitle, file.goalName, file.date]
        .join(" ").toLowerCase();
      return haystack.includes(query);
    });

    if (counter) {
      counter.textContent = filtered.length
        ? filtered.length + " " + plural(filtered.length, "файл", "файла", "файлов")
        : "";
    }

    if (!files.length) {
      panel.innerHTML = emptyState(
        "📎",
        "Подтверждений пока нет",
        "Фото и видео сохраняются в папки confirmations/<код участника>/ после синхронизации из приложения."
      );
      return;
    }
    if (!filtered.length) {
      panel.innerHTML = emptyState("🔎", "Ничего не найдено", "Попробуйте изменить поисковый запрос.");
      return;
    }

    const groups = new Map();
    filtered.forEach((file) => {
      const code = text(file.userCode, "unknown");
      if (!groups.has(code)) groups.set(code, []);
      groups.get(code).push(file);
    });

    const sorted = Array.from(groups.entries()).sort((a, b) => {
      const nameA = text(a[1][0] && a[1][0].userName, a[0]);
      const nameB = text(b[1][0] && b[1][0].userName, b[0]);
      return nameA.localeCompare(nameB, "ru");
    });

    panel.innerHTML = sorted.map(([code, groupFiles]) => {
      const user = userByCode(code);
      const name = text(groupFiles[0] && groupFiles[0].userName, user && user.name, "Участник");
      const avatarUrl = user && user.avatarUrl;
      return '<section class="conf-group">' +
        '<div class="conf-group-head"><div class="conf-user">' + avatar(name, avatarUrl) +
        "<div>" + escapeHtml(name) + "<small>" + escapeHtml(code) + "</small></div></div>" +
        '<span class="counter">' + groupFiles.length + " " + plural(groupFiles.length, "файл", "файла", "файлов") + "</span></div>" +
        '<div class="conf-grid">' + groupFiles.map(confirmationCard).join("") + "</div>" +
        "</section>";
    }).join("");
  }

  function bindEvents() {
    const filters = $("#challenge-filters");
    if (filters) {
      filters.addEventListener("click", (event) => {
        const button = event.target.closest("[data-filter]");
        if (!button) return;
        state.challengeFilter = button.getAttribute("data-filter") || "ALL";
        $$("#challenge-filters .chip").forEach((chip) => {
          chip.classList.toggle("is-active", chip === button);
        });
        renderChallenges();
      });
    }

    const challengeSearch = $("#challenge-search");
    if (challengeSearch) {
      challengeSearch.addEventListener("input", () => {
        state.challengeQuery = challengeSearch.value || "";
        renderChallenges();
      });
    }

    const confirmationSearch = $("#confirmation-search");
    if (confirmationSearch) {
      confirmationSearch.addEventListener("input", () => {
        state.confirmationQuery = confirmationSearch.value || "";
        renderConfirmations();
      });
    }

    const toggle = $("#theme-toggle");
    if (toggle) {
      toggle.addEventListener("click", () => {
        const next = document.documentElement.getAttribute("data-theme") === "dark" ? "light" : "dark";
        applyTheme(next, true);
      });
    }
  }

  function applyTheme(theme, persist) {
    state.theme = theme === "dark" ? "dark" : "light";
    document.documentElement.setAttribute("data-theme", state.theme);
    if (persist) {
      try {
        localStorage.setItem("online-challenges-theme", state.theme);
      } catch (error) {
        // localStorage может быть недоступен — тема просто не запомнится.
      }
    }
  }

  function initTheme() {
    let stored = null;
    try {
      stored = localStorage.getItem("online-challenges-theme");
    } catch (error) {
      stored = null;
    }
    if (stored === "dark" || stored === "light") {
      applyTheme(stored, false);
    } else {
      const prefersDark = window.matchMedia && window.matchMedia("(prefers-color-scheme: dark)").matches;
      applyTheme(prefersDark ? "dark" : "light", false);
    }
  }

  function showToast(message) {
    const toast = $("#toast");
    if (!toast) return;
    toast.textContent = message;
    toast.classList.add("is-visible");
    window.clearTimeout(showToast.timer);
    showToast.timer = window.setTimeout(() => {
      toast.classList.remove("is-visible");
    }, 4200);
  }

  function renderFatal(message) {
    const html = emptyState(
      "⚠️",
      "Не удалось загрузить данные",
      message + " Проверьте, что файлы data/standings.json и confirmations/manifest.json существуют и содержат корректный JSON.",
      '<a class="button button-ghost" href="https://github.com/RomanLyavin/online-challenges" target="_blank" rel="noopener">Открыть репозиторий</a>'
    );
    ["#leaderboard-panel", "#challenges-panel", "#confirmations-panel"].forEach((selector) => {
      const panel = $(selector);
      if (panel) panel.innerHTML = html;
    });
    $("#stat-users").textContent = "—";
    $("#stat-challenges").textContent = "—";
    $("#stat-confirmations").textContent = "—";
    $("#generated-note").textContent = "Данные недоступны.";
  }

  async function loadJson(path) {
    const response = await fetch(path + "?t=" + Date.now(), { cache: "no-store" });
    if (!response.ok) throw new Error(path + ": HTTP " + response.status);
    return response.json();
  }

  async function init() {
    initTheme();
    bindEvents();
    $("#footer-year").textContent = String(new Date().getFullYear());

    try {
      state.data = await loadJson("data/standings.json");
    } catch (error) {
      renderFatal("Не удалось прочитать standings.json.");
      showToast("Проверьте data/standings.json");
      return;
    }

    try {
      state.manifest = await loadJson("confirmations/manifest.json");
    } catch (error) {
      state.manifest = { total: 0, files: [] };
    }

    renderSummary();
    renderLeaderboard();
    renderChallenges();
    renderConfirmations();
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", init);
  } else {
    init();
  }
})();
