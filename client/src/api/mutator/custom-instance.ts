import axios, { type AxiosRequestConfig, type AxiosError } from 'axios'

export const axiosInstance = axios.create({
  baseURL: '/api',
  headers: { 'Content-Type': 'application/json' },
})

// TODO: attach `Authorization: Bearer <token>` when auth lands.

export const customInstance = <T>(config: AxiosRequestConfig): Promise<T> =>
  axiosInstance(config).then(({ data }) => data)

export type ErrorType<E> = AxiosError<E>
