/* A small scene renderer for the HTML prototype. No external engine or assets from researched repositories. */
(() => {
  "use strict";
  const sources = {
    sprout: "../app/src/main/res/drawable-nodpi/savings_seedling_stage_1.png",
    sapling: "../app/src/main/res/drawable-nodpi/savings_seedling_stage_2.png",
    tree: "../app/src/main/res/drawable-nodpi/savings_tree_stage_3.png",
    forest: "assets/study_forest_scene.png",
    can: "assets/study_watering_can.png",
  };
  const images = Object.fromEntries(
    Object.entries(sources).map(([name, src]) => {
      const image = new Image();
      image.src = src;
      return [name, image];
    }),
  );
  const stages = [
    { image: "sprout", size: 95 },
    { image: "sprout", size: 145 },
    { image: "sapling", size: 205 },
    { image: "tree", size: 235 },
    { image: "tree", size: 263 },
    { image: "tree", size: 284 },
  ];
  const clamp = (value) => Math.max(0, Math.min(1, value));
  const ease = (value) => {
    const t = clamp(value);
    return t * t * (3 - 2 * t);
  };
  const ready = (image) => image.complete && image.naturalWidth > 0;

  class StudyGarden {
    constructor(canvas, options) {
      this.canvas = canvas;
      this.ctx = canvas.getContext("2d");
      this.options = options;
      this.visible = false;
      this.destroyed = false;
      this.lastFrame = -Infinity;
      this.reduced = window.matchMedia("(prefers-reduced-motion: reduce)");
      this.resize = () => {
        const bounds = canvas.getBoundingClientRect();
        if (!bounds.width || !bounds.height) return;
        const dpr = Math.min(window.devicePixelRatio || 1, 2);
        canvas.width = Math.round(bounds.width * dpr);
        canvas.height = Math.round(bounds.height * dpr);
        this.draw(performance.now());
      };
      this.visibility = () => {
        cancelAnimationFrame(this.frameId);
        if (this.visible && !document.hidden)
          this.frameId = requestAnimationFrame(this.frame);
      };
      this.frame = (timestamp) => {
        if (this.destroyed || !this.visible || document.hidden) return;
        if (timestamp - this.lastFrame >= 1000 / 30) {
          this.draw(timestamp);
          this.lastFrame = timestamp;
        }
        this.frameId = requestAnimationFrame(this.frame);
      };
      this.resizeObserver = new ResizeObserver(this.resize);
      this.resizeObserver.observe(canvas);
      this.intersection = new IntersectionObserver((entries) => {
        this.visible = entries[0].isIntersecting;
        this.visibility();
      });
      this.intersection.observe(canvas);
      document.addEventListener("visibilitychange", this.visibility);
      this.resize();
    }

    destroy() {
      this.destroyed = true;
      cancelAnimationFrame(this.frameId);
      this.resizeObserver.disconnect();
      this.intersection.disconnect();
      document.removeEventListener("visibilitychange", this.visibility);
    }

    oval(x, y, rx, ry, color) {
      const c = this.ctx;
      c.fillStyle = color;
      c.beginPath();
      c.ellipse(x, y, rx, ry, 0, 0, Math.PI * 2);
      c.fill();
    }

    star(x, y, size, alpha) {
      const c = this.ctx;
      c.save();
      c.globalAlpha = alpha;
      c.strokeStyle = "#b89a52";
      c.lineWidth = 1;
      c.beginPath();
      c.moveTo(x - size, y);
      c.lineTo(x + size, y);
      c.moveTo(x, y - size);
      c.lineTo(x, y + size);
      c.stroke();
      c.restore();
    }

    draw(timestamp) {
      const c = this.ctx;
      if (!c || !this.canvas.width) return;
      c.setTransform(
        this.canvas.width / 360,
        0,
        0,
        this.canvas.height / 285,
        0,
        0,
      );
      c.clearRect(0, 0, 360, 285);
      const t = this.reduced.matches ? 0 : timestamp / 1000;
      const motion = this.options.motion;
      const elapsed = motion ? Math.max(0, timestamp - motion.startedAt) : 0;
      const progress = motion ? clamp(elapsed / motion.duration) : 0;
      const water = motion?.kind === "water";
      const greet = motion?.kind === "greet";
      const plant = motion?.kind === "plant";
      const grow = water ? ease((progress - 0.5) / 0.42) : 0;
      this.canvas.dataset.phase = water
        ? progress < 0.17
          ? "arriving"
          : progress < 0.58
            ? "watering"
            : motion.to === 5
              ? "celebrating"
              : "growing"
        : greet
          ? "greeting"
          : plant
            ? "planting"
            : "idle";

      this.background(t);
      const ground = c.createRadialGradient(175, 224, 5, 175, 224, 145);
      ground.addColorStop(0, "#c7d7a4a0");
      ground.addColorStop(1, "#d8dfbb00");
      this.oval(175, 225, 146, 43, ground);
      this.oval(170, 236, 60, 11, "#8c966b1d");
      this.grass(t);

      if (!this.options.planted && !water && !plant) {
        this.oval(170, 235, 25, 7, "#c9b697");
        this.oval(169, 233, 20, 5, "#ddceb2");
        for (let i = 0; i < 6; i++)
          this.oval(153 + i * 7, 232 + Math.sin(i) * 4, 2, 1.2, "#a99272");
      } else if (plant) {
        const emerging = ease(progress);
        this.tree(0, t, emerging, emerging);
        if (!this.reduced.matches) this.particles(progress, "#a5b873");
      } else if (water) {
        const before = stages[motion.from];
        const after = stages[motion.to];
        const size = before.size + (after.size - before.size) * grow;
        if (before.image === after.image)
          this.tree(motion.to, t, 1, 1, size, grow);
        else {
          if (grow < 1) this.tree(motion.from, t, 1 - grow, 1, size, grow);
          if (grow > 0) this.tree(motion.to, t, grow, 1, size, grow);
        }
        this.watering(progress);
        if (grow > 0 && !this.reduced.matches) this.particles(grow, "#94ad67");
      } else {
        const nod =
          greet && !this.reduced.matches
            ? Math.sin(progress * Math.PI * 4) * Math.sin(progress * Math.PI)
            : 0;
        this.tree(this.options.growth, t, 1, 1, null, 0, nod);
        if (greet) this.hearts(progress);
      }
      this.butterfly(t);
      // Also expose a useful stable status for assistive technology and visual QA.
      this.canvas.parentElement.dataset.phase = this.canvas.dataset.phase;
    }

    background(t) {
      const c = this.ctx;
      if (ready(images.forest)) {
        c.save();
        c.globalAlpha = 0.22;
        c.drawImage(images.forest, 0, 0, 360, 240);
        c.restore();
        const veil = c.createLinearGradient(0, 40, 0, 245);
        veil.addColorStop(0, "#f8f5e62b");
        veil.addColorStop(1, "#f1f1dfed");
        c.fillStyle = veil;
        c.fillRect(0, 0, 360, 285);
      }
      const light = c.createRadialGradient(270, 55, 0, 270, 55, 190);
      light.addColorStop(0, "#fff4b65e");
      light.addColorStop(1, "#fff6cc00");
      c.fillStyle = light;
      c.fillRect(0, 0, 360, 285);
      for (let i = 0; i < 7; i++) {
        const x = 45 + i * 43 + Math.sin(t * 0.3 + i) * 7;
        const y = 70 + ((i * 29) % 115) + Math.cos(t * 0.4 + i) * 9;
        this.star(x, y, 1.6, 0.12 + (Math.sin(t + i) + 1) * 0.13);
      }
    }

    grass(t) {
      const c = this.ctx;
      for (let i = 0; i < 18; i++) {
        const x = 37 + i * 17;
        const y = 226 + Math.sin(i * 1.8) * 12;
        const bend = Math.sin(t * 1.4 + i * 0.7) * 2;
        c.strokeStyle = i % 2 ? "#9dad80" : "#bcc698";
        c.lineWidth = 1.2;
        c.beginPath();
        c.moveTo(x, y + 8);
        c.quadraticCurveTo(x - 1, y, x - 4 + bend, y - 5);
        c.stroke();
        c.beginPath();
        c.moveTo(x, y + 8);
        c.quadraticCurveTo(x + 1, y - 1, x + 5 + bend, y - 9);
        c.stroke();
        if (i % 5 === 0) {
          for (let p = 0; p < 5; p++)
            this.oval(
              x + 5 + Math.cos(p * 1.257) * 3,
              y - 9 + Math.sin(p * 1.257) * 3,
              2.5,
              1.8,
              "#fff9e8",
            );
          this.oval(x + 5, y - 9, 1.8, 1.8, "#c5aa64");
        }
      }
      [
        [58, 243, 6, 2],
        [275, 239, 7, 3],
        [301, 237, 4, 2],
        [81, 247, 4, 2],
      ].forEach(([x, y, rx, ry]) => this.oval(x, y, rx, ry, "#d1c7ad"));
    }

    tree(
      stage,
      t,
      alpha = 1,
      scale = 1,
      sizeOverride = null,
      flourish = 0,
      nod = 0,
    ) {
      const c = this.ctx;
      const spec = stages[Math.max(0, Math.min(5, stage))];
      const image = images[spec.image];
      if (!ready(image)) return;
      const size = sizeOverride || spec.size;
      const sway = this.reduced.matches
        ? 0
        : Math.sin(t * 1.35) * 0.016 + Math.sin(t * 0.73) * 0.008;
      c.save();
      c.globalAlpha = alpha;
      c.translate(170, 237);
      c.rotate(sway + nod * 0.09);
      const stretch =
        1 +
        (!this.reduced.matches ? Math.sin(flourish * Math.PI * 2) * 0.05 : 0);
      c.scale(scale / stretch, scale * stretch);
      if (flourish > 0) {
        c.shadowColor = "#c8dc8a";
        c.shadowBlur = Math.sin(flourish * Math.PI) * 16;
      }
      const root =
        spec.image === "sprout" ? 0.81 : spec.image === "sapling" ? 0.86 : 0.89;
      c.drawImage(image, -size / 2, -size * root, size, size);
      c.restore();
    }

    watering(progress) {
      const c = this.ctx;
      const show = ease(progress / 0.16) * (1 - ease((progress - 0.71) / 0.16));
      if (show <= 0) return;
      const pouring =
        ease((progress - 0.13) / 0.12) * (1 - ease((progress - 0.6) / 0.1));
      const angle = -0.22 - pouring * 0.55;
      const x = 273 + (1 - show) * 80;
      const y = 111 - pouring * 8;
      if (ready(images.can)) {
        c.save();
        c.globalAlpha = show;
        c.translate(x, y);
        c.rotate(angle);
        c.drawImage(images.can, -72, -58, 144, 116);
        c.restore();
      }
      if (progress > 0.22 && progress < 0.7) {
        // Water starts at the rotated sprite's sprinkler head and falls toward the roots.
        const sx = x - 55 * Math.cos(angle) + 23 * Math.sin(angle);
        const sy = y - 55 * Math.sin(angle) - 23 * Math.cos(angle);
        for (let i = 0; i < 22; i++) {
          const phase = ((progress - 0.22) * 6 + i / 22) % 1;
          const dx = sx - phase * (sx - 170) + Math.sin(i * 7) * phase * 10;
          const dy = sy + phase * (234 - sy);
          c.save();
          c.globalAlpha = Math.sin(phase * Math.PI) * 0.85;
          c.strokeStyle = i % 2 ? "#82aeb8" : "#b3d3d5";
          c.lineWidth = 1.8;
          c.beginPath();
          c.moveTo(dx, dy);
          c.lineTo(dx - 1.7, dy + 5);
          c.stroke();
          c.restore();
        }
        const ripple = (progress * 7) % 1;
        c.save();
        c.globalAlpha = (1 - ripple) * 0.4;
        c.strokeStyle = "#80a9a0";
        c.lineWidth = 1;
        c.beginPath();
        c.ellipse(170, 236, 9 + ripple * 23, 2 + ripple * 5, 0, 0, Math.PI * 2);
        c.stroke();
        c.restore();
      }
    }

    particles(progress, color) {
      const c = this.ctx;
      for (let i = 0; i < 13; i++) {
        const phase = (progress * 1.2 + i / 13) % 1;
        const angle = i * 2.4;
        const x = 170 + Math.cos(angle) * (25 + phase * 54);
        const y = 200 - phase * 130 + Math.sin(i) * 22;
        c.save();
        c.translate(x, y);
        c.rotate(angle + phase);
        c.globalAlpha =
          Math.sin(phase * Math.PI) * Math.sin(progress * Math.PI) * 0.9;
        c.fillStyle = color;
        c.beginPath();
        c.ellipse(0, 0, 3.5, 1.6, 0, 0, Math.PI * 2);
        c.fill();
        c.restore();
        this.star(x + 7, y - 9, 2.3, Math.sin(progress * Math.PI) * 0.6);
      }
    }

    hearts(progress) {
      const c = this.ctx;
      for (let i = 0; i < 3; i++) {
        const phase = clamp(progress * 1.3 - i * 0.12);
        c.save();
        c.translate(155 + i * 19 + Math.sin(phase * 5) * 7, 151 - phase * 70);
        c.scale(0.65, 0.65);
        c.globalAlpha = Math.sin(phase * Math.PI) * 0.85;
        c.fillStyle = "#be9973";
        c.beginPath();
        c.moveTo(0, 3);
        c.bezierCurveTo(-13, -5, -3, -13, 0, -6);
        c.bezierCurveTo(3, -13, 13, -5, 0, 3);
        c.fill();
        c.restore();
      }
    }

    butterfly(t) {
      const c = this.ctx;
      const x = 70 + Math.sin(t * 0.25) * 27;
      const y = 118 + Math.cos(t * 0.6) * 14;
      const wing = 0.4 + Math.abs(Math.sin(t * 5)) * 0.6;
      c.save();
      c.translate(x, y);
      c.rotate(-0.2);
      this.oval(-3 * wing, -2, 4 * wing, 5, "#d3b878aa");
      this.oval(3 * wing, -2, 4 * wing, 5, "#dec98bba");
      this.oval(0, 0, 0.8, 4, "#9c926b");
      c.restore();
    }
  }
  window.StudyGarden = StudyGarden;
})();
