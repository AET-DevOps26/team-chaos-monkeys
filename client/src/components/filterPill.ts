// Shared styling for the rounded "filter pill" toggle buttons used across the
// overview pages (lost reports, found items, matches) and the dashboard search
// scope selector. Keeps the active/idle look consistent in one place.
const base = 'rounded-full px-3 py-1 text-xs font-medium transition-colors border'
const active = 'border-accent bg-accent-bg text-accent'
const idle = 'border-border text-text-h hover:border-accent'

export function filterPillClass(isActive: boolean): string {
  return `${base} ${isActive ? active : idle}`
}
