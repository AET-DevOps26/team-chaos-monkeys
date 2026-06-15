import { Routes, Route } from 'react-router-dom'
import Dashboard from '@/pages/Dashboard'
import FoundItemIntake from '@/pages/FoundItemIntake'
import FoundItemsOverview from '@/pages/FoundItemsOverview'
import LostReportsOverview from '@/pages/LostReportsOverview'
import Matching from '@/pages/Matching'
import Login from '@/pages/Login'
import RequireAuth from '@/auth/RequireAuth'
import Layout from '@/components/Layout/Layout'

// The public guest report surface (/report) now lives in its own deployable
// micro-frontend (public-report-client), routed by the edge/ingress.
export default function AppRoutes() {
  return (
    <Routes>
      <Route path="/login" element={<Login />} />
      <Route
        path="/"
        element={
          <RequireAuth>
            <Layout>
              <Dashboard />
            </Layout>
          </RequireAuth>
        }
      />
      <Route
        path="/intake"
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
      <Route
        path="/matches"
        element={
          <RequireAuth>
            <Layout>
              <Matching />
            </Layout>
          </RequireAuth>
        }
      />
      <Route path="*" element={<main className="p-8"><h1>404</h1></main>} />
    </Routes>
  )
}
