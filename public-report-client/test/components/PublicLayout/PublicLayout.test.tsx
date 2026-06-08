import { describe, expect, it } from 'vitest'
import { render, screen } from '@testing-library/react'
import PublicLayout from '@/components/PublicLayout/PublicLayout'

describe('<PublicLayout />', () => {
  it('renders the FoundFlow wordmark and its children', () => {
    render(
      <PublicLayout>
        <p>hello guest</p>
      </PublicLayout>,
    )

    expect(screen.getByText('FoundFlow')).toBeInTheDocument()
    expect(screen.getByText('hello guest')).toBeInTheDocument()
  })

  it('has no navigation links (unauthenticated surface)', () => {
    render(
      <PublicLayout>
        <p>content</p>
      </PublicLayout>,
    )

    expect(screen.queryByRole('link')).not.toBeInTheDocument()
  })
})
