# Gemini Development Guide

This guide outlines the workflow for developing this project and pushing changes to GitHub.

## Git Workflow: Granular Commits

To ensure better version control, we follow a granular commit strategy. Instead of one large "updated project" commit, break your work into multiple small, descriptive commits.

### How to Commit Granularly:

1.  **Stage specific files** for one logical change:
    ```bash
    git add app.py
    ```
2.  **Commit with a clear message**:
    ```bash
    git commit -m "Refactor: Move webform parser to earlier in app.py"
    ```
3.  **Repeat** for the next part of your work:
    ```bash
    git add custom_prompt.txt
    ```
    ```bash
    git commit -m "Prompt: Add verbatim extraction for special conditions"
    ```

## Excluded Files (Local Customizations)

The following files are listed in `.gitignore` and are **excluded from normal pushes** to protect sensitive data and local logic:
- `webform.html` (Contains ActionStep credentials)
- `custom_prompt.txt` (Contains your specific AI instructions)

### How to push an excluded file (If explicitly needed):
If you *intentionally* want to push one of these files (e.g., you've made a template update you want to share), use the `-f` (force) flag when adding:
```bash
git add -f custom_prompt.txt
git commit -m "Prompt: Update core extraction logic template"
git push
```

## Pushing to GitHub

Once you have multiple commits ready, push them to the repository.

### Initial Push (If branch not set):
```bash
git push -u origin main
```

### Subsequent Pushes:
```bash
git push
```

### Tips for Better Version Control:
- **Commit Early, Commit Often**: Don't wait until the end of the day to commit.
- **Descriptive Messages**: Start messages with prefixes like `Feat:`, `Fix:`, `Refactor:`, or `Docs:`.
- **Review Before Pushing**: Use `git log -n 5` to see your recent local commits before pushing.

---

*Note: This file is for developer reference and should be kept updated as the workflow evolves.*
