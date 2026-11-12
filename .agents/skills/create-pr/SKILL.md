---
name: create-pr
description: Guidelines for structuring pull requests and commit message suggestions focused on the why.
---

# Pull Request & Commit Message Skill

Use this skill when preparing pull requests, review descriptions, or commit message suggestions.

## Commit Message Guidelines

1. **Title**:
   - Follow Conventional Commits format (`type(scope): concise imperative subject`).
   - Keep the title focused and under 72 characters.

2. **Body**:
   - **Always describe the WHY**: Detail the problem context, user need, architectural reason, or bug impact that made this change necessary.
   - **Never describe the WHAT and HOW**: Avoid bullet lists of code edits, class names, files modified, or step-by-step implementation mechanics. The diff already explains what and how.

3. **Footer**:
   - **Link to issue**: If possible, link the commit to a GitHub issue like `**Related to**: #123` or `**Closes**: #123`

4. **Workflow**:
   - Stage changes using `git add`.
   - Never commit autonomously; output the recommended commit message for user review.
