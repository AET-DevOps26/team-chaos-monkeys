# Project Work — DevOps: Engineering for Deployment and Operations (CIT423001)

Course: DevOps: Engineering for Deployment and Operations (CIT423001)
Lecture: Project Work (No fixed date)

---

## Table of Contents
1. [Project Details](#1-project-details)
2. [Project Grading](#2-project-grading)
3. [Problem Statement Template](#3-problem-statement-template)
4. [System Overview → Architecture](#4-system-overview--architecture)
5. [Best Practices Microservices](#5-best-practices-microservices)
6. [Best Practices Monitoring with Kubernetes](#6-best-practices-monitoring-with-kubernetes)

---

## 1. Project Details

### Objective

The project requires teams to design, implement, and operate a complete software system that reflects a realistic DevOps workflow. The goal is to demonstrate how a system is structured, integrated, deployed, and maintained in a reproducible and observable way. Development, deployment, and operation must therefore be treated as a single engineering problem rather than as separate phases.

At the technical level, the project must result in a web application that includes a client side, a server side, persistent storage, and a separate Generative AI component. The system must be containerised, runnable locally, automatically tested and deployed through GitHub Actions, deployable to Kubernetes, and observable through Prometheus and Grafana. The application domain is flexible, but the technical and process requirements are fixed and must all be satisfied.

The project is intended to simulate a realistic cloud-native software delivery scenario. The final result must be structured in a way that supports modular development, reproducible setup, automated deployment, and operational visibility.

**Deadline:** to be announced (EOD – 23:59 Munich time)

| Aspect | Requirement |
|---|---|
| Project type | Complete DevOps-oriented software system |
| Main focus | Development, deployment, operation, and observability as one integrated workflow |
| Required system elements | Client, server, database, GenAI, CI/CD, Kubernetes, monitoring |
| Application domain | Flexible, but all technical requirements must be fulfilled |

---

### Team Organisation

Teams consist of three students. Each student must take responsibility for one primary subsystem, typically client, server, or GenAI. However, subsystem ownership does not imply isolated work. Teams are expected to collaborate across subsystem boundaries, especially for integration, deployment, and debugging. A project where each student only works on *their part* without participating in system integration does not reflect the intended DevOps model.

Registration information is required in order to make contribution tracking possible and to connect repository activity to individual team members. Therefore, each student must provide their GitHub username, TUMonline login, and matriculation number.

Work must be transparent and traceable throughout the semester. This means that contributions must be visible through GitHub commits, pull request authorship, code review participation, and involvement in infrastructure tasks such as CI/CD configuration and deployment.

Communication must take place through the official course channels. Tutor feedback, planning, questions, and issue reporting must be visible in the dedicated Artemis team channels. No other communication channel will be taken into account for evaluation.

| Aspect | Requirement |
|---|---|
| Team size | 3 students |
| Registration | GitHub username, TUMonline login, matriculation number |
| Ownership | Each student owns a primary subsystem (client, server, GenAI) |
| Collaboration | Collaborative development across subsystem boundaries is expected |
| Contribution tracking | Visible via commits, PRs, code reviews, and infrastructure work |
| Communication | Only official course channels in Artemis |

---

### Development Workflow

The project must be developed in a GitHub mono-repository. The system must be treated as one integrated deliverable. A mono-repo makes it possible to version client, server, GenAI service, deployment files, CI/CD workflows, and documentation together, and to validate changes across the whole system.

All work must be structured through pull requests. Each feature or bugfix must be developed in a dedicated feature branch. Direct commits to the main branch are not acceptable as a normal workflow. A pull request must be opened, reviewed, and approved before the change is merged into main. Team members must peer-review each other's work. Review is part of the workflow and should be treated as a normal step before merging changes.

The CI pipeline must run automatically on every pull request. At a minimum, it must build the relevant services and execute the automated tests. On merge to main, the CD pipeline must automatically deploy the system to a Kubernetes environment. The intended workflow is: **develop in a feature branch → validate through CI → review through PR → merge into main → deploy automatically**.

| Aspect | Requirement |
|---|---|
| Repository | GitHub mono-repo |
| Branching | Each feature or bugfix developed in a feature branch |
| Pull Requests | Mandatory before merge into main |
| Code review | Peer review and approval by team members required |
| CI checks | Automated tests and validation on every PR |
| CD behaviour | Automatic deployment to Kubernetes on merge to main |

---

### System Architecture

The system must be structured as a set of interacting but separated components. At minimum: a client side, a server side, a database, and a separate GenAI component.

- The **client side** must provide a usable interface and communicate with the backend over REST.
- The **server side** must expose REST APIs, coordinate business logic, and interact with persistent storage.
- The **database** must support persistent data storage and must have a documented schema.
- The **GenAI component** must run as an independent service and communicate with the backend over a defined interface.

The server side must be implemented in **Spring Boot** and must consist of **at least three microservices**. These services do not need to be large, but they must have distinct responsibilities and communicate in a controlled and documented way.

The client side may be implemented in **React, Angular, or Vue.js**.

The database may be **MySQL, PostgreSQL, or a similar** relational/persistent database system, but it must run via Docker in local development.

| Component | Technology | Notes |
|---|---|---|
| Client Side | React, Angular, Vue.js | Usable, responsive UI that interacts with server over REST |
| Server Side | Spring Boot (Java) | Must expose REST APIs and consist of at least 3 microservices; modular architecture required |
| Database | MySQL or PostgreSQL or similar | Must support persistent storage; schema must be documented; run via Docker |
| GenAI Component | Python | Modular microservice, containerised and networked with the server |

#### GenAI Component Details

The GenAI component must be implemented as a separate service in **Python**. It must be deployed as a modular microservice, containerised independently, networked with the server, and integrated through a defined interface.

Functionally, the GenAI component must fulfil a **real user-facing use case**. Acceptable examples include summarisation, generation, question answering, or a similarly meaningful feature. It is not sufficient to include a GenAI service that exists technically but is not connected to an actual user-facing capability.

The system must support both **cloud-based and local large language models**. Cloud support may be implemented through providers such as the OpenAI API. Local model support may be implemented using technologies such as GPT4All or LLaMA.

As an optional advanced bonus, teams may implement a full **retrieval-augmented generation (RAG)** setup using a vector database such as Weaviate.

| Aspect | Requirement |
|---|---|
| Language | Python |
| Deployment | Modular microservice, containerised and networked with the server |
| Functionality | Real user-facing use case, e.g. summarisation, generation, Q&A |
| Model support | Cloud-based models (e.g. OpenAI API) and local models (e.g. GPT4All, LLaMA) |
| Optional bonus | Full RAG architecture using a vector database such as Weaviate |

---

### Environment and Deployment

All components must be fully containerised and runnable locally using a compose-based setup. Each component must have its own Dockerfile. The local setup must support end-to-end system execution through a `docker-compose.yml` file.

The system must be **runnable in three or fewer commands** (e.g. `docker compose up`). The setup must provide sane defaults — no long manual configuration or complex environment preparation.

The system must also be deployable to **Kubernetes**, either through Helm charts or raw Kubernetes manifests. The project must support deployment on the course infrastructure via **Rancher** and on a cloud environment (**Azure**).

Configuration must be externalised using environment variables, Secrets, and similar mechanisms. **Hardcoded credentials or environment-dependent values are not acceptable.**

| Aspect | Requirement |
|---|---|
| Containerisation | All components (server, client, GenAI, DB) must have their own Dockerfile |
| Local orchestration | `docker-compose.yml` must run the system end-to-end locally |
| Setup | Runnable in three or fewer commands; no complex manual ENV setup |
| Kubernetes | Deployable using Helm or raw manifests |
| Environments | Local infrastructure (Rancher) and a cloud option (Azure) |

---

### CI/CD

The system must include a working CI/CD pipeline implemented with **GitHub Actions**.

**Continuous Integration** must build and test all services and perform static analysis or linting where appropriate. The CI pipeline should validate the codebase before integration into the main branch.

**Continuous Deployment** must automatically deploy to Kubernetes after merge to main. The workflow must make correct use of secrets and environment-specific variables. Hardcoded tokens should be avoided.

| Aspect | Requirement |
|---|---|
| Tooling | GitHub Actions |
| CI tasks | Build and test all services; perform static analysis/linting |
| CD tasks | Automatically deploy to Kubernetes on merge to main |
| Configuration | Must use secrets and support environment-specific variables |

---

### Observability

The system must expose basic but meaningful operational visibility. Monitoring should not stop at "Prometheus is installed" or "Grafana is running." The monitored data must allow someone to understand whether the system is behaving correctly or incorrectly.

- **Prometheus** must be used for metrics collection. At minimum, track **request count, latency, and error rate**.
- **Grafana** must be used for visualisation. Dashboards must be submitted as exported `.json` files.
- At least **one meaningful alert rule** must be configured (e.g. service downtime or slow response time).

| Tool | Requirements |
|---|---|
| Prometheus | Metrics collection for at least request count, latency, and error rate |
| Grafana | Dashboards must reflect key system metrics (server, GenAI); must be submitted as `.json` |
| Alerts | At least one meaningful alert rule, e.g. service down or slow response time |

---

### Testing

Tests must cover critical server-side logic, relevant parts of the GenAI component, and important client-side workflows and interactions.

- **Unit tests** are mandatory for critical server and GenAI logic.
- **Client-side tests** should cover core workflows and interactions.
- All tests must run **automatically in the CI pipeline**.

| Aspect | Requirement |
|---|---|
| Unit Tests | Must cover critical server and GenAI logic |
| Client Tests | Should cover core workflows and interactions |
| CI Testing | All tests must run automatically in the CI pipeline |

---

### Engineering Artefacts

Teams must provide engineering artefacts that explain how the system is structured and how it works.

- A high-level architecture description with decomposition into subsystems and their interfaces.
- **UML-style diagrams** are mandatory: Subsystem Decomposition diagram, Use Case Diagram, and Analysis Object Model.
- API documentation through **OpenAPI/Swagger**, exposing Swagger UI or an equivalent interface.

| Aspect | Requirement |
|---|---|
| Architecture | High-level system description |
| Decomposition | Subsystems and interfaces |
| Architecture Diagrams | UML-style diagrams: Subsystem Decomposition, Use Case Diagram, Analysis Object Model are mandatory |
| API documentation | Must provide OpenAPI/Swagger documentation and expose Swagger UI or equivalent |

---

### Deliverables

| Deliverable | Description |
|---|---|
| Source Code | Complete codebase for server, client, and GenAI services |
| Docker Setup | Dockerfiles and `docker-compose.yml` for local setup |
| Kubernetes Deployment | Helm charts or raw Kubernetes YAMLs with setup instructions |
| Monitoring Configuration | Prometheus and Grafana config with exported dashboards and alert rules |
| Testing Suite | Unit/integration tests with instructions to run them |
| Documentation | `README.md` with setup guide, architecture, API docs, CI/CD and monitoring instructions, student responsibilities |

The project concludes with a **final presentation** and **individual oral examination**. The final team presentation must include a live demo. Each student must present and explain their subsystem and be ready to answer technical questions.

---

### Common Pitfalls and How to Avoid Them

#### Effective Project Practices

**Reliability > Feature count**
One of the very common reasons for a team to fail is feature orientation. If you cannot reliably deploy or run every new feature you create, you will eventually fail. One of the important DevOps principles is that fast, reliable flow is more important than feature count. Keep the scope small, make the system deployable early, and iterate on it.

**The system is a single pipeline**
Every part of the system is a different component, but they are all interconnected and dependent on each other. The best practice is to link every component into one chain:
`code → test → build → deploy → observe → improve`

**Reproducibility**
Try to ask yourself after each feature is implemented: "Can someone else run your system without you?" Typical failures include many manual steps and undocumented environments. Make setup trivial and test the setup from scratch at least a couple of times.

**Visible system behaviour**
Many teams "install monitoring," but dashboards show nothing useful. All dashboards must be linked to system behaviour. Visualisation of latency, failures, and load will help you understand the system state.

#### Patterns of Failure

**Project as a checklist**
If you only aim to pass the requirement, the quality of the system drops drastically. Requirements should serve only as a starting point. Try to give every requirement meaning and connect it to real system behaviour.

**Late integration**
Too many teams build components separately and integrate them at the very end. Every time the result is the same: CI/CD breaks, deployment becomes unstable, and eventually the system is incomplete. Start integration as early as possible.

**Fake CI/CD**
It is easy to have CI/CD, but it is extremely hard to have good CI/CD. Very often the pipeline exists, but tests are meaningless or missing. Follow good CI/CD practices from the beginning: https://about.gitlab.com/blog/how-to-keep-up-with-ci-cd-best-practices/

**GenAI as decoration**
Use this opportunity to learn a new technology and think about real challenges modern teams are solving. Integrate GenAI meaningfully, not just as a checkbox.

**"I will document it later"**
Always start a new class or method with a short comment describing its purpose. Try to document as you go and make it a habit. https://www.aleksandrhovhannisyan.com/blog/writing-better-documentation/

#### Team Culture

**Other people cannot read your thoughts**
If something is bothering you, bring it up for discussion as soon as possible. The earlier you do it, the easier it is to resolve.

**Clear roles and responsibilities**
Right after forming a team, one of the first things to discuss is individual responsibilities. Define roles similar to those in real projects and define responsibilities and accountability for various tasks. We recommend using a RACI matrix: https://www.atlassian.com/work-management/project-management/raci-chart

**Individual strength**
Everyone in a team has different strengths and weaknesses. Good teams are technically strong, but great teams consist of people who complement each other.

---

## 2. Project Grading

Project grading consists of three separate grades: an aggregated team grade, a team final grade, and an individual oral examination. These are weighted as follows: **40% aggregated team grade**, **30% team final presentation**, and **30% individual oral examination**.

The team final presentation and individual oral examination are both conducted at the end of the semester. The aggregated team grade is based on three evaluations carried out during the semester, approximately one per month.

### Grade Structure (Diagram Description)

**FINAL GRADE = PROJECT (50%) + EXAM (50%)**

The PROJECT component breaks down as:
- PROJECT = individual grade + team grade
  - **INDIVIDUAL:** Oral examination — 30%
  - **TEAM:** Final presentation — 30% | Aggregated grade — 40%

**Timeline (April → August):**
- **Individual oral examination:** Single sole grade given at end of semester (August)
- **Team aggregated grade:** 3 checkpoint grades — 1st grade (May), 2nd grade (June), 3rd grade (July)
- **Team final presentation:** Single sole grade given at end of semester (August)

---

### Individual Oral Examination (30%)

The individual oral examination is the sole individual grading mechanism for assessing each student's personal contribution and understanding. It is conducted at the end of the semester by a pair of tutors.

Each student presents **one artefact of their choice** that they personally developed within the project. The artefact should correspond to the student's declared responsibility area (e.g. a CI/CD pipeline, monitoring setup, deployment configuration, Docker-based environment, testing setup, service implementation, logging or alerting mechanism, or an infrastructure-as-code component).

Each student is given a **15-minute slot** assessed using prepared questions.

**During the examination, you are expected to:**
- Explain your subsystem and its role in the overall system
- Describe technical decisions you made
- Demonstrate understanding of how your component integrates with others

**Your contribution will be evaluated based on:**
- Code quality (clarity, structure, maintainability)
- Contribution (balance between difficulty and quantity of work)
- Collaboration (participation in reviews, integration, and team work)

---

### Team Aggregated Grade (40%)

The team aggregated grade reflects how the team works over the course of the semester. It is based on **three checkpoint evaluations, one per month**, carried out by the tutor.

> **You will receive your intermediate grade from your tutor after each evaluation. That will allow you to understand your strengths and weaknesses, and to work on it.**

**The following parameters are evaluated during each iteration:**
- Planning and task distribution
- Progress since last evaluation
- Technical integration
- Collaboration and communication
- Responsiveness to feedback
- Risks and problems occurring during the project execution and how they were handled

**Deployment must be stable for evaluation.** This requires:
- A deployed instance available via URL (on course infrastructure or cloud)
- A working system that reflects the final submission
- Clear instructions on how to access and use the system
- No reliance on local-only setups for evaluation

---

### Team Final Presentation (30%)

The team final presentation and demonstration is the main end-of-semester assessment of the project as a whole. Evaluated jointly by tutors and instructors.

The team is expected to:
- Present the overall goal and scope of the project clearly
- Explain the architecture and DevOps pipeline
- Justify the main engineering decisions made
- Show trade-offs involved in their solution
- Demonstrate the system live, showing it actually works end-to-end

---

### Project Grading Criteria

The project is graded as **failed** if:
- Contributions are not transparently documented (Artemis + Confluence)
- Team members cannot clearly explain their own subsystem during the presentation
- No working end-to-end system is demonstrated

#### System

| Category | Evaluation | Explanation |
|---|---|---|
| Functional System | Excellent | Full end-to-end system works reliably across all components; no major failures |
| | Good | Core functionality works; minor issues in integration or edge cases |
| | Basic | Partial functionality; several components not fully integrated |
| | Poor | System does not function as a coherent whole |
| Architecture Quality | Excellent | Clear modular structure; components/services well-separated with defined interfaces |
| | Good | Mostly modular; interfaces exist but inconsistencies present |
| | Basic | Limited modularity; significant coupling between components |
| | Poor | No recognisable modular structure |
| User-Facing Value | Excellent | System provides clear user workflows; functionality solves a meaningful problem; UI supports usage well |
| | Good | Functionality usable but limited or partially unclear |
| | Basic | Minimal functionality; usability issues present |
| | Poor | No meaningful user-facing functionality |

#### DevOps & Infrastructure

| Category | Evaluation | Explanation |
|---|---|---|
| Build and Deployment | Excellent | Fully automated CI/CD: build, test, image creation, and deployment work reliably |
| | Good | CI automated (build + test); deployment partially automated or unstable |
| | Basic | Partial automation (only build or test); deployment manual |
| | Poor | No functional CI/CD pipeline |
| Runtime and Observability | Excellent | Metrics reflect system behaviour (e.g. latency, errors, load); dashboards clearly visualise system state; alerts are meaningful |
| | Good | Metrics and dashboards exist but limited coverage or unclear interpretation |
| | Basic | Basic monitoring setup present but not useful for understanding system behaviour |
| | Poor | No meaningful observability setup |
| Environment and Reproducibility | Excellent | System fully containerised; local setup reproducible with minimal steps and no manual fixes |
| | Good | Mostly reproducible; minor setup issues or manual steps required |
| | Basic | Setup works but requires significant manual intervention |
| | Poor | System cannot be reliably set up locally |
| Testing Strategy | Excellent | Tests cover critical flows, edge cases, and failures; integrated into CI |
| | Good | Tests cover main functionality; limited edge case coverage |
| | Basic | Few tests; limited relevance to system behaviour |
| | Poor | No meaningful testing |
| Engineering Artefacts | Excellent | Architecture and system design clearly documented and consistent with implementation |
| | Good | Documentation exists but incomplete or partially inconsistent |
| | Basic | Minimal documentation; unclear structure |
| | Poor | No useful engineering artefacts |
| Documentation | Excellent | Documentation enables full setup, usage, and understanding; responsibilities clearly traceable |
| | Good | Documentation usable but incomplete |
| | Basic | Limited documentation; unclear instructions |
| | Poor | No usable documentation |

#### Bonus

For exceptional additional technical characteristics of the project, additional credit may be given by the evaluator. These are awarded for technical extensions beyond baseline requirements.

| Level | Description |
|---|---|
| Advanced DevOps | e.g. autoscaling, self-healing, advanced deployment strategies |
| Advanced Observability | e.g. tracing, log aggregation, custom metrics |
| Advanced AI | e.g. RAG pipeline, vector database integration |
| System Excellence | clearly above baseline in design or implementation |
| Additional justified improvements | technically meaningful extensions |

> Additional consideration may be given in cases where the team is not full (e.g. two or one member). Since the grading structure is designed for full teams, adjustments may be made to reflect the workload in such cases.

---

## 3. Problem Statement Template

> **Deadline for submission: 08.05.2026**
>
> Please complete this document carefully. It will help you to structure your ideas early and plan your development efficiently.

### 1. Problem Statement

Describe clearly what problem your application will solve or what user need it addresses.

**Include:**
- What is the main functionality?
- Who are the intended users?
- How will you integrate GenAI meaningfully?
- Describe some scenarios how your app will function?

### Important Notes
- This document must be stored in your team's GitHub repository.

---

## 4. System Overview → Architecture

> **Deadline for submission: 08.05.2026**
>
> Please complete this document carefully. It will help you to structure your ideas early and plan your development efficiently.

### 1. Initial System Structure

Describe how you plan to divide the system technically. You must cover:

- **Server:** Spring Boot REST API
- **Client:** React / Angular / Vue.js frontend
- **GenAI Service:** Python, LangChain microservice
- **Database:** (e.g., PostgreSQL, MongoDB)

Include three UML diagrams (Analysis object model, Use cases, and Top-level architecture):
- A simple analysis object model in the form of a UML class diagram
- A use case diagram
- A UML component diagram to visualize the architecture (this can be understood as the "top-level architecture diagram")

You can use tools like Apollon (https://apollon.ase.in.tum.de/).

### 2. First Product Backlog

Prepare a simple backlog in a Markdown table or GitHub Project. Each item should be a feature or task.

### Important Notes
- This document must be stored in your team's GitHub repository.

---

## 5. Best Practices Microservices

### Concrete Tips and Hints

#### 1. Microservice Architecture & Design Best Practices

- **Single Responsibility Services:** Each microservice should encapsulate one specific domain or business capability. Avoid feature creep and overlapping responsibilities.
- **Stateless Services:** Design services to be stateless. Store session/context in tokens (e.g., JWT) or shared databases/cache layers like Redis if necessary.
- **Language Boundary Awareness:** Carefully define the interface (OpenAPI spec) between Spring Boot (Java) and LangChain (Python). Use JSON over HTTP, not internal data structures.

---

#### 2. API-Driven Design and OpenAPI Usage

**Design First**
Teams should define and review the OpenAPI specs collaboratively before implementing any logic. Use tools like Swagger Editor or Stoplight.

**Use OpenAPI Code Generators**
- Java (Spring Boot): `springdoc-openapi` or OpenAPI Generator
- Python: `openapi-python-client`
- Versioning: Always version APIs (e.g., `/api/v1/resource`) from day one to prevent breaking clients during iteration.

**API-First, Contract-Strong**

| Task | Concrete Tool / Command |
|---|---|
| Edit & lint spec | `npx @redocly/cli lint api/openapi.yaml` (Spectral rules) |
| Generate Java stubs | `openapi-generator-cli generate -i api/openapi.yaml -g spring -o services/spring-order/generated` |
| Generate Python client | `openapi-python-client --path api/openapi.yaml --output services/py-recommender/client` |
| Generate TypeScript SDK | `npx openapi-typescript api/openapi.yaml -o web-client/src/api.ts` |
| Mock server for client | `npx prism mock api/openapi.yaml` (runs on port 4010) |

Never merge without running the linter; add it to a **pre-commit hook**.

```yaml
# .pre-commit-config.yaml
repos:
  - repo: https://github.com/Redocly/openapi-cli
    rev: v1.0.0-beta.92
    hooks:
      - id: openapi-cli-lint
```

Run with: `pre-commit run -a`

**Mono-repo Layout**
```
repo/
├── api/
│   ├── openapi.yaml
│   └── scripts/
│       └── gen-all.sh
├── services/
│   ├── spring-order/
│   ├── py-recommender/
│   └── web-client/
```

**Minimal gen-all.sh helper (drop in /api/scripts):**

```bash
#!/usr/bin/env bash
set -euo pipefail

openapi-generator-cli generate -i api/openapi.yaml -g spring \
  -o services/spring-order/generated --skip-validate-spec

openapi-python-client --path api/openapi.yaml \
  --output services/py-recommender/client --config api/scripts/py-config.json

npx openapi-typescript api/openapi.yaml -o web-client/src/api.ts
```

Make it executable and callable from CI; run `./api/scripts/gen-all.sh` after each spec change.

> Tip: Add a Git hook (post-checkout, post-merge) to run gen-all.sh automatically to keep generated clients up-to-date.

---

#### 3. Security Best Practices

- Use OAuth2 / OIDC via API gateway (e.g., Keycloak, GitHub, Auth0).
- Pass JWTs through HTTP headers.
- Each service must verify the token using a shared public key.
- Gateways (like Traefik or NGINX) can centralize token validation.

> Tip: Place the gateway as the entrypoint to intercept and validate tokens before forwarding to services.

---

#### 4. Development and Deployment Practices

- **Contract Testing:** Use Pact or similar tools to ensure API contract fidelity between producer (Spring Boot) and consumer (LangChain).
- **Service Discovery:** Use an API gateway (e.g., Traefik, NGINX) to route and secure APIs. Avoid direct service-to-service calls across languages unless encapsulated.
- **Consistent Error Handling:** Use a unified error schema in OpenAPI: `{code, message, details}`. Enforce it across all services.
- **CI/CD Pipeline:** Automate OpenAPI linting, stub generation, and testing in GitHub Actions. Always validate that OpenAPI specs match implementation on each build.
- **Docker Image Publishing:** Push container images to a registry with semantic version tags (e.g., `ghcr.io/org/service:1.0.0`) and/or Git commit SHA to ensure traceability.

**GitHub Actions Pipeline Example:**

```yaml
# .github/workflows/ci.yml
name: CI

on:
  push:
    branches:
      - main
    paths-ignore:
      - 'README.md'
  pull_request:

concurrency: ci-${{ github.ref }}

jobs:
  generate-and-test:
    runs-on: ubuntu-latest
    strategy:
      matrix:
        service: [spring-order, py-recommender, web-client]

    steps:
      - uses: actions/checkout@v4

      - name: Lint OpenAPI
        run: npx @redocly/cli lint api/openapi.yaml

      - name: Generate code
        run: ./api/scripts/gen-all.sh
        shell: bash

      - name: Cache Maven / npm / pip
        uses: actions/cache@v4
        with:
          path: |
            ~/.m2/repository
            ~/.cache/pip
            ~/.npm
          key: ${{ runner.os }}-${{ hashFiles('**/lockfiles') }}

      - name: Build & test
        working-directory: services/${{ matrix.service }}
        run: |
          case ${{ matrix.service }} in
            spring-order) ./mvnw verify ;;
            py-recommender) pip install -r req.txt && pytest ;;
            web-client) npm ci && npm test ;;
          esac

  contract-test:
    needs: generate-and-test
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: Pact verification
        run: ./scripts/run-pact.sh
```

**Local Development and Runtime:**

| Area | Hands-on Choice | Why |
|---|---|---|
| Service discovery | Simple API Gateway | Leads to one ingress, easier CORS, security |
| Data isolation | One PostgreSQL database per service (logical schema ok) | Classic microservice rule — avoids coupling |
| Dev containers | devcontainer.json + VS Code, Docker-in-Docker enabled | Requires zero local setup |
| Local orchestration | docker-compose.yml in /infra | Offers DBs + gateway + both services in one command |
| Cross-service call | Python to Java via generated client (py-recommender/client) | Consistent via OpenAPI, avoids hidden internal formats |
| Observability | Spring Boot Actuator + Prometheus; LangChain tracing to OpenTelemetry | Production-grade insights with minimal code |

---

#### 5. Client Integration

- Import generated `api.ts` in React or similar.
- Runtime base URL comes from `.env` file: `VITE_API_URL=http://localhost:8080`
- For CORS, configure gateway:

```yaml
spring:
  cloud:
    gateway:
      globalcors:
        corsConfigurations:
          '[/**]':
            allowedOrigins: "*"
            allowedMethods: "*"
```

- Use **SWR** or **React Query** for caching and retries.

---

#### 6. Collaboration Best Practices

| Ritual | Concrete Action |
|---|---|
| API review | Weekly 15-min sync to review openapi.yaml changes |
| Definition of Done | PR must include: Passing CI, Updated spec, Short doc (/docs/adr-xyz.md) |
| Doc automation | Generate and publish OpenAPI docs with redoc-cli, deploy with GH Pages |
| Issue template | Add checkboxes: "Affects API?", "Spec updated?"; Create a PR template |

---

#### 7. Write Tests

- Consider implementing **integration** and **end-to-end (E2E)** tests per microservice.
- These tests should include interactions with the database and any external APIs to ensure that services behave correctly as the system evolves.
- This helps maintain stability and avoid regressions when introducing new features.

---

#### 8. What Not To Do

- No direct HTTP calls without generated client
- No shared DTOs/utilities outside the OpenAPI spec
- No long-running branches (max 2 days — rebase/merge)
- No manual production deploys — everything must go through CI/CD

---

## 6. Best Practices Monitoring with Kubernetes

### Monitoring Best Practices in Kubernetes

#### 1. Separate Namespace

Deploy Prometheus and Grafana in a dedicated namespace (e.g., `monitoring`) to isolate observability tools from application microservices.

Benefits:
- Easier user permission management
- Resource limits (CPU/memory) via Kubernetes quotas
- Clear separation for upgrades, troubleshooting, and maintenance

#### 2. Persistent Volumes

Ensure that Prometheus and Grafana retain important data across restarts:
- Use Persistent Volumes (PVs) for Prometheus to keep historical metrics
- Use PVs for Grafana to persist dashboards and configuration

#### 3. Label-Based Discovery

Label your services and pods consistently so Prometheus can discover them automatically.

**Recommended labels:**
```yaml
app: my-service
monitoring: "true"
```

**PrometheusSelector Example:**
```yaml
matchLabels:
  monitoring: "true"
```

#### 4. Use ServiceMonitor / PodMonitor

Use `ServiceMonitor` or `PodMonitor` CRDs (when using Prometheus Operator) to define scrape targets declaratively. This is preferred over static scrape configs.

#### 5. Configuration as Code

Avoid manual configuration in environments:
- Store Grafana dashboards as version-controlled files (JSON)
- Use Helm or config maps to provision dashboards and data sources
- This ensures consistency and easier collaboration

#### 6. Optional: Access Control

Secure your monitoring tools:
- Protect Prometheus and Grafana with authentication (e.g., via Rancher)
- Use TLS for encrypted connections
- Apply role-based access control (RBAC) to manage user permissions

#### 7. Version Visibility

Expose application version information as a custom Prometheus metric:
- Helps track which version is currently deployed
- Makes it easier to correlate performance changes or issues with specific releases
- Visualize versions in Grafana dashboards

#### 8. Alerting Rules

Set up meaningful and actionable alerts. Define rules such as:
- High error rate
- Pod restart count > 5
- Slow response time

Use `PrometheusRule` CRDs if working with Prometheus Operator.

Connect to **Alertmanager** for routing alerts to:
- Email
- Slack
- PagerDuty
