// 浮窗拖动自检：模拟 PointerEvent 拖动，验证「拖得动 + 不越界 + 记忆位置」。
const vm = require('vm');
const fs = require('fs');
const js = fs.readFileSync('/home/SQL916/zuoyebang_xposed/.agent/snips/webkiller.js', 'utf8');

const timeouts = [];
let saved = null;
const winListeners = [];
const lockCalls = [];

function El(tag) {
  this.tagName = tag || 'DIV';
  this.children = [];
  this.attrs = {};
  this.handlers = {};
  this.className = '';
  this.id = '';
  this.textContent = '';
  this.childElementCount = 0;
  this.clientHeight = 0;
  this.offsetWidth = 86;
  this.offsetHeight = 200;
  this.parentElement = null;
  const self = this;
  this.style = {
    cssText: '', display: '', opacity: '', left: '', top: '',
    setProperty: function (k, v) { self.style[k] = v; },
    getPropertyValue: function () { return ''; }
  };
}
El.prototype.setAttribute = function (k, v) { this.attrs[k] = v; };
El.prototype.getAttribute = function (k) { return this.attrs[k] === undefined ? null : this.attrs[k]; };
El.prototype.addEventListener = function (t, fn) { (this.handlers[t] = this.handlers[t] || []).push(fn); };
El.prototype.dispatch = function (t, ev) {
  ev.type = t;
  (this.handlers[t] || []).forEach(function (fn) { fn(ev); });
};
El.prototype.appendChild = function (c) { this.children.push(c); c.parentElement = this; return c; };
El.prototype.remove = function () {};
El.prototype.querySelector = function (sel) {
  if (sel === '[data-zybhide]') {
    for (let i = 0; i < this.children.length; i++) {
      if (this.children[i].getAttribute('data-zybhide')) return this.children[i];
    }
  }
  return null;
};
El.prototype.querySelectorAll = function () { return []; };
El.prototype.closest = function () { return null; };
El.prototype.matches = function () { return false; };
El.prototype.getBoundingClientRect = function () {
  return { left: parseFloat(this.style.left) || 0, top: parseFloat(this.style.top) || 0, width: 86, height: 200 };
};
El.prototype.setPointerCapture = function () { this.captured = true; };

const body = new El('BODY');
body.textContent = '题目'.repeat(200) + '答案解析';
const docEl = new El('HTML');
const head = new El('HEAD');

const document = {
  body: body, documentElement: docEl, head: head, hidden: false,
  createElement: function (t) { return new El(t.toUpperCase()); },
  getElementById: function (id) {
    for (let i = 0; i < body.children.length; i++) if (body.children[i].id === id) return body.children[i];
    return null;
  },
  querySelector: function () { return null; },
  querySelectorAll: function () { return []; },
  addEventListener: function () {}
};

const sandbox = {
  document,
  addEventListener(t, fn, opt) { winListeners.push([t, fn, opt]); },
  removeEventListener(t, fn) {
    for (let i = winListeners.length - 1; i >= 0; i--) {
      if (winListeners[i][0] === t && winListeners[i][1] === fn) winListeners.splice(i, 1);
    }
  },
  zybcleanHost: {
    lockTouch(lock) { lockCalls.push(lock); },
    toast() {}
  },
  location: { href: 'https://example.test/answer' },
  innerWidth: 1080, innerHeight: 800,
  navigator: {},
  PointerEvent: function () {},
  localStorage: {
    getItem() { return null; },
    setItem(k, v) { saved = v; },
    removeItem() {}
  },
  getComputedStyle: function () { return { display: 'block', visibility: 'visible', opacity: '1', position: 'static' }; },
  MutationObserver: function () { this.observe = function () {}; },
  setTimeout: function (fn) { timeouts.push(fn); return timeouts.length; },
  clearTimeout: function () {},
  setInterval: function () { return 1; },
  Promise, Date, Math, JSON, String, RegExp, console
};
sandbox.window = sandbox;
sandbox.self = sandbox;

