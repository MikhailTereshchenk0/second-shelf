const SESSION_KEY = 'secondShelf.session';
const API_BASE_KEY = 'secondShelf.apiBase';
const LEGACY_SESSION_KEY = 'secondShelfSession';

const state = {
  view: 'catalog',
  busy: false,
  notice: null,
  apiBase: loadApiBase(),
  session: loadSession(),
  currentUser: null,
  profile: null,
  catalog: [],
  myBooks: [],
  outgoing: [],
  incoming: [],
  notifications: [],
  unreadCount: 0,
  terminalFailedOutbox: [],
  selectedRequestedBookId: null,
  editingBookId: null,
  publicProfile: null,
  adminResult: null,
  dlqResult: null,
  healthMessage: '',
  filters: {
    catalog: '',
    notifications: 'all'
  }
};

const root = document.getElementById('root');

const views = [
  { id: 'catalog', label: 'Каталог' },
  { id: 'books', label: 'Мои книги', auth: true },
  { id: 'exchanges', label: 'Обмены', auth: true },
  { id: 'notifications', label: 'Уведомления', auth: true },
  { id: 'profile', label: 'Профиль', auth: true },
  { id: 'admin', label: 'Администрирование', admin: true },
  { id: 'settings', label: 'Настройки' }
];

const exchangeLabels = {
  PENDING: 'Ожидает владельца',
  OWNER_OFFERED: 'Есть встречное предложение',
  ACCEPTED: 'Согласован',
  COMPLETION_PENDING: 'Ждет второго подтверждения',
  REPAIR_REQUIRED: 'Требует ремонта',
  DECLINED: 'Отклонен',
  CANCELLED: 'Отменен',
  COMPLETED: 'Завершен'
};

const bookStatusLabels = {
  AVAILABLE: 'Доступна',
  RESERVED: 'Зарезервирована',
  EXCHANGED: 'Обменяна'
};

const visibilityLabels = {
  PUBLIC: 'Публичная',
  PRIVATE: 'Приватная'
};

function defaultApiBase() {
  if (window.SECOND_SHELF_API_BASE) {
    return normalizeApiBase(window.SECOND_SHELF_API_BASE);
  }
  return 'http://localhost:8088/api';
}

function normalizeApiBase(value) {
  const raw = String(value || '').trim();
  if (!raw) return defaultApiBase();
  if (raw.startsWith('/')) return raw.replace(/\/+$/, '');

  try {
    const url = new URL(raw);
    if (url.pathname === '/' || url.pathname === '') {
      url.pathname = '/api';
    }
    return url.toString().replace(/\/+$/, '');
  } catch (_) {
    return raw.replace(/\/+$/, '');
  }
}

function loadApiBase() {
  return normalizeApiBase(localStorage.getItem(API_BASE_KEY) || defaultApiBase());
}

function loadSession() {
  try {
    const stored = localStorage.getItem(SESSION_KEY) || localStorage.getItem(LEGACY_SESSION_KEY);
    return JSON.parse(stored) || null;
  } catch (_) {
    return null;
  }
}

function saveSession(session) {
  state.session = session;
  if (session) {
    localStorage.setItem(SESSION_KEY, JSON.stringify(session));
    localStorage.removeItem(LEGACY_SESSION_KEY);
  } else {
    localStorage.removeItem(SESSION_KEY);
    localStorage.removeItem(LEGACY_SESSION_KEY);
  }
}

function isAuthenticated() {
  return Boolean(state.session?.accessToken);
}

function roles() {
  return state.profile?.roles || state.currentUser?.roles || [];
}

function isAdmin() {
  return roles().some(role => role === 'ROLE_ADMIN' || role === 'ADMIN');
}

function pageContent(pageOrArray) {
  if (!pageOrArray) return [];
  return Array.isArray(pageOrArray) ? pageOrArray : (pageOrArray.content || []);
}

function escapeHtml(value) {
  return String(value ?? '')
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#039;');
}

function text(value, fallback = '-') {
  const normalized = value === null || value === undefined || value === '' ? fallback : value;
  return escapeHtml(normalized);
}

function valueAttr(value) {
  return escapeHtml(value ?? '');
}

function numberValue(value) {
  if (value === null || value === undefined || value === '') return null;
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : null;
}

function truncate(value, size = 120) {
  const normalized = String(value ?? '');
  if (normalized.length <= size) return normalized;
  return `${normalized.slice(0, size - 1)}...`;
}

function formatDate(value) {
  if (!value) return '-';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return text(value);
  return date.toLocaleString('ru-RU', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit'
  });
}

