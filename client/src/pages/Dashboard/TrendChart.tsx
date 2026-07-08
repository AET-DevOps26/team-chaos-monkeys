import {
  CartesianGrid,
  Legend,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'

import { isWithinWindow, type Granularity } from './trendChartUtils'

type Bucket = { bucketStart?: string; count?: number }
type Histogram =
  | { perDay?: Bucket[]; perWeek?: Bucket[]; perMonth?: Bucket[] }
  | undefined

type Series = { key: string; label: string; color: string; data: Histogram }

type TrendChartProps = {
  granularity: Granularity
  series: Series[]
  isLoading: boolean
}

type Row = { bucketStart: string } & Record<string, number | string>

function mergeSeries(series: Series[], granularity: Granularity): Row[] {
  const rows = new Map<string, Row>()
  for (const s of series) {
    for (const bucket of s.data?.[granularity] ?? []) {
      if (!bucket.bucketStart) continue
      if (!isWithinWindow(bucket.bucketStart, granularity)) continue
      const row = rows.get(bucket.bucketStart) ?? {
        bucketStart: bucket.bucketStart,
      }
      row[s.key] = bucket.count ?? 0
      rows.set(bucket.bucketStart, row)
    }
  }
  return [...rows.values()]
    .sort((a, b) => a.bucketStart.localeCompare(b.bucketStart))
    .map((row) => {
      for (const s of series) row[s.key] = (row[s.key] as number) ?? 0
      return row
    })
}

export default function TrendChart({
  granularity,
  series,
  isLoading,
}: TrendChartProps) {
  if (isLoading) {
    return (
      <div
        className="h-80 w-full animate-pulse rounded-lg border border-border bg-border/30"
        aria-hidden="true"
      />
    )
  }

  const data = mergeSeries(series, granularity)

  if (data.length === 0) {
    return (
      <div className="flex h-80 w-full items-center justify-center rounded-lg border border-border bg-surface text-sm text-text">
        No activity in this period yet.
      </div>
    )
  }

  return (
    <div className="h-80 w-full rounded-lg border border-border bg-surface p-4">
      <ResponsiveContainer width="100%" height="100%">
        <LineChart data={data} margin={{ top: 8, right: 16, bottom: 8, left: 0 }}>
          <CartesianGrid strokeDasharray="3 3" className="stroke-border" />
          <XAxis dataKey="bucketStart" fontSize={12} stroke="currentColor" />
          <YAxis allowDecimals={false} fontSize={12} stroke="currentColor" />
          <Tooltip />
          <Legend />
          {series.map((s) => (
            <Line
              key={s.key}
              type="monotone"
              dataKey={s.key}
              name={s.label}
              stroke={s.color}
              strokeWidth={2}
              dot={false}
            />
          ))}
        </LineChart>
      </ResponsiveContainer>
    </div>
  )
}
