import { profile } from '../auth/authGuard.js';
import { initSidebar } from '../components/sidebar.js';
import api from '../api/axiosClient.js';

initSidebar('classroom');
document.getElementById('header-avatar').textContent = (profile.fullName?.[0] ?? '?').toUpperCase();

const isParent = profile.role === 'PARENT';
const isTeacher = profile.role === 'TEACHER' || profile.role === 'ADMIN';

// Colour palette cycles for classroom cards
const CARD_PALETTES = [
  { header: '#FFD700', badge: '#4A3F35', btn: 'bg-primary text-ink' },
  { header: '#FF8C42', badge: '#FFFFFF', btn: 'bg-secondary text-white' },
  { header: '#4CAF50', badge: '#FFFFFF', btn: 'bg-success text-white' },
  { header: '#FF9F43', badge: '#FFFFFF', btn: 'bg-warning text-ink' },
];

function classroomCard(name, teacherOrParentLabel, studentCount, studentId, palette, index) {
  const p = palette ?? CARD_PALETTES[index % CARD_PALETTES.length];
  const initials = name?.split(' ').map(w => w[0]).join('').slice(0, 2).toUpperCase() ?? 'CL';
  return `
    <div class="bg-surface rounded-xl overflow-hidden hover:-translate-y-1 transition-transform duration-300"
      style="box-shadow:0 12px 32px -4px rgba(74,63,53,.10)">
      <div class="h-36 flex items-center justify-center relative" style="background:${p.header}">
        <span class="text-4xl font-black opacity-30" style="color:${p.badge}">${initials}</span>
        ${studentCount != null
          ? `<span class="absolute top-4 right-4 px-3 py-1 rounded-full text-xs font-bold"
              style="background:rgba(255,255,255,.75);color:#4A3F35">${studentCount} students</span>`
          : ''}
      </div>
      <div class="p-6">
        <h4 class="text-xl font-bold text-ink mb-1">${name}</h4>
        <p class="text-sm text-ink-muted mb-4">${teacherOrParentLabel ?? ''}</p>
        <div class="flex gap-2">
          ${studentId != null
            ? `<a href="health.html?id=${studentId}"
                class="flex-1 py-2 rounded-full font-bold text-sm text-center hover:opacity-80 transition-opacity ${p.btn}">
                View Health
              </a>
               <a href="academic.html?id=${studentId}"
                class="flex-1 py-2 rounded-full font-bold text-sm text-center bg-surface-high text-ink hover:bg-surface-high/70 transition-colors">
                Academic
              </a>`
            : `<a href="students.html"
                class="flex-1 py-2 rounded-full font-bold text-sm text-center hover:opacity-80 transition-opacity ${p.btn}">
                View Students
              </a>`}
        </div>
      </div>
    </div>
  `;
}

document.getElementById('loading-state').classList.remove('hidden');

try {
  if (isParent) {
    // Parent: show their children's classrooms
    document.getElementById('hero-title').textContent = 'Your Digital Classroom Scrapbook';
    document.getElementById('hero-sub').textContent =
      "Explore the spaces where your little ones learn, grow, and play every day.";
    document.getElementById('grid-title').innerHTML =
      '<span class="material-symbols-outlined text-secondary">auto_awesome</span> Active Classrooms';

    const { data: children } = await api.get('/students/my-children');
    document.getElementById('loading-state').classList.add('hidden');

    const withClass = children.filter(c => c.className);

    if (withClass.length === 0) {
      document.getElementById('empty-state').classList.remove('hidden');
    } else {
      document.getElementById('classroom-grid').classList.remove('hidden');
      document.getElementById('grid-count').textContent = `${withClass.length} classroom${withClass.length !== 1 ? 's' : ''}`;
      document.getElementById('cards-container').innerHTML = withClass.map((child, i) =>
        classroomCard(
          child.className,
          `${child.fullName}'s Class`,
          null,
          child.id,
          CARD_PALETTES[i % CARD_PALETTES.length],
          i
        )
      ).join('');
    }

    // Also show children without a class assignment
    const noClass = children.filter(c => !c.className);
    if (noClass.length > 0) {
      const note = document.createElement('p');
      note.className = 'text-sm text-ink-muted mt-4';
      note.textContent = `${noClass.map(c => c.fullName).join(', ')} ${noClass.length > 1 ? 'have' : 'has'} not been assigned to a class yet.`;
      document.getElementById('classroom-grid').appendChild(note);
    }

  } else if (isTeacher) {
    // Teacher: derive classrooms from student list grouped by className
    document.getElementById('hero-title').textContent = "Teacher's Classroom Hub";
    document.getElementById('hero-sub').textContent =
      'Manage your students, track growth, and monitor health across all your classes.';
    document.getElementById('grid-title').innerHTML =
      '<span class="material-symbols-outlined text-secondary">school</span> My Managed Classrooms';

    const { data: students } = await api.get('/students');
    document.getElementById('loading-state').classList.add('hidden');

    // Group by className
    const classMap = new Map();
    for (const s of students) {
      const key = s.className ?? '__unassigned__';
      if (!classMap.has(key)) classMap.set(key, []);
      classMap.get(key).push(s);
    }

    const classes = [...classMap.entries()].filter(([k]) => k !== '__unassigned__');

    if (classes.length === 0) {
      document.getElementById('empty-state').classList.remove('hidden');
    } else {
      document.getElementById('classroom-grid').classList.remove('hidden');
      document.getElementById('grid-count').textContent = `${classes.length} classroom${classes.length !== 1 ? 's' : ''}`;
      document.getElementById('cards-container').innerHTML = classes.map(([name, members], i) =>
        classroomCard(
          name,
          'Early Childhood Programme',
          members.length,
          null,
          CARD_PALETTES[i % CARD_PALETTES.length],
          i
        )
      ).join('');
    }

    const unassigned = classMap.get('__unassigned__') ?? [];
    if (unassigned.length > 0) {
      const note = document.createElement('p');
      note.className = 'text-sm text-ink-muted mt-2';
      note.textContent = `${unassigned.length} student${unassigned.length !== 1 ? 's' : ''} not yet assigned to a class.`;
      document.getElementById('classroom-grid').appendChild(note);
    }
  }

  // Show coming-soon section for all roles
  document.getElementById('coming-soon-section').classList.remove('hidden');

} catch (err) {
  document.getElementById('loading-state').innerHTML =
    `<p class="font-medium" style="color:#FF6B6B">
      Failed to load classrooms. ${err.response?.status === 403 ? 'Access denied.' : 'Please try again.'}
    </p>`;
}
