# FoundFlow — Problem Statement

**Course:** DevOps: Engineering for Deployment and Operations (CIT423001)
**Team:** Chaos Monkeys
**Document deadline:** 08.05.2026

---

## 1. Background and Problem

Hospitality and event venues — hotels, clubs, transit hubs, museums, universities — handle a constant stream of lost-and-found cases. The process at most venues today is overwhelmingly manual: staff log found items in spreadsheets or paper notebooks, guests email descriptions back and forth with reception, and matching depends on whoever happens to remember a particular item. The result is low return rates, frustrated guests, and a meaningful share of staff time spent on fragmented administrative work that does not scale with case volume.

The underlying pain is narrow and unsolved at most venues: **the information needed to match a lost report to a found item exists, but it lives in unstructured human language on both sides** — a quick guest email, a hand-written tag on a found object — and reconciling them is left to human memory and goodwill.

FoundFlow targets that gap directly. It digitises both intake paths, structures the unstructured input with a GenAI service, and uses semantic search to surface candidate matches that simple attribute filters would miss.

---

## 2. Main Functionality

The system covers four core capabilities:

1. **Found-item intake.** Venue staff register a found item through a web app — a photo and a short note. The item is persisted with structured attributes, indexed for search, and made visible on the operational dashboard.
2. **Guest lost-item reporting.** Guests describe what they lost in free-text natural language through a public web form, typically reached via a QR code at the venue. The GenAI service extracts structured attributes from the description so the matching service can act on it.
3. **Matching.** The system continuously searches for likely pairings between open lost reports and found items, combining structured attribute matching with vector-based semantic similarity over item descriptions.
4. **Notification and claim.** When a candidate match is identified, the system contacts the guest with the relevant details and pickup instructions. The guest confirms, staff release the item, the case is closed.

Around these flows the system provides an operational dashboard for staff (open cases, recovery KPIs, audit trail), authentication and configuration, and the operational surface required for the course (containerised deployment, CI/CD, Prometheus and Grafana observability).

---

## 3. Intended Users

The system serves three distinct roles, each with a different entry point and a different definition of "the system worked."

- **Venue staff** (front desk, housekeeping, security) — log found items quickly, ideally in under thirty seconds, often from a tablet at the location where the item was found. Their success criterion is low time-per-item.
- **Operations manager** — monitors open cases, recovery rates, and SLA-style metrics through the dashboard. Uses the system to run lost-and-found as a tracked process rather than ad-hoc admin. Success criterion is visibility and a falling open-case queue.
- **Guest** — the end user who lost something. Interacts only through a public link, often reached via a QR code at the venue. Reports the loss in their own words and is contacted when there is a candidate match. Success criterion is recovering the item without phone tag.

---

## 4. GenAI Integration

The GenAI component is a separate Python microservice running as a peer of the Spring Boot services. It exposes a small, well-defined HTTP interface and supports both cloud-hosted models (OpenAI API) and a local model (LLaMA-family via a local runtime such as Ollama, or GPT4All) selectable through configuration. Switching between the two does not require a code change in the calling services.

The service is load-bearing: removing it would visibly degrade the system, not just remove a feature. It contributes in three places:

**a) Structured attribute extraction from guest reports.** Guests describe lost items in unstructured natural language ("black North Face puffer jacket with a small Berlin enamel pin, lost Saturday around 11pm near the cloakroom"). The GenAI service extracts a structured representation — category, brand, colour, distinguishing marks, approximate time, location hint — that the matching service can act on. This is the bridge between human input and any automated matching.

**b) Semantic search over found items via RAG.** Found-item descriptions and guest queries are embedded and stored in a vector index (pgvector or Weaviate; decided during the architecture phase). When attribute matching alone is too narrow or too noisy, semantic similarity recovers the long tail: a guest who describes "brown leather wallet with my German ID" is matched against a staff entry that reads "old leather wallet, brown, contains Personalausweis." Staff use the same retrieval path as a search interface for ad-hoc queries that come in by phone or email. This is the system's primary differentiator.

**c) Match verification and explanation.** Vector similarity is good at recall but noisy on precision — two different black jackets can score highly. When the matching service surfaces a candidate pair, it calls the GenAI component to judge, from the item descriptions alone, whether they are the same physical item, and to produce a short rationale. This makes the LLM a second opinion that catches false positives, and turns the opaque match score staff see into an explanation. It completes the RAG loop: retrieval (embeddings) followed by LLM reasoning over the retrieved candidate.

---

## 5. Scenarios

### Scenario A — Guest reports a lost item; the system finds a candidate match

A guest leaves a venue and only notices later that their jacket is missing. They scan the QR code from the venue's confirmation email and land on the public lost-item form. They type a short paragraph describing the jacket and the rough time and place. The intake service forwards the text to the GenAI service, which returns a structured attribute set. The lost report is persisted and an event is emitted. The matching service combines attribute matching with vector similarity over the found-item index, finds a likely candidate, and triggers the notification service. The guest receives an email with a photo and pickup details. They confirm. The case is now awaiting in-person handover.

### Scenario B — Staff log a found item; the system pairs it to an existing lost report

Front-desk staff find a wallet during a closing walkthrough. They open the staff app on a tablet, take a photo, add one line of text ("brown leather wallet, by the bar"), and submit. The intake service stores the item and emits an event. The matching service finds an open lost report with high attribute and semantic similarity, generated from a guest message earlier that night. The notification service sends a pickup email. The guest replies that they will collect it the next morning. Staff see the case as "claim confirmed" and set the wallet aside.

### Scenario C — Staff resolve an ambiguous query through semantic search

A guest calls the venue directly to ask whether their wallet has been turned in. The staff member types the description into the dashboard's search bar ("brown leather wallet, maybe German ID, last weekend, near the bar"). The query goes to the GenAI service's search endpoint, which embeds it and retrieves the top candidates from the found-item vector index, with a short rationale per hit. The staff member identifies the matching item and reserves it for the caller — without having to remember or re-open the original case manually.

---

## 6. Scope

The scope is intentionally narrow so that engineering effort lands on a reliable, observable, automatically deployed end-to-end system rather than on feature count.

**In scope:** a single venue, staff intake of found items, guest lost reporting through a public link, GenAI-driven attribute extraction and semantic matching with embeddings, automated guest notification, and manual pickup confirmation. This is the slice that will be containerised, deployed to Kubernetes through CI/CD, and instrumented for Prometheus and Grafana.

**Explicitly out of scope** for this iteration: payment and restitution flows, courier and shipping integration, multi-tenancy and per-venue billing, single sign-on, native mobile applications, fine-grained role-based access control, and computer-vision-based image-to-attribute extraction (the GenAI service covers the language side of that problem; we do not attempt the vision side).
