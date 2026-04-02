# Contributing to aiven-skills-bundle

Thank you for your interest in contributing! This repository is a curated collection of
**AI agent skills** for Aiven — structured instructions and supporting code that let AI
assistants (Cursor, Claude CLI, etc.) manage Aiven-managed data services end-to-end.

If you have an idea for a new skill or an improvement to an existing one, please read
this guide before opening a pull request.

---

## Table of Contents

1. [Repository Layout](#repository-layout)
2. [Skill Structure](#skill-structure)
3. [SKILL.md Requirements](#skillmd-requirements)
4. [Quality Guidelines](#quality-guidelines)
5. [Testing & Verification](#testing--verification)
6. [Opening a Pull Request](#opening-a-pull-request)
7. [Commit Messages](#commit-messages)
8. [Security](#security)

---

## Repository Layout

```
aiven-skills-bundle/
├── skills/
│   └── <skill-name>/          # one directory per skill
│       ├── SKILL.md           # required — agent instructions
│       ├── reference.md       # optional — CLI cheat sheet / troubleshooting
│       ├── scripts/           # optional — shell scripts invoked by the skill
│       └── templates/         # optional — code templates copied to the workspace
├── README.md
├── CONTRIBUTING.md
└── SECURITY.md
```

Each skill lives in `skills/<skill-name>/`. The name must be lowercase, hyphen-separated,
and describe both the Aiven service and the tooling used (e.g. `aiven-kafka-setup-avn`).

---

## Skill Structure

A minimal skill contains only `SKILL.md`. A full skill looks like:

```
skills/aiven-kafka-setup-avn/
├── SKILL.md
├── SERVICE_CREATION_AVN.md    # interactive region/plan selection detail
├── reference.md               # avn CLI cheat sheet
├── scripts/
│   ├── setup_aiven_kafka.sh
│   ├── run_producer.sh
│   ├── run_consumer.sh
│   ├── generate_orders.py
│   ├── verify_output.py
│   └── teardown.sh
└── templates/
    ├── order.avsc
    ├── producer_consumer_java/
    │   ├── Producer.java
    │   ├── Consumer.java
    │   ├── Order.java
    │   └── pom.xml
    └── producer_consumer_python/
        ├── producer.py
        ├── consumer.py
        ├── order.py
        └── requirements.txt
```

### Scripts

- Must be `bash` or `python3` (using only the standard library — no additional dependencies).
- Must be idempotent: running them twice must not break anything.
- Must not print credentials or secrets to stdout/stderr.
- Passwords must only ever be stored in environment variables and verified by length
  (`${#VAR}`), never echoed.

### Templates

- Code templates are copied into the user's workspace at runtime; they must compile
  and run without modification once environment variables are sourced.
- Java templates require Java 17+ and Maven. Python templates require Python 3.9+.
- All production-quality conventions apply: structured logging (no bare `print`),
  specific exception handling, type hints (Python), linting (Checkstyle / PEP 8).

---

## SKILL.md Requirements

`SKILL.md` is the file the AI agent reads. It must start with a YAML front-matter block:

```yaml
---
name: aiven-<service>-setup-<tool>
description: >
  One or two sentences describing WHAT the skill does and WHEN to trigger it.
  Include natural-language trigger phrases (e.g. "create a Kafka cluster",
  "set up Kafka", "start a Kafka service").
version: "0.1"
license: Apache-2.0
allowed-tools: Bash(avn:*) Bash(jq:*) Read
---
```

| Field | Requirement |
|---|---|
| `name` | Lowercase, hyphens only, max 64 chars, unique in this repo |
| `description` | Max 1 024 chars; include natural-language trigger phrases |
| `version` | Semantic version string, quoted (`"0.1"`, `"1.0"`) |
| `license` | Must be `Apache-2.0` |
| `allowed-tools` | Explicit allowlist of Bash commands the agent may run |

### Required Sections

Every `SKILL.md` must contain these sections (in order):

1. **Prerequisites** — what the agent must verify before doing anything (CLI login,
   tool versions, language runtimes). Include a clear stop-and-wait instruction if a
   prerequisite is not met.
2. **User input** — before running any scripts, it is good practice to ask the user
   clarifying questions (e.g. cloud region, service plan, language preference). Use the
   `AskQuestion` tool so the agent collects all required information upfront rather than
   interrupting the workflow mid-execution. Document the exact questions and their
   default values here.
3. **Step-by-step instructions** — numbered, each with a concrete shell command or
   agent action and a verification step.
4. **Teardown** — how to clean up. The agent must **not** run teardown automatically;
   it should inform the user how to do it when they are ready.
5. **File Reference** — a table mapping every supporting file to its purpose.

### Agent Behaviour Constraints

Document these explicitly in `SKILL.md` when relevant:

- **Fail fast on missing runtimes.** If `java` or `python3` is absent and required,
  stop immediately. Do not advise the user how to install programming languages.
- **Advise on missing CLI tools.** If `avn`, `jq`, or similar tools are absent, suggest
  the install command (e.g. `python3 -m pip install aiven-client`), but first check
  that the required installer (`python3`) is available.
- **Never read secrets.** Do not `cat` or `echo` files that contain passwords (e.g.
  `env.sh`). Verify credentials by length only.
- **Always run end-to-end.** A skill that creates a service but never runs a working
  example is incomplete. Every skill must produce a verifiable, runnable result.

---

## Quality Guidelines

| Area | Requirement |
|---|---|
| **Completeness** | The agent must reach a runnable end state without human intervention (beyond answering prompted questions). |
| **Verifiability** | Every significant step must include a check command so the agent can confirm success before proceeding. |
| **Idempotency** | Scripts must tolerate being run more than once (e.g. `avn service create` should check existence first). |
| **No hardcoded secrets** | Credentials, passwords, and tokens must come from environment variables, never from files committed to the repo. |
| **Language quality** | Code templates must meet the same bar as production code: structured logging, specific exception handling, linting passes. |
| **Minimal scope** | One skill, one workflow. If you need to cover multiple tools (e.g. `avn` vs Terraform vs REST API), create separate skills. |

---

## Testing & Verification

Because the expected output of a skill is a **running Aiven service with a working
example**, testing is done through an agentic IDE or CLI tool, not a unit test suite.

### Option A — Cursor (Agentic IDE)

1. Open this repository in Cursor.
2. Open a new chat in **Agent** mode.
3. Type a natural-language trigger phrase, for example:

   ```
   Create me a simple Kafka cluster
   ```

4. Observe the agent read `SKILL.md`, ask any required questions, and execute all steps
   autonomously.
5. Verify the final output by checking that `orders_completed.csv` contains the expected
   rows (or whatever the skill's verification step specifies).

### Option B — Claude CLI

```bash
claude "create me a simple Kafka"
```

The Claude CLI will discover `SKILL.md` in the repository context and follow the
instructions end-to-end.

### What "passing" looks like

A skill is considered verified when:

- [ ] All prerequisite checks pass (or the agent stops correctly on failure).
- [ ] The cloud service reaches `RUNNING` state.
- [ ] The producer runs and exits cleanly.
- [ ] The consumer runs and exits cleanly.
- [ ] The verification script (`verify_output.py` or equivalent) reports success.
- [ ] The agent prints teardown instructions without running them automatically.

If **any** of the above steps fail, the skill is not ready to merge.

---

## Opening a Pull Request

1. **Fork** the repository and create a branch:

   ```bash
   git checkout -b feat/aiven-pg-setup-avn
   ```

2. **Add your skill** under `skills/<skill-name>/`.

3. **Test end-to-end** using Cursor or the Claude CLI (see [Testing](#testing--verification)).
   Include a brief description of your test run in the PR body (which cloud provider,
   which plan, which language template).

4. **Open a pull request** with:
   - A clear title following [Conventional Commits](#commit-messages) style.
   - The PR body describing:
     - What the skill does and when it triggers.
     - How you tested it (agentic IDE / CLI, region, plan, language).
     - Any known limitations or follow-up work.

5. Stay responsive to review feedback. We may ask for additional verification steps or
   code changes before merging.

---

## Commit Messages

This project follows the [Conventional Commits](https://www.conventionalcommits.org/en/v1.0.0/)
specification. Please use one of these types:

| Type | When to use |
|---|---|
| `feat` | New skill or new capability in an existing skill |
| `fix` | Bug fix in a script, template, or instruction |
| `docs` | Changes to `SKILL.md`, `reference.md`, `README.md`, or `CONTRIBUTING.md` |
| `refactor` | Code restructuring with no behaviour change |
| `test` | New or updated verification scripts |
| `chore` | Dependency updates, CI, tooling |

**Examples:**

```
feat(aiven-pg-setup-avn): add PostgreSQL skill with psql template
fix(aiven-kafka-setup-avn): handle avn schema create missing in older CLI versions
docs(aiven-kafka-setup-avn): clarify TRUSTSTORE_PASSWORD in SERVICE_CREATION_AVN.md
```

For guidance on writing good commit messages, see
[Chris Beams' post](https://cbea.ms/git-commit/).

---

## Security

**Do NOT include in any commit:**

- API keys, passwords, or tokens.
- Hardcoded Aiven project names or account IDs.
- Output of `env.sh` or any file containing credentials.
- Any `avn service user-list` output or similar.

All credential handling must go through environment variables. Verify values by length
(`${#VAR} -gt 0`), never by printing them.

To report a security vulnerability, follow the process described in
[SECURITY.md](SECURITY.md).
