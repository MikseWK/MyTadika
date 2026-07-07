async function loadSidebar(role, activeNav) {
    try {
        const res = await fetch('/components/sidebar-' + role + '.html');
        const html = await res.text();
        document.body.insertAdjacentHTML('afterbegin', html);

        // Highlight active nav item
        const activeEl = document.querySelector('[data-nav="' + activeNav + '"]');
        if (activeEl) {
            activeEl.className = 'flex items-center gap-3 bg-[#ffd709] text-[#6c5a00] font-bold rounded-full px-4 py-3 mx-2 transition-all translate-x-1 duration-200';
            const icon = activeEl.querySelector('.nav-icon');
            if (icon) icon.style.fontVariationSettings = "'FILL' 1";
        }

        // Populate user name
        const nameEl = document.getElementById('sidebar-user-name');
        if (nameEl) {
            const name = localStorage.getItem('fullName') || sessionStorage.getItem('fullName') || 'Profile';
            nameEl.textContent = name;
        }

        // Fetch unread badge immediately then poll every 5s
        updateSidebarBadge(role);
        setInterval(() => updateSidebarBadge(role), 5000);

    } catch (e) {
        console.error('Sidebar load failed:', e);
    }
}

async function updateSidebarBadge(role) {
    const accountId = localStorage.getItem('accountId') || sessionStorage.getItem('accountId');
    const badge = document.getElementById('nav-unread-badge');
    if (!accountId || !badge) return;

    try {
        let total = 0;
        if (role === 'admin') {
            // Admin uses a shared inbox: unread counts live on /admin-inbox/contacts, not /contacts/{id}.
            const res = await fetch('/api/chat/admin-inbox/contacts');
            const contacts = res.ok ? await res.json() : [];
            total = (contacts || []).reduce((sum, c) => sum + (c.unreadCount || 0), 0);
        } else {
            const res = await fetch('/api/chat/contacts/' + accountId);
            const contacts = res.ok ? await res.json() : [];
            total = (contacts || []).reduce((sum, c) => sum + (c.unreadCount || 0), 0);

            // Parent/teacher contacts don't include the pinned School Admin thread; add it in.
            const identityRes = await fetch('/api/chat/admin-inbox/identity');
            if (identityRes.ok) {
                const identity = await identityRes.json();
                if (identity.accountId) {
                    const msgsRes = await fetch(`/api/chat/messages/${accountId}/${identity.accountId}`);
                    if (msgsRes.ok) {
                        const msgs = await msgsRes.json();
                        total += msgs.filter(m => m.receiverId === accountId && !m.read).length;
                    }
                }
            }
        }

        if (total > 0) {
            badge.textContent = total > 99 ? '99+' : total;
            badge.classList.remove('hidden');
        } else {
            badge.classList.add('hidden');
        }
    } catch (e) {
        // silently ignore network errors
    }
}

