# aiven-skills-bundle

A curated collection of AI agent skills for Aiven. This repository enables developers
to manage, monitor, and scale Aiven-managed data services directly through AI code
editors like Cursor and Claude CLI.

## What is a Skill?

A **skill** is a self-contained package of structured instructions and supporting code
that an AI agent reads and executes step-by-step. Each skill includes:

- A `SKILL.md` instruction file — the agent's source of truth for the entire workflow.
- Optional supporting assets: shell scripts, code templates, Avro schemas, reference docs.

> **Key principle:** A skill is not documentation. The agent follows it literally and
> produces a working, verifiable result — without manual intervention beyond answering
> a few upfront questions.

## How to Use a Skill

Skills work with any agentic AI tool that can read files from this repository.

### Cursor (Agentic IDE)

1. Open this repository in Cursor.
2. Start a new chat in **Agent** mode.
3. Type a natural-language trigger, for example:

   ```
   Create me a simple Kafka cluster
   ```

4. The agent reads the matching `SKILL.md`, asks any required clarifying questions
   (region, plan, language), and drives the entire workflow — service creation,
   configuration, and a running producer/consumer example — autonomously.

### Claude CLI

```bash
claude "create me a simple Kafka"
```

The CLI discovers `SKILL.md` in the repository context and follows the instructions
end-to-end, prompting you only when a decision is needed.

---

## Available Skills

| Skill Name | Trigger phrase | Description |
|------------|----------------|-------------|
| Aiven Kafka Setup (avn CLI) | `aiven-kafka-setup-avn` | Create and configure an Apache Kafka cluster using the `avn` CLI tool, including SASL_SSL auth, Schema Registry, and a working producer-consumer example. |

---

## Contributing

Want to add a new skill or improve an existing one? See [CONTRIBUTING.md](CONTRIBUTING.md)
for the skill structure, `SKILL.md` format, testing requirements, and PR guidelines.
