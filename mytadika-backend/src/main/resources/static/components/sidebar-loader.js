// Parents and teachers must fill in the required profile fields (everything except the
// photo) before using the rest of the system. Skipped for the profile/edit-profile pages
// themselves so there's always a way in to actually complete it, and skipped entirely for
// admin. Fails open (doesn't block) if the profile fetch itself errors.
async function enforceProfileComplete(role, activeNav) {
    if (activeNav === 'profile') return;
    if (role !== 'parent' && role !== 'teacher') return;
    const accountId = localStorage.getItem('accountId') || sessionStorage.getItem('accountId');
    if (!accountId) return;
    try {
        const res = await fetch('/api/profile/' + accountId);
        if (!res.ok) return;
        const p = await res.json();
        const required = role === 'teacher'
            ? ['phoneNumber', 'address', 'qualification', 'experience', 'focusArea', 'description']
            : ['phoneNumber', 'address'];
        const incomplete = required.some(f => !p[f] || !String(p[f]).trim());
        if (incomplete) {
            window.location.replace('/' + role + '/' + role + 'editprofile.html?incomplete=1');
        }
    } catch (e) { /* fail open — don't block access if the check itself errors */ }
}

async function loadSidebar(role, activeNav) {
    await enforceProfileComplete(role, activeNav);
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

        if (role === 'parent') {
            updateFeesBadge();
            setInterval(updateFeesBadge, 5000);
        }

    } catch (e) {
        console.error('Sidebar load failed:', e);
    }
}

