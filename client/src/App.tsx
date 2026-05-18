import { useGetAllUsers } from '@/api/auth/user-controller/user-controller'

function App() {
  const { data, isLoading, error } = useGetAllUsers()

  const status = isLoading ? '…' : error ? 'error' : (data?.length ?? 0)

  return (
    <main style={{ padding: '2rem', fontFamily: 'Arial' }}>
      <h1>FoundFlow</h1>
      <p>Users loaded: {status}</p>
    </main>
  )
}

export default App
