import type { SearchResultItem } from '@/api/matches/model'

// A retrieved item card for the search panel: shows the matched item's category,
// a text snippet, and a coarse similarity score. Read-only — it does not link out.
function snippet(text: string | undefined): string {
  const firstLine = text?.split(/\r?\n/)[0]?.trim()
  return firstLine || text?.trim() || '—'
}

// distance is a cosine distance (lower = closer); surface it as a coarse
// similarity so staff get a sense of confidence without exposing raw vectors.
function similarityLabel(distance: number | undefined): string | null {
  if (distance == null || Number.isNaN(distance)) return null
  const sim = Math.max(0, Math.min(1, 1 - distance))
  return `${Math.round(sim * 100)}% match`
}

type Props = {
  item: SearchResultItem
}

export default function SearchResultCard({ item }: Props) {
  const similarity = similarityLabel(item.distance)
  const category = item.category?.trim() || 'Item'

  return (
    <div
      data-testid="search-result-card"
      className="flex flex-col gap-1"
    >
      <div className="flex items-center justify-between gap-2">
        <span className="text-sm font-medium text-text-h">{category}</span>
        <span className="shrink-0 text-[11px] font-medium uppercase tracking-wide text-text">
          {item.itemType}
        </span>
      </div>
      <p className="text-sm text-text">{snippet(item.text)}</p>
      {similarity && (
        <span className="text-[11px] text-text">{similarity}</span>
      )}
    </div>
  )
}
