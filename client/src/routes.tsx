import { Routes, Route } from 'react-router-dom'
import FoundItemIntake from '@/pages/FoundItemIntake'
import FoundItemsOverview from '@/pages/FoundItemsOverview'
import LostReportsOverview from '@/pages/LostReportsOverview'
import ReportLostItem from '@/pages/public/ReportLostItem'
import ReportConfirmation from '@/pages/public/ReportLostItem/ReportConfirmation'
import Login from '@/pages/Login'
import RequireAuth from '@/auth/RequireAuth'
import Layout from '@/components/Layout/Layout'

export default function AppRoutes() {
  return (
    <Routes>
      <Route path="/login" element={<Login />} />
      <Route path="/report" element={<ReportLostItem />} />
      <Route path="/report/confirmation" element={<ReportConfirmation />} />
      <Route
        path="/"
        element={
          <RequireAuth>
            <Layout>
              <FoundItemIntake />
            </Layout>
          </RequireAuth>
        }
      />
      <Route
        path="/found-items"
        element={
          <RequireAuth>
            <Layout>
              <FoundItemsOverview />
            </Layout>
          </RequireAuth>
        }
      />
      <Route
        path="/lost-items"
        element={
          <RequireAuth>
            <Layout>
              <LostReportsOverview />
            </Layout>
          </RequireAuth>
        }
      />
      <Route path="*" element={<main className="p-8"><h1>404</h1></main>} />
    </Routes>
  )
}
