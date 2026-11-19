(() => {
  "use strict";
  const SUBJECTS = [
    { id: "english", name: "英语", hint: "单词 · 阅读", color: "#b99b65" },
    { id: "calculus", name: "高等数学", hint: "微积分", color: "#8ca378" },
    { id: "linear", name: "线性代数", hint: "矩阵 · 向量", color: "#ba8a70" },
    { id: "ds", name: "数据结构", hint: "408 / 01", color: "#62826b" },
    { id: "co", name: "计算机组成原理", hint: "408 / 02", color: "#8ba5af" },
    { id: "os", name: "操作系统", hint: "408 / 03", color: "#a498ad" },
    { id: "net", name: "计算机网络", hint: "408 / 04", color: "#caab83" },
  ];
  const icon = (name, cls = "") =>
    `<svg class="${cls}" aria-hidden="true"><use href="#i-${name}"/></svg>`;
  const escape = (value) =>
    String(value).replace(
      /[&<>"']/g,
      (char) =>
        ({
          "&": "&amp;",
          "<": "&lt;",
          ">": "&gt;",
          '"': "&quot;",
          "'": "&#39;",
        })[char],
    );
  const subject = (id) =>
    SUBJECTS.find((item) => item.id === id) || SUBJECTS[0];
  const dateKey = (value) => {
    const d = new Date(value);
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
  };
  const today = () => dateKey(Date.now());
  const formatDuration = (seconds) =>
    seconds < 60
      ? `${seconds} 秒`
      : `${Math.floor(seconds / 60)} 分钟${seconds % 60 ? ` ${seconds % 60} 秒` : ""}`;
  const studyTime = (seconds) => {
    const h = Math.floor(seconds / 3600);
    const m = Math.floor((seconds % 3600) / 60);
    return h ? `${h} 小时${m ? ` ${m} 分钟` : ""}` : formatDuration(seconds);
  };
  const compactStudyTime = (seconds) => {
    const h = Math.floor(seconds / 3600);
    const m = Math.floor((seconds % 3600) / 60);
    return h
      ? `${h}时${m ? `${m}分` : ""}`
      : seconds < 60
        ? `${seconds}秒`
        : `${m}分`;
  };
  const clock = (ms) => {
    const seconds = Math.floor(ms / 1000);
    return [
      Math.floor(seconds / 3600),
      Math.floor(seconds / 60) % 60,
      seconds % 60,
    ]
      .map((v) => String(v).padStart(2, "0"))
      .join(":");
  };
  const totalSeconds = (list) =>
    list.reduce((sum, item) => sum + item.seconds, 0);
  function makeState(demo) {
    const records = [];
    if (demo) {
      const notes = [
        "阅读精读 · 长难句",
        "极限与连续",
        "线性方程组",
        "二叉树与遍历",
        "指令系统",
        "进程与线程",
        "网络分层模型",
      ];
      const plan = [
        [0, "english", 35],
        [0, "calculus", 50],
        [0, "ds", 45],
        [1, "linear", 40],
        [1, "os", 50],
        [1, "english", 30],
        [2, "co", 60],
        [2, "net", 40],
        [3, "ds", 70],
        [3, "calculus", 45],
        [4, "english", 45],
        [4, "linear", 35],
        [5, "os", 50],
        [5, "calculus", 55],
        [6, "net", 45],
        [6, "co", 35],
      ];
      plan.forEach(([ago, id, minutes], i) => {
        const end = new Date();
        end.setDate(end.getDate() - ago);
        end.setHours(9 + (i % 3) * 2, 50, 0, 0);
        // Today's sample sessions always precede the preview's opening time.
        const endedAt =
          ago === 0 ? Date.now() - (3 - i) * 3600000 : end.getTime();
        records.push({
          id: `sample-${i}`,
          subject: id,
          seconds: minutes * 60,
          endedAt,
          date: dateKey(endedAt),
          note: notes[SUBJECTS.findIndex((s) => s.id === id)],
          demo: true,
        });
      });
    }
    return {
      version: 1,
      selected: "ds",
      goal: 45,
      dailyGoal: 180,
      active: null,
      records,
      water: demo ? 6 : 0,
      seeds: demo ? 2 : 1,
      planted: demo,
      growth: demo ? 2 : 0,
      trees: demo ? 3 : 0,
      seedDays: [],
      earnedWater: 0,
    };
  }
  let mode = "demo";
  let storageAvailable = true;
  try {
    mode =
      localStorage.getItem("pkm-study-preview-mode") === "personal"
        ? "personal"
        : "demo";
  } catch {
    storageAvailable = false;
  }
  const memory = {};
  function readState() {
    try {
      const raw = storageAvailable
        ? localStorage.getItem(`pkm-study-preview-v1-${mode}`)
        : memory[mode];
      const value = raw ? JSON.parse(raw) : null;
      if (
        value &&
        value.version === 1 &&
        Array.isArray(value.records) &&
        Number.isFinite(value.water) &&
        Array.isArray(value.seedDays)
      )
        return value;
    } catch {
      /* A damaged preview starts with fresh sample data. */
    }
    return makeState(mode === "demo");
  }
  let state = readState();
  function persist() {
    memory[mode] = JSON.stringify(state);
    if (storageAvailable)
      try {
        localStorage.setItem(`pkm-study-preview-v1-${mode}`, memory[mode]);
        localStorage.setItem("pkm-study-preview-mode", mode);
      } catch {
        storageAvailable = false;
      }
    updateReviewTools();
  }
  function updateReviewTools() {
    document.getElementById("mode-toggle").textContent =
      mode === "demo" ? "查看空白状态 ↗" : "返回示例数据 ↗";
    document.getElementById("demo-session").hidden = mode !== "demo";
    document.getElementById("storage-hint").textContent =
      `${mode === "demo" ? "示例数据" : "空白体验 · 独立记录"} · ${storageAvailable ? "仅保存在当前浏览器" : "浏览器存储不可用，刷新后不保留"}`;
  }
  const todayRecords = () =>
    state.records.filter((item) => item.date === today());
  const elapsed = () =>
    state.active
      ? state.active.elapsedMs +
        (state.active.startedAt === null
          ? 0
          : Math.max(0, Date.now() - state.active.startedAt))
      : 0;
  const phones = [...document.querySelectorAll(".phone")];
  const views = phones.map((phone) => ({
    phone,
    page: phone.dataset.page,
    chart: "ring",
    period: "week",
  }));
  const sheet = document.getElementById("sheet");
  let sheetType = null;
  let toastTimeout;
  let gardenMotion = null;
  const gardenBusy = () => gardenMotion && gardenMotion.kind !== "greet";
  function animateGarden(kind, from, to, done) {
    const motion = {
      kind,
      from,
      to,
      startedAt: performance.now(),
      duration: window.matchMedia("(prefers-reduced-motion: reduce)").matches
        ? 450
        : kind === "water"
          ? 3100
          : 1500,
    };
    gardenMotion = motion;
    renderAll();
    setTimeout(() => {
      if (gardenMotion !== motion) return;
      gardenMotion = null;
      renderAll();
      done?.();
    }, motion.duration);
  }
  function toast(message) {
    const node = document.getElementById("toast");
    node.textContent = message;
    node.classList.add("visible");
    clearTimeout(toastTimeout);
    toastTimeout = setTimeout(() => node.classList.remove("visible"), 3500);
  }
  function showSheet(type, markup) {
    sheetType = type;
    document.getElementById("sheet-content").innerHTML = markup;
    if (!sheet.open) sheet.showModal();
  }
  function closeSheet() {
    sheet.close();
    sheet.classList.remove("subject-sheet");
    sheetType = null;
  }
  document.querySelector(".close-sheet").addEventListener("click", closeSheet);
  sheet.addEventListener("click", (event) => {
    if (event.target === sheet) {
      const r = sheet.getBoundingClientRect();
      if (
        event.clientX < r.left ||
        event.clientX > r.right ||
        event.clientY < r.top ||
        event.clientY > r.bottom
      )
        closeSheet();
    }
  });
  sheet.addEventListener("close", () => {
    sheet.classList.remove("subject-sheet");
    sheetType = null;
  });
  const studyNavigation = [
    ["focus", "book", "专注"],
    ["stats", "chart", "学习足迹"],
    ["garden", "seed", "小林地"],
  ];
  const lifeNavigation = [
    ["life-details", "list", "明细"],
    ["life-charts", "chart", "图表"],
    ["life-record", "plus", "记账"],
    ["life-savings", "seed", "攒钱"],
    ["life-profile", "user", "我的"],
  ];
  function navigation(view, isLife) {
    return `<nav class="app-nav ${isLife ? "life-nav" : ""}" aria-label="${isLife ? "生活" : "学习"}模块导航">${(isLife ? lifeNavigation : studyNavigation).map(([id, image, label]) => `<button class="nav-button ${view.page === id ? "active" : ""}" data-action="navigate" data-value="${id}" ${view.page === id ? 'aria-current="page"' : ""}>${icon(image)}<span>${label}</span></button>`).join("")}</nav>`;
  }
  function render(view) {
    const scrollTop = view.phone.querySelector(".page-scroll")?.scrollTop || 0;
    const isHub = view.page === "hub",
      isLife = view.page.startsWith("life-");
    const existingFrame = view.phone.querySelector(".life-frame");
    if (isLife && existingFrame) {
      if (existingFrame.dataset.screen !== view.page.slice(5)) {
        window.ForestHome.selectLife(existingFrame, view.page.slice(5));
        view.phone.querySelector(".app-nav").outerHTML = navigation(view, true);
      }
      return;
    }
    view.garden?.destroy();
    view.garden = null;
    view.phone.classList.toggle("is-hub", isHub);
    view.phone.classList.toggle("is-life", isLife);
    const topbar = isHub
      ? `<span class="app-wordmark">${icon("leaf")}芽叶记</span><span class="module-tag">你的生活集合</span>`
      : `<button class="return-forest" data-action="navigate" data-value="hub" aria-label="返回森林入口"><img src="../app/src/main/res/drawable-nodpi/return_home_door.webp" alt=""/><span>森林小窝</span></button><span class="module-tag">${isLife ? "生活小屋 · 账本" : "学习小屋"}</span>`;
    const content = isHub
      ? window.ForestHome.portal({
          active: state.active,
          subject: subject(state.active?.subject || state.selected).name,
          clock: clock(elapsed()),
        })
      : isLife
        ? '<iframe class="life-frame" src="about:blank" title="生活小屋 · 记账页面设计"></iframe>'
        : view.page === "focus"
          ? focusPage()
          : view.page === "stats"
            ? statsPage(view)
            : gardenPage();
    view.phone.innerHTML = `<div class="status-bar"><span>9:41</span><span class="status-icons"><span class="signal"></span><span>◔</span><span class="battery"></span></span></div>${isHub || isLife ? `<div class="app-topbar">${topbar}</div>` : ""}<div class="page-scroll">${!isHub && !isLife ? `<div class="app-topbar">${topbar}</div>` : ""}${content}</div>${isHub ? '<div class="portal-home-indicator" aria-hidden="true"></div>' : navigation(view, isLife)}`;
    view.phone.querySelector(".page-scroll").scrollTop = scrollTop;
    const canvas = view.phone.querySelector(".garden-canvas");
    if (canvas)
      view.garden = new window.StudyGarden(canvas, {
        planted: state.planted,
        growth: state.growth,
        motion: gardenMotion,
      });
    const frame = view.phone.querySelector(".life-frame");
    if (frame)
      window.ForestHome.attachLife(frame, view.page.slice(5), (value) =>
        actions.navigate(view, value),
      );
  }
  function renderAll() {
    views.forEach(render);
    updateClock();
    updateReviewTools();
  }
  function subjectButton(s) {
    return `<button class="subject-button ${state.selected === s.id ? "active" : ""}" style="--subject:${s.color}" data-action="subject" data-value="${s.id}" aria-pressed="${state.selected === s.id}" ${state.active ? "disabled" : ""}>${icon("book")}<span>${s.name}<small>${s.hint}</small></span>${state.selected === s.id ? '<i class="selected-dot"></i>' : ""}</button>`;
  }
  function clockDial() {
    const ticks = Array.from({length:60},(_,i)=>`<path transform="rotate(${i*6} 110 110)" d="M110 17v${i%5===0?7:3}" stroke="${i%5===0?'#9b9f7d':'#d4d4bd'}" stroke-width="${i%5===0?1.3:.7}"/>`).join('');
    return `<svg class="clock-dial" viewBox="0 0 220 220" aria-hidden="true"><circle cx="110" cy="112" r="103" fill="#e4dfcd" stroke="none"/><circle cx="110" cy="110" r="103" fill="#f9f7ed" stroke="#c3c8ab"/><circle cx="110" cy="110" r="98" fill="none" stroke="#d4d4bd"/>${ticks}<circle cx="110" cy="110" r="78" stroke="#e0e5d2" stroke-width="2"/><circle class="dial-progress" cx="110" cy="110" r="78" stroke="#456444" stroke-width="2" stroke-dasharray="0 491"/><circle cx="110" cy="52" r="2" fill="#b8a171" stroke="none"/></svg>`;
  }
  function focusPage() {
    const current = subject(state.active?.subject || state.selected);
    const total = totalSeconds(todayRecords());
    const goal = state.active?.goal || state.goal;
    const status = state.active ? (state.active.startedAt === null ? '休息一下' : '正在专注') : '准备好就开始';
    return `<div class="hero"><div class="hero-copy"><div class="date-note">${new Date().getMonth()+1} 月 ${new Date().getDate()} 日</div><h2>森林自习室</h2><p>让知识和小树，慢慢长大。</p></div></div><div class="content"><section class="focus-card"><button class="subject-selector" data-action="subject-sheet" ${state.active?'disabled':''}>${icon('book')}<strong>${current.name}</strong><span>${state.active?'本次科目':'切换科目 ›'}</span></button><div class="timer" data-status="${status}">${clockDial()}<div class="clock" role="timer" aria-label="本次学习计时">${clock(elapsed())}</div><div class="timer-subject">目标 ${goal} 分钟</div></div><div class="goal-picker"><span>本次目标</span>${[25,45,60].map(m=>`<button class="goal-chip ${goal===m?'active':''}" data-action="session-goal" data-value="${m}" aria-pressed="${goal===m}" ${state.active?'disabled':''}>${m} 分钟</button>`).join('')}</div>${state.active?`<div class="timer-actions"><button class="primary-button" data-action="${state.active.startedAt===null?'resume':'pause'}">${icon(state.active.startedAt===null?'play':'pause')}${state.active.startedAt===null?'继续学习':'暂停一下'}</button><button class="secondary-button" data-action="finish">完成学习</button></div>`:`<button class="primary-button" data-action="start">${icon('play','play-icon')}开始专注，种下今天</button>`}<p class="gentle-note">${state.active?'暂停时间不计入时长 · 完成后可以切换科目':'正向计时 · 随时暂停 · 到达目标仍可继续'}</p></section><div class="daily-summary"><div>今日已专注 <strong>${Math.floor(total/60)}<small style="display:inline;font-size:11px"> 分钟</small></strong><small>每日目标 ${state.dailyGoal} 分钟 · ${todayRecords().length} 次学习</small></div><button data-action="daily-goal">调整目标</button></div></div>`;
  }
  function periodRecords(period) {
    const start = new Date();
    start.setHours(0, 0, 0, 0);
    if (period === "week") start.setDate(start.getDate() - 6);
    if (period === "month") start.setDate(start.getDate() - 29);
    return state.records.filter(
      (r) => r.date >= dateKey(start) && r.date <= today(),
    );
  }
  function statsPage(view) {
    const records = periodRecords(view.period);
    const total = totalSeconds(records);
    const days = new Set(records.map((r) => r.date)).size;
    const distribution = SUBJECTS.map((s) => ({
      ...s,
      seconds: totalSeconds(records.filter((r) => r.subject === s.id)),
    }));
    const highest = [...distribution].sort((a, b) => b.seconds - a.seconds)[0];
    const dates =
      view.period === "today"
        ? "今天的每一小段努力"
        : `最近 ${view.period === "week" ? "7" : "30"} 天 · 截至 ${new Date().getMonth() + 1} 月 ${new Date().getDate()} 日`;
    return `<header class="page-title"><div><h2>学习足迹</h2><p>走过的每一步，森林都记得。</p></div>${icon("chart", "title-decoration")}</header><div class="content"><div class="period-picker" role="group" aria-label="统计时间范围">${[
      ["today", "今天"],
      ["week", "近 7 天"],
      ["month", "近 30 天"],
    ]
      .map(
        ([id, label]) =>
          `<button data-action="period" data-value="${id}" class="${view.period === id ? "active" : ""}" aria-pressed="${view.period === id}">${label}</button>`,
      )
      .join(
        "",
      )}</div><p class="range-label">${dates} · ${mode === "demo" ? "示例数据" : "个人体验数据"}</p><div class="stats-summary"><div class="stat-item"><small>累计专注</small><div class="stat-number">${Math.floor(total / 3600)}<em>小时</em><span class="stat-minutes"> ${Math.floor((total % 3600) / 60)}<em>分</em></span></div></div><div class="stat-item"><small>学习次数</small><div class="stat-number">${records.length}<em>次</em></div></div><div class="stat-item"><small>有学习的日子</small><div class="stat-number">${days}<em>天</em></div></div></div><section class="chart-card"><div class="chart-heading"><h3>${view.chart === "trend" ? "努力，有自己的节奏" : "每个科目，学了多久"}</h3><small>三种图表可切换</small></div><div class="chart-tabs" role="group" aria-label="图表样式">${[
      ["ring", "环形时长"],
      ["bars", "科目时长"],
      ["trend", "每日趋势"],
    ]
      .map(
        ([id, label]) =>
          `<button data-action="chart" data-value="${id}" class="${view.chart === id ? "active" : ""}" aria-pressed="${view.chart === id}">${label}</button>`,
      )
      .join(
        "",
      )}</div>${total === 0 ? `<div class="empty-chart">${icon("seed")}还没有学习记录<br>完成一次专注，足迹就会出现在这里。</div>` : view.chart === "ring" ? ringChart(distribution, total) : view.chart === "bars" ? barChart(distribution) : trendChart(view.period, records)}<div class="chart-insight">${icon("leaf")}<span>${total ? `这段时间，「${highest.name}」陪伴你最多。<br>不必每天一样多，有积累就很好。` : "不着急，从喜欢的科目开始就好。"}</span></div></section><div class="section-heading"><h3>专注手记</h3><span>最近 ${Math.min(5, records.length)} 条</span></div>${recordList(
      records
        .slice()
        .sort((a, b) => b.endedAt - a.endedAt)
        .slice(0, 5),
    )}<p class="week-trend-note">每次学习都单独保存，暂停时间不计入时长。</p></div>`;
  }
  function ringChart(data, total) {
    const cx = 180,
      cy = 135,
      radius = 69,
      outer = 82;
    const circumference = 2 * Math.PI * radius;
    let start = 0;
    const slices = data
      .filter((s) => s.seconds > 0)
      .map((s) => {
        const fraction = s.seconds / total;
        const angle = (start + fraction / 2) * Math.PI * 2 - Math.PI / 2;
        const result = {
          ...s,
          start,
          fraction,
          angle,
          side: Math.cos(angle) >= 0 ? 1 : -1,
          x: cx + Math.cos(angle) * outer,
          y: cy + Math.sin(angle) * outer,
          labelY: cy + Math.sin(angle) * 106,
        };
        start += fraction;
        return result;
      });
    // Lay out each side independently; even several tiny adjacent slices keep readable labels.
    for (const side of [-1, 1]) {
      const labels = slices
        .filter((s) => s.side === side)
        .sort((a, b) => a.labelY - b.labelY);
      labels.forEach((s, i) => {
        s.labelY = Math.max(s.labelY, i ? labels[i - 1].labelY + 34 : 26);
      });
      if (labels.length) {
        labels[labels.length - 1].labelY = Math.min(
          244,
          labels[labels.length - 1].labelY,
        );
        for (let i = labels.length - 2; i >= 0; i--)
          labels[i].labelY = Math.min(
            labels[i].labelY,
            labels[i + 1].labelY - 34,
          );
      }
    }
    const arcs = slices
      .map((s) => {
        const length = s.fraction * circumference;
        const gap = slices.length > 1 ? Math.min(1.8, length * 0.08) : 0;
        return `<circle class="donut-segment" cx="${cx}" cy="${cy}" r="${radius}" fill="none" stroke="${s.color}" stroke-width="25" stroke-dasharray="${length - gap} ${circumference - length + gap}" stroke-dashoffset="${-s.start * circumference}" transform="rotate(-90 ${cx} ${cy})"><title>${s.name} · ${studyTime(s.seconds)}</title></circle>`;
      })
      .join("");
    const labels = slices
      .map((s) => {
        const elbow = s.side > 0 ? 269 : 91;
        const end = s.side > 0 ? 278 : 82;
        const textX = s.side > 0 ? 284 : 76;
        const anchor = s.side > 0 ? "start" : "end";
        return `<g class="donut-callout" data-subject="${s.id}"><polyline class="donut-leader" points="${s.x},${s.y} ${elbow},${s.y} ${end},${s.labelY} ${end + s.side * 2},${s.labelY}"/><circle cx="${s.x}" cy="${s.y}" r="2" fill="${s.color}"/><text x="${textX}" y="${s.labelY - 5}" text-anchor="${anchor}" class="donut-label-name">${s.name}</text><text x="${textX}" y="${s.labelY + 9}" text-anchor="${anchor}" class="donut-label-time">${studyTime(s.seconds)}</text></g>`;
      })
      .join("");
    const h = Math.floor(total / 3600),
      m = Math.floor((total % 3600) / 60);
    const center = h
      ? `${h}<tspan class="donut-unit">小时</tspan>${m ? ` ${m}<tspan class="donut-unit">分</tspan>` : ""}`
      : `${m || total}<tspan class="donut-unit">${m ? "分钟" : "秒"}</tspan>`;
    const empty = data.filter((s) => !s.seconds).map((s) => s.name);
    return `<figure class="donut-figure"><svg class="donut-chart" viewBox="0 0 360 270" role="img" aria-label="各科目学习时长，累计 ${studyTime(total)}"><circle cx="${cx}" cy="${cy}" r="${radius}" fill="none" stroke="#e8e8d9" stroke-width="25"/>${arcs}${labels}<text x="${cx}" y="${cy - 17}" text-anchor="middle" class="donut-center-label">累计专注</text><text x="${cx}" y="${cy + 7}" text-anchor="middle" class="donut-total">${center}</text><text x="${cx}" y="${cy + 25}" text-anchor="middle" class="donut-center-label">一点点，也在生长</text></svg><figcaption>${empty.length ? `尚未开始：${empty.join("、")}` : "每一段时间，都有具体的去向。"}</figcaption></figure>`;
  }
  function barChart(data) {
    const max = Math.max(...data.map((s) => s.seconds));
    return `<div class="bar-list" role="img" aria-label="各科目累计学习时长">${data.map((s) => `<div class="bar-item" style="--subject:${s.color}"><div class="bar-title"><span>${s.name}</span><strong>${studyTime(s.seconds)}</strong></div><div class="bar-track"><div class="bar-fill" style="width:${(s.seconds / max) * 100}%"></div></div></div>`).join("")}</div>`;
  }
  function trendChart(period, records) {
    // Thirty-day mode uses six non-overlapping five-day groups to stay legible on a phone.
    const count = period === "month" ? 6 : period === "today" ? 1 : 7;
    const width = period === "month" ? 5 : 1;
    const buckets = Array.from({ length: count }, (_, i) => {
      const end = new Date();
      end.setDate(end.getDate() - (count - 1 - i) * width);
      const start = new Date(end);
      start.setDate(start.getDate() - width + 1);
      return {
        label:
          period === "today"
            ? "今天"
            : `${start.getMonth() + 1}/${start.getDate()}`,
        seconds: totalSeconds(
          records.filter(
            (r) => r.date >= dateKey(start) && r.date <= dateKey(end),
          ),
        ),
      };
    });
    const max = Math.max(...buckets.map((b) => b.seconds), 1);
    return `<div class="trend-unit">具体学习时长${period === "month" ? " · 每柱合计 5 天，日期为区间起日" : " · 每柱代表一天"}</div><div class="trend" role="img" aria-label="${period === "month" ? "最近30天，每5天一组" : "每日"}学习时长">${buckets.map((b, i) => `<div class="trend-column ${i === count - 1 ? "today" : ""}"><span class="trend-value">${compactStudyTime(b.seconds)}</span><div class="trend-bar" style="height:${(b.seconds / max) * 78}%"></div><span class="trend-label">${b.label}</span></div>`).join("")}</div>`;
  }
  function recordList(records) {
    if (!records.length)
      return '<p class="empty-records">这里还留着空白。<br>先完成一小段学习，留下第一片叶子吧。</p>';
    return `<div class="record-list">${records
      .map((r) => {
        const s = subject(r.subject);
        const d = new Date(r.endedAt);
        return `<div class="record-item" style="--subject:${s.color}"><div class="record-icon">${icon("book")}</div><div class="record-body"><strong>${s.name}${r.demo ? "" : '<span class="new-record-tag">新足迹</span>'}</strong><small>${d.getMonth() + 1}/${d.getDate()} ${String(d.getHours()).padStart(2, "0")}:${String(d.getMinutes()).padStart(2, "0")} · ${escape(r.note || "又认真地向前走了一小步")}</small></div><div class="record-duration">${r.seconds < 60 ? r.seconds : Math.floor(r.seconds / 60)}<small>${r.seconds < 60 ? "秒" : "分钟"}</small></div></div>`;
      })
      .join("")}</div>`;
  }
  function gardenScene() {
    const note = gardenBusy()
      ? "给每一片叶子，一点滋养"
      : !state.planted
        ? "这片土地，等一颗小小的种子"
        : gardenMotion
          ? "听见啦，今天也一起长大"
          : "轻轻点我，我会回应你";
    return `<div class="garden-scene" aria-busy="${Boolean(gardenBusy())}"><span class="scene-label"><i></i> 会呼吸的小林地</span><button class="garden-touch" data-action="greet" aria-label="${state.planted ? "摸摸小树" : "看看林地"}" ${gardenBusy() ? "disabled" : ""}><canvas class="garden-canvas" role="img" aria-label="${state.planted ? "微风中摇曳的手绘树苗，点击可以互动" : "等待播种的林间土地"}">互动林地：点击小树打招呼，点击浇水让它成长。</canvas></button><span class="floating-note">${note}</span><span class="scene-hint">${gardenBusy() ? "水壶 → 洒水 → 舒展生长" : state.planted ? "点点小树，和它打个招呼" : "先在这里，种下一颗种子"}</span></div>`;
  }
  function gardenPage() {
    const stages = [
      "刚安家的种子",
      "探出头的小芽",
      "舒展叶片的树苗",
      "向着阳光的小树",
      "快长大的小树",
    ];
    return `<header class="page-title garden-top"><div><h2>我的小林地</h2><p>你在认真生活，森林就在悄悄长大。</p></div>${icon("sun", "title-decoration")}</header><div class="wallet"><span class="wallet-chip">${icon("drop")}水滴 <b>${state.water}</b></span><span class="wallet-chip">${icon("seed")}种子 <b>${state.seeds}</b></span></div>${gardenScene()}<div class="content"><section class="tree-card"><h3 class="tree-name">${state.planted ? stages[state.growth] : "种下一点新的期待"}</h3><p class="tree-subtitle">${state.planted ? `这是你的第 ${state.trees + 1} 棵树 · 已浇水 ${state.growth} / 5 次<br>集满 5 次浇水，它就会住进你的小森林。` : "用 1 颗种子，开始照顾一棵小树。<br>只要愿意开始，什么时候都不算晚。"}</p><div class="growth-dots" aria-label="成长进度 ${state.growth} / 5">${Array.from({ length: 5 }, (_, i) => `<i class="growth-dot ${i < state.growth ? "filled" : ""}"></i>`).join("")}</div><button class="primary-button water-button" data-action="${state.planted ? "water" : "plant"}" ${gardenBusy() ? "disabled" : ""}>${icon(state.planted ? "drop" : "seed")}${gardenBusy() ? "小树正在吸收你的照顾…" : state.planted ? "给小树浇水 · 1 水滴" : "种下一颗种子 · 1 种子"}</button><p class="reward-caption">${state.planted ? "休息不会枯萎，小树会一直等你。" : "第一颗种子免费送给愿意开始的你。"}</p></section><div class="section-heading"><h3>一点点，长成了森林</h3><span>我的收藏</span></div><section class="forest-collection"><div class="forest-thumbnails">${state.trees ? Array.from({ length: Math.min(state.trees, 3) }, () => '<img src="../app/src/main/res/drawable-nodpi/savings_tree_stage_3.png" alt="已长成的小树">').join("") : icon("seed")}</div><div><h3>${state.trees ? "每一棵，都有你的时间" : "森林的故事，等你写下"}</h3><p>${state.trees ? "长大的树会留在这里，陪你走完备考。" : "第一次浇满 5 次水，就会收藏一棵树。"}</p></div><span class="collection-number">${state.trees}</span></section><div class="section-heading"><h3>森林的小小约定</h3><span>努力有回响</span></div><ul class="rewards-list"><li><span class="reward-icon">${icon("drop")}</span><span>每累计专注 25 分钟<small>跨次累计，短短一段也不会浪费</small></span><b>+1 水滴</b></li><li><span class="reward-icon">${icon("seed")}</span><span>达成每日 ${state.dailyGoal} 分钟目标<small>每天最多获得一次</small></span><b>+1 种子</b></li><li><span class="reward-icon">${icon("tree")}</span><span>给一棵树浇水 5 次<small>移入森林，还能再种下新的期待</small></span><b>收藏小树</b></li></ul></div>`;
  }
  function updateClock() {
    document.querySelectorAll('.dial-progress').forEach(node => {
      const value = Math.min(1, elapsed() / ((state.active?.goal || state.goal) * 60000));
      node.setAttribute('stroke-dasharray', `${value * 490.1} 491`);
    });
    document.querySelectorAll(".clock, .hub-active-clock").forEach((node) => {
      node.textContent = clock(elapsed());
    });
    document.querySelectorAll(".focus-progress").forEach((node) => {
      node.style.width = `${Math.min(100, (elapsed() / ((state.active?.goal || state.goal) * 60000)) * 100)}%`;
    });
  }
  function pause() {
    if (state.active && state.active.startedAt !== null) {
      state.active.elapsedMs = elapsed();
      state.active.startedAt = null;
      persist();
    }
  }
  function finish() {
    if (!state.active) return;
    pause();
    renderAll();
    const seconds = Math.floor(elapsed() / 1000);
    if (!seconds) {
      toast("先专注一小会儿，再保存第一段记录吧。");
      return;
    }
    showSheet(
      "finish",
      `${icon("leaf", "sheet-leaf")}<h2>今天，又前进了一点。</h2><p>${subject(state.active.subject).name} · 这一段专注值得被记住。</p><div class="save-duration">${formatDuration(seconds)}</div><label for="study-note">留一句学习手记（选填）</label><textarea id="study-note" maxlength="120" placeholder="例如：复习了二叉树，明天再练两题。"></textarea><p>保存后会累计水滴。不到 25 分钟的时长，也会留到下一次。</p><button class="primary-button" type="submit">${icon("check")}收好这段专注</button>`,
    );
  }
  function addRecord(id, seconds, note, demo = false) {
    const before = Math.floor(totalSeconds(state.records) / 1500);
    const timestamp = Date.now();
    state.records.push({
      id: `record-${timestamp}-${Math.random().toString(36).slice(2, 7)}`,
      subject: id,
      seconds,
      endedAt: timestamp,
      date: today(),
      note,
      demo,
    });
    const earned = Math.floor(totalSeconds(state.records) / 1500) - before;
    state.water += earned;
    let seed = 0;
    if (
      totalSeconds(todayRecords()) >= state.dailyGoal * 60 &&
      !state.seedDays.includes(today())
    ) {
      state.seeds++;
      state.seedDays.push(today());
      seed = 1;
    }
    state.active = null;
    persist();
    renderAll();
    showSheet(
      "reward",
      `${icon("seed", "sheet-leaf")}<h2>一分耕耘，一点新绿。</h2><p>已收好「${subject(id).name}」的 ${formatDuration(seconds)}。<br>${earned ? "把水滴带去林地，给小树一点滋养吧。" : "这次的时间已积攒，累计满 25 分钟会收到水滴。"}</p><div class="reward-result">${icon("drop")} +${earned} 水滴 ${seed ? `${icon("seed")} +1 种子` : ""}</div><button type="submit" class="primary-button">去看看我的小树 ${icon("arrow")}</button>`,
    );
  }
  const actions = {
    navigate(view, value) {
      const allowed = [
        "hub",
        ...studyNavigation.map((item) => item[0]),
        ...lifeNavigation.map((item) => item[0]),
      ];
      if (!allowed.includes(value)) return;
      if (value === "hub" && state.active?.startedAt !== null && state.active)
        toast("已回到森林入口，学习计时仍在继续。");
      view.page = value;
      view.phone.dataset.page = value;
      render(view);
      view.phone.querySelector(".page-scroll").scrollTop = 0;
    },
    "subject-sheet"() {
      if(state.active) return;
      showSheet('subjects', `<h2>今天，想学点什么？</h2><p>选择一本书，从这一小段专注开始。</p><div class="subjects">${SUBJECTS.slice(0,3).map(subjectButton).join('')}</div><p class="subject-group-label">408 计算机学科专业基础</p><div class="subjects professional">${SUBJECTS.slice(3).map(subjectButton).join('')}</div>`);
      sheet.classList.add('subject-sheet');
    },
    subject(view, value) {
      if (state.active) return;
      state.selected = value;
      persist();
      renderAll();
    },
    "session-goal"(view, value) {
      if (state.active) return;
      state.goal = Number(value);
      persist();
      renderAll();
    },
    start() {
      if (state.active) return;
      state.active = {
        subject: state.selected,
        goal: state.goal,
        startedAt: Date.now(),
        elapsedMs: 0,
      };
      persist();
      renderAll();
      toast(`开始学习${subject(state.selected).name}。慢慢来，每一秒都算数。`);
    },
    pause() {
      pause();
      renderAll();
    },
    resume() {
      if (state.active?.startedAt === null) {
        state.active.startedAt = Date.now();
        persist();
        renderAll();
      }
    },
    finish,
    period(view, value) {
      view.period = value;
      render(view);
    },
    chart(view, value) {
      view.chart = value;
      render(view);
    },
    "daily-goal"() {
      showSheet(
        "goal",
        `${icon("sun", "sheet-leaf")}<h2>给今天一个小期待。</h2><p>目标只是温柔的路标，可以按自己的节奏来。每天达标送一颗种子，每天最多一次。</p><label for="daily-goal">每日学习目标（分钟）</label><input id="daily-goal" type="number" min="10" max="720" step="5" value="${state.dailyGoal}" required><button type="submit" class="primary-button">就从这个目标开始</button>`,
      );
    },
    water() {
      if (gardenBusy()) return;
      if (!state.planted) {
        toast("先种下一颗种子，再来浇水吧。");
        return;
      }
      if (state.water < 1) {
        toast("水滴还在路上。累计学习 25 分钟，就能收获 1 滴。");
        return;
      }
      const from = state.growth;
      state.water--;
      state.growth++;
      const grown = state.growth === 5;
      if (grown) {
        state.trees++;
        state.growth = 0;
        state.planted = false;
      }
      persist();
      animateGarden("water", from, from + 1, () => {
        if (grown)
          showSheet(
            "grown",
            `${icon("tree", "sheet-leaf")}<h2>你的小森林，多了一棵树。</h2><p>5 次浇水，让一颗小小的种子长大了。它已经留在林地里，也记着你认真学习的日子。</p><div class="reward-result">${icon("tree")} 已收藏第 ${state.trees} 棵小树</div><button type="submit" class="primary-button">把这份成长收好</button>`,
          );
        else
          toast(`小树喝到水了 · 成长 ${state.growth} / 5。谢谢你今天的照顾。`);
      });
    },
    plant() {
      if (gardenBusy()) return;
      if (state.planted) return;
      if (state.seeds < 1) {
        toast("今天达成学习目标，就能再得到一颗种子。");
        return;
      }
      state.seeds--;
      state.planted = true;
      state.growth = 0;
      persist();
      animateGarden("plant", -1, 0);
      toast("新的种子住进了林地。慢慢照顾它吧。");
    },
    greet() {
      if (gardenBusy()) return;
      if (!state.planted) {
        toast("这片小小的空地，正在等你种下一颗种子。");
        return;
      }
      animateGarden("greet", state.growth, state.growth);
      toast("叶子轻轻晃了晃：今天也一起加油。摸摸小树不消耗水滴。");
    },
  };
  views.forEach((view) =>
    view.phone.addEventListener("click", (event) => {
      const button = event.target.closest("[data-action]");
      if (button && !button.disabled)
        actions[button.dataset.action]?.(view, button.dataset.value);
    }),
  );
  sheet.addEventListener('click',event=>{
    const choice=event.target.closest('[data-action="subject"]');
    if(choice&&!choice.disabled){actions.subject(null,choice.dataset.value);closeSheet();}
  });
  document.addEventListener('pointerdown',event=>{
    const button=event.target.closest('button');
    if(!button||button.disabled) return;
    button.classList.add('press-feedback');
    const release=()=>{button.classList.remove('press-feedback');document.removeEventListener('pointerup',release);document.removeEventListener('pointercancel',release)};
    document.addEventListener('pointerup',release,{once:true});document.addEventListener('pointercancel',release,{once:true});
  });
  document.getElementById("sheet-form").addEventListener("submit", (event) => {
    event.preventDefault();
    if (sheetType === "finish" && state.active) {
      const note = document.getElementById("study-note").value.trim();
      const id = state.active.subject;
      const seconds = Math.floor(elapsed() / 1000);
      closeSheet();
      addRecord(id, seconds, note);
    } else if (sheetType === "goal") {
      const input = document.getElementById("daily-goal");
      if (!input.reportValidity()) return;
      state.dailyGoal = Number(input.value);
      persist();
      closeSheet();
      renderAll();
      toast("小目标更新了。按自己的节奏慢慢来。");
    } else if (sheetType === "reward") {
      closeSheet();
      const target = window.matchMedia("(max-width:760px)").matches
        ? views[0]
        : views.find((view) =>
            studyNavigation.some((item) => item[0] === view.page),
          ) || views[1];
      target.page = "garden";
      render(target);
      target.phone.querySelector(".page-scroll").scrollTop = 0;
    } else closeSheet();
  });
  document.getElementById("mode-toggle").addEventListener("click", () => {
    if (gardenBusy()) {
      toast("等小树喝完这口水，再切换体验数据吧。");
      return;
    }
    gardenMotion = null;
    if (state.active) {
      toast("先完成这段专注，再切换体验数据吧。");
      return;
    }
    persist();
    mode = mode === "demo" ? "personal" : "demo";
    state = readState();
    persist();
    renderAll();
    toast(
      mode === "demo"
        ? "已返回示例数据。"
        : "已打开空白体验，与示例数据分别保存。",
    );
  });
  document.getElementById("demo-session").addEventListener("click", () => {
    if (mode !== "demo") return;
    if (state.active) {
      toast("先完成正在进行的专注，再体验示例奖励。");
      return;
    }
    addRecord(state.selected, 1500, "原型体验 · 模拟完成 25 分钟", true);
  });
  window.addEventListener("pagehide", persist);
  setInterval(updateClock, 1000);
  persist();
  renderAll();
})();
