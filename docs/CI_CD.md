# CollabMind CI/CD

The repository uses separate workflows so a failure has a clear owner and privileged release permissions are not granted to ordinary test jobs.

## Pull-request gates

| Workflow | What it proves |
|---|---|
| `CI` | TypeScript lint/format, dependency audit, coverage thresholds, production web build, every Maven service, Redis/Kafka Testcontainers, Playwright, Kubernetes render/security checks, full-stack REST/WebSocket smoke, and a short k6 threshold run. |
| `Security` | CodeQL for Java and TypeScript, Git history secret scanning, high/critical dependency scanning, dependency-change review, and an SPDX JSON software bill of materials. |
| `Container Images` | Every deployable image builds and has no known fixed high/critical vulnerability before merge. Pull requests never publish images. |

Dependabot opens weekly grouped updates for npm, every Maven service, Docker, and GitHub Actions.

## Main-branch delivery

After merge, `Container Images` rebuilds and scans all six images and publishes them to GHCR with the immutable Git commit SHA. No `latest` tag is used, so a deployment is reproducible and auditable.

The `Deploy` workflow starts after a successful main-branch image workflow. Staging remains disabled until repository variable `ENABLE_STAGING_DEPLOY=true` is configured. It requires a protected GitHub `staging` environment containing:

- Secret `KUBE_CONFIG`: base64-encoded kubeconfig
- Variable `STAGING_URL`: public application origin

Production runs only by manual dispatch with a specific image SHA and `ENABLE_PRODUCTION_DEPLOY=true`. Configure the GitHub `production` environment with required reviewers so approval is enforced outside the YAML. Both environments wait for all six rollouts and issue `rollout undo` on a failed release.

## Required branch protection

In GitHub repository settings, protect `main` and require these checks before merge:

- Frontend quality, tests, and build
- All five backend matrix jobs
- Redis and Kafka integration
- Playwright browser journeys
- Kubernetes schema and security checks
- Full-stack container smoke test
- Both CodeQL jobs
- Secret and dependency scan
- Dependency change review
- All six container build-and-scan jobs

Also require pull-request approval, dismiss stale approvals, block force pushes, and require branches to be current before merging. Workflow files cannot turn on repository branch protection themselves.

## Why some deployment jobs are disabled initially

CI is safe to run immediately. Deployment is deliberately inert until a real cluster, registry access, environment URL, secrets, and GitHub approval rules exist. This prevents a sample value such as `chat.example.com` from being treated as a production target.

## Evidence and retention

- JUnit reports: 14 days
- Frontend coverage: 14 days
- Playwright HTML report, screenshots, videos, traces: 14 days
- Full-stack logs on failure: 14 days
- SPDX software bill of materials: 30 days
- CodeQL and Trivy SARIF: GitHub Security tab

Increase retention according to compliance requirements before operating with customer data.
