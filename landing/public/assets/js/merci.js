const EVENT_ENDPOINT = '/api/events';

function sendEvent(event, context = {}) {
  const payload = {
    event,
    path: window.location.pathname,
    referrer: document.referrer || '',
    session_id: window.localStorage.getItem('biotrack_lp_session_id') || '',
    context
  };

  const body = JSON.stringify(payload);

  if (navigator.sendBeacon) {
    navigator.sendBeacon(EVENT_ENDPOINT, new Blob([body], { type: 'application/json; charset=utf-8' }));
    return;
  }

  fetch(EVENT_ENDPOINT, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json; charset=utf-8' },
    body,
    keepalive: true
  }).catch(() => undefined);
}

function setupStatus() {
  const params = new URLSearchParams(window.location.search);
  const confirmed = params.get('confirmed');
  const statusNode = document.querySelector('#confirmation-status');
  const debugNode = document.querySelector('#debug-confirmation');
  const debugUrl = params.get('debug_confirmation_url');

  if (confirmed === '1' && statusNode) {
    statusNode.textContent = 'Email confirmé. Ta place dans la waitlist est maintenant active.';
  }

  if (debugUrl && debugNode) {
    debugNode.innerHTML = `Lien de confirmation (environnement dev): <a href="${debugUrl}">${debugUrl}</a>`;
  }
}

function setupShare() {
  const shareButton = document.querySelector('#share-button');
  if (!shareButton) {
    return;
  }

  shareButton.addEventListener('click', async () => {
    const shareData = {
      title: 'AJUSTE',
      text: 'Découvre AJUSTE, app iOS biohacking orientée performance et confidentialité.',
      url: window.location.origin
    };

    try {
      if (navigator.share) {
        await navigator.share(shareData);
      } else {
        await navigator.clipboard.writeText(shareData.url);
      }
      sendEvent('cta_click', { location: 'thank_you_share' });
    } catch {
      // Intentional no-op for canceled share dialogs.
    }
  });
}

function setupFeedbackButtons() {
  const output = document.querySelector('#feedback-status');
  const buttons = document.querySelectorAll('.feedback-btn');

  buttons.forEach((button) => {
    button.addEventListener('click', () => {
      const answer = button.dataset.answer || 'unknown';
      buttons.forEach((item) => item.classList.remove('btn-primary'));
      button.classList.add('btn-primary');
      if (output) {
        output.textContent = 'Merci, ton retour a été pris en compte.';
      }
      sendEvent('cta_click', { location: 'thank_you_quick_survey', answer });
    });
  });
}

window.addEventListener('DOMContentLoaded', () => {
  setupStatus();
  setupShare();
  setupFeedbackButtons();
  sendEvent('lp_view', { location: 'thank_you' });
});