function idempotencyKey() {
  if (window.crypto?.randomUUID) return window.crypto.randomUUID();
  return `second-shelf-${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

function cleanPayload(payload, options = {}) {
  const keepEmpty = new Set(options.keepEmpty || []);
  return Object.fromEntries(Object.entries(payload)
    .map(([key, value]) => [key, typeof value === 'string' ? value.trim() : value])
    .filter(([key, value]) => keepEmpty.has(key) || value !== ''));
}

function notify(message, type = 'success') {
  state.notice = { message, type };
  renderNotice();
  window.clearTimeout(notify.timer);
  notify.timer = window.setTimeout(() => {
    state.notice = null;
    renderNotice();
  }, 5200);
}

function renderNotice() {
  const el = document.getElementById('notice-slot');
  if (!el) return;
  el.innerHTML = state.notice ? `
    <div class="notice ${state.notice.type}" role="status">
      <span>${text(state.notice.message)}</span>
      <button class="btn-ghost" type="button" data-action="dismiss-notice">Закрыть</button>
    </div>
  ` : '';
}

async function apiFetch(path, options = {}) {
  const headers = {
    Accept: 'application/json',
    ...(options.headers || {})
  };

  if (options.body !== undefined && !(options.body instanceof FormData)) {
    headers['Content-Type'] = 'application/json';
  }

  if (options.auth !== false && state.session?.accessToken) {
    headers.Authorization = `Bearer ${state.session.accessToken}`;
  }

  const response = await fetch(`${state.apiBase}${path}`, {
    method: options.method || 'GET',
    headers,
    body: options.body === undefined || options.body instanceof FormData
      ? options.body
      : JSON.stringify(options.body)
  });

  if (response.status === 401 && options.auth !== false && !options.skipRefresh && state.session?.refreshToken) {
    const refreshed = await refreshTokens();
    if (refreshed) {
      return apiFetch(path, { ...options, skipRefresh: true });
    }
  }

  if (response.status === 204) return null;

  const contentType = response.headers.get('content-type') || '';
  const raw = await response.text();
  const payload = raw && contentType.includes('application/json') ? JSON.parse(raw) : raw;

  if (!response.ok) {
    throw new Error(normalizeError(payload, `HTTP ${response.status}`));
  }

  return payload;
}

function normalizeError(payload, fallback) {
  if (!payload) return fallback;
  if (typeof payload === 'string') return payload || fallback;
  if (payload.message) return payload.message;
  if (payload.error) return payload.error;
  if (payload.code && payload.message) return `${payload.code}: ${payload.message}`;
  if (payload.details) {
    const details = Object.values(payload.details).flat().filter(Boolean);
    if (details.length) return details.join('; ');
  }
  return fallback;
}

async function refreshTokens() {
  if (!state.session?.refreshToken) return false;
  try {
    const tokens = await apiFetch('/auth/refresh', {
      method: 'POST',
      auth: false,
      skipRefresh: true,
      body: { refreshToken: state.session.refreshToken }
    });
    saveSession(tokens);
    return true;
  } catch (_) {
    clearAuthenticatedState();
    return false;
  }
}

function clearAuthenticatedState() {
  saveSession(null);
  state.currentUser = null;
  state.profile = null;
  state.myBooks = [];
  state.outgoing = [];
  state.incoming = [];
  state.notifications = [];
  state.unreadCount = 0;
  state.terminalFailedOutbox = [];
  state.selectedRequestedBookId = null;
  state.editingBookId = null;
  state.publicProfile = null;
  state.adminResult = null;
  state.dlqResult = null;
  if (!['catalog', 'settings'].includes(state.view)) {
    state.view = 'catalog';
  }
}

async function runAction(action, successMessage) {
  try {
    state.busy = true;
    render();
    await action();
    if (successMessage) notify(successMessage, 'success');
  } catch (error) {
    notify(error.message || 'Не удалось выполнить действие', 'error');
  } finally {
    state.busy = false;
    render();
  }
}

async function loadPublicCatalog() {
  const catalog = await apiFetch('/v1/books/public?size=100&sort=createdAt,desc', { auth: false });
  state.catalog = pageContent(catalog);
}

async function loadAuthenticatedData() {
  state.currentUser = await apiFetch('/auth/me');
  state.profile = await apiFetch('/v1/users/me');

  const [myBooks, outgoing, incoming, notifications, unread] = await Promise.all([
    apiFetch('/v1/books/my?size=100&sort=createdAt,desc'),
    apiFetch('/v1/exchanges/my/outgoing?size=100&sort=createdAt,desc'),
    apiFetch('/v1/exchanges/my/incoming?size=100&sort=createdAt,desc'),
    apiFetch('/v1/notifications?size=100&sort=createdAt,desc'),
    apiFetch('/v1/notifications/unread-count')
  ]);

  state.myBooks = pageContent(myBooks);
  state.outgoing = pageContent(outgoing);
  state.incoming = pageContent(incoming);
  state.notifications = pageContent(notifications);
  state.unreadCount = unread?.count || 0;

  if (isAdmin()) {
    await loadAdminData();
  } else {
    state.terminalFailedOutbox = [];
  }
}

async function loadAdminData() {
  state.terminalFailedOutbox = await apiFetch('/v1/admin/outbox/terminal-failed');
}

async function refreshAll() {
  await loadPublicCatalog();
  if (isAuthenticated()) {
    await loadAuthenticatedData();
  }
}

function render() {
  const active = activeView();
  root.innerHTML = `
    <div class="app-layout">
      <aside class="sidebar">
        ${renderBrand()}
        ${renderSessionBox()}
        ${renderNavigation()}
        <div class="side-note">
          API: <b>${text(state.apiBase)}</b><br />
          ${state.busy ? 'Выполняется запрос...' : 'Готов к работе'}
        </div>
      </aside>
      <main class="main">
        <header class="topbar">
          <div>
            <h1>${text(active.label)}</h1>
            <p>${text(viewDescription(active.id))}</p>
          </div>
          <div class="topbar-actions">
            <button class="btn-secondary" type="button" data-action="refresh" ${state.busy ? 'disabled' : ''}>Обновить</button>
            <button class="btn-ghost" type="button" data-view="settings">API</button>
          </div>
        </header>
        <div id="notice-slot"></div>
        ${renderMain(active.id)}
      </main>
    </div>
  `;
  renderNotice();
}

function renderBrand() {
  return `
    <div class="brand">
      <div class="brand-mark">SS</div>
      <div>
        <p class="brand-title">Second Shelf</p>
        <p class="brand-subtitle">Обмен книгами</p>
      </div>
    </div>
  `;
}

function renderSessionBox() {
  if (!isAuthenticated()) {
    return `
      <section class="session-box">
        <p class="session-name">Гость</p>
        <p class="session-meta">Каталог доступен без входа. Для обменов нужен аккаунт.</p>
        <div class="actions">
          <button class="btn" type="button" data-view="auth">Войти</button>
        </div>
      </section>
    `;
  }

  return `
    <section class="session-box">
      <p class="session-name">${text(state.profile?.username || state.currentUser?.username)}</p>
      <p class="session-meta">${text(state.profile?.email || 'Авторизован')}</p>
      <div class="badge-row">
        <span class="badge">ID ${text(state.profile?.id)}</span>
        <span class="badge ${isAdmin() ? 'warning' : 'blue'}">${text(roles().join(', ') || 'ROLE_USER')}</span>
      </div>
      <div class="actions">
        <button class="btn-ghost" type="button" data-action="logout">Выйти</button>
      </div>
    </section>
  `;
}

function renderNavigation() {
  const availableViews = views.filter(view => {
    if (view.admin) return isAuthenticated() && isAdmin();
    if (view.auth) return isAuthenticated();
    return true;
  });

  if (!isAuthenticated() && !availableViews.some(view => view.id === 'auth')) {
    availableViews.splice(1, 0, { id: 'auth', label: 'Вход' });
  }

  return `
    <nav class="nav-list" aria-label="Основная навигация">
      ${availableViews.map(view => `
        <button class="nav-button ${state.view === view.id ? 'active' : ''}" type="button" data-view="${view.id}">
          <span>${text(view.label)}</span>
          ${view.id === 'notifications' && state.unreadCount ? `<span class="badge danger">${state.unreadCount}</span>` : ''}
        </button>
      `).join('')}
    </nav>
  `;
}

function activeView() {
  const known = views.find(view => view.id === state.view) || { id: 'auth', label: 'Вход' };
  if (known.auth && !isAuthenticated()) return { id: 'auth', label: 'Вход' };
  if (known.admin && !isAdmin()) return { id: 'catalog', label: 'Каталог' };
  return known;
}

function viewDescription(id) {
  return {
    catalog: 'Публичные книги и создание заявок на обмен.',
    books: 'Ваши книги, видимость и жизненный цикл публикации.',
    exchanges: 'Входящие, исходящие, встречные предложения и завершение.',
    notifications: 'События обменов из notification-service.',
    profile: 'Личные данные, контакты и публичные профили.',
    admin: 'Операционные действия администратора.',
    settings: 'Подключение к API Gateway и локальные настройки.',
    auth: 'Вход или создание нового пользователя.'
  }[id] || '';
}

function renderMain(view) {
  if (view === 'auth') return renderAuthView();

  return `
    <div class="view-stack">
      ${renderMetrics()}
      ${renderView(view)}
    </div>
  `;
}

function renderView(view) {
  switch (view) {
    case 'books': return renderBooksView();
    case 'exchanges': return renderExchangesView();
    case 'notifications': return renderNotificationsView();
    case 'profile': return renderProfileView();
    case 'admin': return renderAdminView();
    case 'settings': return renderSettingsView();
    case 'catalog':
    default: return renderCatalogView();
  }
}

function renderMetrics() {
  return `
    <section class="dashboard-strip" aria-label="Сводка">
      <div class="metric"><span>Каталог</span><b>${state.catalog.length}</b></div>
      <div class="metric"><span>Мои книги</span><b>${state.myBooks.length}</b></div>
      <div class="metric"><span>Активные обмены</span><b>${activeExchangeCount()}</b></div>
      <div class="metric"><span>Новые уведомления</span><b>${state.unreadCount}</b></div>
    </section>
  `;
}

function activeExchangeCount() {
  const active = new Set(['PENDING', 'OWNER_OFFERED', 'ACCEPTED', 'COMPLETION_PENDING', 'REPAIR_REQUIRED']);
  return [...state.incoming, ...state.outgoing].filter(exchange => active.has(exchange.status)).length;
}

function renderAuthView() {
  return `
    <section class="auth-grid">
      <div class="panel">
        <div class="panel-header">
          <div>
            <h2>Вход</h2>
            <p>Для локальной демонстрации обычно доступен seed-админ.</p>
          </div>
        </div>
        <form class="form-grid single" data-form="login">
          <div class="field">
            <label for="login-username">Username</label>
            <input id="login-username" name="username" autocomplete="username" value="admin" required />
          </div>
          <div class="field">
            <label for="login-password">Password</label>
            <input id="login-password" name="password" type="password" autocomplete="current-password" value="admin12345" required />
          </div>
          <div class="form-actions">
            <button class="btn" type="submit" ${state.busy ? 'disabled' : ''}>Войти</button>
          </div>
        </form>
      </div>

      <div class="panel">
        <div class="panel-header">
          <div>
            <h2>Новый пользователь</h2>
            <p>Контактный телефон будет показан второй стороне только на нужном этапе обмена.</p>
          </div>
        </div>
        <form class="form-grid" data-form="register">
          <div class="field"><label>Username</label><input name="username" minlength="3" maxlength="50" required /></div>
          <div class="field"><label>Email</label><input name="email" type="email" maxlength="100" required /></div>
          <div class="field"><label>Имя</label><input name="firstName" minlength="2" maxlength="50" required /></div>
          <div class="field"><label>Фамилия</label><input name="lastName" minlength="2" maxlength="50" required /></div>
          <div class="field"><label>Город</label><input name="city" maxlength="50" /></div>
          <div class="field"><label>Телефон</label><input name="phoneNumber" maxlength="32" placeholder="+375..." /></div>
          <div class="field full"><label>О себе</label><textarea name="about" maxlength="1000"></textarea></div>
          <div class="field full"><label>Пароль</label><input name="password" type="password" autocomplete="new-password" placeholder="StrongPass1!" required /></div>
          <div class="form-actions full">
            <button class="btn" type="submit" ${state.busy ? 'disabled' : ''}>Создать аккаунт</button>
          </div>
        </form>
      </div>
    </section>
    <div class="divider"></div>
    ${renderCatalogView()}
  `;
}

function renderCatalogView() {
  const selectedBook = state.catalog.find(book => String(book.id) === String(state.selectedRequestedBookId));
  return `
    <section class="content-grid">
      <div class="panel">
        <div class="panel-header">
          <div>
            <h2>Публичный каталог</h2>
            <p>Показаны книги со статусом AVAILABLE и видимостью PUBLIC.</p>
          </div>
          <div class="toolbar">
            <div class="field">
              <label for="catalog-search">Поиск</label>
              <input id="catalog-search" type="search" data-filter="catalog" value="${valueAttr(state.filters.catalog)}" placeholder="Название, автор, описание" />
            </div>
          </div>
        </div>
        <div id="catalog-grid">
          ${renderBooksGrid(filteredCatalog(), { catalog: true })}
        </div>
      </div>
      <aside class="panel">
        <h2>Заявка на обмен</h2>
        ${renderCreateExchangePanel(selectedBook)}
      </aside>
    </section>
  `;
}

function renderCreateExchangePanel(book) {
  if (!isAuthenticated()) {
    return `
      <p class="muted">Войдите или зарегистрируйтесь, чтобы отправить владельцу заявку на книгу.</p>
      <div class="actions">
        <button class="btn" type="button" data-view="auth">Перейти ко входу</button>
      </div>
    `;
  }

  if (!book) {
    return '<p class="muted">Выберите книгу в каталоге. После отправки владелец сможет выбрать одну из ваших публичных доступных книг как встречное предложение.</p>';
  }

  return `
    <div class="mini-box">
      <span>Выбрана книга</span>
      <b>${text(book.title)}</b>
      <p class="muted small">${text(book.author)} · owner ID ${text(book.ownerId)}</p>
    </div>
    <form class="form-grid single" data-form="create-exchange">
      <input type="hidden" name="requestedBookId" value="${valueAttr(book.id)}" />
      <div class="field">
        <label>Сообщение владельцу</label>
        <textarea name="message" maxlength="1000" placeholder="Например: могу встретиться вечером в центре"></textarea>
      </div>
      <div class="form-actions">
        <button class="btn" type="submit" ${state.busy ? 'disabled' : ''}>Отправить заявку</button>
        <button class="btn-ghost" type="button" data-action="clear-selected-book">Сбросить</button>
      </div>
    </form>
  `;
}

function filteredCatalog() {
  const term = state.filters.catalog.trim().toLowerCase();
  if (!term) return state.catalog;
  return state.catalog.filter(book => [book.title, book.author, book.description]
    .filter(Boolean)
    .some(value => String(value).toLowerCase().includes(term)));
}

function renderBooksView() {
  return `
    <section class="content-grid">
      <div class="panel">
        <div class="panel-header">
          <div>
            <h2>Мои книги</h2>
            <p>Редактировать, скрывать и удалять можно только книги со статусом AVAILABLE.</p>
          </div>
        </div>
        ${renderBooksGrid(state.myBooks, { mine: true })}
      </div>
      <aside class="panel">
        <h2>Добавить книгу</h2>
        <form class="form-grid single" data-form="create-book">
          <div class="field"><label>Название</label><input name="title" maxlength="200" required /></div>
          <div class="field"><label>Автор</label><input name="author" maxlength="200" required /></div>
          <div class="field">
            <label>Видимость</label>
            <select name="visibility">
              <option value="PUBLIC">Публичная</option>
              <option value="PRIVATE">Приватная</option>
            </select>
          </div>
          <div class="field"><label>Описание</label><textarea name="description" maxlength="2000"></textarea></div>
          <div class="form-actions"><button class="btn" type="submit" ${state.busy ? 'disabled' : ''}>Создать</button></div>
        </form>
      </aside>
    </section>
  `;
}

function renderBooksGrid(books, options = {}) {
  if (!books.length) {
    return '<div class="empty-state">Книг пока нет.</div>';
  }
  return `<div class="cards-grid">${books.map(book => renderBookCard(book, options)).join('')}</div>`;
}

function renderBookCard(book, options = {}) {
  const mine = state.profile && String(book.ownerId) === String(state.profile.id);
  const available = book.status === 'AVAILABLE';
  const canRequest = options.catalog && isAuthenticated() && !mine && available;
  const isEditing = String(state.editingBookId) === String(book.id);

  return `
    <article class="item-card book-card">
      <div class="book-cover ${coverClass(book.id)}">${text(bookInitials(book))}</div>
      <div class="book-body">
        <div class="card-header">
          <div>
            <h3>${text(book.title)}</h3>
            <p>${text(book.author)}</p>
          </div>
          <span class="badge">#${text(book.id)}</span>
        </div>
        <p class="description">${text(truncate(book.description || 'Описание не указано'))}</p>
        <div class="badge-row">
          <span class="badge ${book.visibility === 'PUBLIC' ? 'success' : 'blue'}">${text(visibilityLabels[book.visibility] || book.visibility)}</span>
          <span class="badge ${bookStatusClass(book.status)}">${text(bookStatusLabels[book.status] || book.status)}</span>
          <span class="badge">owner ${text(book.ownerId)}</span>
        </div>
        <p class="muted small">Создана: ${formatDate(book.createdAt)}</p>
        ${isEditing ? renderBookEditForm(book) : ''}
        <div class="actions">
          ${canRequest ? `<button class="btn" type="button" data-select-book="${book.id}">Запросить обмен</button>` : ''}
          ${options.catalog && mine ? '<span class="badge blue">Это ваша книга</span>' : ''}
          ${options.mine && available ? `
            <button class="btn-secondary" type="button" data-edit-book="${book.id}">Редактировать</button>
            ${book.visibility === 'PUBLIC'
              ? `<button class="btn-secondary" type="button" data-book-action="hide" data-book-id="${book.id}">Скрыть</button>`
              : `<button class="btn-secondary" type="button" data-book-action="publish" data-book-id="${book.id}">Опубликовать</button>`}
            <button class="btn-danger" type="button" data-book-action="delete" data-book-id="${book.id}">Удалить</button>
          ` : ''}
          ${options.mine && !available ? '<span class="badge warning">Действия ограничены статусом</span>' : ''}
        </div>
      </div>
    </article>
  `;
}

function coverClass(id) {
  return ['green', 'blue', 'amber'][Number(id || 0) % 3] || '';
}

function bookInitials(book) {
  const source = `${book.title || ''} ${book.author || ''}`.trim();
  if (!source) return 'B';
  return source.split(/\s+/).slice(0, 2).map(part => part[0]).join('').toUpperCase();
}

function bookStatusClass(status) {
  if (status === 'AVAILABLE') return 'success';
  if (status === 'RESERVED') return 'warning';
  if (status === 'EXCHANGED') return 'blue';
  return '';
}

function renderBookEditForm(book) {
  return `
    <form class="inline-form" data-form="update-book" data-book-id="${valueAttr(book.id)}">
      <div class="field"><label>Название</label><input name="title" value="${valueAttr(book.title)}" maxlength="200" /></div>
      <div class="field"><label>Автор</label><input name="author" value="${valueAttr(book.author)}" maxlength="200" /></div>
      <div class="field"><label>Описание</label><textarea name="description" maxlength="2000">${valueAttr(book.description)}</textarea></div>
      <div class="form-actions">
        <button class="btn" type="submit" ${state.busy ? 'disabled' : ''}>Сохранить</button>
        <button class="btn-ghost" type="button" data-action="cancel-edit">Отмена</button>
      </div>
    </form>
  `;
}

function renderExchangesView() {
  return `
    <section class="content-grid equal">
      <div class="panel">
        <div class="panel-header">
          <div>
            <h2>Входящие</h2>
            <p>Заявки на ваши книги. Выберите книгу пользователя как встречное предложение.</p>
          </div>
        </div>
        ${renderExchangeList(state.incoming, 'incoming')}
      </div>
      <div class="panel">
        <div class="panel-header">
          <div>
            <h2>Исходящие</h2>
            <p>Ваши запросы. Примите или отклоните предложение владельца.</p>
          </div>
        </div>
        ${renderExchangeList(state.outgoing, 'outgoing')}
      </div>
    </section>
  `;
}

function renderExchangeList(exchanges, direction) {
  if (!exchanges.length) {
    return '<div class="empty-state">Заявок пока нет.</div>';
  }
  return `<div class="list">${exchanges.map(exchange => renderExchangeCard(exchange, direction)).join('')}</div>`;
}

function renderExchangeCard(exchange, direction) {
  return `
    <article class="item-card exchange-card">
      <div class="row-header">
        <div>
          <h3>Заявка #${text(exchange.id)}</h3>
          <div class="status-line">
            <span class="badge ${exchangeStatusClass(exchange.status)}">${text(exchangeLabels[exchange.status] || exchange.status)}</span>
            <span>Создана: ${formatDate(exchange.createdAt)}</span>
          </div>
        </div>
        ${isAdmin() && exchange.status === 'REPAIR_REQUIRED'
          ? `<button class="btn-secondary" type="button" data-exchange-action="repair" data-exchange-id="${exchange.id}">Repair</button>`
          : ''}
      </div>

      <div class="exchange-books">
        <div class="mini-box">
          <span>Запрошена</span>
          <b>${text(exchange.requestedBookTitle)}</b>
          <p class="muted small">${text(exchange.requestedBookAuthor)} · book ${text(exchange.requestedBookId)}</p>
        </div>
        <div class="mini-box">
          <span>Встречное предложение</span>
          <b>${text(exchange.offeredBookTitle, 'Еще не выбрано')}</b>
          <p class="muted small">${text(exchange.offeredBookAuthor, direction === 'incoming' ? 'Выберите книгу ниже' : 'Ожидайте владельца')} ${exchange.offeredBookId ? `· book ${text(exchange.offeredBookId)}` : ''}</p>
        </div>
      </div>

      <div class="kv-list">
        <div class="kv"><span>Владелец</span><b>${text(exchange.ownerUsernameSnapshot || exchange.ownerId)}</b></div>
        <div class="kv"><span>Инициатор</span><b>${text(exchange.requesterUsernameSnapshot || exchange.requesterId)}</b></div>
        ${exchange.ownerPhoneNumber ? `<div class="kv"><span>Телефон владельца</span><b>${text(exchange.ownerPhoneNumber)}</b></div>` : ''}
        ${exchange.requesterPhoneNumber ? `<div class="kv"><span>Телефон инициатора</span><b>${text(exchange.requesterPhoneNumber)}</b></div>` : ''}
      </div>

      ${exchange.message ? `<p>${text(exchange.message)}</p>` : ''}
      ${renderCompletionState(exchange)}
      ${exchange.repairReason ? `<p class="muted small">Причина ремонта: ${text(exchange.repairReason)}</p>` : ''}
      ${renderExchangeActions(exchange, direction)}
    </article>
  `;
}

function exchangeStatusClass(status) {
  if (status === 'COMPLETED') return 'success';
  if (status === 'DECLINED' || status === 'CANCELLED' || status === 'REPAIR_REQUIRED') return 'danger';
  if (status === 'OWNER_OFFERED' || status === 'PENDING' || status === 'COMPLETION_PENDING') return 'warning';
  if (status === 'ACCEPTED') return 'blue';
  return '';
}

function renderCompletionState(exchange) {
  if (!['ACCEPTED', 'COMPLETION_PENDING', 'COMPLETED'].includes(exchange.status)) return '';
  return `
    <div class="badge-row">
      <span class="badge ${exchange.ownerCompletionConfirmedAt ? 'success' : ''}">Владелец: ${exchange.ownerCompletionConfirmedAt ? formatDate(exchange.ownerCompletionConfirmedAt) : 'не подтвердил'}</span>
      <span class="badge ${exchange.requesterCompletionConfirmedAt ? 'success' : ''}">Инициатор: ${exchange.requesterCompletionConfirmedAt ? formatDate(exchange.requesterCompletionConfirmedAt) : 'не подтвердил'}</span>
    </div>
  `;
}

function renderExchangeActions(exchange, direction) {
  const actions = [];

  if (direction === 'incoming' && exchange.status === 'PENDING') {
    actions.push(renderOwnerOfferForm(exchange));
    actions.push(`<button class="btn-ghost" type="button" data-exchange-action="decline" data-exchange-id="${exchange.id}">Отклонить</button>`);
  }

  if (direction === 'outgoing' && exchange.status === 'OWNER_OFFERED') {
    actions.push(`<button class="btn" type="button" data-exchange-action="accept" data-exchange-id="${exchange.id}">Принять предложение</button>`);
    actions.push(`<button class="btn-ghost" type="button" data-exchange-action="decline-offer" data-exchange-id="${exchange.id}">Отклонить предложение</button>`);
  }

  if (direction === 'outgoing' && canCancel(exchange)) {
    actions.push(`<button class="btn-ghost" type="button" data-exchange-action="cancel" data-exchange-id="${exchange.id}">Отменить</button>`);
  }

  if (canComplete(exchange)) {
    actions.push(`<button class="btn" type="button" data-exchange-action="complete" data-exchange-id="${exchange.id}">Подтвердить завершение</button>`);
  }

  if (!actions.length) return '';
  return `<div class="actions">${actions.join('')}</div>`;
}

function renderOwnerOfferForm(exchange) {
  const books = exchange.requesterAvailableBooks || [];
  if (!books.length) {
    return '<span class="badge warning">У инициатора нет публичных доступных книг</span>';
  }

  return `
    <form class="inline-form" data-form="owner-offer" data-exchange-id="${valueAttr(exchange.id)}">
      <div class="field">
        <label>Книга инициатора для обмена</label>
        <select name="offeredBookId" required>
          ${books.map(book => `<option value="${valueAttr(book.id)}">${text(book.title)} - ${text(book.author)} (#${text(book.id)})</option>`).join('')}
        </select>
      </div>
      <div class="form-actions">
        <button class="btn-secondary" type="submit" ${state.busy ? 'disabled' : ''}>Сделать предложение</button>
      </div>
    </form>
  `;
}

function canCancel(exchange) {
  return ['PENDING', 'OWNER_OFFERED'].includes(exchange.status)
    || (exchange.status === 'ACCEPTED' && !exchange.ownerCompletionConfirmedAt && !exchange.requesterCompletionConfirmedAt);
}

function canComplete(exchange) {
  if (!state.profile || !['ACCEPTED', 'COMPLETION_PENDING'].includes(exchange.status)) return false;
  const myId = String(state.profile.id);
  if (String(exchange.ownerId) === myId) return !exchange.ownerCompletionConfirmedAt;
  if (String(exchange.requesterId) === myId) return !exchange.requesterCompletionConfirmedAt;
  return false;
}

function renderNotificationsView() {
  const notifications = filteredNotifications();
  return `
    <section class="panel">
      <div class="panel-header">
        <div>
          <h2>Уведомления</h2>
          <p>Новые события приходят из RabbitMQ через notification-service.</p>
        </div>
        <div class="toolbar">
          <div class="field">
            <label>Фильтр</label>
            <select data-filter="notifications">
              <option value="all" ${state.filters.notifications === 'all' ? 'selected' : ''}>Все</option>
              <option value="UNREAD" ${state.filters.notifications === 'UNREAD' ? 'selected' : ''}>Непрочитанные</option>
              <option value="READ" ${state.filters.notifications === 'READ' ? 'selected' : ''}>Прочитанные</option>
            </select>
          </div>
          <button class="btn-secondary" type="button" data-action="mark-all-read" ${state.notifications.length ? '' : 'disabled'}>Прочитать все</button>
        </div>
      </div>
      ${renderNotificationList(notifications)}
    </section>
  `;
}

function filteredNotifications() {
  if (state.filters.notifications === 'all') return state.notifications;
  return state.notifications.filter(item => item.status === state.filters.notifications);
}

function renderNotificationList(notifications) {
  if (!notifications.length) {
    return '<div class="empty-state">Уведомлений пока нет.</div>';
  }

  return `
    <div class="list">
      ${notifications.map(notification => `
        <article class="item-card">
          <div class="row-header">
            <div>
              <h3>${text(notification.title)}</h3>
              <p class="muted">${text(notification.message)}</p>
            </div>
            <span class="badge ${notification.status === 'UNREAD' ? 'danger' : 'success'}">${text(notification.status)}</span>
          </div>
          <div class="badge-row">
            <span class="badge blue">${text(notification.type)}</span>
            <span class="badge">${formatDate(notification.createdAt)}</span>
            ${notification.relatedEntityId ? `<span class="badge">${text(notification.relatedEntityType)} #${text(notification.relatedEntityId)}</span>` : ''}
          </div>
          <div class="actions">
            ${notification.status === 'UNREAD' ? `<button class="btn-secondary" type="button" data-notification-read="${notification.id}">Прочитать</button>` : ''}
          </div>
        </article>
      `).join('')}
    </div>
  `;
}

function renderProfileView() {
  return `
    <section class="content-grid">
      <div class="panel">
        <div class="panel-header">
          <div>
            <h2>Мой профиль</h2>
            <p>Телефон используется только в согласованных обменах.</p>
          </div>
        </div>
        <div class="kv-list">
          <div class="kv"><span>ID</span><b>${text(state.profile?.id)}</b></div>
          <div class="kv"><span>Username</span><b>${text(state.profile?.username)}</b></div>
          <div class="kv"><span>Email</span><b>${text(state.profile?.email)}</b></div>
          <div class="kv"><span>Роли</span><b>${text(roles().join(', '))}</b></div>
          <div class="kv"><span>Активен</span><b>${state.profile?.enabled ? 'Да' : 'Нет'}</b></div>
          <div class="kv"><span>Создан</span><b>${formatDate(state.profile?.createdAt)}</b></div>
        </div>
        <div class="divider"></div>
        <form class="form-grid" data-form="update-profile">
          <div class="field"><label>Имя</label><input name="firstName" value="${valueAttr(state.profile?.firstName)}" minlength="2" maxlength="50" /></div>
          <div class="field"><label>Фамилия</label><input name="lastName" value="${valueAttr(state.profile?.lastName)}" minlength="2" maxlength="50" /></div>
          <div class="field"><label>Город</label><input name="city" value="${valueAttr(state.profile?.city)}" maxlength="50" /></div>
          <div class="field"><label>Телефон</label><input name="phoneNumber" value="${valueAttr(state.profile?.phoneNumber)}" maxlength="32" /></div>
          <div class="field full"><label>О себе</label><textarea name="about" maxlength="1000">${valueAttr(state.profile?.about)}</textarea></div>
          <div class="form-actions full"><button class="btn" type="submit" ${state.busy ? 'disabled' : ''}>Сохранить</button></div>
        </form>
      </div>

      <aside class="panel">
        <h2>Публичный профиль</h2>
        <p class="muted">Можно проверить, какие данные видны другим пользователям.</p>
        <form class="form-grid single" data-form="public-profile">
          <div class="field"><label>ID пользователя</label><input name="userId" type="number" min="1" /></div>
          <div class="field"><label>или username</label><input name="username" maxlength="50" /></div>
          <div class="form-actions"><button class="btn-secondary" type="submit">Найти</button></div>
        </form>
        ${state.publicProfile ? `
          <div class="divider"></div>
          <div class="kv-list">
            <div class="kv"><span>ID</span><b>${text(state.publicProfile.id)}</b></div>
            <div class="kv"><span>Username</span><b>${text(state.publicProfile.username)}</b></div>
            <div class="kv"><span>Город</span><b>${text(state.publicProfile.city)}</b></div>
            <div class="kv"><span>О себе</span><b>${text(state.publicProfile.about)}</b></div>
          </div>
        ` : ''}
        <div class="divider"></div>
        <div class="actions">
          <button class="btn-ghost" type="button" data-action="logout">Выйти</button>
          <button class="btn-danger" type="button" data-action="logout-all">Выйти везде</button>
        </div>
      </aside>
    </section>
  `;
}

function renderAdminView() {
  if (!isAdmin()) {
    return '<section class="panel"><div class="empty-state">Для этого раздела нужна роль администратора.</div></section>';
  }

  return `
    <section class="content-grid">
      <div class="panel">
        <div class="panel-header">
          <div>
            <h2>Пользователи</h2>
            <p>Роли и блокировка пользователя по внутреннему ID.</p>
          </div>
        </div>
        <form class="form-grid" data-form="admin-user">
          <div class="field"><label>User ID</label><input name="userId" type="number" min="1" required /></div>
          <div class="field">
            <label>Роли</label>
            <select name="roles">
              <option value="ROLE_USER">ROLE_USER</option>
              <option value="ROLE_USER,ROLE_ADMIN">ROLE_USER + ROLE_ADMIN</option>
            </select>
          </div>
          <div class="form-actions full">
            <button class="btn-secondary" type="submit" name="operation" value="roles">Обновить роли</button>
            <button class="btn-ghost" type="submit" name="operation" value="block">Заблокировать</button>
            <button class="btn-ghost" type="submit" name="operation" value="unblock">Разблокировать</button>
          </div>
        </form>
        ${state.adminResult ? renderAdminResult(state.adminResult) : ''}
      </div>

      <aside class="panel">
        <h2>DLQ уведомлений</h2>
        <form class="form-grid single" data-form="dlq-redrive">
          <div class="field"><label>Лимит сообщений</label><input name="limit" type="number" min="1" max="500" value="100" /></div>
          <div class="form-actions"><button class="btn-secondary" type="submit">Redrive DLQ</button></div>
        </form>
        ${state.dlqResult ? renderDlqResult(state.dlqResult) : ''}
      </aside>
    </section>
    <section class="panel">
      <div class="panel-header">
        <div>
          <h2>Outbox terminal failed</h2>
          <p>Повторная постановка событий exchange-service в очередь публикации.</p>
        </div>
      </div>
      ${renderOutboxList()}
    </section>
  `;
}

function renderAdminResult(profile) {
  return `
    <div class="divider"></div>
    <div class="kv-list">
      <div class="kv"><span>Пользователь</span><b>${text(profile.username)} (#${text(profile.id)})</b></div>
      <div class="kv"><span>Роли</span><b>${text((profile.roles || []).join(', '))}</b></div>
      <div class="kv"><span>Активен</span><b>${profile.enabled ? 'Да' : 'Нет'}</b></div>
    </div>
  `;
}

function renderDlqResult(result) {
  return `
    <div class="divider"></div>
    <div class="kv-list">
      <div class="kv"><span>Запрошено</span><b>${text(result.requestedLimit)}</b></div>
      <div class="kv"><span>Переотправлено</span><b>${text(result.redrivenCount)}</b></div>
      <div class="kv"><span>Пропущено</span><b>${text(result.skippedCount)}</b></div>
      <div class="kv"><span>Ошибок</span><b>${text(result.failedCount)}</b></div>
    </div>
    ${(result.errors || []).length ? `
      <div class="list">
        ${result.errors.map(error => `<div class="mini-box"><b>${text(error.eventType || error.eventId)}</b><p class="muted small">${text(error.reason)}</p></div>`).join('')}
      </div>
    ` : ''}
  `;
}

function renderOutboxList() {
  if (!state.terminalFailedOutbox.length) {
    return '<div class="empty-state">Terminal failed событий нет.</div>';
  }
  return `
    <div class="list">
      ${state.terminalFailedOutbox.map(event => `
        <article class="item-card">
          <div class="row-header">
            <div>
              <h3>${text(event.eventType)}</h3>
              <p class="muted">eventId: ${text(event.eventId)}</p>
            </div>
            <span class="badge danger">${text(event.status)}</span>
          </div>
          <div class="kv-list">
            <div class="kv"><span>Aggregate</span><b>${text(event.aggregateType)} #${text(event.aggregateId)}</b></div>
            <div class="kv"><span>Попытки</span><b>${text(event.attemptsCount)} + manual ${text(event.manualRetryCount)}</b></div>
            <div class="kv"><span>Ошибка</span><b>${text(event.lastError || event.errorCode)}</b></div>
            <div class="kv"><span>Failed at</span><b>${formatDate(event.failedAt)}</b></div>
          </div>
          <div class="actions">
            <button class="btn-secondary" type="button" data-outbox-retry="${valueAttr(event.eventId)}">Retry</button>
          </div>
        </article>
      `).join('')}
    </div>
  `;
}

function renderSettingsView() {
  return `
    <section class="content-grid">
      <div class="panel api-box">
        <div>
          <h2>API Gateway</h2>
          <p class="muted">По умолчанию интерфейс обращается к локальному gateway на порту 8088.</p>
        </div>
        <form class="form-grid single" data-form="api-settings">
          <div class="field">
            <label>Base URL</label>
            <input name="apiBase" value="${valueAttr(state.apiBase)}" placeholder="http://localhost:8088/api" required />
          </div>
          <div class="form-actions">
            <button class="btn" type="submit">Сохранить</button>
            <button class="btn-secondary" type="button" data-action="test-api">Проверить</button>
            <button class="btn-ghost" type="button" data-action="reset-api">Сбросить</button>
          </div>
        </form>
        ${state.healthMessage ? `<div class="mini-box"><b>Проверка</b><p class="muted">${text(state.healthMessage)}</p></div>` : ''}
      </div>

      <aside class="panel">
        <h2>Локальное состояние</h2>
        <div class="kv-list">
          <div class="kv"><span>Сессия</span><b>${isAuthenticated() ? 'Есть access token' : 'Нет'}</b></div>
          <div class="kv"><span>Профиль</span><b>${text(state.profile?.username)}</b></div>
          <div class="kv"><span>Gateway</span><b>${text(state.apiBase)}</b></div>
        </div>
        <div class="divider"></div>
        <div class="actions">
          <button class="btn-ghost" type="button" data-action="clear-local-state">Очистить локальное состояние</button>
        </div>
      </aside>
    </section>
  `;
}

function formData(form) {
  return Object.fromEntries(new FormData(form).entries());
}

async function handleSubmit(event) {
  const form = event.target.closest('form[data-form]');
  if (!form) return;
  event.preventDefault();

  const type = form.dataset.form;
  const data = formData(form);

  await runAction(async () => {
    switch (type) {
      case 'login':
        await login(data);
        break;
      case 'register':
        await register(data);
        break;
      case 'create-book':
        await createBook(data, form);
        break;
      case 'update-book':
        await updateBook(form.dataset.bookId, data);
        break;
      case 'create-exchange':
        await createExchange(data);
        break;
      case 'owner-offer':
        await ownerOffer(form.dataset.exchangeId, data);
        break;
      case 'update-profile':
        await updateProfile(data);
        break;
      case 'public-profile':
        await lookupPublicProfile(data);
        break;
      case 'admin-user':
        await adminUser(data, event.submitter?.value);
        break;
      case 'dlq-redrive':
        await dlqRedrive(data);
        break;
      case 'api-settings':
        saveApiSettings(data);
        break;
      default:
        break;
    }
  }, successMessage(type));
}

async function login(data) {
  const tokens = await apiFetch('/auth/login', {
    method: 'POST',
    auth: false,
    body: cleanPayload(data)
  });
  saveSession(tokens);
  state.view = 'catalog';
  await refreshAll();
}

async function register(data) {
  const tokens = await apiFetch('/auth/register', {
    method: 'POST',
    auth: false,
    body: cleanPayload(data)
  });
  saveSession(tokens);
  state.view = 'catalog';
  await refreshAll();
}

async function createBook(data, form) {
  await apiFetch('/v1/books', {
    method: 'POST',
    body: cleanPayload(data)
  });
  form.reset();
  await refreshAll();
}

async function updateBook(bookId, data) {
  await apiFetch(`/v1/books/${bookId}`, {
    method: 'PATCH',
    body: cleanPayload(data, { keepEmpty: ['description'] })
  });
  state.editingBookId = null;
  await refreshAll();
}

async function createExchange(data) {
  const requestedBookId = numberValue(data.requestedBookId);
  await apiFetch('/v1/exchanges', {
    method: 'POST',
    headers: { 'Idempotency-Key': idempotencyKey() },
    body: cleanPayload({
      requestedBookId,
      message: data.message
    })
  });
  state.selectedRequestedBookId = null;
  state.view = 'exchanges';
  await refreshAll();
}

async function ownerOffer(exchangeId, data) {
  await apiFetch(`/v1/exchanges/${exchangeId}/offer`, {
    method: 'POST',
    body: { offeredBookId: numberValue(data.offeredBookId) }
  });
  await refreshAll();
}

async function updateProfile(data) {
  await apiFetch(`/v1/users/${state.profile.id}`, {
    method: 'PUT',
    body: cleanPayload(data, { keepEmpty: ['city', 'phoneNumber', 'about'] })
  });
  await loadAuthenticatedData();
}

async function lookupPublicProfile(data) {
  const clean = cleanPayload(data);
  if (clean.userId) {
    state.publicProfile = await apiFetch(`/v1/users/${encodeURIComponent(clean.userId)}`);
  } else if (clean.username) {
    state.publicProfile = await apiFetch(`/v1/users/by-username?username=${encodeURIComponent(clean.username)}`);
  } else {
    throw new Error('Укажите ID или username.');
  }
}

async function adminUser(data, operation) {
  const userId = encodeURIComponent(data.userId);
  if (operation === 'roles') {
    state.adminResult = await apiFetch(`/v1/admin/users/${userId}/roles`, {
      method: 'PUT',
      body: { roles: data.roles.split(',') }
    });
  } else if (operation === 'block' || operation === 'unblock') {
    state.adminResult = await apiFetch(`/v1/admin/users/${userId}/${operation}`, { method: 'PUT' });
  }
  await loadAuthenticatedData();
}

async function dlqRedrive(data) {
  state.dlqResult = await apiFetch(`/v1/admin/notifications/dlq/redrive?limit=${encodeURIComponent(data.limit || '100')}`, {
    method: 'POST'
  });
}

function saveApiSettings(data) {
  state.apiBase = normalizeApiBase(data.apiBase);
  localStorage.setItem(API_BASE_KEY, state.apiBase);
  state.healthMessage = 'Адрес сохранен. Нажмите "Проверить", чтобы убедиться в доступности gateway.';
}

function successMessage(type) {
  return ({
    login: 'Вход выполнен.',
    register: 'Аккаунт создан.',
    'create-book': 'Книга создана.',
    'update-book': 'Книга обновлена.',
    'create-exchange': 'Заявка отправлена.',
    'owner-offer': 'Встречное предложение отправлено.',
    'update-profile': 'Профиль обновлен.',
    'public-profile': 'Публичный профиль загружен.',
    'admin-user': 'Админ-операция выполнена.',
    'dlq-redrive': 'DLQ redrive выполнен.',
    'api-settings': 'Настройки API сохранены.'
  })[type];
}

async function handleClick(event) {
  const view = event.target.closest('[data-view]')?.dataset.view;
  if (view) {
    state.view = view;
    render();
    return;
  }

  const action = event.target.closest('[data-action]')?.dataset.action;
  if (action) {
    await handleAction(action);
    return;
  }

  const selectedBook = event.target.closest('[data-select-book]')?.dataset.selectBook;
  if (selectedBook) {
    state.selectedRequestedBookId = selectedBook;
    state.view = 'catalog';
    render();
    return;
  }

  const editBook = event.target.closest('[data-edit-book]')?.dataset.editBook;
  if (editBook) {
    state.editingBookId = editBook;
    render();
    return;
  }

  const bookAction = event.target.closest('[data-book-action]');
  if (bookAction) {
    await handleBookAction(bookAction.dataset.bookAction, bookAction.dataset.bookId);
    return;
  }

  const exchangeAction = event.target.closest('[data-exchange-action]');
  if (exchangeAction) {
    await handleExchangeAction(exchangeAction.dataset.exchangeAction, exchangeAction.dataset.exchangeId);
    return;
  }

  const notificationId = event.target.closest('[data-notification-read]')?.dataset.notificationRead;
  if (notificationId) {
    await runAction(async () => {
      await apiFetch(`/v1/notifications/${notificationId}/read`, { method: 'POST' });
      await loadAuthenticatedData();
    }, 'Уведомление прочитано.');
    return;
  }

  const outboxEventId = event.target.closest('[data-outbox-retry]')?.dataset.outboxRetry;
  if (outboxEventId) {
    await runAction(async () => {
      await apiFetch(`/v1/admin/outbox/${encodeURIComponent(outboxEventId)}/retry`, { method: 'POST' });
      await loadAuthenticatedData();
    }, 'Outbox-событие поставлено на повтор.');
  }
}

async function handleAction(action) {
  switch (action) {
    case 'dismiss-notice':
      state.notice = null;
      renderNotice();
      break;
    case 'refresh':
      await runAction(refreshAll, 'Данные обновлены.');
      break;
    case 'clear-selected-book':
      state.selectedRequestedBookId = null;
      render();
      break;
    case 'cancel-edit':
      state.editingBookId = null;
      render();
      break;
    case 'logout':
      await logout(false);
      break;
    case 'logout-all':
      await logout(true);
      break;
    case 'mark-all-read':
      await runAction(async () => {
        await apiFetch('/v1/notifications/read-all', { method: 'POST' });
        await loadAuthenticatedData();
      }, 'Все уведомления прочитаны.');
      break;
    case 'test-api':
      await runAction(testApi, 'Gateway доступен.');
      break;
    case 'reset-api':
      state.apiBase = defaultApiBase();
      localStorage.setItem(API_BASE_KEY, state.apiBase);
      state.healthMessage = '';
      render();
      break;
    case 'clear-local-state':
      localStorage.removeItem(SESSION_KEY);
      localStorage.removeItem(LEGACY_SESSION_KEY);
      localStorage.removeItem(API_BASE_KEY);
      state.apiBase = defaultApiBase();
      clearAuthenticatedState();
      state.healthMessage = '';
      notify('Локальное состояние очищено.', 'success');
      render();
      break;
    default:
      break;
  }
}

async function logout(allSessions) {
  await runAction(async () => {
    if (state.session?.refreshToken) {
      const path = allSessions ? '/auth/logout-all' : '/auth/logout';
      try {
        await apiFetch(path, {
          method: 'POST',
          auth: false,
          body: { refreshToken: state.session.refreshToken }
        });
      } catch (_) {
        // Локальный выход должен сработать даже если refresh token уже недействителен.
      }
    }
    clearAuthenticatedState();
    await loadPublicCatalog();
  }, allSessions ? 'Вы вышли из всех сессий.' : 'Вы вышли из системы.');
}

async function testApi() {
  const response = await fetch(`${state.apiBase}/auth/ping`, { headers: { Accept: 'text/plain' } });
  const body = await response.text();
  if (!response.ok) throw new Error(`Gateway ответил HTTP ${response.status}`);
  state.healthMessage = body || 'Auth service ответил успешно.';
}

async function handleBookAction(action, bookId) {
  if (action === 'delete' && !window.confirm('Удалить книгу?')) return;
  await runAction(async () => {
    if (action === 'delete') {
      await apiFetch(`/v1/books/${bookId}`, { method: 'DELETE' });
    } else {
      await apiFetch(`/v1/books/${bookId}/${action}`, { method: 'PUT' });
    }
    await refreshAll();
  }, 'Книга обновлена.');
}

async function handleExchangeAction(action, exchangeId) {
  const adminActions = new Set(['repair']);
  const path = adminActions.has(action)
    ? `/v1/admin/exchanges/${exchangeId}/repair`
    : `/v1/exchanges/${exchangeId}/${action}`;

  await runAction(async () => {
    await apiFetch(path, { method: 'POST' });
    await refreshAll();
  }, 'Заявка обновлена.');
}

function handleInput(event) {
  const filter = event.target.closest('[data-filter]')?.dataset.filter;
  if (!filter) return;
  state.filters[filter] = event.target.value;

  if (filter === 'catalog') {
    const grid = document.getElementById('catalog-grid');
    if (grid) grid.innerHTML = renderBooksGrid(filteredCatalog(), { catalog: true });
    return;
  }

  render();
}

document.addEventListener('submit', handleSubmit);
document.addEventListener('click', handleClick);
document.addEventListener('input', handleInput);
document.addEventListener('change', handleInput);

(async function init() {
  render();
  try {
    state.busy = true;
    render();
    await loadPublicCatalog();
    if (isAuthenticated()) {
      await loadAuthenticatedData();
    }
  } catch (error) {
    notify(error.message || 'Не удалось загрузить данные. Проверьте, что backend и API Gateway запущены.', 'error');
  } finally {
    state.busy = false;
    render();
  }
})();
