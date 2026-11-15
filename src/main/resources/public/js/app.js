// Mini-Xchange front end.
// The page only DISPLAYS data: every rule (matching, validation, money) lives in the Java server.

// ------------------------------------------------------------ session
let token = null, username = null;
try {
  token = localStorage.getItem("minix_token");
  username = localStorage.getItem("minix_user");
} catch (ignored) {}
if (!token) window.location.href = "/";

const state = {
  selected: null,        // symbol currently shown
  side: "BUY",
  type: "LIMIT",
  stocks: {},            // symbol -> stock JSON
  books: {},             // symbol -> order book JSON
  portfolio: null,
  recent: [],            // latest trades (all stocks)
};

// ------------------------------------------------------------ helpers
const $ = (id) => document.getElementById(id);
const fmt = (n) => n == null ? "–" : Number(n).toLocaleString("en-IN", { minimumFractionDigits: 2, maximumFractionDigits: 2 });
const money = (n) => "₹" + fmt(n);
const time = (ms) => new Date(ms).toLocaleTimeString("en-GB");
const signed = (n) => (n >= 0 ? "+" : "") + fmt(n);
const cls = (n) => n > 0 ? "up" : n < 0 ? "down" : "";
const esc = (s) => String(s).replace(/[&<>"]/g, c => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" }[c]));

/** Calls the Java API. GET for reads; POST sends form fields. Throws with the server's message. */
async function api(path, fields) {
  const opts = { headers: { "X-Auth-Token": token } };
  if (fields) { opts.method = "POST"; opts.body = new URLSearchParams(fields); }
  const res = await fetch("/api/" + path, opts);
  const data = await res.json();
  if (res.status === 401) { logout(); throw new Error(data.error); }
  if (!res.ok) throw new Error(data.error || "Request failed");
  return data;
}

function toast(msg, kind = "") {
  const el = document.createElement("div");
  el.className = "toast " + kind;
  el.textContent = msg;
  $("toasts").appendChild(el);
  setTimeout(() => el.remove(), 4500);
}

function logout() {
  fetch("/api/logout", { method: "POST", headers: { "X-Auth-Token": token } }).catch(() => {});
  try { localStorage.removeItem("minix_token"); localStorage.removeItem("minix_user"); } catch (ignored) {}
  window.location.href = "/";
}

// ------------------------------------------------------------ chart (Chart.js)
const chart = new Chart($("chart"), {
  type: "line",
  data: { labels: [], datasets: [{ data: [], borderColor: "#4f8cff", borderWidth: 2, pointRadius: 0, tension: 0.15, fill: true,
          backgroundColor: "rgba(79,140,255,0.08)" }] },
  options: {
    animation: false, maintainAspectRatio: false,
    plugins: { legend: { display: false } },
    scales: {
      x: { ticks: { color: "#8b96ad", maxTicksLimit: 8 }, grid: { color: "rgba(34,44,64,0.5)" } },
      y: { ticks: { color: "#8b96ad" }, grid: { color: "rgba(34,44,64,0.5)" } },
    },
  },
});

async function loadHistory(symbol) {
  const h = await api("history?symbol=" + symbol);
  chart.data.labels = h.points.map(p => time(p.t));
  chart.data.datasets[0].data = h.points.map(p => p.p);
  chart.update();
}

function addChartPoint(trade) {
  chart.data.labels.push(time(trade.time));
  chart.data.datasets[0].data.push(trade.price);
  if (chart.data.labels.length > 300) { chart.data.labels.shift(); chart.data.datasets[0].data.shift(); }
}

// ------------------------------------------------------------ rendering
function renderTickers() {
  const box = $("tickers");
  for (const s of Object.values(state.stocks)) {
    let el = $("tk-" + s.symbol);
    if (!el) {
      el = document.createElement("div");
      el.className = "ticker";
      el.id = "tk-" + s.symbol;
      el.onclick = () => selectStock(s.symbol);
      box.appendChild(el);
    }
    const old = Number(el.dataset.price || s.price);
    if (s.price !== old) {
      el.classList.remove("flash-up", "flash-down");
      void el.offsetWidth;   // restart the CSS animation
      el.classList.add(s.price > old ? "flash-up" : "flash-down");
    }
    el.dataset.price = s.price;
    el.classList.toggle("selected", s.symbol === state.selected);
    el.innerHTML = `<div class="sym">${esc(s.symbol)}</div><div class="name">${esc(s.name)}</div>
      <div class="row"><span class="px num">${fmt(s.price)}</span>
      <span class="num ${cls(s.changePct)}">${signed(s.changePct)}%</span></div>`;
  }
}

