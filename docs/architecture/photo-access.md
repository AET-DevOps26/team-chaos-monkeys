# Photo Access

Item photos are stored in MinIO, but MinIO is not browser-facing in AET or in
local deployments. Browser clients must use backend proxy URLs instead of
presigned MinIO URLs.

Staff access:
- Found item photos use `GET /api/found-items/{id}/photo`.
- Lost report photos use `GET /api/lost-items/{id}/photo`.
- These routes stay behind the gateway resource server and require the same
  staff JWT as the item read endpoints.

Guest match access:
- Public match pages use `GET /api/matches/public/{token}/found-item/photo`.
- The matching service verifies the existing match-view magic-link token, checks
  that the match belongs to the token venue, and then fetches the photo through
  the found-item service internal API.

MinIO remains internal-only. The legacy signed URL endpoints may still exist for
internal or alternate storage adapters, but browser UI should consume the
`photoUrl` value returned by item read DTOs.
