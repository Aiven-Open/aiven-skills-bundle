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

## Prerequisites

The **Aiven Kafka Setup (avn CLI)** skill requires [aiven-client (`avn`)](https://github.com/aiven/aiven-client). See the repository for installation instructions.

---

## Installation

---

### Option 1: CLI Install (Recommended)

Use [npx skills](https://github.com/agentskillsio/skills) to install skills directly:

```bash
# Install all skills
npx skills add Aiven-Open/aiven-skills-bundle

# Install a specific skill
npx skills add Aiven-Open/aiven-skills-bundle --skill aiven-kafka-setup-avn

# List available skills
npx skills add Aiven-Open/aiven-skills-bundle --list
```

This automatically installs to your `.agents/skills/` directory (and symlinks into
`.claude/skills/` for Claude Code compatibility).

---

### Option 2: Clone and Copy

Clone the repository and copy the `skills/` folder into your project. Works with any agent tool, no extra tooling required:

```bash
git clone https://github.com/Aiven-Open/aiven-skills-bundle.git
cp -r aiven-skills-bundle/skills/* .agents/skills/
```

Your agent will discover skills automatically from `.agents/skills/`.

---

### Option 3: Git Submodule

Add this repository as a submodule so you can pull updates with a single command:

```bash
git submodule add https://github.com/Aiven-Open/aiven-skills-bundle.git .agents/aiven-skills-bundle
```

Reference skills from `.agents/aiven-skills-bundle/skills/`.

To update to the latest version later:

```bash
git submodule update --remote .agents/aiven-skills-bundle
```

---

### Option 4: Claude Code Plugin

Install via Claude Code's built-in plugin system (requires Claude Code). This repository
acts as both the marketplace and the plugin, so add it once and install from it:

```bash
# 1. Register this repository as a marketplace
/plugin marketplace add Aiven-Open/aiven-skills-bundle

# 2. Install the plugin (user scope — available across all projects)
claude plugin install aiven-skills-bundle@aiven-skills-bundle --scope user

# Or install to project scope (shared with your team via version control)
claude plugin install aiven-skills-bundle@aiven-skills-bundle --scope project
```

The plugin manifest is at [`.claude-plugin/plugin.json`](.claude-plugin/plugin.json) and
the marketplace catalog is at [`.claude-plugin/marketplace.json`](.claude-plugin/marketplace.json).

---

## Contributing

Want to add a new skill or improve an existing one? See [CONTRIBUTING.md](CONTRIBUTING.md)
for the skill structure, `SKILL.md` format, testing requirements, and PR guidelines.
