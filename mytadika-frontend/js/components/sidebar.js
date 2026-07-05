
// Links are relative to pages/ directory.

export function initSidebar(activeKey) {
  const profile = JSON.parse(localStorage.getItem('userProfile') || 'null');
  const role = profile?.role ?? 'PARENT';
  const isTeacher = role === 'TEACHER' || role === 'ADMIN';

  const childId = localStorage.getItem('selectedChildId');
  const academicHref = childId ? `academic.html?id=${childId}` : 'dashboard-parent.html';
  const healthHrefParent = childId ? `health.html?id=${childId}` : 'dashboard-parent.html';

  const parentLinks = [
    { key: 'home',      icon: 'home',            label: 'Home',          href: 'dashboard-parent.html' },
    { key: 'academic',  icon: 'assessment',       label: 'View Report',   href: academicHref },
    { key: 'health',    icon: 'medical_services', label: 'Health',        href: healthHrefParent },
    { key: 'classroom', icon: 'school',           label: 'Classroom',     href: 'classroom.html' },
    { key: 'messages',  icon: 'chat_bubble',      label: 'Messages',      href: '#' },
    { key: 'memory',    icon: 'auto_stories',     label: 'Memory Box',    href: '#' },
    { key: 'events',    icon: 'calendar_today',   label: 'Events',        href: '#' },
  ];

  const teacherLinks = [
    { key: 'home',      icon: 'home',            label: 'Home',            href: 'dashboard-teacher.html' },
    { key: 'classroom', icon: 'school',           label: 'Classroom',       href: 'classroom.html' },
    { key: 'students',  icon: 'groups',           label: 'Student Reports', href: 'students.html' },
    { key: 'health',    icon: 'medical_services', label: 'Health',          href: 'students.html' },
    { key: 'messages',  icon: 'mail',             label: 'Messages',        href: '#' },
    { key: 'memory',    icon: 'auto_stories',     label: 'Memory Box',      href: '#' },
    { key: 'events',    icon: 'event',            label: 'Events',          href: '#' },
  ];

  const links = isTeacher ? teacherLinks : parentLinks;
  const tagline = isTeacher ? 'NURTURING GROWTH' : 'THE LIVING SCRAPBOOK';
  const initials = (profile?.fullName?.[0] ?? '?').toUpperCase();
  const firstName = profile?.fullName?.split(' ')[0] ?? 'User';
  const isProfileActive = activeKey === 'profile';

  const sidebar = document.getElementById('sidebar');
  if (!sidebar) return;

  // Switch to cream background matching the concept
  sidebar.classList.remove('bg-surface');
  sidebar.classList.add('bg-surface-low');

  sidebar.innerHTML = `
    <div class="px-6 mb-8">
      <div class="flex items-center gap-3">
        <div class="w-10 h-10 rounded-full bg-primary flex items-center justify-center flex-shrink-0">
          <span class="material-symbols-outlined text-ink" style="font-size:20px;font-variation-settings:'FILL' 1">wb_sunny</span>
        </div>
        <div>
          <div class="text-lg font-extrabold text-ink tracking-tight leading-tight">MyTadika</div>
          <div class="text-[9px] text-ink-muted font-bold uppercase tracking-widest">${tagline}</div>
        </div>
      </div>
    </div>

    <nav class="flex-1 px-3 space-y-0.5 overflow-y-auto">
      ${links.map(l => {
        const active = l.key === activeKey;
        const cls = active
          ? 'flex items-center gap-3 px-4 py-3 rounded-xl text-sm font-bold bg-primary text-ink shadow-sm'
          : 'flex items-center gap-3 px-4 py-3 rounded-xl text-sm font-medium text-ink-muted hover:bg-white/70 hover:text-ink transition-all duration-150';
        return `<a href="${l.href}" class="${cls}">
          <span class="material-symbols-outlined" style="font-size:20px${active ? ";font-variation-settings:'FILL' 1" : ''}">${l.icon}</span>
          <span>${l.label}</span>
        </a>`;
      }).join('')}
    </nav>

    <div class="px-3 pt-4 mt-4 border-t border-outline space-y-0.5">
      <a href="profile.html" class="flex items-center gap-3 px-4 py-3 rounded-xl transition-all duration-150 ${isProfileActive ? 'bg-primary text-ink font-bold shadow-sm' : 'text-ink-muted hover:bg-white/70 hover:text-ink'}">
        <div class="w-7 h-7 rounded-full bg-primary flex items-center justify-center text-ink font-extrabold text-xs flex-shrink-0">
          ${initials}
        </div>
        <div class="min-w-0 flex-1">
          <div class="text-sm font-bold text-ink truncate">${firstName}</div>
          <div class="text-[10px] text-ink-muted capitalize">${role.toLowerCase()}</div>
        </div>
      </a>
      <button id="sidebar-logout"
        class="w-full flex items-center gap-3 px-4 py-3 rounded-xl text-sm font-medium transition-colors hover:bg-red-50"
        style="color:#FF6B6B">
        <span class="material-symbols-outlined" style="font-size:20px">logout</span>
        <span>Logout</span>
      </button>
    </div>
  `;

  document.getElementById('sidebar-logout').addEventListener('click', () => {
    localStorage.clear();
    window.location.href = 'login.html';
  });
}
