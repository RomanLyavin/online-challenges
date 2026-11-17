(function () {
  "use strict";

  const state = {
    data: null,
    challengeFilter: "ALL",
    challengeQuery: "",
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
    const goals = challenges.reduce((sum, challenge) => {
      const list = Array.isArray(challenge && challenge.goals) ? challenge.goals : [];
      return sum + list.length;
    }, 0);

    $("#stat-users").textContent = String(num(totals.users, users.length));
    $("#stat-challenges").textContent = String(num(totals.challenges, challenges.length));
    $("#stat-goals").textContent = String(num(totals.goals, goals));

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
      return '<div class="empty">Отметок пока нет.</div>';
    }
    return '<div class="recent-list">' + recent.map((entry) => {
      const results = Array.isArray(entry.results) ? entry.results : [];
      const goals = results.map((result) => {
        const className = result.completed ? "ok" : "partial";
        const line = '<div class="' + className + '">' + escapeHtml(text(result.emoji, "•")) + " " +
          escapeHtml(text(result.goal, "Цель")) + ": " + escapeHtml(formatNumber(result.actual)) + " / " +
          escapeHtml(formatNumber(result.target)) + " " + escapeHtml(text(result.unit, "")) + "</div>";
        const confirmedBy = Array.isArray(result.confirmedBy)
          ? result.confirmedBy.map((name) => text(name, "")).filter(Boolean)
          : [];
        const confirm = confirmedBy.length
          ? '<div class="recent-confirm">✓ Подтверждено: ' + escapeHtml(confirmedBy.join(", ")) + "</div>"
          : "";
        return line + confirm;
      }).join("");
      return '<div class="recent-item">' +
        '<div class="recent-top"><span class="recent-name">' + escapeHtml(text(entry.name, "Участник")) + "</span>" +
        '<span class="recent-date">' + escapeHtml(formatShortDate(entry.date)) + "</span></div>" +
        '<div class="recent-goals">' + goals + "</div>" +
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
      '<div class="subpanel"><div class="subpanel-head"><span>Последние отметки</span><span>за последнее время</span></div>' +
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
      message + " Проверьте, что файл data/standings.json существует и содержит корректный JSON.",
      '<a class="button button-ghost" href="https://github.com/RomanLyavin/online-challenges" target="_blank" rel="noopener">Открыть репозиторий</a>'
    );
    ["#leaderboard-panel", "#challenges-panel"].forEach((selector) => {
      const panel = $(selector);
      if (panel) panel.innerHTML = html;
    });
    $("#stat-users").textContent = "—";
    $("#stat-challenges").textContent = "—";
    $("#stat-goals").textContent = "—";
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

    renderSummary();
    renderLeaderboard();
    renderChallenges();
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", init);
  } else {
    init();
  }
})();
