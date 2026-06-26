import { useQuery } from '@tanstack/react-query'
import { axiosInstance } from '@/api/mutator/custom-instance'

// A venue as exposed by the operations-service public endpoint
// (`GET /api/venues/public`). This is the only venue data the guest report
// app needs: the human-readable name and the id we submit reports against.
export type PublicVenue = {
  venueId: string
  name: string
}

// Turn a venue name into a URL-friendly slug, e.g. "Grand Hotel" -> "grand-hotel".
// We put the slug in the report URL (/report/grand-hotel) instead of the raw
// UUID so the per-venue QR links are human-readable. Matching is done on the
// slug, so capitalisation and spaces in the name don't matter.
export function slugify(name: string): string {
  return name
    .toLowerCase()
    .trim()
    .replace(/[^a-z0-9]+/g, '-') // any run of non-alphanumerics -> single hyphen
    .replace(/^-+|-+$/g, '') // trim leading/trailing hyphens
}

// Fetch the list of public venues. The guest app is unauthenticated and this
// endpoint is open (permitAll in operations-service), so a bare GET is enough.
async function fetchPublicVenues(): Promise<PublicVenue[]> {
  const { data } = await axiosInstance.get<PublicVenue[]>('/api/venues/public')
  return data
}

// React Query hook: loads the public venues once and caches them. The report
// page uses this to translate the venue name in the URL into a venue id.
export function usePublicVenues() {
  return useQuery({
    queryKey: ['public-venues'],
    queryFn: fetchPublicVenues,
  })
}

// Find the venue whose name matches the slug from the URL, or undefined if the
// link points at a venue that doesn't exist.
export function findVenueBySlug(
  venues: PublicVenue[] | undefined,
  slug: string | undefined,
): PublicVenue | undefined {
  if (!venues || !slug) return undefined
  return venues.find((venue) => slugify(venue.name) === slug)
}
