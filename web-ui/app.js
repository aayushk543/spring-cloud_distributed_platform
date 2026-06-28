// ==================== CONFIG ====================
const API = 'http://localhost:8080'; // API Gateway
const WS_URL = 'http://localhost:8083/ws'; // Direct to chat service for WebSocket
let token = localStorage.getItem('token');
let currentUser = localStorage.getItem('username');
let stompClient = null;
let currentRoom = null;

// ==================== INIT ====================
document.addEventListener('DOMContentLoaded', () => {
  setupTabs();
  if (token && currentUser) {
    updateAuthUI(true);
    loadDashboardData();
  }
});

// ==================== TABS ====================
function setupTabs() {
  document.querySelectorAll('.nav-link').forEach(link => {
    link.addEventListener('click', (e) => {
      e.preventDefault();
      const tab = link.dataset.tab;
      document.querySelectorAll('.nav-link').forEach(l => l.classList.remove('active'));
      document.querySelectorAll('.tab-content').forEach(t => t.classList.remove('active'));
      link.classList.add('active');
      document.getElementById(`tab-${tab}`).classList.add('active');

      if (tab === 'jobs') loadJobStats();
      if (tab === 'chat') loadRooms();
      if (tab === 'checkout') { loadProducts(); loadOrders(); }
      if (tab === 'dashboard') loadDashboardData();
    });
  });
}

// ==================== AUTH ====================
async function register(e) {
  e.preventDefault();
  const body = {
    username: document.getElementById('regUsername').value,
    email: document.getElementById('regEmail').value,
    password: document.getElementById('regPassword').value,
  };
  try {
    const res = await api('POST', '/auth/register', body, false);
    token = res.token;
    currentUser = res.username;
    localStorage.setItem('token', token);
    localStorage.setItem('username', currentUser);
    updateAuthUI(true);
    hideModal('loginModal');
    showToast('Registered successfully!', 'success');
  } catch (err) {
    showAuthMsg(err.message, 'error');
  }
}

async function login(e) {
  e.preventDefault();
  const body = {
    username: document.getElementById('loginUsername').value,
    password: document.getElementById('loginPassword').value,
  };
  try {
    const res = await api('POST', '/auth/login', body, false);
    token = res.token;
    currentUser = res.username;
    localStorage.setItem('token', token);
    localStorage.setItem('username', currentUser);
    updateAuthUI(true);
    hideModal('loginModal');
    showToast('Logged in!', 'success');
    loadDashboardData();
  } catch (err) {
    showAuthMsg(err.message, 'error');
  }
}

function logout() {
  token = null;
  currentUser = null;
  localStorage.removeItem('token');
  localStorage.removeItem('username');
  updateAuthUI(false);
  if (stompClient) { stompClient.disconnect(); stompClient = null; }
  showToast('Logged out');
}

function updateAuthUI(loggedIn) {
  const section = document.getElementById('authSection');
  if (loggedIn) {
    section.innerHTML = `<span style="color:var(--text-dim);font-size:14px">👤 ${currentUser}</span>
      <button class="btn btn-secondary btn-sm" onclick="logout()">Logout</button>`;
  } else {
    section.innerHTML = `<button class="btn btn-primary" onclick="showModal('loginModal')">Login</button>`;
  }
}

// ==================== DASHBOARD ====================
async function loadDashboardData() {
  try {
    const [jobStats, rooms, orders] = await Promise.allSettled([
      api('GET', '/jobs/stats'), api('GET', '/chat/rooms'), api('GET', '/orders'),
    ]);
    if (jobStats.status === 'fulfilled') document.getElementById('totalJobs').textContent = jobStats.value.totalJobs || 0;
    if (rooms.status === 'fulfilled') document.getElementById('totalRooms').textContent = rooms.value.length || 0;
    if (orders.status === 'fulfilled') document.getElementById('totalOrders').textContent = orders.value.length || 0;
  } catch (e) { /* silent */ }
}

// ==================== JOB QUEUE ====================
async function submitJob(e) {
  e.preventDefault();
  requireAuth();
  const body = {
    name: document.getElementById('jobName').value,
    payload: document.getElementById('jobPayload').value || '{}',
    priority: document.getElementById('jobPriority').value,
    maxRetries: parseInt(document.getElementById('jobRetries').value),
  };
  try {
    await api('POST', '/jobs', body);
    showToast('Job submitted!', 'success');
    document.getElementById('jobForm').reset();
    loadJobStats();
  } catch (err) { showToast(err.message, 'error'); }
}

