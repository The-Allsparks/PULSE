# Security Policy

## Supported versions

| Version | Supported |
| ------- | --------- |
| 0.1.x   | Yes       |

## Reporting a vulnerability

Please do **not** open a public issue for security problems that could put robots, students, or machines at risk.

**Private channel (preferred):** use [GitHub Private Vulnerability Reporting](https://github.com/The-Allsparks/PULSE/security/advisories/new).

PULSE reads hardware; it must not command motors. Treat unexpected actuator writes, background Hub threads, or presenting stale samples as fresh as a safety defect.

## Secrets

Never store passwords, Wi-Fi credentials, API keys, or tokens in the repository.
