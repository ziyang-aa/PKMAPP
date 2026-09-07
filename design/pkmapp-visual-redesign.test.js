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
