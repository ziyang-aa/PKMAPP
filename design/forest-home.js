(() => {
  "use strict";
  const leaf = '<svg aria-hidden="true"><use href="#i-leaf"/></svg>';
  const arrow = '<svg aria-hidden="true"><use href="#i-arrow"/></svg>';
  const assets = "../app/src/main/res/drawable-nodpi/";
  const names = {
    details: "明细",
    charts: "图表",
    record: "记账",
    savings: "攒钱",
    profile: "我的",
  };

  function portal({ active, subject, clock }) {
    return `<section class="forest-portal"><div class="portal-scene"><div class="portal-greeting"><span class="portal-overline">今天，想和谁一起出发？</span><h2>欢迎回到<br>你的森林小窝。</h2></div><div class="portal-hut-signs"><button data-action="navigate" data-value="focus" class="hut-sign" aria-label="从门牌进入学习小屋"><span class="visually-hidden">学习小屋</span></button><button data-action="navigate" data-value="life-details" class="hut-sign life-sign" aria-label="从门牌进入生活小屋"><span class="visually-hidden">生活小屋</span></button></div><div class="portal-fireflies" aria-hidden="true"><i></i><i></i><i></i></div></div><div class="companion-choices"><button class="companion-choice study-choice" data-action="navigate" data-value="focus" aria-label="和学习伙伴松栗进入学习小屋"><span class="companion-image"><img src="assets/study_squirrel_reading.png" alt="戴绿色围巾、抱着书的小松鼠"/></span><span class="companion-kicker">学习伙伴 · 松栗</span><strong>把努力，慢慢种下</strong><span class="companion-description">专注计时 · 学习足迹 · 林地</span><span class="companion-enter">进入学习小屋 ${arrow}</span></button><button class="companion-choice life-choice" data-action="navigate" data-value="life-details" aria-label="和生活伙伴小芽进入生活小屋"><span class="companion-image"><img src="${assets}balance_bulbasaur_coin.png" alt="陪你记账、照顾储蓄罐的妙蛙种子"/></span><span class="companion-kicker">生活伙伴 · 小芽</span><strong>把日子，好好收着</strong><span class="companion-description">日常账本 · 攒钱 · 生活工具</span><span class="companion-enter">进入生活小屋 ${arrow}</span></button></div>${active ? `<button class="portal-resume" data-action="navigate" data-value="focus"><span class="resume-indicator"></span><span>${subject} · ${active.startedAt === null ? "专注已暂停" : "学习计时仍在继续"}</span><strong class="hub-active-clock">${clock}</strong>${arrow}</button>` : `<div class="portal-letter">${leaf}<p>认真学习，也好好生活。<br>每一次回来，都有伙伴在等你。</p></div>`}<p class="portal-footer-note">属于你的生活集合 · 更多小屋，慢慢生长</p></section>`;
  }

  function selectLife(frame, screen) {
    frame.dataset.screen = screen;
    if (!frame.contentDocument?.querySelector(".screen-grid")) return;
    const doc = frame.contentDocument;
    doc.querySelectorAll("[data-screen]").forEach((node) => {
      node.hidden = node.dataset.screen !== screen;
    });
    doc.documentElement.scrollTop = 0;
    doc.title = `生活小屋 · ${names[screen] || "账本"}`;
  }

  let lifeSource;
  const lifeStyles = `html,body{min-width:0!important;width:100%!important;min-height:100%!important;margin:0!important;padding:0!important;background:#f5f1e7!important}body{overflow-x:hidden}.board-header,.board-note,.screen-label,.bottom-nav,.screen-header .door-button{display:none!important}.screen-grid{display:block!important;max-width:none!important;width:100%!important;margin:0!important;padding:0!important}.screen{margin:0!important;width:100%!important;max-width:none!important}.screen[hidden]{display:none!important}.phone{width:100%!important;height:auto!important;min-height:100vh!important;max-width:none!important;margin:0!important;border:0!important;border-radius:0!important;box-shadow:none!important;overflow:visible!important}.screen-content{height:auto!important;min-height:100vh!important;overflow:visible!important;padding:16px 18px 30px!important;scroll-padding-bottom:30px!important}.fixed-page-background{background-image:url('../app/src/main/res/drawable-nodpi/bg_fixed_forest.png')!important}.screen-header{margin-bottom:16px!important}.screen-header h2{font-family:Georgia,'Songti SC',SimSun,serif!important;font-weight:500!important}.details-tool-grid{gap:8px!important}.details-tool-button{padding:12px 10px!important}`;
  function attachLife(frame, screen, navigate) {
    frame.dataset.screen = screen;
    frame.addEventListener("load", () => {
      try {
        const doc = frame.contentDocument;
        if (!doc?.querySelector(".screen-grid")) return;
        doc.addEventListener(
          "click",
          (event) => {
            const jump = event.target.closest("[data-jump]");
            if (!jump) return;
            event.preventDefault();
            event.stopImmediatePropagation();
            navigate(
              jump.dataset.jump === "home"
                ? "hub"
                : `life-${jump.dataset.jump}`,
            );
          },
          true,
        );
        selectLife(frame, frame.dataset.screen);
        frame.dataset.ready = "true";
      } catch {
        frame.insertAdjacentHTML(
          "afterend",
          '<p class="life-load-note">生活小屋需要通过本地预览地址打开。<a href="pkmapp-visual-redesign.html">也可查看原记账设计</a></p>',
        );
      }
    });
    lifeSource ||= fetch("pkmapp-visual-redesign.html").then((response) => {
      if (!response.ok) throw new Error("Life preview unavailable");
      return response.text();
    });
    lifeSource
      .then((source) => {
        if (!frame.isConnected) return;
        const adapted = source
          .replaceAll(
            "../宝可梦账本素材/装饰素材/主页面背景/主页面.png",
            "../app/src/main/res/drawable-nodpi/home_panorama_replacement.png",
          )
          .replaceAll("pkmapp-savings-goal", "pkm-life-preview-goal")
          .replace(
            /<section class="screen" data-screen="([^"]+)">/g,
            (tag, name) =>
              name === frame.dataset.screen
                ? tag
                : tag.replace(">", " hidden>"),
          )
          .replace(
            "</head>",
            `<base href="${new URL(".", location.href).href}"><style>${lifeStyles}</style></head>`,
          );
        frame.srcdoc = adapted;
      })
      .catch(() => {
        if (frame.isConnected)
          frame.outerHTML =
            '<p class="life-load-note">请通过本地预览地址打开生活小屋。<a href="pkmapp-visual-redesign.html">查看原记账设计</a></p>';
      });
  }
  window.ForestHome = { portal, attachLife, selectLife };
})();
