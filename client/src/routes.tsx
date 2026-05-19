import { Routes, Route } from 'react-router-dom'
import FoundItemIntake from '@/pages/FoundItemIntake'
import ReportLostItem from '@/pages/public/ReportLostItem'

export default function AppRoutes() {
  return (
    <Routes>
      <Route path="/" element={<FoundItemIntake />} />
      <Route path="/report" element={<ReportLostItem />} />
      <Route path="*" element={<main className="p-8"><h1>404</h1></main>} />
    </Routes>
  )
}
