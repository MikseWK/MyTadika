
// Links are relative to pages/ directory.

export function initSidebar(activeKey) {
  const profile = JSON.parse(localStorage.getItem('userProfile') || 'null');
  const role = profile?.role ?? 'PARENT';

  // For parent deep-links: use the stored selected child ID, fall back to dashboard
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
    { key: 'profile',   icon: 'account_circle',   label: 'Profile',       href: 'profile.html' },
  ];

  const teacherLinks = [
    { key: 'home',      icon: 'home',            label: 'Home',            href: 'dashboard-teacher.html' },
    { key: 'classroom', icon: 'school',           label: 'Classroom',       href: 'classroom.html' },
    { key: 'students',  icon: 'groups',           label: 'Student Reports', href: 'students.html' },
    { key: 'health',    icon: 'medical_services', label: 'Health',          href: 'students.html' },
    { key: 'messages',  icon: 'mail',             label: 'Messages',        href: '#' },
    { key: 'memory',    icon: 'auto_stories',     label: 'Memory Box',      href: '#' },
    { key: 'events',    icon: 'event',            label: 'Events',          href: '#' },
    { key: 'profile',   icon: 'account_circle',   label: 'Profile',         href: 'profile.html' },
  ];

  const links = (role === 'TEACHER' || role === 'ADMIN') ? teacherLinks : parentLinks;

  const sidebar = document.getElementById('sidebar');
  if (!sidebar) return;

  sidebar.innerHTML = `
    <div class="px-6 mb-8">
      <div class="flex items-center gap-3">
        <div class="w-10 h-10 rounded-full bg-primary flex items-center justify-center">
          <span class="material-symbols-outlined text-ink" style="font-size:20px">school</span>
        </div>
        <div>
          <div class="text-lg font-extrabold text-ink tracking-tight">MyTadika</div>
          <div class="text-[10px] text-ink-muted uppercase tracking-widest">Portal</div>
        </div>
      </div>
    </div>
    <nav class="flex-1 space-y-1 overflow-y-auto">
      ${links.map(l => {
        const active = l.key === activeKey;
        const cls = active
          ? 'flex items-center gap-3 px-4 py-3 mx-2 rounded-full text-sm font-bold bg-primary text-ink shadow-sm translate-x-1'
          : 'flex items-center gap-3 px-4 py-3 mx-2 rounded-full text-sm font-medium text-ink-muted hover:bg-primary/20 hover:translate-x-1 transition-all duration-200';
        const fillStyle = active ? `style="font-variation-settings:'FILL' 1"` : '';
        return `<a href="${l.href}" class="${cls}">
          <span class="material-symbols-outlined" style="font-size:20px${active ? ";font-variation-settings:'FILL' 1" : ''}">${l.icon}</span>
          ${l.label}
        </a>`;
      }).join('')}
    </nav>
    <div class="mt-auto px-4 pt-4 border-t border-outline">
      <button id="sidebar-logout"
        class="w-full flex items-center gap-3 px-4 py-3 rounded-full text-sm font-medium hover:bg-danger/10 transition-colors"
        style="color:#FF6B6B">
        <span class="material-symbols-outlined" style="font-size:20px">logout</span>
        Logout
      </button>
    </div>
  `;

  document.getElementById('sidebar-logout').addEventListener('click', () => {
    localStorage.clear();
    window.location.href = 'login.html';
  });
}
