import axios, { type AxiosRequestConfig, type AxiosError } from 'axios'
import { dispatchUnauthorized, getCurrentToken } from '@/auth/token-store'

export const axiosInstance = axios.create({
  // No baseURL: generated client paths already include the `/api` prefix
  // (e.g. `/api/found-items`), which nginx proxies straight to the gateway.
  headers: { 'Content-Type': 'application/json' },
})

axiosInstance.interceptors.request.use((config) => {
  const token = getCurrentToken()
  if (token) {
    config.headers.set('Authorization', `Bearer ${token}`)
  }
  if (typeof FormData !== 'undefined' && config.data instanceof FormData) {
    config.headers.delete('Content-Type')
  }
  return config
})

axiosInstance.interceptors.response.use(
  (response) => response,
  (error: AxiosError) => {
    if (error.response?.status === 401) {
      dispatchUnauthorized()
    }
    return Promise.reject(error)
  },
)

export const customInstance = <T>(config: AxiosRequestConfig): Promise<T> =>
  axiosInstance(config).then(({ data }) => data)

export type ErrorType<E> = AxiosError<E>
