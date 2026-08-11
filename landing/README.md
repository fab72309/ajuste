# AJUSTE Landing (FR)

Landing page SEO/AEO + API de waitlist qualifiée pour AJUSTE.

## Fonctionnalités

- Landing FR orientée conversion avec sections SEO riches.
- JSON-LD: `Organization`, `WebSite`, `SoftwareApplication`, `FAQPage`.
- Formulaire waitlist: prénom, nom, email, objectif principal, consentement.
- Endpoint API: `POST /api/leads`.
- Double opt-in: génération de token + URL de confirmation.
- Endpoint de confirmation: `GET /api/leads/confirm?token=...`.
- Anti-spam: honeypot + rate limit + validation serveur.
- Tracking d'événements: `POST /api/events`.
- Pages trust: mentions légales, confidentialité, contact.

## Démarrage

```bash
cd landing
npm run dev
```

Serveur par défaut: `http://localhost:8787`.

## Variables d'environnement

- `PORT`: port HTTP (défaut `8787`).
- `HOST`: host d'écoute (défaut `0.0.0.0`).
- `PUBLIC_BASE_URL`: URL publique utilisée pour construire les liens de confirmation.
- `LEADS_DATA_FILE`: chemin du fichier JSON de stockage des leads.
- `EVENTS_FILE`: chemin du fichier log des événements.
- `CONFIRMATION_WEBHOOK_URL`: webhook optionnel pour déléguer l'envoi d'email double opt-in.
- `IP_HASH_SALT`: sel pour le hash IP.

## Contrat API

### `POST /api/leads`

Payload JSON attendu:

```json
{
  "first_name": "Jane",
  "last_name": "Doe",
  "email": "jane@example.com",
  "primary_goal": "concentration",
  "consent_marketing": true,
  "source": "utm:google:cpc:launch",
  "created_at": "2026-02-05T12:00:00.000Z"
}
```

Réponses:

- `201`: `{ "success": true, "lead_id": "..." }`
- `400`: `{ "success": false, "error_code": "VALIDATION_ERROR" }`
- `409`: `{ "success": false, "error_code": "EMAIL_ALREADY_EXISTS" }`
- `429`: `{ "success": false, "error_code": "RATE_LIMIT" }`

### `POST /api/events`

Événements supportés: `lp_view`, `cta_click`, `form_start`, `form_submit_success`, `form_submit_error`.

## Tests

```bash
cd landing
npm test
```
