import { Routes, Route } from 'react-router-dom'
import Home from '@/pages/Home'
import ReportLostItem from '@/pages/public/ReportLostItem'

export default function AppRoutes() {
  return (
    <Routes>
      <Route path="/" element={<Home />} />
      <Route path="/report" element={<ReportLostItem />} />
      <Route path="*" element={<main className="p-8"><h1>404</h1></main>} />
    </Routes>
  )
}
