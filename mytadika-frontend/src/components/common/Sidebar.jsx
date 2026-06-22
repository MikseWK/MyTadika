import { NavLink } from 'react-router-dom'
import { useAuth } from '../../context/AuthContext'
import { useMyChildren } from '../../hooks/useStudents'
import './Sidebar.css'

function NavList({ items }) {
  return (
    <nav className="sidebar-nav">
      {items.map((item) => (
        <NavLink
          key={item.label}
          to={item.to}
          className={({ isActive }) => `sidebar-link${isActive ? ' active' : ''}`}
        >
          {item.label}
        </NavLink>
      ))}
    </nav>
  )
}

function SidebarFooter() {
  const { account, signOut } = useAuth()
  return (
    <div className="sidebar-footer">
      <div className="sidebar-account">
        <div className="sidebar-account-name">{account?.fullName}</div>
        <div className="sidebar-account-role">{account?.role?.toLowerCase()}</div>
      </div>
      <button type="button" className="btn btn-secondary" onClick={signOut}>
        Sign out
      </button>
    </div>
  )
}

function ParentSidebar() {
  const { data: children } = useMyChildren()
  const singleChildId = children?.length === 1 ? children[0].id : null

  const items = [
    { label: 'Home', to: '/parent/dashboard' },
    { label: 'Academic Report', to: singleChildId ? `/students/${singleChildId}/academic` : '/parent/dashboard' },
    { label: 'Health', to: singleChildId ? `/students/${singleChildId}/health` : '/parent/dashboard' },
    { label: 'Classroom', to: '/classroom' },
    { label: 'Messages', to: '/messages' },
    { label: 'Memory Box', to: '/memory-box' },
    { label: 'Events', to: '/events' },
    { label: 'Profile', to: '/profile' },
    { label: 'Help', to: '/help' },
  ]

  return <NavList items={items} />
}

function TeacherSidebar() {
  const items = [
    { label: 'Home', to: '/teacher/dashboard' },
    { label: 'Classroom', to: '/classroom' },
    { label: 'Student Reports', to: '/students' },
    { label: 'Health', to: '/students' },
    { label: 'Messages', to: '/messages' },
    { label: 'Memory Box', to: '/memory-box' },
    { label: 'Profile', to: '/profile' },
  ]

  return <NavList items={items} />
}

function AdminSidebar() {
  const items = [
    { label: 'Home', to: '/admin/dashboard' },
    { label: 'Students', to: '/students' },
    { label: 'Profile', to: '/profile' },
  ]

  return <NavList items={items} />
}

function Sidebar({ role }) {
  return (
    <aside className="app-sidebar">
      <div className="sidebar-brand">
        <span>☀️</span>
        <span>MyTadika</span>
      </div>
      {role === 'PARENT' && <ParentSidebar />}
      {role === 'TEACHER' && <TeacherSidebar />}
      {role === 'ADMIN' && <AdminSidebar />}
      <SidebarFooter />
    </aside>
  )
}

export default Sidebar
