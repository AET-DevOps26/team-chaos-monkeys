import { Routes, Route } from 'react-router-dom'
import PublicLayout from '@/components/PublicLayout/PublicLayout'
import ReportLostItem from '@/pages/ReportLostItem'
import ReportConfirmation from '@/pages/ReportLostItem/ReportConfirmation'

// Routes are relative to the router basename (/report).
export default function AppRoutes() {
  return (
    <Routes>
      <Route
        path="/"
        element={
          <PublicLayout>
            <ReportLostItem />
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
      <Route path="*" element={<main className="p-8"><h1>404</h1></main>} />
    </Routes>
  )
}
