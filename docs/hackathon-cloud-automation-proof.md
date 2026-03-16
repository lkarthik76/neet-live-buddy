# Hackathon Proof: Automated Cloud Deployment

This document is provided as explicit proof that cloud deployment is automated via scripts in this repository.

## Requirement Mapping

Hackathon requirement: _"Prove you automated your Cloud Deployment using scripts or infrastructure-as-code tools. This code must be included in your public repository."_

This repository satisfies that requirement with executable deployment scripts under `infra/`.

## Automation Scripts Included

- `infra/setup-gcp.sh`
  - Enables required Google Cloud APIs for the project.
- `infra/deploy-go-tutor.sh`
  - Builds and deploys Go tutor microservice to Cloud Run.
- `infra/deploy-cloud-run.sh`
  - Builds and deploys frontend service to Cloud Run.
- `infra/deploy-full-stack.sh`
  - Deploys both services and auto-wires frontend to Go tutor service URL.
- `infra/cloudbuild-go-tutor.yaml`
  - Cloud Build config used for Go service image build.

## One-Command Full Deployment

After setting required environment variables, run:

```bash
./infra/deploy-full-stack.sh
```

This performs:

1. Build + deploy Go tutor backend
2. Resolve backend URL from Cloud Run
3. Build + deploy frontend with backend URL injected

## Reproducibility

All commands and environment setup are documented in:

- `docs/deploy-gcp-end-to-end.md`
- `docs/setup-and-run.md`
- `docs/ncert-rag-setup.md`

## Suggested Evidence for Devpost Submission

Include one of the following in your submission video or additional clip:

1. Terminal recording running `./infra/deploy-full-stack.sh` and showing successful deployment output.
2. Google Cloud Run console showing the two services updated after script run.
3. Repository links to the scripts above in the "cloud deployment proof" section.