async function loadJobStats() {
  try {
    const stats = await api('GET', '/jobs/stats');
    document.getElementById('statQueued').textContent = stats.queuedJobs || 0;
    document.getElementById('statProcessing').textContent = stats.processingJobs || 0;
    document.getElementById('statCompleted').textContent = stats.completedJobs || 0;
    document.getElementById('statFailed').textContent = stats.failedJobs || 0;
    document.getElementById('statDlq').textContent = stats.dlqJobs || 0;
    document.getElementById('statSuccessRate').textContent = (stats.successRate || 0).toFixed(1) + '%';

    const jobs = await api('GET', '/jobs');
    const tbody = document.getElementById('jobsBody');
    tbody.innerHTML = (Array.isArray(jobs) ? jobs : []).slice(0, 20).map(j => `
      <tr>
        <td style="font-family:monospace;font-size:11px">${(j.id || '').substring(0, 8)}...</td>
        <td>${j.name}</td>
        <td><span class="badge badge-${(j.priority||'').toLowerCase()}">${j.priority}</span></td>
        <td><span class="badge badge-${(j.status||'').toLowerCase()}">${j.status}</span></td>
        <td>${j.retryCount}/${j.maxRetries}</td>
        <td style="font-size:12px;color:var(--text-dim)">${formatTime(j.createdAt)}</td>
      </tr>
    `).join('');
  } catch (e) { /* silent */ }
}

// ==================== CHAT ====================
async function loadRooms() {
  try {
    const rooms = await api('GET', '/chat/rooms');
    const list = document.getElementById('roomList');
    if (rooms.length === 0) {
      list.innerHTML = '<p style="color:var(--text-dim);font-size:13px">No rooms yet. Create one!</p>';
    } else {
      list.innerHTML = rooms.map(r => `
        <div class="room-item ${currentRoom === r.id ? 'active' : ''}" onclick="joinRoom('${r.id}')">
          # ${r.id}
        </div>
      `).join('');
    }
  } catch (e) { /* silent */ }
}

async function createRoom(e) {
  e.preventDefault();
  requireAuth();
  const body = { id: document.getElementById('roomId').value, description: document.getElementById('roomDesc').value };
  try {
    await api('POST', '/chat/rooms', body);
    hideModal('roomModal');
    showToast('Room created!', 'success');
    loadRooms();
  } catch (err) { showToast(err.message, 'error'); }
}

function joinRoom(roomId) {
  requireAuth();
  currentRoom = roomId;
  document.getElementById('chatHeader').textContent = `# ${roomId}`;
  document.getElementById('chatInput').disabled = false;
  document.getElementById('chatSendBtn').disabled = false;
  document.getElementById('chatMessages').innerHTML = '';
  loadRooms();
  loadChatHistory(roomId);
  connectWebSocket(roomId);
}

async function loadChatHistory(roomId) {
  try {
    const page = await api('GET', `/chat/rooms/${roomId}/messages?page=0&size=50`);
    const messages = (page.content || []).reverse();
    const container = document.getElementById('chatMessages');
    messages.forEach(m => appendChatMessage(m));
    container.scrollTop = container.scrollHeight;
  } catch (e) { /* silent */ }
}

function connectWebSocket(roomId) {
  if (stompClient) { stompClient.disconnect(); }
  const socket = new SockJS(WS_URL);
  stompClient = Stomp.over(socket);
  stompClient.debug = null; // Silence debug logs

  stompClient.connect({}, () => {
    stompClient.subscribe(`/topic/room/${roomId}`, (msg) => {
      const message = JSON.parse(msg.body);
      appendChatMessage(message);
    });
    // Send join
    stompClient.send(`/app/chat.join/${roomId}`, {}, JSON.stringify({
      sender: currentUser, roomId: roomId, type: 'JOIN',
    }));
  });
}

function sendChatMessage(e) {
  e.preventDefault();
  const input = document.getElementById('chatInput');
  if (!input.value.trim() || !stompClient || !currentRoom) return;

  stompClient.send(`/app/chat.send/${currentRoom}`, {}, JSON.stringify({
    sender: currentUser, content: input.value, roomId: currentRoom, type: 'CHAT',
  }));
  input.value = '';
}

function appendChatMessage(msg) {
  const container = document.getElementById('chatMessages');
  const isOwn = msg.sender === currentUser;
  const isSystem = msg.type === 'JOIN' || msg.type === 'LEAVE' || msg.type === 'SYSTEM';

  if (isSystem) {
    container.innerHTML += `<div class="chat-msg system">${msg.content || msg.sender + ' ' + (msg.type === 'JOIN' ? 'joined' : 'left')}</div>`;
  } else {
    container.innerHTML += `<div class="chat-msg ${isOwn ? 'own' : ''}">
      <div class="msg-sender">${msg.sender}</div>
      <div>${msg.content}</div>
      <div class="msg-time">${formatTime(msg.timestamp)}</div>
    </div>`;
  }
  container.scrollTop = container.scrollHeight;
}

