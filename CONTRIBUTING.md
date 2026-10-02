# Contributing

Thank you for your interest in Household Finance Manager.

## Before you start

- Open an issue to discuss any change larger than a small fix, so effort is not wasted.
- Bugs and feature requests use the issue templates.
- Never attach real financial data, statements or receipts to an issue. Anonymize samples first.

## Support

- Questions and bug reports: GitHub Issues (please use the templates).
- Email: info-rann-apps@NorthMail.ca

## Code contributions

The project is licensed under GPL-3.0-or-later. **Code contributions are not being accepted at this time**; bug reports, translation corrections and suggestions through Issues are very welcome. If code contributions open later, the terms will be published here first.

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
