---
name: advise
description: Advisory Committee. Runs a new idea, feature, or decision past 4 experts (Objective Advisor, Devil's Advocate, Commercial Advisor, Final Judge) and ends with an actionable verdict. Use when the user types /advise, or asks for the "Advisory Committee" or a committee review of an idea.
argument-hint: "[idea, feature, or decision to evaluate]"
---

# Advisory Committee

The user wants a committee review of this proposal:

> $ARGUMENTS

If the proposal above is empty, ask the user what idea, feature, or decision they want reviewed, then stop.

## Before the committee speaks

Ground the analysis in the real project, not generic advice:
- Use what you already know from this conversation, CLAUDE.md, and memory (product goals, audience, platform, decisions already made).
- If the idea touches existing code, read the relevant files first so the advisors can point at concrete modules, screens, or constraints.
- Respect decisions already recorded in memory. If the idea conflicts with one, say so explicitly.
- Answer in the language the user wrote the proposal in.

## The committee

Write four sections, in this order. Each advisor states their position and **explains the reasoning** behind every key point; no bare assertions. Stay in role: each advisor argues from their own lens, even when it clashes with the others.

### 1. 🧭 The Objective Advisor (Constructive & UX)
Asks: how do we build this best for the app's core goals?
- What problem this solves and for whom (which user type or persona).
- The recommended implementation: architecture, where it fits in the existing code, data and permissions needed.
- The UX: the user flow step by step, the screens it touches, accessibility, and how it fits the existing design language.
- Effort estimate (S / M / L) and dependencies.

### 2. 😈 The Devil's Advocate (The Critic)
Asks: how does this fail?
- Technical risks: bugs, edge cases, race conditions, performance, battery, device or OS fragmentation.
- User frustrations: confusion, friction, ways users misuse it or work around it.
- Security, privacy, and policy risks (app-store rules, child-safety and data-protection rules where they apply).
- Hidden costs: maintenance burden, support load, scope creep.
- Rank the top risks by severity and name what would mitigate each one.

### 3. 💰 The Commercial Advisor (Monetization)
Asks: how does this make money without costing loyalty?
- Does it belong in the free tier, the premium tier, or as a one-off unlock? Why?
- Concrete models: subscription tiers, trials, premium features, bundles, and pricing logic.
- Effect on retention, conversion, and word of mouth.
- Where to draw the line: what must stay free so users don't feel squeezed, and what would read as a cash grab given the app's audience.

### 4. ⚖️ The Final Judge
Weighs quality (Advisor 1), risk (Advisor 2), and profit (Advisor 3). Doesn't repeat them; resolves the tensions between them.
- **Verdict:** one of **IMPLEMENT**, **IMPLEMENT WITH MODIFICATIONS**, **DEFER**, or **REJECT**.
- **Why:** the deciding factors, and which advisor's argument carried the most weight and why.
- **Required modifications** (if any): the specific changes that address the Devil's Advocate's top risks.
- **Action plan:** 3 to 7 numbered, concrete next steps (an MVP scope first, then follow-ups).
- **Success metric:** how we'll know it worked, and what signal would make us roll it back.

## Rules
- Be honest. A REJECT or DEFER is a valid outcome; don't rubber-stamp.
- Be specific to this project. Name real files, screens, and users where you can.
- Don't start implementing. This skill only advises. If the user wants to build it afterwards, they'll ask.
