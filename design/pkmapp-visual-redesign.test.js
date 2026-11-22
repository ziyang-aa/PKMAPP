const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const htmlPath = path.join(__dirname, 'pkmapp-visual-redesign.html');

function page() {
  return fs.readFileSync(htmlPath, 'utf8');
}

function test(name, assertion) {
  try {
    assertion();
    console.log(`PASS ${name}`);
  } catch (error) {
    console.error(`FAIL ${name}`);
    throw error;
  }
}

test('设计板包含六页导航和记账双状态', () => {
  const source = page();
  for (const id of ['home', 'details', 'charts', 'record', 'savings', 'profile']) {
    assert.match(source, new RegExp(`data-screen=["']${id}["']`));
  }
  assert.match(source, /data-record-state=["']categories["']/);
  assert.match(source, /data-record-state=["']entry["']/);
  assert.match(source, /ic_category_expense_dining\.webp/);
  assert.match(source, /ic_category_income_salary\.webp/);
});

test('设计板包含移动端可访问性和减弱动效约束', () => {
  const source = page();
  assert.match(source, /aria-label=/);
  assert.match(source, /min-height:\s*48px/);
  assert.match(source, /prefers-reduced-motion/);
  assert.match(source, /#F5F1E7/i);
  assert.match(source, /#FFF8DF/i);
});

test('首页使用单张森林小窝背景并移除首页功能卡', () => {
  const source = page();
  assert.match(source, /data-home-panorama/);
  assert.match(source, /\.\.\/宝可梦账本素材\/装饰素材\/主页面背景\/主页面\.png/);
  assert.doesNotMatch(source, /class="quick-actions"/);
  assert.doesNotMatch(source, /林间最近发生/);
  assert.match(source, /data-panorama-track/);
  assert.match(source, /setProperty\(['"]--panorama-x/);
});

test('选择分类后在下方展开计算器', () => {
  const source = page();
  assert.match(source, /data-calculator/);
  assert.match(source, /data-calculator-key/);
  assert.match(source, /data-calc-display/);
  assert.match(source, /data-calculator[^>]*hidden/);
  assert.match(source, /data-calculator-equals/);
  assert.match(source, /setRecordState\(['"]entry['"]\)/);
});

test('攒钱页提供三版卡片方案并默认使用空白目标', () => {
  const source = page();
  assert.match(source, /data-goal-variant=["']seedling["']/);
  assert.match(source, /data-goal-variant=["']treasure["']/);
  assert.match(source, /data-goal-variant=["']journal["']/);
  assert.match(source, /data-goal-card/);
  assert.match(source, /data-goal-empty/);
  assert.match(source, /data-goal-name/);
  assert.match(source, /data-goal-target/);
  assert.match(source, /第二株小幼苗\.png/);
  assert.match(source, /小树\.png/);
  assert.match(source, /data-goal-progress-art/);
  assert.doesNotMatch(source, /秋日旅行|森林小屋/);
});

test('攒钱页使用卡内编辑和存入操作，不依赖旧式弹窗', () => {
  const source = page();
  assert.match(source, /data-goal-save/);
  assert.match(source, /data-deposit-amount/);
  assert.match(source, /data-deposit-submit/);
  assert.match(source, /data-goal-variant-switch/);
  assert.match(source, /data-goal-storage/);
  assert.doesNotMatch(source, /data-savings-dialog|data-create-goal-dialog|prompt\(/);
});

test('攒钱页保留超额存入的真实金额', () => {
  const source = page();
  assert.match(source, /goal\.saved \+= amount/);
  assert.doesNotMatch(source, /saved:\s*Math\.min\(goalData\.saved,\s*target\)/);
  assert.doesNotMatch(source, /goal\.saved = Math\.min\(goal\.target,\s*goal\.saved \+ amount\)/);
  assert.match(source, /data-goal-saved-label/);
});

test('攒钱页支持从已有目标开始新建目标', () => {
  const source = page();
  assert.match(source, /data-goal-new/);
  assert.match(source, /draftGoal/);
  assert.match(source, /新建目标/);
});

test('新建目标保留原目标并追加新卡片', () => {
  const source = page();
  assert.match(source, /data-goals-list/);
  assert.match(source, /goalList\.push/);
  assert.match(source, /goalList\.length/);
  assert.doesNotMatch(source, /goalData = \{ name: '', target: 0, saved: 0, variant: goalData\.variant \}/);
});

test('攒钱目标卡底部不被固定森林背景遮挡', () => {
  const source = page();
  assert.match(source, /\.goal-card\s*\{[^}]*background:\s*#fff8df/);
  assert.doesNotMatch(source, /\.goal-card::after\s*\{[^}]*linear-gradient/);
  assert.match(source, /\.screen\[data-screen="savings"\] \.screen-content\s*\{[^}]*padding-bottom:\s*150px/);
});
