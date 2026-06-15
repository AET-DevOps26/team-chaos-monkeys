import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { useLocation, useNavigate } from 'react-router-dom'
import { useLogin } from '@/api/auth/auth-controller/auth-controller'
import { loginBody } from '@/api/auth/zod'
import type { LoginRequest } from '@/api/auth/model'
import { useAuth } from '@/auth/useAuth'

const inputCls =
  'rounded border border-border bg-transparent p-2 text-sm outline-none focus:border-accent'
const labelCls = 'text-xs font-medium text-text-h'

export default function Login() {
  const { login } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const from = (location.state as { from?: { pathname?: string } } | null)?.from?.pathname ?? '/'

  const [submitError, setSubmitError] = useState<string | null>(null)
  const { mutateAsync: loginRequest, isPending } = useLogin()

  const {
    register,
    handleSubmit,
    formState: { errors, isValid },
  } = useForm<LoginRequest>({
    resolver: zodResolver(loginBody),
    mode: 'onChange',
    defaultValues: { email: '', password: '' },
  })

  const onSubmit = async (data: LoginRequest) => {
    setSubmitError(null)
    try {
      const tokens = await loginRequest({ data })
      if (!tokens.accessToken) throw new Error('No access token in response')
      login(tokens)
      navigate(from, { replace: true })
    } catch (err) {
      const status = (err as { response?: { status?: number } }).response?.status
      setSubmitError(status === 401 ? 'Invalid email or password.' : 'Login failed. Try again.')
    }
  }

  return (
    <main className="mx-auto flex h-screen w-full max-w-sm flex-col justify-center p-4">
      <form onSubmit={handleSubmit(onSubmit)} className="flex flex-col gap-4" noValidate>
        <h1 className="text-xl font-medium text-text-h">Sign in</h1>

        <div className="flex flex-col gap-1">
          <label htmlFor="email" className={labelCls}>Email</label>
          <input
            id="email"
            type="email"
            autoComplete="username"
            className={inputCls}
            {...register('email')}
          />
          {errors.email && (
            <span className="text-xs text-red-500">{errors.email.message}</span>
          )}
        </div>

        <div className="flex flex-col gap-1">
          <label htmlFor="password" className={labelCls}>Password</label>
          <input
            id="password"
            type="password"
            autoComplete="current-password"
            className={inputCls}
            {...register('password')}
          />
          {errors.password && (
            <span className="text-xs text-red-500">{errors.password.message}</span>
          )}
        </div>

        {submitError && (
          <>
          <span className="text-xs text-red-500">{submitError} {submitError?.includes('Invalid email or password') && (
            <span className="text-xs text-red-500">
              wrong password. did you mean 'password123'?
            </span>
          )}</span>
          
          </>
        )}

        <button
          type="submit"
          disabled={!isValid || isPending}
          className="rounded bg-accent px-4 py-2 text-sm font-medium text-white transition-opacity disabled:cursor-not-allowed disabled:opacity-50"
        >
          {isPending ? 'Signing in…' : 'Sign in'}
        </button>
      </form>
    </main>
  )
}
