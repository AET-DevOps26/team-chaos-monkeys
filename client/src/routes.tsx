import { Routes, Route } from 'react-router-dom'
import FoundItemIntake from '@/pages/FoundItemIntake'
import ReportLostItem from '@/pages/public/ReportLostItem'
import ReportConfirmation from '@/pages/public/ReportLostItem/ReportConfirmation'
import Login from '@/pages/Login'
import RequireAuth from '@/auth/RequireAuth'

export default function AppRoutes() {
  return (
    <Routes>
      <Route path="/login" element={<Login />} />
      <Route path="/report" element={<ReportLostItem />} />
      <Route path="/report/confirmation/:id" element={<ReportConfirmation />} />
      <Route
        path="/"
        element={
          <RequireAuth>
            <FoundItemIntake />
          </RequireAuth>
        }
      />
      <Route path="*" element={<main className="p-8"><h1>404</h1></main>} />
    </Routes>
  )
}
