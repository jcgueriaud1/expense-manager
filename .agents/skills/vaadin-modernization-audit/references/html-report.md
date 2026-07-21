# HTML Report Format

The audit report is rendered as a single **fully self-contained** HTML file, written outside the repo (resolve `$TMPDIR`, fall back to `/tmp`; `<tmpdir>/vaadin-audit-<timestamp>.html`). Open it for the user if a browser/display is available (`xdg-open` / `open` / `start`); otherwise present the file and tell them the absolute path.

**No external resources of any kind** — no CDN scripts or stylesheets, no web fonts, no remote images. Sandboxed viewers, CSP policies, and offline/CI environments silently block them and the report renders unstyled. All CSS goes in one `<style>` block in the head (plain CSS, system font stack, ~60 lines covers the whole format); the only script is the tiny inline copy-to-clipboard helper. If the report doesn't render correctly from `file://` with networking disabled, it's broken.

If HTML output is impossible in the current environment, fall back to the markdown report skeleton in `finding-schema.md`. The finding data is identical; only the rendering differs.

## Scaffold

```html
<!doctype html>
<html lang="en">
  <head>
    <meta charset="utf-8" />
    <title>Modernization audit — {{project}}</title>
    <style>/* embedded styles — see rules above; badge colors: tier1 indigo, tier2 violet,
   confidence emerald/amber/slate, risk red; panels grid collapses to one column <720px */</style>
  </head>
  <body class="bg-stone-50 text-slate-900 font-sans">
    <main class="max-w-5xl mx-auto px-6 py-12 space-y-12">
      <header>...</header>
      <section id="tier-1" class="space-y-8">...</section>
      <section id="tier-2" class="space-y-8">...</section>
      <section id="top-recommendation">...</section>
      <section id="skipped">...</section>
      <footer id="next-steps">...</footer>
    </main>
    <script>/* one small copyPrompt(id) helper, nothing else */</script>
  </body>
</html>
```

## Header

Project name, installed Vaadin version (and delta range if a delta audit), date, and a one-line tally: "6 findings — 4 component, 2 pattern · 3 high confidence". No introduction paragraph — straight into the findings.

## Finding card

The before/after code panels carry the weight. Prose is sparse. One `<article>` per finding:

- **Title** — names the change: "CustomBadge → Badge", "DashboardView: listener chain → signal bindings".
- **Badge row**:
  - Tier: `Tier 1` (indigo) / `Tier 2` (violet)
  - Confidence: `High` (emerald) / `Medium` (amber) / `Low` (slate)
  - Risk: `Mechanical` / `Behavioral` / `Threading` (threading always red)
  - `Decision required` (amber, only when the Delta field is non-empty)
  - `Preview` (amber, when the suggested component is a Preview feature — the card carries a one-line warning: feature flag required, API may change)
  - `Human checkpoint` (red, on Threading-risk findings — the session prompt starts with assumptions to confirm)
- **Files** — monospaced list, monospaced, small, usage count.
- **Before / After code panels** — the centrepiece. Two columns side by side, `<pre><code>` in bordered cards, ~15 lines max each. Before = the actual project code (trimmed, real class names). After = the modernized shape. Highlight the load-bearing lines (e.g. a light green tint on the added binding, light red on the listener chain being deleted). If either panel needs more than ~15 lines, you've scoped the finding too big — split it.
- **Problem** — one sentence. What hurts today.
- **Solution** — one sentence. What changes.
- **Wins** — bullets, ≤6 words each: "3 listeners deleted", "one update path", "official component, themed for free", "custom CSS removed". Name concrete deltas, never "cleaner code" or "easier to maintain".
- **Delta callout** (component findings, when applicable) — amber box, one line: what the custom version does that the official one doesn't, and that this makes the finding a decision.
- **Preview warning** (when the suggested component is a Preview feature) — amber box, one line: names the feature flag and that the API may change or be removed in any release, so adoption is a bet, not a mechanical swap.
- **Verification** — one line: existing coverage yes/no; if no, "session starts with a characterization test".
- **Why now** — one line, only in delta audits: "Card in Flow since 24.8 — you were on Vaadin 10 until last week".
- **Learn** — one line of links to the official docs for the capability (from the catalog entry), rendered as normal anchors. Mandatory on every card.
- **Session prompt** — collapsed `<details>` element with a copy button. On Threading-risk findings the prompt's first lines are the `Human checkpoint` block; render it visually distinct (red-tinted) inside the details.

No paragraphs of explanation anywhere in a card. If the code panels need a paragraph to be understood, re-trim the panels.

## Top recommendation

One larger card after the tiers: which finding to run first and one sentence why. Prefer a high-confidence Tier 1 quick win, or the designated pilot if a Tier 2 pattern repeats. Anchor-link to its card.

## Skipped section

Compact table, three columns: candidate, why skipped (`exceptions file`, `ambiguous intent`, `below maturity gate`), reference (exception entry or version). This section is what makes the third audit run as trustworthy as the first — never omit it.

## Next steps footer

Two short lines:
1. Rejecting a finding for a durable reason? Say so — it gets recorded in `.vaadin/modernization.md` so future audits stay quiet.
2. Accepted findings run **one per fresh session** via their copied session prompt — not in bulk, not in this session.

## Vocabulary

Use Vaadin's own nouns, exactly: component, view, binding, signal, effect, listener, theme variant, data provider. Never invent synonyms ("widget", "reactive hook", "observer"). Domain names come from the project's code — if the class is `OrderListView`, say `OrderListView`, not "the order list screen".

## Style guidance

- Editorial, not corporate-dashboard: generous whitespace, one accent color (indigo) plus emerald/amber/red/slate for the badge semantics above.
- Code panels: small monospaced text, real syntax, no line numbers, no ellipsis-soup — trim to the shape that matters.
- Cards ≤ one screen. If a card scrolls, the finding is overloaded — split it.
- No hedging, no throat-clearing. If a sentence could be a bullet, make it a bullet. If a bullet could be cut, cut it.
