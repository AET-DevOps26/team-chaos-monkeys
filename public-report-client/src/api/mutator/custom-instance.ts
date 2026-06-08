import axios, { type AxiosRequestConfig, type AxiosError } from 'axios'

// The public report app is unauthenticated: no bearer token, no 401-refresh.
// This is a deliberately bare axios instance (cf. the admin client's
// interceptor-laden mutator).
export const axiosInstance = axios.create({
  // No baseURL: generated client paths already include the `/api` prefix
  // (e.g. `/api/lost-items`), which the edge/ingress proxies to the gateway.
  headers: { 'Content-Type': 'application/json' },
})

export const customInstance = <T>(config: AxiosRequestConfig): Promise<T> => {
  if (config.data instanceof FormData) {
    config.headers = { ...config.headers, 'Content-Type': undefined }
  }
  return axiosInstance(config).then(({ data }) => data)
}

export type ErrorType<E> = AxiosError<E>