async function loadTopbar(role) {
    try {
        const res = await fetch('/components/topbar-' + role + '.html');
        const html = await res.text();
        document.body.insertAdjacentHTML('afterbegin', html);

        // Populate name and role badge
        const nameEl = document.getElementById('topbar-user-name');
        const roleEl = document.getElementById('topbar-user-role');
        if (nameEl) nameEl.textContent = localStorage.getItem('fullName') || sessionStorage.getItem('fullName') || 'Profile';
        if (roleEl) {
            const roleType = localStorage.getItem('roleType') || sessionStorage.getItem('roleType') || '';
            if (roleType) roleEl.textContent = roleType;
        }

        // Greeting (only present on topbars that opted in, e.g. admin)
        const greetingEl = document.getElementById('topbar-greeting');
        if (greetingEl) {
            const firstName = (localStorage.getItem('fullName') || sessionStorage.getItem('fullName') || '').split(' ')[0];
            const hour = new Date().getHours();
            const timeOfDay = hour < 12 ? 'morning' : hour < 18 ? 'afternoon' : 'evening';
            const dateStr = new Date().toLocaleDateString('en-GB', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' });
            greetingEl.innerHTML = `<p class="text-sm font-bold text-[#312f23]">Good ${timeOfDay}${firstName ? ', ' + firstName : ''}</p><p class="text-xs text-stone-400">${dateStr}</p>`;
        }

        // Load profile avatar
        const accountId = localStorage.getItem('accountId') || sessionStorage.getItem('accountId');
        if (accountId) {
            try {
                const profileRes = await fetch('/api/profile/' + accountId);
                if (profileRes.ok) {
                    const profile = await profileRes.json();
                    if (profile.profileImageUrl) {
                        const img = document.getElementById('topbar-avatar-img');
                        const icon = document.getElementById('topbar-avatar-icon');
                        if (img) { img.src = profile.profileImageUrl; img.classList.remove('hidden'); }
                        if (icon) icon.classList.add('hidden');
                    }
                }
            } catch (_) {}
        }

        // Init notifications for parent/teacher
        if ((role === 'parent' || role === 'teacher' || role === 'admin') && accountId) {
            initNotifications(accountId, role);
        }
    } catch (e) {
        console.error('Topbar load failed:', e);
    }
}

// ── Notification system ──────────────────────────────────────────────────────

let _notifAccountId = null;

function _escapeHtml(str) {
    if (!str) return '';
    return str.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
}

async function _fetchNotifCount() {
    if (!_notifAccountId) return;
    try {
        const res = await fetch('/api/notifications/my/' + _notifAccountId + '/unread-count');
        if (!res.ok) return;
        const data = await res.json();
        const count = data.count || 0;
        const badge = document.getElementById('notif-badge');
        if (!badge) return;
        if (count > 0) {
            badge.textContent = count > 99 ? '99+' : count;
            badge.classList.remove('hidden');
        } else {
            badge.classList.add('hidden');
        }
    } catch (_) {}
}

async function initNotifications(accountId, role) {
    _notifAccountId = accountId;

    await _fetchNotifCount();
    setInterval(_fetchNotifCount, 30000);

    const btn = document.getElementById('notif-btn');
    if (!btn) return;

    btn.addEventListener('click', () => {
        window.location.href = '/' + role + '/' + role + 'notifications.html';
    });
}

// ── Full notifications page ──────────────────────────────────────────────────

let _notifPageAccountId = null;
let _notifPageData = [];
let _expandedNotifId = null;

async function initNotificationsPage(role, accountId) {
    _notifPageAccountId = accountId;
    await loadAllNotifications();

    const markAllBtn = document.getElementById('notif-page-mark-all');
    if (markAllBtn) {
        markAllBtn.addEventListener('click', async () => {
            try {
                await fetch('/api/notifications/read-all/' + accountId, { method: 'POST' });
                _notifPageData.forEach(n => n.isRead = true);
                renderNotifPage();
                _fetchNotifCount();
            } catch (_) {}
        });
    }
}

async function loadAllNotifications() {
    const list = document.getElementById('notif-page-list');
    if (list) list.innerHTML = '<div class="py-16 text-center text-stone-400 text-sm">Loading...</div>';
    try {
        const res = await fetch('/api/notifications/my/' + _notifPageAccountId);
        _notifPageData = res.ok ? await res.json() : [];
    } catch (_) {
        _notifPageData = [];
    }
    renderNotifPage();
}

function renderNotifPage() {
    const list = document.getElementById('notif-page-list');
    if (!list) return;
    if (!_notifPageData.length) {
        list.innerHTML = `
            <div class="py-16 text-center text-stone-400">
                <span class="material-symbols-outlined text-5xl mb-3 block">notifications_off</span>
                <p class="font-semibold text-sm">No notifications yet</p>
            </div>`;
        return;
    }
    list.innerHTML = _notifPageData.map(n => {
        const expanded = _expandedNotifId === n.id;
        const read = !!n.isRead;
        return `
        <div class="bg-white rounded-2xl border ${read ? 'border-stone-100' : 'border-[#ffd709]/50'} shadow-sm mb-3 overflow-hidden">
            <div class="flex items-start gap-3 px-5 py-4 cursor-pointer transition-colors ${read ? 'bg-stone-50 hover:bg-stone-100' : 'bg-amber-50/40 hover:bg-amber-50'}"
                 onclick="toggleNotifItem(${n.id})">
                <span class="mt-1.5 w-2 h-2 rounded-full flex-shrink-0 ${read ? 'bg-transparent' : 'bg-[#ffd709]'}"></span>
                <div class="flex-1 min-w-0">
                    <p class="text-sm font-bold ${read ? 'text-stone-400' : 'text-stone-800'}">${_escapeHtml(n.title)}</p>
                    <p class="text-[11px] text-stone-400 mt-0.5">${n.createdAt}</p>
                    ${expanded ? `<p class="text-sm mt-3 leading-relaxed ${read ? 'text-stone-400' : 'text-stone-600'}">${_escapeHtml(n.body) || 'No further details.'}</p>` : ''}
                    ${expanded && n.link ? `<a href="${_escapeHtml(n.link)}" onclick="event.stopPropagation()"
                        class="group inline-flex items-center gap-1 mt-3 text-xs font-bold text-[#6c5a00] no-underline">
                        <span class="group-hover:underline">View in classroom</span>
                        <span class="material-symbols-outlined text-sm">arrow_forward</span>
                    </a>` : ''}
                </div>
                <button onclick="event.stopPropagation(); deleteNotifItem(${n.id})"
                        class="text-stone-300 hover:text-red-500 hover:bg-red-50 transition-colors flex-shrink-0 p-1.5 rounded-full">
                    <span class="material-symbols-outlined text-lg">delete</span>
                </button>
            </div>
        </div>`;
    }).join('');
}

function toggleNotifItem(id) {
    _expandedNotifId = _expandedNotifId === id ? null : id;

    const n = _notifPageData.find(x => x.id === id);
    if (n && !n.isRead) {
        n.isRead = true;
        fetch('/api/notifications/' + id + '/read', { method: 'POST' }).then(_fetchNotifCount).catch(() => {});
    }
    renderNotifPage();
}

async function deleteNotifItem(id) {
    if (!confirm('Delete this notification?')) return;
    try {
        await fetch('/api/notifications/' + id, { method: 'DELETE' });
        _notifPageData = _notifPageData.filter(n => n.id !== id);
        if (_expandedNotifId === id) _expandedNotifId = null;
        renderNotifPage();
        _fetchNotifCount();
    } catch (_) {}
}

// ── Image lightbox (click to zoom + download) ────────────────────────────────

let _lightboxZoom = 1;
let _lightboxPanX = 0;
let _lightboxPanY = 0;
let _lightboxDragging = false;
let _lightboxDragStart = { x: 0, y: 0 };

function _ensureImageLightbox() {
    if (document.getElementById('img-lightbox')) return;
    document.body.insertAdjacentHTML('beforeend', `
        <div id="img-lightbox" class="hidden fixed inset-0 z-[100] bg-black/90 flex items-center justify-center p-6">
            <div id="img-lightbox-frame" class="relative w-[92vw] h-[88vh] max-w-5xl flex items-center justify-center overflow-hidden">
                <img id="img-lightbox-img" src="" alt="" draggable="false"
                     class="w-full h-full object-contain rounded-xl shadow-2xl select-none" style="cursor: zoom-in;">
            </div>
            <div class="absolute top-6 right-6 flex gap-2">
                <button id="img-lightbox-download"
                        class="w-10 h-10 rounded-full bg-white/90 hover:bg-white flex items-center justify-center text-stone-700 transition-colors" title="Download">
                    <span class="material-symbols-outlined">download</span>
                </button>
                <button id="img-lightbox-close"
                        class="w-10 h-10 rounded-full bg-white/90 hover:bg-white flex items-center justify-center text-stone-700 transition-colors" title="Close">
                    <span class="material-symbols-outlined">close</span>
                </button>
            </div>
            <div class="absolute bottom-6 left-1/2 -translate-x-1/2 text-white/70 text-xs bg-black/40 px-3 py-1.5 rounded-full pointer-events-none">
                Scroll to zoom · Drag to pan · Double-click to reset
            </div>
        </div>
    `);

    const overlay = document.getElementById('img-lightbox');
    const img = document.getElementById('img-lightbox-img');

    overlay.addEventListener('click', (e) => { if (e.target === overlay) closeImageLightbox(); });
    document.getElementById('img-lightbox-close').addEventListener('click', closeImageLightbox);
    document.getElementById('img-lightbox-download').addEventListener('click', _lightboxDownload);

    overlay.addEventListener('wheel', (e) => {
        e.preventDefault();
        const delta = e.deltaY < 0 ? 0.3 : -0.3;
        _lightboxZoom = Math.min(6, Math.max(1, _lightboxZoom + delta));
        if (_lightboxZoom === 1) { _lightboxPanX = 0; _lightboxPanY = 0; }
        _lightboxApplyTransform();
    }, { passive: false });

    img.addEventListener('dblclick', _lightboxResetZoom);

    img.addEventListener('mousedown', (e) => {
        if (_lightboxZoom <= 1) return;
        e.preventDefault();
        _lightboxDragging = true;
        _lightboxDragStart = { x: e.clientX - _lightboxPanX, y: e.clientY - _lightboxPanY };
        img.style.cursor = 'grabbing';
    });

    document.addEventListener('mousemove', (e) => {
        if (!_lightboxDragging) return;
        _lightboxPanX = e.clientX - _lightboxDragStart.x;
        _lightboxPanY = e.clientY - _lightboxDragStart.y;
        _lightboxApplyTransform();
    });

    document.addEventListener('mouseup', () => {
        if (!_lightboxDragging) return;
        _lightboxDragging = false;
        _lightboxApplyTransform();
    });

    document.addEventListener('keydown', (e) => {
        if (e.key === 'Escape') closeImageLightbox();
    });
}

function _lightboxApplyTransform() {
    const img = document.getElementById('img-lightbox-img');
    if (!img) return;
    img.style.transform = `translate(${_lightboxPanX}px, ${_lightboxPanY}px) scale(${_lightboxZoom})`;
    img.style.cursor = _lightboxZoom > 1 ? 'grab' : 'zoom-in';
}

function _lightboxResetZoom() {
    _lightboxZoom = 1;
    _lightboxPanX = 0;
    _lightboxPanY = 0;
    _lightboxApplyTransform();
}

async function _lightboxDownload() {
    const img = document.getElementById('img-lightbox-img');
    const btn = document.getElementById('img-lightbox-download');
    if (!img || !img.src) return;
    const original = btn.innerHTML;
    btn.innerHTML = '<span class="material-symbols-outlined animate-spin">progress_activity</span>';
    try {
        const res = await fetch(img.src);
        const blob = await res.blob();
        const blobUrl = URL.createObjectURL(blob);
        const ext = (img.src.split('.').pop().split('?')[0] || 'jpg').toLowerCase();
        const a = document.createElement('a');
        a.href = blobUrl;
        a.download = 'image.' + ext;
        document.body.appendChild(a);
        a.click();
        a.remove();
        URL.revokeObjectURL(blobUrl);
    } catch (_) {
        window.open(img.src, '_blank');
    } finally {
        btn.innerHTML = original;
    }
}

function openImageLightbox(url, showDownload) {
    _ensureImageLightbox();
    document.getElementById('img-lightbox-img').src = url;
    document.getElementById('img-lightbox-download').style.display = (showDownload === false) ? 'none' : '';
    document.getElementById('img-lightbox').classList.remove('hidden');
    _lightboxResetZoom();
}

function closeImageLightbox() {
    const el = document.getElementById('img-lightbox');
    if (el) el.classList.add('hidden');
}

function logout() {
    localStorage.clear();
    sessionStorage.clear();
    window.location.href = '/login.html';
}
