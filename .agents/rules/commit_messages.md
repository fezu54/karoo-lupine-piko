---
description: Rules for commit message suggestions and guidelines.
---

# Commit Message Guidelines

- Always follow the Conventional Commits format (`type(scope): summary`).
- Never commit automatically: stage changes into git and provide suggested commit messages for the user to review, commit, and push.
- **Body Focus (The Why)**: The body of a commit message suggestion must ALWAYS explain the **why**:
  - What problem, limitation, or requirement triggered this change?
  - Why is this change necessary from a user, domain, or operational perspective?
  - What is the motivation and background context?
- **Prohibited in Body (The What & How)**: Never describe the **what** or the **how** in the body (do not include lists of files changed, method names, implementation steps, or code mechanics, as these are already self-evident in the diff).
