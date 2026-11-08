// 用最小 DOM 桩跑一遍注入脚本：抓运行期错误 + 验证 scanKill/scanFloat 的「两阶段 + 导航条保护」语义。
const vm = require('vm');
const fs = require('fs');
const js = fs.readFileSync('/home/SQL916/zuoyebang_xposed/.agent/snips/webkiller.js', 'utf8');

const intervals = [], timeouts = [], observers = [];

function El(tag) {
  this.tagName = tag || 'DIV';
  this.children = [];
  this.attrs = {};
  this.className = '';
  this.id = '';
  this.textContent = '';
  this.childElementCount = 0;
  this.clientHeight = 0;
  this.parentElement = null;
  this.fakePos = 'static';
  const self = this;
  this.style = {
    cssText: '', display: '', opacity: '', left: '', top: '',
    setProperty: function (k, v) { self.style[k] = v; },
    getPropertyValue: function () { return ''; }
  };
}
El.prototype.setAttribute = function (k, v) { this.attrs[k] = v; };
El.prototype.getAttribute = function (k) { return Object.prototype.hasOwnProperty.call(this.attrs, k) ? this.attrs[k] : null; };
El.prototype.addEventListener = function () {};
El.prototype.appendChild = function (c) { this.children.push(c); c.parentElement = this; return c; };
El.prototype.remove = function () {};
El.prototype.querySelector = function () { return null; };
El.prototype.querySelectorAll = function () { return []; };
El.prototype.closest = function () { return null; };
El.prototype.matches = function () { return false; };
El.prototype.getBoundingClientRect = function () { return { left: 0, top: 0, width: 100, height: 20 }; };

/* ---- 假页面：AI 入口（普通容器内） / AI 入口（导航条 li 内） / 视频浮卡 / 全屏播放器 ---- */
const p1 = new El('DIV');                 // AI 入口的父容器 → 期望被 hide
const ai = new El('SPAN'); ai.parentElement = p1;
const li = new El('LI');                  // 导航条项 → 期望原样
const aiNav = new El('SPAN');
aiNav.closest = function () { return li; };

const fx = new El('DIV'); fx.clientHeight = 100; fx.fakePos = 'fixed';   // 100 < 0.6*800
const vid = new El('DIV'); vid.parentElement = fx;
const big = new El('DIV'); big.clientHeight = 3000; big.fakePos = 'fixed'; // 全屏播放器 → 期望原样
const vid2 = new El('DIV'); vid2.parentElement = big;

const body = new El('BODY');
body.textContent = '题目'.repeat(200) + '答案解析';    // 404 字，含关键词
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
  querySelectorAll: function (sel) {
    if (sel.indexOf('ai-tab') >= 0) return [ai, aiNav];
    if (sel.indexOf('video') >= 0) return [vid, vid2];
    return [];
  },
  addEventListener: function () {}
};

const sandbox = {
  document,
  location: { href: 'https://example.test/answer' },
  innerWidth: 1080, innerHeight: 800,
  navigator: {},
  localStorage: { getItem() { return null; }, setItem() {}, removeItem() {} },
  getComputedStyle: function (el) {
    return { display: 'block', visibility: 'visible', opacity: '1', position: el.fakePos || 'static' };
  },
  MutationObserver: function (fn) { this.fn = fn; this.observe = function () {}; observers.push(this); },
  setTimeout: function (fn) { timeouts.push(fn); return timeouts.length; },
  clearTimeout: function () {},
  setInterval: function (fn) { intervals.push(fn); return intervals.length; },
  Promise, Date, Math, JSON, String, RegExp, console
};
sandbox.window = sandbox;
sandbox.self = sandbox;

vm.createContext(sandbox);
vm.runInContext(js, sandbox, { filename: 'webkiller.js' });

let fail = 0;
function chk(name, ok) { console.log((ok ? 'ok   ' : 'FAIL ') + name); if (!ok) fail++; }

chk('__zybclean 标记', sandbox.__zybclean === 1);
chk('window.__zybBodyLen 已缓存=404', sandbox.__zybBodyLen === 404);
chk('浮窗已创建', document.getElementById('zyb-bar') !== null);
chk('gate 通过 → display flex', document.getElementById('zyb-bar').style.display === 'flex');
chk('scanKill 隐藏了 AI 入口父容器', p1.style.display === 'none');
chk('导航条 li 内节点未被隐藏', li.style.display !== 'none' && aiNav.style.display !== 'none');
chk('视频浮卡被隐藏', fx.style.display === 'none');
chk('全屏播放器未被隐藏', big.style.display !== 'none');
chk('__zybBarShow 存在', typeof sandbox.__zybBarShow === 'function');
chk('__zybBarShow() 返回 true', sandbox.__zybBarShow() === true);

// 模拟 DOM 变更 → observer + 450ms 防抖；再跑 3s 心跳
observers[0].fn();
const pend = timeouts.splice(0);
pend.forEach(function (fn) { fn(); });
intervals.forEach(function (fn) { fn(); });
chk('变更后仍无异常且浮窗保持 flex', document.getElementById('zyb-bar').style.display === 'flex');

// 长按消失（__zybBarOp=0）后 gate 不得把浮窗拉回来
sandbox.__zybBarOp = 0;
intervals.forEach(function (fn) { fn(); });
chk('__zybBarOp=0 时 gate 隐藏浮窗', document.getElementById('zyb-bar').style.display === 'none');

sandbox.__zybBarOp = 1;   // 复位：上面那条测的是长按消失态
// 换 URL（SPA）→ 重新测量并恢复
sandbox.location.href = 'https://example.test/answer/2';
intervals.forEach(function (fn) { fn(); });
chk('换 URL 后重新测量并显示', sandbox.__zybBodyLen === 404 &&
    document.getElementById('zyb-bar').style.display === 'flex');

console.log(fail === 0 ? 'SMOKE_ALL_PASS' : ('SMOKE_FAIL=' + fail));
process.exit(fail === 0 ? 0 : 1);
