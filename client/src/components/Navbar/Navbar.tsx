import { NavLink } from 'react-router-dom'
import { useAuth } from '@/auth/useAuth'

const linkBase =
  'rounded px-3 py-1.5 text-sm font-medium transition-colors hover:text-accent'
const linkActive = 'bg-accent-bg text-accent'
const linkIdle = 'text-text-h'

export default function Navbar() {
  const { user, logout } = useAuth()

  return (
    <header className="w-full rounded-b-2xl border-b border-border bg-bg shadow-[var(--shadow)]">
      <nav
        aria-label="Primary"
        className="mx-auto flex h-14 w-full max-w-6xl items-center gap-6 px-6"
      >
        <span className="text-base font-semibold text-text-h">FoundFlow</span>

        <ul className="flex flex-1 items-center gap-1">
          <li>
            <NavLink
              to="/"
              end
              className={({ isActive }) =>
                `${linkBase} ${isActive ? linkActive : linkIdle}`
              }
            >
              Dashboard
            </NavLink>
          </li>
          <li>
            <NavLink
              to="/intake"
              className={({ isActive }) =>
                `${linkBase} ${isActive ? linkActive : linkIdle}`
              }
            >
              New Intake
            </NavLink>
          </li>
          <li>
            <NavLink
              to="/found-items"
              className={({ isActive }) =>
                `${linkBase} ${isActive ? linkActive : linkIdle}`
              }
            >
              Found Items
            </NavLink>
          </li>
          <li>
            <NavLink
              to="/lost-items"
              className={({ isActive }) =>
                `${linkBase} ${isActive ? linkActive : linkIdle}`
              }
            >
              Lost Items
            </NavLink>
          </li>
          <li>
            <NavLink
              to="/matches"
              className={({ isActive }) =>
                `${linkBase} ${isActive ? linkActive : linkIdle}`
              }
            >
              Matches
            </NavLink>
          </li>
          <li>
            <NavLink
              to="/returns"
              className={({ isActive }) =>
                `${linkBase} ${isActive ? linkActive : linkIdle}`
              }
            >
              Returns
            </NavLink>
          </li>
        </ul>

        {user && (
          <div className="flex items-center gap-3">
            <span className="hidden text-xs text-text sm:inline" aria-label="Signed in as">
              {user.sub}
            </span>
            <button
              type="button"
              onClick={logout}
              className="rounded border border-border px-3 py-1 text-sm text-text-h transition-colors hover:border-accent hover:text-accent"
            >
              Log out
            </button>
          </div>
        )}
      </nav>
    </header>
  )
}
