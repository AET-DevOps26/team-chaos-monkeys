type StatCardProps = {
  label: string
  value: number | undefined
  isLoading: boolean
  accent?: boolean
}

export default function StatCard({
  label,
  value,
  isLoading,
  accent = false,
}: StatCardProps) {
  return (
    <article
      className={`flex flex-col gap-2 rounded-lg border border-border p-5 ${
        accent ? 'bg-accent-bg' : 'bg-surface'
      }`}
    >
      <span className="text-sm font-medium text-text">{label}</span>
      {isLoading ? (
        <div
          className="h-9 w-20 animate-pulse rounded bg-border/50"
          aria-hidden="true"
        />
      ) : (
        <span
          className={`text-3xl font-semibold ${
            accent ? 'text-accent' : 'text-text-h'
          }`}
        >
          {(value ?? 0).toLocaleString()}
        </span>
      )}
    </article>
  )
}