function renderSelected() {
  const s = state.stocks[state.selected];
  if (!s) return;
  $("selName").textContent = `${s.symbol} · ${s.name}`;
  $("selPrice").textContent = fmt(s.price);
  $("selChange").textContent = `${signed(s.price - s.open)} (${signed(s.changePct)}%)`;
  $("selChange").className = "num " + cls(s.changePct);
  $("selOpen").textContent = fmt(s.open);
  $("selHigh").textContent = fmt(s.high);
  $("selLow").textContent = fmt(s.low);
  $("selVol").textContent = s.volume.toLocaleString("en-IN");
  $("formSymbol").textContent = s.symbol;
  $("bookSymbol").textContent = s.symbol;
}

function renderBook() {
  const book = state.books[state.selected];
  if (!book) return;
  const maxQty = Math.max(1, ...book.asks.map(l => l.quantity), ...book.bids.map(l => l.quantity));
  const row = (l, side) => `<div class="book-row ${side}" data-price="${l.price}">
      <div class="bar" style="width:${(l.quantity / maxQty * 100).toFixed(1)}%"></div>
      <span>${fmt(l.price)}</span><span>${l.quantity}</span><span>${l.orders}</span></div>`;
  // asks are shown highest at the top, so the best (lowest) ask sits just above the spread
  $("asks").innerHTML = book.asks.slice().reverse().map(l => row(l, "ask")).join("") || `<div class="empty">No sellers</div>`;
  $("bids").innerHTML = book.bids.map(l => row(l, "bid")).join("") || `<div class="empty">No buyers</div>`;
  const bestAsk = book.asks[0], bestBid = book.bids[0];
  $("spread").textContent = bestAsk && bestBid
    ? `Spread ${fmt(bestAsk.price - bestBid.price)}`
    : "–";
  // clicking a level copies its price into the order form
  document.querySelectorAll(".book-row").forEach(r => r.onclick = () => {
    $("price").value = r.dataset.price; updateEstimate();
  });
}

function renderRecent() {
  $("recentTrades").innerHTML = state.recent.slice(0, 40).map(t => `<tr>
      <td class="num muted">${time(t.time)}</td><td><b>${esc(t.symbol)}</b></td>
      <td class="r num ${t.side === "BUY" ? "up" : "down"}">${fmt(t.price)}</td><td class="r num">${t.quantity}</td>
      <td>${t.buyer === username ? "<b>You</b>" : esc(t.buyer)}</td>
      <td>${t.seller === username ? "<b>You</b>" : esc(t.seller)}</td></tr>`).join("")
    || `<tr><td colspan="6" class="empty">No trades yet</td></tr>`;
}

async function refreshOpenOrders() {
  const orders = await api("orders");
  $("openOrders").innerHTML = orders.map(o => `<tr>
      <td class="num muted">${o.id}</td><td><b>${esc(o.symbol)}</b></td>
      <td><span class="tag ${o.side}">${o.side}</span></td><td>${o.type}</td>
      <td class="r num">${fmt(o.price)}</td><td class="r num">${o.filled} / ${o.quantity}</td>
      <td class="r"><button class="ghost small" onclick="cancelOrder(${o.id})">Cancel</button></td></tr>`).join("")
    || `<tr><td colspan="7" class="empty">No open orders</td></tr>`;
}

async function refreshPortfolio() {
  const p = await api("portfolio");
  state.portfolio = p;
  $("topValue").textContent = money(p.totalValue);
  $("topPnl").textContent = `${signed(p.totalPnlPct)}%`;
  $("topPnl").className = "num " + cls(p.totalPnl);
  $("pTotal").textContent = money(p.totalValue);
  $("pPnl").textContent = `${signed(p.totalPnl)} (${signed(p.totalPnlPct)}%)`;
  $("pPnl").className = "num " + cls(p.totalPnl);
  $("pCash").textContent = `${money(p.availableCash)} / ${money(p.reservedCash)}`;
  $("pHoldings").textContent = money(p.holdingsValue);
  $("holdings").innerHTML = p.holdings.map(h => `<tr>
      <td><b>${esc(h.symbol)}</b></td><td class="r num">${h.quantity}</td><td class="r num">${h.reserved}</td>
      <td class="r num">${fmt(h.avgPrice)}</td><td class="r num">${fmt(h.lastPrice)}</td>
      <td class="r num">${fmt(h.value)}</td>
      <td class="r num ${cls(h.pnl)}">${signed(h.pnl)} (${signed(h.pnlPct)}%)</td></tr>`).join("")
    || `<tr><td colspan="7" class="empty">You don't own any shares yet. Buy some from the Market tab!</td></tr>`;
  updateEstimate();
}

async function refreshMyTrades() {
  const trades = await api("mytrades");
  $("myTrades").innerHTML = trades.map(t => `<tr>
      <td class="num muted">${new Date(t.time).toLocaleString("en-GB")}</td><td><b>${esc(t.symbol)}</b></td>
      <td><span class="tag ${t.side}">${t.side}</span></td><td class="r num">${t.quantity}</td>
      <td class="r num">${fmt(t.price)}</td><td class="r num">${fmt(t.value)}</td><td>${esc(t.counterparty)}</td></tr>`).join("")
    || `<tr><td colspan="7" class="empty">No trades yet</td></tr>`;
}

