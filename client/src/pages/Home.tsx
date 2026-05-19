import { useGetAllUsers } from '@/api/auth/user-controller/user-controller'

export default function Home() {
  const { data, isLoading, error } = useGetAllUsers()
  const status = isLoading ? '…' : error ? 'error' : (data?.length ?? 0)
  return (
    <main className="p-8 font-sans">
      <h1>FoundFlow</h1>
      <p>Users loaded: {status}</p>
    </main>
  )
}