async function updateFeesBadge() {
    const accountId = localStorage.getItem('accountId') || sessionStorage.getItem('accountId');
    const badge = document.getElementById('nav-fees-badge');
    if (!accountId || !badge) return;

    try {
        const res = await fetch('/api/fees/pending-count/' + accountId);
        const data = res.ok ? await res.json() : { count: 0 };
        const count = data.count || 0;
        if (count > 0) {
            badge.textContent = count > 99 ? '99+' : count;
            badge.classList.remove('hidden');
        } else {
            badge.classList.add('hidden');
        }
    } catch (e) {
        // silently ignore network errors
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

async function loadTopbar(role, options) {
    const dateOnly = options && options.dateOnly;
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

        // Greeting (only present on topbars that opted in, e.g. admin) — pages that already show
        // their own greeting (e.g. the home pages) pass { dateOnly: true } to show just the date,
        // on the same line as the notification bell, instead of duplicating the name greeting too.
        const greetingEl = document.getElementById('topbar-greeting');
        if (greetingEl && dateOnly) {
            const dateStr = new Date().toLocaleDateString('en-GB', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' });
            greetingEl.innerHTML = `<p class="text-sm font-bold text-[#312f23]">${dateStr}</p>`;
        } else if (greetingEl) {
            const fullName = localStorage.getItem('fullName') || sessionStorage.getItem('fullName') || '';
            const hour = new Date().getHours();
            const timeOfDay = hour < 12 ? 'morning' : hour < 18 ? 'afternoon' : 'evening';
            const dateStr = new Date().toLocaleDateString('en-GB', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' });
            greetingEl.innerHTML = `<p class="text-sm font-bold text-[#312f23]">Good ${timeOfDay}${fullName ? ', ' + fullName : ''}</p><p class="text-xs text-stone-400">${dateStr}</p>`;
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

        // Floating help chatbot — appears on every page for every role, no per-page wiring needed.
        initChatbot(role);
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

    const deleteAllBtn = document.getElementById('notif-page-delete-all');
    if (deleteAllBtn) {
        deleteAllBtn.addEventListener('click', async () => {
            if (!_notifPageData.length) return;
            if (!confirm('Delete all notifications? This cannot be undone.')) return;
            try {
                await fetch('/api/notifications/all/' + accountId, { method: 'DELETE' });
                _notifPageData = [];
                _expandedNotifId = null;
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

// ── Help chatbot (rule-based intent router) ─────────────────────────────────
// Not an LLM — a small keyword-scored lookup per role. Every possible answer is a
// pre-written {reply, linkText, link} pointing at a real page, so it can never
// recommend a page or feature that doesn't exist.

// Word-boundary match, not plain substring — plain .includes() let short keywords
// like "fee" match inside unrelated words (e.g. "feeling", "coffee"), causing wrong answers.
// Trailing ('?s)? tolerates plurals/possessives (e.g. "classroom" still matches "classrooms").
function _kwMatch(q, keyword) {
    const escaped = keyword.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
    return new RegExp(`\\b${escaped}('?s)?\\b`).test(q);
}
const CHATBOT_INTENTS = {
    parent: {
        fallbackLink: '/parent/parentchatwithtecher.html?openAdmin=1',
        fallbackLinkText: 'Message Admin',
        intents: [
            { keywords: ['home', 'dashboard', 'main page', 'homepage', 'overview', 'how many children', 'my children', 'list of children', 'number of children', 'laman utama', 'muka utama'],
              reply: "Your Home page shows your child's overview, upcoming classwork, recent memories, and quick actions — if you have more than one child, they'll appear as tabs at the top of Home (and most other pages).",
              linkText: 'Go to Home', link: '/parent/parenthome.html' },
            { keywords: ['classroom', 'class', 'teacher info', 'teacher detail', 'teacher bio', 'classmate', 'class code', 'join classroom', 'mark done', 'mark complete', 'kelas', 'darjah', 'rakan sekelas', 'kod kelas'],
              reply: "You can see your child's classroom, classmates, and teacher details, join a classroom with a class code, and mark classwork done on the Classroom page.",
              linkText: 'Go to Classroom', link: '/parent/parentclassroom.html' },
            { keywords: ['fee', 'fees', 'pay', 'payment', 'invoice', 'tuition', 'bill', 'receipt', 'owe', 'yuran', 'bayaran', 'bil', 'bayar'],
              reply: "You can view and pay outstanding fees, and download receipts, on the Fees page.",
              linkText: 'Go to Fees', link: '/parent/parentfees.html' },
            { keywords: ['photo', 'photos', 'video', 'videos', 'memory', 'memories', 'picture', 'pictures', 'comment', 'react', 'download photo', 'download video', 'gambar', 'foto'],
              reply: "Photos and videos your child's teacher shares are in the Memory Box — you can react and comment there too.",
              linkText: 'Go to Memory Box', link: '/parent/parentmemory.html' },
            { keywords: ['health', 'allergy', 'allergies', 'weight', 'height', 'growth', 'measurement', 'muac', 'sick', 'unwell', 'ill', 'kesihatan', 'alahan', 'berat', 'tinggi', 'sakit'],
              reply: "Your child's growth measurements are on the Health page, and you can add or update their allergy info yourself there too.",
              linkText: 'Go to Health', link: '/parent/parenthealth.html' },
            { keywords: ['academic', 'grade', 'grades', 'score', 'scores', 'report', 'exam', 'result', 'progress', 'strength area', 'interest area', 'akademik', 'markah', 'keputusan', 'gred'],
              reply: "Term scores, grades, and your child's Interest & Strength Area progress are shown on the Academic Report page.",
              linkText: 'Go to Academic Report', link: '/parent/parentacademic.html' },
            { keywords: ['message teacher', 'contact teacher', 'talk to teacher', 'chat teacher', 'ask teacher', 'call teacher', 'call', 'phone teacher', 'add contact', 'add teacher', 'send photo', 'sent photo', 'send file', 'sent file', 'send image', 'sent image', 'attach file', 'attach photo', 'file', 'message', 'chat', 'contact', 'mesej cikgu', 'hubungi cikgu', 'cakap dengan cikgu', 'mesej', 'hubungi', 'cakap'],
              reply: "You can message or call your child's teacher — and send photos or files — directly from the Messages page.",
              linkText: 'Go to Messages', link: '/parent/parentchatwithtecher.html' },
            { keywords: ['message admin', 'contact admin', 'talk to admin', 'ask admin', 'contact school', 'complain', 'complaint', 'school office', 'admin', 'pentadbir', 'hubungi pentadbir', 'mesej pentadbir', 'aduan', 'komplen'],
              reply: "You can message the school admin directly from the Messages page.",
              linkText: 'Message Admin', link: '/parent/parentchatwithtecher.html?openAdmin=1' },
            { keywords: ['event', 'events', 'calendar', 'holiday', 'holidays', 'schedule', 'acara', 'kalendar', 'cuti'],
              reply: "School events and public holidays are listed on the Events calendar.",
              linkText: 'Go to Events', link: '/parent/parentevents.html' },
            { keywords: ['profile', 'edit profile', 'my account', 'phone number', 'address', 'home address', 'full name', 'profile picture', 'profile photo', 'update address', 'change address', 'update phone', 'profil', 'alamat', 'nombor telefon'],
              reply: "You can update your name, phone number, home address, or profile picture on the Edit Profile page.",
              linkText: 'Go to Edit Profile', link: '/parent/parenteditprofile.html' },
            { keywords: ['password', 'change password', 'reset password', 'kata laluan', 'tukar kata laluan'],
              reply: "You can change your password from the Privacy & Security section on your Profile page.",
              linkText: 'Go to Profile', link: '/parent/parentprofile.html' },
            { keywords: ['change email', 'update email', 'my email', 'edit email'],
              reply: "You can't change your own email address — message the school admin and ask them to update it for you.",
              linkText: 'Message Admin', link: '/parent/parentchatwithtecher.html?openAdmin=1' },
            { keywords: ['logout', 'log out', 'sign out', 'signout', 'log off', 'keluar', 'log keluar'],
              reply: "You can log out anytime using the Logout button at the bottom of the sidebar, or from your Profile page.",
              linkText: 'Logout', link: null },
            { keywords: ['notification', 'notifications', 'alert', 'alerts', 'reminder', 'mark all read', 'delete notification', 'notifikasi', 'pemberitahuan'],
              reply: "All your past notifications (fee reminders, new photos, messages, etc.) are on the Notifications page.",
              linkText: 'Go to Notifications', link: '/parent/parentnotifications.html' },
        ]
    },
    teacher: {
        fallbackLink: '/teacher/teacherchatwithparent.html?openAdmin=1',
        fallbackLinkText: 'Message Admin',
        intents: [
            { keywords: ['home', 'dashboard', 'main page', 'homepage', 'laman utama', 'muka utama'],
              reply: "Your Home page shows your classrooms, upcoming classwork, and recent Memory Box posts.",
              linkText: 'Go to Home', link: '/teacher/teacherhome.html' },
            { keywords: ['classroom', 'class', 'manage class', 'add student', 'add teacher', 'assignment', 'classwork', 'enroll', 'class code', 'announcement', 'stream', 'add activity', 'kelas', 'darjah', 'tambah pelajar', 'tugasan'],
              reply: "You can manage your classes, students, announcements, and classwork assignments on the Classroom page.",
              linkText: 'Go to Classroom', link: '/teacher/teacherclassroom.html' },
            { keywords: ['photo', 'photos', 'video', 'videos', 'memory', 'memories', 'upload', 'post', 'share', 'cover photo', 'comment', 'react', 'gambar', 'foto', 'muat naik'],
              reply: "You can share photos and videos with parents, set a cover photo, and reply to comments from the Memory Box.",
              linkText: 'Go to Memory Box', link: '/teacher/teachermemory.html' },
            { keywords: ['health', 'allergy', 'allergies', 'growth', 'measurement', 'muac', 'activity level', 'regenerate advice', 'regenerate', 'kesihatan', 'alahan', 'berat', 'tinggi'],
              reply: "You can log growth measurements, manage allergy info, and regenerate AI wellness advice on the Health page.",
              linkText: 'Go to Health', link: '/teacher/teacherhealthlist.html' },
            { keywords: ['academic', 'grade', 'grades', 'score', 'scores', 'report', 'term scores', 'enter scores', 'submit scores', 'akademik', 'markah', 'gred'],
              reply: "You can enter and view term scores on the Academic page.",
              linkText: 'Go to Academic', link: '/teacher/teacheracademiclist.html' },
            { keywords: ['message parent', 'contact parent', 'chat parent', 'call parent', 'call', 'add contact', 'add parent', 'send photo', 'sent photo', 'send file', 'sent file', 'send image', 'sent image', 'attach file', 'attach photo', 'file', 'parent details', 'see parent', 'message', 'chat', 'contact', 'mesej ibu bapa', 'hubungi ibu bapa', 'mesej', 'hubungi', 'cakap'],
              reply: "You can add a parent contact, message or call them, send photos/files, and view their details from the Messages page.",
              linkText: 'Go to Messages', link: '/teacher/teacherchatwithparent.html' },
            { keywords: ['message admin', 'contact admin', 'talk to admin', 'ask admin', 'complain', 'complaint', 'admin', 'pentadbir', 'hubungi pentadbir', 'mesej pentadbir', 'aduan', 'komplen'],
              reply: "You can message the school admin directly from the Messages page.",
              linkText: 'Message Admin', link: '/teacher/teacherchatwithparent.html?openAdmin=1' },
            { keywords: ['event', 'events', 'calendar', 'holiday', 'holidays', 'acara', 'kalendar', 'cuti'],
              reply: "School events and public holidays are listed on the Events calendar.",
              linkText: 'Go to Events', link: '/teacher/teacherevents.html' },
            { keywords: ['profile', 'edit profile', 'my account', 'phone number', 'address', 'home address', 'full name', 'profile picture', 'profile photo', 'description', 'about me', 'bio', 'qualification', 'experience', 'focus area', 'profil', 'alamat'],
              reply: "You can update your name, phone, address, bio, qualifications, or profile picture on the Edit Profile page.",
              linkText: 'Go to Edit Profile', link: '/teacher/teachereditprofile.html' },
            { keywords: ['password', 'change password', 'kata laluan'],
              reply: "You can change your password from the Profile page — look for the Change Password option.",
              linkText: 'Go to Profile', link: '/teacher/teacherprofile.html' },
            { keywords: ['logout', 'log out', 'sign out', 'signout', 'log off', 'keluar', 'log keluar'],
              reply: "You can log out anytime using the Logout button at the bottom of the sidebar, or from your Profile page.",
              linkText: 'Logout', link: null },
            { keywords: ['notification', 'notifications', 'alert', 'alerts', 'reminder', 'mark all read', 'delete all', 'notifikasi', 'pemberitahuan'],
              reply: "All your past notifications are on the Notifications page.",
              linkText: 'Go to Notifications', link: '/teacher/teachernotifications.html' },
        ]
    },
    admin: {
        fallbackLink: null,
        fallbackLinkText: null,
        intents: [
            { keywords: ['home', 'dashboard', 'stats', 'statistics', 'overview', 'papan pemuka', 'statistik'],
              reply: "The Dashboard shows school-wide stats and recently joined accounts.",
              linkText: 'Go to Dashboard', link: '/admin/index.html' },
            { keywords: ['account', 'accounts', 'user', 'users', 'create account', 'teacher account', 'parent account', 'suspend', 'ban', 'disable account', 'delete account', 'akaun', 'cipta akaun', 'pengguna'],
              reply: "You can create, edit, and permanently delete teacher, parent, and admin accounts on the Accounts page. There's no suspend/ban feature — deleting is the only way to remove access.",
              linkText: 'Go to Accounts', link: '/admin/adminaccounts.html' },
            { keywords: ['student', 'students', 'enroll', 'child', 'children', 'parent email', 'link parent', 'emergency contact', 'pelajar', 'murid', 'anak'],
              reply: "You can manage enrolled students, link them to a parent account, and set emergency contact info on the Students page.",
              linkText: 'Go to Students', link: '/admin/adminstudents.html' },
            { keywords: ['classroom', 'classrooms', 'class', 'create class', 'manage classroom', 'student code', 'remove student', 'remove teacher', 'kelas', 'darjah', 'cipta kelas'],
              reply: "You can create and manage classrooms, and add or remove teachers/students, on the Classrooms page.",
              linkText: 'Go to Classrooms', link: '/admin/adminclassrooms.html' },
            { keywords: ['fee', 'fees', 'payment', 'payments', 'invoice', 'bulk edit', 'bulk delete', 'mark paid', 'late fee', 'download report', 'download receipt', 'print receipt', 'yuran', 'bayaran'],
              reply: "You can create, edit, bulk-edit a whole class, track payments, and download receipts on the Fees page.",
              linkText: 'Go to Fees', link: '/admin/adminfees.html' },
            { keywords: ['health', 'allergy', 'allergies', 'allergy profile', 'log measurement', 'kesihatan', 'alahan'],
              reply: "Student growth measurements and allergy info are on the Health page.",
              linkText: 'Go to Health', link: '/admin/adminhealthlist.html' },
            { keywords: ['academic', 'grade', 'grades', 'academic report', 'student academic', 'term scores', 'enter scores', 'akademik', 'markah'],
              reply: "School-wide academic records are on the Academic page — you can view, add, edit, and delete a student's term scores there.",
              linkText: 'Go to Academic', link: '/admin/adminacademiclist.html' },
            { keywords: ['photo', 'photos', 'video', 'videos', 'memory', 'gallery', 'like', 'comment', 'moderate comment', 'cover photo', 'gambar', 'foto', 'galeri'],
              reply: "Every Memory Box post across the school is in the Gallery, where you can react, comment, and moderate posts too.",
              linkText: 'Go to Gallery', link: '/admin/admingallery.html' },
            { keywords: ['message', 'messages', 'chat', 'contact', 'new message', 'add contact', 'start conversation', 'send photo', 'sent photo', 'send file', 'sent file', 'send image', 'sent image', 'attach file', 'attach photo', 'file', 'parent profile', 'teacher profile', 'see parent', 'see teacher', 'parent details', 'teacher details', 'mesej', 'hubungi'],
              reply: "You can start new conversations, message parents/teachers, send photos/files, and view their profiles on the Messages page.",
              linkText: 'Go to Messages', link: '/admin/adminmessages.html' },
            { keywords: ['event', 'events', 'calendar', 'holiday', 'holidays', 'acara', 'kalendar', 'cuti'],
              reply: "School events and public holidays are managed on the Events page.",
              linkText: 'Go to Events', link: '/admin/adminevents.html' },
            { keywords: ['profile', 'edit profile', 'my account', 'phone number', 'full name', 'profile picture', 'profile photo', 'profil'],
              reply: "You can update your name, phone number, or profile picture on the Edit Profile page.",
              linkText: 'Go to Edit Profile', link: '/admin/admineditprofile.html' },
            { keywords: ['password', 'change password', 'reset password', 'kata laluan'],
              reply: "There's currently no self-service password change for admin accounts. If you need your password reset, ask another admin or a developer for help.",
              linkText: null, link: null },
            { keywords: ['change email', 'update email', 'my email', 'edit email'],
              reply: "You can't change your own email address — ask another admin to update it for you from the Accounts page.",
              linkText: 'Go to Accounts', link: '/admin/adminaccounts.html' },
            { keywords: ['logout', 'log out', 'sign out', 'signout', 'log off', 'keluar', 'log keluar'],
              reply: "You can log out anytime using the Logout button at the bottom of the sidebar, or from your Profile page.",
              linkText: 'Logout', link: null },
            { keywords: ['notification', 'notifications', 'alert', 'alerts', 'reminder', 'mark all read', 'delete all', 'notifikasi', 'pemberitahuan'],
              reply: "All notifications sent out (fees, events, etc.) are on the Notifications page.",
              linkText: 'Go to Notifications', link: '/admin/adminnotifications.html' },
        ]
    }
};

// Pre-check for actions a role isn't permitted to do (e.g. a parent asking how to
// "delete classroom"). Runs before topic matching so we never point someone at a
// page for an action they can't actually perform there. Admin has no restrictions —
// it's the top authority and can do everything the intents point at.
const ACTION_VERBS = ['delete', 'remove', 'create', 'add', 'edit', 'update', 'change', 'modify', 'ban', 'suspend',
    'padam', 'buang', 'hapus', 'cipta', 'buat', 'tambah', 'ubah', 'tukar', 'gantung'];
// Substrings that mean the request is actually one of the allowed sub-actions
// (e.g. "add a comment" contains "add" but isn't restricted) — skip the block if present.
const CHATBOT_ALLOWED_SUBACTIONS = ['comment', 'react', 'reaction', 'like', 'view', 'see', 'pay', 'download', 'read', 'password', 'profile',
    'lihat', 'tengok', 'bayar', 'muat turun', 'kata laluan', 'profil'];

const CHATBOT_RESTRICTED = {
    parent: [
        { topics: ['classroom', 'class', 'kelas', 'darjah'],
          reply: "Creating, editing, or deleting classrooms is handled by the school admin — as a parent you can only view your child's classroom." },
        { topics: ['student', 'account', 'enroll', 'pelajar', 'murid', 'akaun'],
          reply: "Adding, editing, or removing student and account records is handled by the school admin, not parents." },
        { topics: ['fee', 'invoice', 'payment', 'yuran', 'bayaran', 'bil'],
          reply: "Fee records are set up and managed by the school admin. As a parent you can view and pay fees, but not create, edit, or delete them." },
        { topics: ['health', 'growth', 'measurement', 'muac', 'weight', 'height', 'kesihatan', 'berat', 'tinggi'],
          reply: "Growth measurements (weight, height, MUAC) are logged by teachers and admin — as a parent you can view those, but not edit them. You can add or update your child's allergy info yourself, though." },
        { topics: ['academic', 'grade', 'score', 'report', 'exam', 'akademik', 'markah', 'keputusan'],
          reply: "Academic reports are entered by teachers. As a parent you can view reports, but not create or edit them." },
        { topics: ['photo', 'video', 'memory', 'picture', 'post', 'gallery', 'album', 'gambar', 'foto', 'galeri'],
          reply: "Only teachers can upload, edit, or delete Memory Box posts. As a parent you can view and react to posts, but not create or edit them." },
    ],
    teacher: [
        { topics: ['fee', 'invoice', 'payment', 'yuran', 'bayaran'],
          reply: "Fee records are fully managed by the school admin — teachers don't have access to create, edit, or delete fees." },
        { topics: ['account', 'akaun'],
          reply: "Creating, editing, or deleting parent/teacher/admin accounts is handled by the school admin." },
    ],
    admin: []
};

function _checkRestrictedAction(role, q) {
    const rules = CHATBOT_RESTRICTED[role];
    if (!rules || !rules.length) return null;
    if (!ACTION_VERBS.some(v => _kwMatch(q, v))) return null;
    if (CHATBOT_ALLOWED_SUBACTIONS.some(a => _kwMatch(q, a))) return null;
    const rule = rules.find(r => r.topics.some(t => _kwMatch(q, t)));
    return rule ? rule.reply : null;
}

let _chatbotRole = null;
let _chatbotInitialized = false;
let _chatbotJustDragged = false;

function initChatbot(role) {
    if (!CHATBOT_INTENTS[role] || _chatbotInitialized) return;

    // A dedicated full-page Help page (parenthelp.html / teacherhelp.html /
    // adminhelp.html) provides this container — when present, render the same
    // chatbot engine directly into the page instead of the floating widget,
    // so there's exactly one chatbot on screen, not two.
    const fullPageContainer = document.getElementById('chatbot-fullpage-container');
    if (fullPageContainer) {
        _chatbotRole = role;
        _chatbotInitialized = true;
        fullPageContainer.innerHTML = `
            <div id="chatbot-panel" class="flex flex-col bg-white rounded-2xl shadow-sm border border-stone-100 overflow-hidden h-full">
                <div class="bg-[#ffd709]/20 px-5 py-4 flex items-center gap-2 border-b border-stone-100 flex-shrink-0">
                    <span class="material-symbols-outlined text-[#6c5a00] text-2xl">support_agent</span>
                    <span class="font-bold text-base text-[#6c5a00]">MyTadika Assistant</span>
                </div>
                <div id="chatbot-messages" class="flex-1 overflow-y-auto px-5 py-4 space-y-3 text-sm"></div>
                <div class="border-t border-stone-100 p-3 flex gap-2 flex-shrink-0">
                    <input id="chatbot-input" type="text" placeholder="Ask me anything…"
                        class="flex-1 border border-stone-200 rounded-full px-4 py-3 text-sm focus:outline-none focus:ring-2 focus:ring-[#ffd709]"
                        onkeydown="if(event.key==='Enter'){event.preventDefault();sendChatbotMessage();}">
                    <button onclick="sendChatbotMessage()" class="w-11 h-11 flex-shrink-0 flex items-center justify-center rounded-full bg-[#ffd709] text-[#6c5a00] hover:bg-[#f5cc00] transition-colors">
                        <span class="material-symbols-outlined text-lg">send</span>
                    </button>
                </div>
            </div>`;
        _appendChatbotMessage('bot', `Hi! I'm the MyTadika Assistant 👋<br>Ask me things like <i>"where is fees"</i> or <i>"how do I message the teacher"</i>.`);
        document.getElementById('chatbot-input').focus();
        return;
    }

    _chatbotRole = role;
    _chatbotInitialized = true;

    document.body.insertAdjacentHTML('beforeend', `
        <div id="chatbot-root" class="fixed bottom-6 left-6 z-50">
            <button id="chatbot-toggle-btn" onclick="toggleChatbot()" style="touch-action:none;"
                class="w-14 h-14 rounded-full bg-[#ffd709] shadow-lg flex items-center justify-center hover:bg-[#f5cc00] transition-colors cursor-grab active:cursor-grabbing select-none">
                <span class="material-symbols-outlined text-[#6c5a00] text-2xl">support_agent</span>
            </button>
            <div id="chatbot-panel" class="hidden absolute w-80 sm:w-96 h-[28rem] bg-white rounded-2xl shadow-2xl border border-stone-100 flex flex-col overflow-hidden">
                <div class="bg-[#ffd709]/20 px-4 py-3 flex items-center justify-between border-b border-stone-100 flex-shrink-0">
                    <div class="flex items-center gap-2">
                        <span class="material-symbols-outlined text-[#6c5a00]">support_agent</span>
                        <span class="font-bold text-sm text-[#6c5a00]">MyTadika Assistant</span>
                    </div>
                    <button onclick="toggleChatbot()" class="text-stone-400 hover:text-stone-600">
                        <span class="material-symbols-outlined text-lg">close</span>
                    </button>
                </div>
                <div id="chatbot-messages" class="flex-1 overflow-y-auto px-3 py-3 space-y-2 text-sm"></div>
                <div class="border-t border-stone-100 p-2 flex gap-2 flex-shrink-0">
                    <input id="chatbot-input" type="text" placeholder="Ask me anything…"
                        class="flex-1 border border-stone-200 rounded-full px-3 py-2 text-xs focus:outline-none focus:ring-2 focus:ring-[#ffd709]"
                        onkeydown="if(event.key==='Enter'){event.preventDefault();sendChatbotMessage();}">
                    <button onclick="sendChatbotMessage()" class="w-9 h-9 flex-shrink-0 flex items-center justify-center rounded-full bg-[#ffd709] text-[#6c5a00] hover:bg-[#f5cc00]">
                        <span class="material-symbols-outlined text-base">send</span>
                    </button>
                </div>
            </div>
        </div>`);

    _initChatbotDrag();
}

// Lets the toggle button (and the panel anchored to it) be dragged anywhere
// on screen, on both mouse and touch. Position persists across page
// navigations via localStorage since the widget is re-injected fresh on
// every page load.
function _initChatbotDrag() {
    const root = document.getElementById('chatbot-root');
    const btn = document.getElementById('chatbot-toggle-btn');
    if (!root || !btn) return;

    function clampToViewport() {
        const rect = root.getBoundingClientRect();
        const maxLeft = Math.max(0, window.innerWidth - rect.width);
        const maxTop = Math.max(0, window.innerHeight - rect.height);
        const left = Math.min(Math.max(rect.left, 0), maxLeft);
        const top = Math.min(Math.max(rect.top, 0), maxTop);
        root.style.left = left + 'px';
        root.style.top = top + 'px';
    }

    const saved = localStorage.getItem('chatbotPosition');
    if (saved) {
        try {
            const pos = JSON.parse(saved);
            root.style.left = pos.left + 'px';
            root.style.top = pos.top + 'px';
            root.style.right = 'auto';
            root.style.bottom = 'auto';
            clampToViewport();
        } catch (e) { /* ignore malformed saved position */ }
    }

    let dragging = false;
    let moved = false;
    let startX, startY, startLeft, startTop;
    const DRAG_THRESHOLD = 5; // px — below this, treat it as a click, not a drag

    btn.addEventListener('pointerdown', (e) => {
        dragging = true;
        moved = false;
        const rect = root.getBoundingClientRect();
        startX = e.clientX;
        startY = e.clientY;
        startLeft = rect.left;
        startTop = rect.top;
        root.style.left = startLeft + 'px';
        root.style.top = startTop + 'px';
        root.style.right = 'auto';
        root.style.bottom = 'auto';
        btn.setPointerCapture(e.pointerId);
    });

    btn.addEventListener('pointermove', (e) => {
        if (!dragging) return;
        const dx = e.clientX - startX;
        const dy = e.clientY - startY;
        if (!moved && Math.hypot(dx, dy) < DRAG_THRESHOLD) return;
        moved = true;

        const rect = root.getBoundingClientRect();
        const maxLeft = Math.max(0, window.innerWidth - rect.width);
        const maxTop = Math.max(0, window.innerHeight - rect.height);
        root.style.left = Math.min(Math.max(startLeft + dx, 0), maxLeft) + 'px';
        root.style.top = Math.min(Math.max(startTop + dy, 0), maxTop) + 'px';
    });

    btn.addEventListener('pointerup', (e) => {
        if (!dragging) return;
        dragging = false;
        if (moved) {
            _chatbotJustDragged = true;
            const rect = root.getBoundingClientRect();
            localStorage.setItem('chatbotPosition', JSON.stringify({ left: rect.left, top: rect.top }));
            _positionChatbotPanel(); // button may have crossed the top/bottom-half line while dragging
        }
    });

    btn.addEventListener('pointercancel', () => { dragging = false; });
    window.addEventListener('resize', () => { clampToViewport(); _positionChatbotPanel(); });
}

// Opens the panel above the button when there's room, below it when there
// isn't (e.g. the button has been dragged near the top of the screen) —
// recomputed every time, since the button can move between opens.
function _positionChatbotPanel() {
    const root = document.getElementById('chatbot-root');
    const panel = document.getElementById('chatbot-panel');
    if (!root || !panel) return;

    // Measure the button alone: momentarily hide the panel (if open) so its own
    // size doesn't inflate root's bounding rect while we measure.
    const wasHidden = panel.classList.contains('hidden');
    if (!wasHidden) panel.classList.add('hidden');
    const rootRect = root.getBoundingClientRect();
    if (!wasHidden) panel.classList.remove('hidden');

    const PANEL_HEIGHT = 448; // px, matches the h-[28rem] class on #chatbot-panel
    const PANEL_WIDTH = window.innerWidth >= 640 ? 384 : 320; // matches w-80 sm:w-96
    const GAP = 12;

    const spaceAbove = rootRect.top;
    const spaceBelow = window.innerHeight - rootRect.bottom;
    if (spaceAbove >= PANEL_HEIGHT + GAP || spaceAbove >= spaceBelow) {
        panel.style.bottom = (rootRect.height + GAP) + 'px';
        panel.style.top = 'auto';
    } else {
        panel.style.top = (rootRect.height + GAP) + 'px';
        panel.style.bottom = 'auto';
    }

    const spaceLeft = rootRect.left;
    const spaceRight = window.innerWidth - rootRect.right;
    if (spaceLeft >= PANEL_WIDTH + GAP || spaceLeft >= spaceRight) {
        // enough room to the left (or more room left than right) — open leftward
        panel.style.right = '0px';
        panel.style.left = 'auto';
    } else {
        // button is near the left edge — open rightward instead
        panel.style.left = '0px';
        panel.style.right = 'auto';
    }
}

function toggleChatbot() {
    if (_chatbotJustDragged) { _chatbotJustDragged = false; return; }
    const panel = document.getElementById('chatbot-panel');
    if (!panel) return;
    const opening = panel.classList.contains('hidden');
    if (opening) _positionChatbotPanel();
    panel.classList.toggle('hidden');
    if (opening) {
        document.getElementById('chatbot-input').focus();
        if (!document.getElementById('chatbot-messages').children.length) {
            _appendChatbotMessage('bot', `Hi! I'm the MyTadika Assistant 👋<br>Ask me things like <i>"where is fees"</i> or <i>"how do I message the teacher"</i>.`);
        }
    }
}

function _appendChatbotMessage(sender, html) {
    const container = document.getElementById('chatbot-messages');
    const bubble = document.createElement('div');
    bubble.className = sender === 'user'
        ? 'ml-auto max-w-[85%] bg-[#ffd709] text-[#6c5a00] rounded-2xl rounded-br-sm px-3 py-2 font-medium'
        : 'mr-auto max-w-[85%] bg-stone-100 text-stone-700 rounded-2xl rounded-bl-sm px-3 py-2';
    bubble.innerHTML = html;
    container.appendChild(bubble);
    container.scrollTop = container.scrollHeight;
}

function sendChatbotMessage() {
    const input = document.getElementById('chatbot-input');
    const text = input.value.trim();
    if (!text) return;
    _appendChatbotMessage('user', _escapeHtml(text));
    input.value = '';
    setTimeout(() => _respondToChatbot(text), 300);
}

function _chatbotLinkButton(linkText, link) {
    return `<a href="${link}" class="inline-block mt-1 mr-1 mb-1 px-3 py-1.5 rounded-full bg-[#ffd709] text-[#6c5a00] text-xs font-bold hover:bg-[#f5cc00] transition-colors">${_escapeHtml(linkText)} →</a>`;
}

function _respondToChatbot(text) {
    const q = text.toLowerCase();
    const config = CHATBOT_INTENTS[_chatbotRole];
    if (!config) return;

    const restricted = _checkRestrictedAction(_chatbotRole, q);
    if (restricted) {
        const fallback = config.fallbackLink
            ? `<br>${_chatbotLinkButton(config.fallbackLinkText, config.fallbackLink)}`
            : '';
        _appendChatbotMessage('bot', `${_escapeHtml(restricted)}${fallback}`);
        return;
    }

    if (/^(hi|hello|hey|sup|yo)\b/.test(q)) {
        _appendChatbotMessage('bot', `Hello! Ask me where to find something, e.g. <i>"fees"</i>, <i>"classroom"</i>, or <i>"message teacher"</i>.`);
        return;
    }
    if (/help|what can you do|menu|options|topics/.test(q)) {
        const list = config.intents.map(i => `• ${_escapeHtml((i.linkText || i.reply).replace('Go to ', ''))}`).join('<br>');
        _appendChatbotMessage('bot', `I can help you find:<br>${list}<br><br>Just ask, e.g. <i>"where is fees"</i>.`);
        return;
    }

    let best = [];
    let bestScore = 0;
    config.intents.forEach(intent => {
        const score = intent.keywords.filter(k => _kwMatch(q, k)).length;
        if (score > bestScore) { best = [intent]; bestScore = score; }
        else if (score === bestScore && score > 0) best.push(intent);
    });

    if (!best.length) {
        const fallback = config.fallbackLink
            ? `<br>${_chatbotLinkButton(config.fallbackLinkText, config.fallbackLink)}`
            : '';
        _appendChatbotMessage('bot', `Sorry, I'm not sure about that. Try asking about Fees, Classroom, Memory Box, Health, Academic, Events, or Messages.${fallback}`);
        return;
    }

    // Not every intent has a page to link to (e.g. "Logout" lives in the sidebar,
    // and admin's own password change isn't self-service at all) — only render
    // the button when a link actually exists.
    function renderOne(i) {
        const btn = i.link ? `<br>${_chatbotLinkButton(i.linkText, i.link)}` : '';
        return `${_escapeHtml(i.reply)}${btn}`;
    }

    if (best.length === 1) {
        _appendChatbotMessage('bot', renderOne(best[0]));
    } else {
        const parts = best.map(renderOne).join('<br><br>');
        _appendChatbotMessage('bot', `I found a couple of things that might help:<br><br>${parts}`);
    }
}