// ------------------------------------------------------------ order form
function updateEstimate() {
  const s = state.stocks[state.selected];
  if (!s) return;
  const qty = Number($("qty").value) || 0;
  const price = state.type === "LIMIT" ? Number($("price").value) || 0 : s.price;
  $("estValue").textContent = (state.type === "MARKET" ? "≈ " : "") + money(qty * price);
  const p = state.portfolio;
  if (state.side === "BUY") {
    $("availLabel").textContent = "Available cash";
    $("availValue").textContent = p ? money(p.availableCash) : "–";
  } else {
    const h = p ? p.holdings.find(x => x.symbol === s.symbol) : null;
    $("availLabel").textContent = "Shares available to sell";
    $("availValue").textContent = h ? (h.quantity - h.reserved) : 0;
  }
}

function setSide(side) {
  state.side = side;
  document.querySelectorAll(".side-toggle button").forEach(b => b.classList.toggle("active", b.dataset.side === side));
  const btn = $("submitOrder");
  btn.className = side.toLowerCase();
  btn.textContent = `${side === "BUY" ? "Buy" : "Sell"} ${state.selected || ""}`;
  updateEstimate();
}

async function submitOrder() {
  const fields = { symbol: state.selected, side: state.side, type: state.type, quantity: $("qty").value };
  if (state.type === "LIMIT") fields.price = $("price").value;
  try {
    const r = await api("orders", fields);
    toast(r.message, r.filled > 0 ? "ok" : "");
    await Promise.all([refreshPortfolio(), refreshOpenOrders()]);
  } catch (e) {
    toast(e.message, "err");   // e.g. "Insufficient funds: ..." from InsufficientFundsException
  }
}

async function cancelOrder(id) {
  try {
    const r = await api("orders/cancel", { id });
    toast(r.message);
    await Promise.all([refreshPortfolio(), refreshOpenOrders()]);
  } catch (e) { toast(e.message, "err"); }
}
window.cancelOrder = cancelOrder;

async function selectStock(symbol) {
  state.selected = symbol;
  $("price").value = state.stocks[symbol].price.toFixed(2);
  renderTickers(); renderSelected(); renderBook(); setSide(state.side);
  await loadHistory(symbol);
}

// ------------------------------------------------------------ live feed (Server-Sent Events)
function connectLive() {
  const es = new EventSource("/api/stream");
  es.onopen = () => $("liveDot").classList.add("on");
  es.onerror = () => $("liveDot").classList.remove("on");   // the browser reconnects automatically
  es.onmessage = (ev) => {
    const msg = JSON.parse(ev.data);
    msg.stocks.forEach(s => state.stocks[s.symbol] = s);
    state.books = msg.books;

    let mine = false;
    // msg.trades is newest first; add oldest first so the chart stays in time order
    msg.trades.slice().reverse().forEach(t => {
      if (state.recent.some(r => r.id === t.id)) return;
      state.recent.unshift(t);
      if (t.symbol === state.selected) addChartPoint(t);
      if (t.buyer === username || t.seller === username) mine = true;
    });
    state.recent = state.recent.slice(0, 50);
    chart.update();

    if (!state.selected && msg.stocks.length) selectStock(msg.stocks[0].symbol);
    renderTickers(); renderSelected(); renderBook(); renderRecent();
    if (mine) {
      toast("One of your orders just traded.", "ok");
      refreshPortfolio(); refreshOpenOrders();
    }
  };
}

// ------------------------------------------------------------ wiring
document.querySelectorAll(".nav button").forEach(b => b.onclick = () => {
  document.querySelectorAll(".nav button").forEach(x => x.classList.toggle("active", x === b));
  ["market", "portfolio", "history"].forEach(t => $("tab-" + t).classList.toggle("hidden", t !== b.dataset.tab));
  if (b.dataset.tab === "portfolio") refreshPortfolio();
  if (b.dataset.tab === "history") refreshMyTrades();
});
document.querySelectorAll(".side-toggle button").forEach(b => b.onclick = () => setSide(b.dataset.side));
document.querySelectorAll("input[name=otype]").forEach(r => r.onchange = () => {
  state.type = r.value;
  $("priceWrap").classList.toggle("hidden", state.type === "MARKET");
  updateEstimate();
});
$("qty").oninput = updateEstimate;
$("price").oninput = updateEstimate;
$("submitOrder").onclick = submitOrder;
$("logoutBtn").onclick = logout;
$("exportBtn").onclick = () => { window.location.href = "/api/export?token=" + encodeURIComponent(token); };
$("who").textContent = username;

connectLive();
refreshPortfolio().catch(() => {});
refreshOpenOrders().catch(() => {});
setInterval(() => refreshPortfolio().catch(() => {}), 3000);   // keep profit/loss live
