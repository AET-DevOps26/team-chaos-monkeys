import { useState, type FormEvent } from 'react'
import { useSearch } from '@/api/matches/item-search-controller/item-search-controller'
import type { ItemSearchRequestScope, ItemSearchResponse } from '@/api/matches/model'
import { ItemSearchRequestScope as Scope } from '@/api/matches/model'
import { filterPillClass } from '@/components/filterPill'
import { uid } from '@/lib/uid'
import SearchResultCard from './SearchResultCard'

const SCOPES: { value: ItemSearchRequestScope; label: string }[] = [
  { value: Scope.BOTH, label: 'Both' },
  { value: Scope.LOST, label: 'Lost' },
  { value: Scope.FOUND, label: 'Found' },
]

type Turn = {
  id: string
  query: string
  status: 'pending' | 'done' | 'error'
  response?: ItemSearchResponse
}

function AnswerBlock({ response }: { response: ItemSearchResponse }) {
  // Only surface items the answer explicitly cited, if nothing was cited there is no grounded match to show.
  const citations = new Set(response.citations ?? [])
  const cited = (response.results ?? []).filter(
    (item) => item.id && citations.has(item.id),
  )
  // The orchestrator returns answer:null when synthesis is unavailable/disabled.
  const degraded = response.answer == null
  const grounded = response.grounded !== false && cited.length > 0

  return (
    <div className="flex flex-col gap-3">
      {!grounded ? (
        <p className="text-sm text-text">No matching items found.</p>
      ) : degraded ? (
        <p className="text-sm italic text-text">
          Summary unavailable — showing the cited matches.
        </p>
      ) : (
        <p className="whitespace-pre-wrap text-sm text-text-h">{response.answer}</p>
      )}

      {grounded && (
        <div className="flex flex-col gap-3 border-t border-border pt-3">
          {cited.map((item) => (
            <SearchResultCard key={item.id} item={item} />
          ))}
        </div>
      )}
    </div>
  )
}

export default function ItemSearchPanel() {
  const [query, setQuery] = useState('')
  const [scope, setScope] = useState<ItemSearchRequestScope>(Scope.BOTH)
  const [turns, setTurns] = useState<Turn[]>([])
  const { mutateAsync, isPending } = useSearch()

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault()
    const q = query.trim()
    if (!q || isPending) return
    const id = uid()
    setTurns((t) => [...t, { id, query: q, status: 'pending' }])
    setQuery('')
    try {
      const response = await mutateAsync({ data: { query: q, scope } })
      setTurns((t) =>
        t.map((x) => (x.id === id ? { ...x, status: 'done', response } : x)),
      )
    } catch {
      setTurns((t) =>
        t.map((x) => (x.id === id ? { ...x, status: 'error' } : x)),
      )
    }
  }

  return (
    <section
      aria-label="Item search"
      className="flex flex-col gap-4 rounded-lg border border-border bg-surface p-5"
    >
      <div className="flex flex-col gap-1">
        <h2 className="text-lg font-semibold text-text-h">Ask about items</h2>
        <p className="text-sm text-text">
          Search the lost &amp; found records in plain language.
        </p>
      </div>

      {turns.length > 0 && (
        <ol className="flex flex-col gap-4">
          {turns.map((turn) => (
            <li key={turn.id} className="flex flex-col gap-2">
              {/* User query: right-aligned rounded rectangle. */}
              <div className="self-end max-w-[85%] rounded-lg border border-border px-3 py-2 text-sm font-medium text-text-h">
                {turn.query}
              </div>
              {/* Response: left-aligned rounded rectangle. */}
              <div className="self-start max-w-[85%] rounded-lg border border-border px-3 py-2">
                {turn.status === 'pending' ? (
                  <p className="text-sm text-text" role="status">
                    Searching…
                  </p>
                ) : turn.status === 'error' ? (
                  <p className="text-sm text-red-500" role="alert">
                    Search failed. Please try again.
                  </p>
                ) : (
                  turn.response && <AnswerBlock response={turn.response} />
                )}
              </div>
            </li>
          ))}
        </ol>
      )}

      <form onSubmit={onSubmit} className="flex flex-col gap-3">
        <div className="flex gap-2" role="group" aria-label="Search scope">
          {SCOPES.map(({ value, label }) => (
            <button
              key={value}
              type="button"
              aria-pressed={scope === value}
              onClick={() => setScope(value)}
              className={filterPillClass(scope === value)}
            >
              {label}
            </button>
          ))}
        </div>
        <div className="flex gap-2">
          <input
            type="text"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            aria-label="Search query"
            placeholder="e.g. Did we find any silver keys?"
            className="flex-1 rounded border border-border bg-transparent p-2 text-sm outline-none focus:border-accent"
          />
          <button
            type="submit"
            disabled={!query.trim() || isPending}
            className="rounded bg-accent px-4 py-2 text-sm font-medium text-white transition-opacity disabled:cursor-not-allowed disabled:opacity-50"
          >
            {isPending ? 'Searching…' : 'Search'}
          </button>
        </div>
      </form>
    </section>
  )
}
