import type { FoundItemResponse } from '@/api/found-items/model'

// A single "label: value" row, only rendered when the value is present.
function DetailRow({ label, value }: { label: string; value: string | undefined }) {
  if (!value?.trim()) return null
  return (
    <div className="flex gap-2">
      <span className="shrink-0 text-text">{label}</span>
      <span className="text-text-h">{value}</span>
    </div>
  )
}

/**
 * The expandable panel under a found-item card. It shows the free-text intake
 * note plus the attributes the GenAI service extracted (category, brand, colour,
 * distinguishing marks). Marks are rendered as little chips.
 */
export default function FoundItemDetails({ item }: { item: FoundItemResponse }) {
  const attrs = item.attributes
  const marks = attrs?.marks?.filter((m) => m.trim()) ?? []

  const hasAnything =
    item.intakeText?.trim() ||
    item.location?.trim() ||
    attrs?.category?.trim() ||
    attrs?.brand?.trim() ||
    attrs?.color?.trim() ||
    marks.length > 0

  return (
    <div className="flex flex-col gap-2 rounded border border-border bg-bg/60 p-3 text-xs animate-[foundItemFadeIn_180ms_ease-out_both]">
      {item.intakeText?.trim() && (
        <p className="whitespace-pre-line text-text-h">{item.intakeText}</p>
      )}

      <div className="flex flex-col gap-1">
        <DetailRow label="Location" value={item.location} />
        <DetailRow label="Category" value={attrs?.category} />
        <DetailRow label="Brand" value={attrs?.brand} />
        <DetailRow label="Colour" value={attrs?.color} />
      </div>

      {marks.length > 0 && (
        <div className="flex flex-col gap-1">
          <span className="text-text">Marks</span>
          <div className="flex flex-wrap gap-1">
            {marks.map((mark) => (
              <span
                key={mark}
                className="rounded-full bg-accent-bg px-2 py-0.5 text-[10px] text-accent"
              >
                {mark}
              </span>
            ))}
          </div>
        </div>
      )}

      {!hasAnything && <span className="text-text">No extra details yet.</span>}

      <span className="mt-1 text-[10px] uppercase tracking-wide text-text/70">
        Attributes extracted by AI
      </span>
    </div>
  )
}
