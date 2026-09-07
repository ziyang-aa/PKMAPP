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
