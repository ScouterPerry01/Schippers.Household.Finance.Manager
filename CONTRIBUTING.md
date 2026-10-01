# Contributing

Thank you for your interest in Household Finance Manager.

## Before you start

- Open an issue to discuss any change larger than a small fix, so effort is not wasted.
- Bugs and feature requests use the issue templates.
- Never attach real financial data, statements or receipts to an issue. Anonymize samples first.

## Contributor terms

The project is licensed under GPL-3.0-or-later. So that the licence can be changed later if ever needed (see section 16.2 of the requirements), every contribution must be covered by a contributor licence agreement (CLA) granting the copyright holder the right to relicense the contribution.

**The CLA text is still being prepared. Until it is published, external pull requests cannot be merged.**

## Code guidelines

- Kotlin, official code style. The build treats warnings as errors.
- Money is always `ca.schippers.hfm.money.Money` (exact minor units). Never use `Double` or `Float` for amounts.
- Every financial calculation lives in `core/calc` and has tests against independent reference figures.
- Every user-facing string goes through `core/i18n` (desktop) or `strings.xml` (Android), in both English and French. In `.properties` files, write apostrophes as `''`.
- Database changes go through SQLDelight migrations (`.sqm` files), never by editing a released schema.

## Running checks

```sh
./gradlew test
```
