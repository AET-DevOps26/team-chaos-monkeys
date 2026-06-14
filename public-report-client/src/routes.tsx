import { Routes, Route } from 'react-router-dom'
import PublicLayout from '@/components/PublicLayout/PublicLayout'
import ReportLostItem from '@/pages/ReportLostItem'
import ReportConfirmation from '@/pages/ReportLostItem/ReportConfirmation'

// Routes are relative to the router basename (/report). A report is always
// scoped to a venue, supplied as the first path segment (e.g. /report/<venueId>,
// typically reached via a per-venue QR link). The static `/confirmation` route
// is ranked above the dynamic `:venueId` segment by the router.
export default function AppRoutes() {
  return (
    <Routes>
      <Route
        path="/"
        element={
          <PublicLayout>
            <MissingVenue />
          </PublicLayout>
        }
      />
      <Route
        path="/confirmation"
        element={
          <PublicLayout>
            <ReportConfirmation />
          </PublicLayout>
        }
      />
      <Route
        path="/:venueId"
        element={
          <PublicLayout>
            <ReportLostItem />
          </PublicLayout>
        }
      />
      <Route path="*" element={<main className="p-8"><h1>404</h1></main>} />
    </Routes>
  )
}

function MissingVenue() {
  return (
    <main className="mx-auto w-full max-w-xl p-6">
      <h1 className="mb-3 text-3xl font-medium text-text-h">Report a lost item</h1>
      <p className="text-sm text-text">
        Please use the link or QR code provided at your venue to report a lost item.
      </p>
    </main>
  )
}