// ==================== CHECKOUT ====================
async function loadProducts() {
  try {
    const products = await api('GET', '/inventory');
    const grid = document.getElementById('productGrid');
    const select = document.getElementById('orderProduct');
    grid.innerHTML = products.map(p => `
      <div class="product-card">
        <h4>${p.name}</h4>
        <p style="font-size:12px;color:var(--text-dim)">${p.description}</p>
        <div class="price">$${p.price}</div>
        <div class="stock">Stock: ${p.quantity - p.reservedQuantity} / ${p.quantity}</div>
      </div>
    `).join('');
    select.innerHTML = products.map(p => `<option value="${p.id}">${p.name} - $${p.price}</option>`).join('');
  } catch (e) { /* silent */ }
}

async function placeOrder(e) {
  e.preventDefault();
  requireAuth();
  const body = {
    userId: currentUser,
    productId: document.getElementById('orderProduct').value,
    quantity: parseInt(document.getElementById('orderQty').value),
    idempotencyKey: `order-${Date.now()}-${Math.random().toString(36).slice(2)}`,
  };
  try {
    await api('POST', '/orders', body);
    showToast('Order placed! Saga started...', 'success');
    setTimeout(loadOrders, 2000); // Let saga progress
    setTimeout(loadOrders, 5000);
    setTimeout(loadProducts, 5000);
  } catch (err) { showToast(err.message, 'error'); }
}

async function loadOrders() {
  try {
    const orders = await api('GET', '/orders');
    const tbody = document.getElementById('ordersBody');
    tbody.innerHTML = (orders || []).slice(0, 20).map(o => `
      <tr>
        <td style="font-family:monospace;font-size:11px">${(o.id||'').substring(0, 8)}...</td>
        <td>${o.productId}</td>
        <td>${o.quantity}</td>
        <td><span class="badge badge-${(o.status||'').toLowerCase()}">${o.status}</span></td>
        <td><span class="badge badge-${o.sagaStatus === 'COMPLETED' ? 'completed' : o.sagaStatus === 'FAILED' ? 'failed' : 'processing'}">${o.sagaStatus}</span></td>
        <td><button class="btn btn-secondary btn-sm" onclick="viewSaga('${o.id}')">View Saga</button></td>
      </tr>
    `).join('');
  } catch (e) { /* silent */ }
}

async function viewSaga(orderId) {
  try {
    const logs = await api('GET', `/orders/${orderId}/saga`);
    const timeline = document.getElementById('sagaTimeline');
    timeline.innerHTML = logs.map(l => `
      <div class="saga-step">
        <div class="saga-dot ${l.status === 'SUCCESS' ? 'success' : l.status === 'FAILED' ? 'failed' : 'pending'}"></div>
        <div class="saga-step-info">
          <div class="saga-step-name">${l.step}</div>
          <div class="saga-step-time">${l.payload} • ${formatTime(l.timestamp)}</div>
        </div>
      </div>
    `).join('');
    showModal('sagaModal');
  } catch (err) { showToast(err.message, 'error'); }
}

// ==================== HELPERS ====================
async function api(method, path, body = null, auth = true) {
  const headers = { 'Content-Type': 'application/json' };
  if (auth && token) headers['Authorization'] = `Bearer ${token}`;
  const opts = { method, headers };
  if (body) opts.body = JSON.stringify(body);
  const res = await fetch(`${API}${path}`, opts);
  if (!res.ok) {
    const err = await res.json().catch(() => ({ error: res.statusText }));
    throw new Error(err.error || err.message || 'Request failed');
  }
  return res.json().catch(() => ({}));
}

function requireAuth() {
  if (!token) { showModal('loginModal'); throw new Error('Login required'); }
}

function showModal(id) { document.getElementById(id).classList.add('active'); }
function hideModal(id) { document.getElementById(id).classList.remove('active'); }

function switchModalTab(btn, formId) {
  document.querySelectorAll('.mtab').forEach(b => b.classList.remove('active'));
  btn.classList.add('active');
  document.querySelectorAll('.modal-form').forEach(f => f.style.display = 'none');
  document.getElementById(formId).style.display = 'flex';
}

function showAuthMsg(msg, type) {
  const el = document.getElementById('authMessage');
  el.textContent = msg;
  el.className = `auth-msg ${type}`;
}

function showToast(msg, type = '') {
  const toast = document.getElementById('toast');
  toast.textContent = msg;
  toast.className = `toast show ${type}`;
  setTimeout(() => toast.classList.remove('show'), 3000);
}

function formatTime(ts) {
  if (!ts) return '';
  try { return new Date(ts).toLocaleTimeString(); } catch { return ts; }
}