vm.createContext(sandbox);
vm.runInContext(js, sandbox, { filename: 'webkiller.js' });

let fail = 0;
function chk(name, ok) { console.log((ok ? 'ok   ' : 'FAIL ') + name); if (!ok) fail++; }

const bar = document.getElementById('zyb-bar');
chk('浮窗存在', bar !== null);
chk('touch-action:none 已设置', /touch-action:none/.test(bar.style.cssText));
chk('pointerdown 已注册', typeof (bar.handlers.pointerdown || [])[0] === 'function');
chk('pointermove 已注册', typeof (bar.handlers.pointermove || [])[0] === 'function');

const m0 = /left:(-?\d+)px;top:(-?\d+)px/.exec(bar.style.cssText) || [0, 0, 0];
const startLeft = parseFloat(m0[1]) || 0;
const startTop = parseFloat(m0[2]) || 0;
bar.style.left = startLeft + 'px';
bar.style.top = startTop + 'px';

// 按下 → 向左上拖动 (-300,-200)，落在可视区内
bar.dispatch('pointerdown', { pointerId: 1, clientX: 500, clientY: 500, cancelable: true, preventDefault() {} });
bar.dispatch('pointermove', { pointerId: 1, clientX: 200, clientY: 300, cancelable: true, preventDefault() {} });
chk('横向拖动生效 ' + startLeft + ' → ' + bar.style.left,
    parseFloat(bar.style.left) === startLeft - 300);
chk('纵向拖动生效 ' + startTop + ' → ' + bar.style.top,
    parseFloat(bar.style.top) === startTop - 200);
chk('setPointerCapture 已拿走指针', bar.captured === true);

// 拖动期间必须锁住原生父级，并挂上非被动 touchmove 吃掉页面滚动
chk('pointerdown 已调用 lockTouch(true)', lockCalls[0] === true);
const tm = winListeners.filter(function (x) { return x[0] === 'touchmove'; })[0];
chk('window 上挂了 touchmove 且 passive:false',
    tm && tm[2] && tm[2].passive === false && tm[2].capture === true);
let prevented = 0;
if (tm) tm[1]({ cancelable: true, preventDefault: function () { prevented++; } });
chk('touchmove 已经 preventDefault（页面不跟着滚）', prevented === 1);

// 越界：拖到屏幕外 → 必须被夹在可视区内
bar.dispatch('pointermove', { pointerId: 1, clientX: 9999, clientY: 9999, cancelable: true, preventDefault() {} });
chk('右下越界被夹住 x=' + bar.style.left + ' y=' + bar.style.top,
    parseFloat(bar.style.left) === 1080 - 86 && parseFloat(bar.style.top) === 800 - 200);

bar.dispatch('pointermove', { pointerId: 1, clientX: -9999, clientY: -9999, cancelable: true, preventDefault() {} });
chk('左上越界被夹住 x=' + bar.style.left + ' y=' + bar.style.top,
    parseFloat(bar.style.left) === 0 && parseFloat(bar.style.top) === 0);

bar.dispatch('pointerup', { pointerId: 1, clientX: 0, clientY: 0 });
chk('pointerup 已解锁 lockTouch(false)', lockCalls[lockCalls.length - 1] === false);
chk('松手后 touchmove 监听已摘掉',
    winListeners.filter(function (x) { return x[0] === 'touchmove'; }).length === 0);
chk('拖动后位置已写入 localStorage: ' + saved, saved === '0,0');

// 别的指针 id 不该干扰
bar.dispatch('pointermove', { pointerId: 9, clientX: 300, clientY: 300, cancelable: true, preventDefault() {} });
chk('其它 pointerId 被忽略', parseFloat(bar.style.left) === 0);

console.log(fail === 0 ? 'DRAG_ALL_PASS' : ('DRAG_FAIL=' + fail));
process.exit(fail === 0 ? 0 : 1);
