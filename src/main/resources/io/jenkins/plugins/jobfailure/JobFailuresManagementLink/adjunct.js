(function () {
  const emailEnabled = document.getElementById('failure-radar-email-enabled');
  const recipientsField = document.getElementById('failure-radar-email-recipients');
  const scheduleField = document.getElementById('failure-radar-email-schedule');
  const testEmailButton = document.getElementById('failure-radar-send-test-email');
  const nextEmail = document.getElementById('failure-radar-next-email');
  if (!emailEnabled || !recipientsField || !scheduleField || !testEmailButton || !nextEmail) return;

  let timer;

  function refreshNextEmail() {
    nextEmail.textContent = nextEmail.dataset.calculating;
    const url = new URL('previewSchedule', window.location.href);
    url.searchParams.set('schedule', scheduleField.value);
    fetch(url, { credentials: 'same-origin' })
      .then(response => response.text().then(text => ({ ok: response.ok, text: text })))
      .then(result => {
        nextEmail.textContent = result.text;
        nextEmail.setAttribute('aria-invalid', result.ok ? 'false' : 'true');
      })
      .catch(() => {
        nextEmail.textContent = nextEmail.dataset.unavailable;
        nextEmail.setAttribute('aria-invalid', 'true');
      });
  }

  function updateEmailSettingsState() {
    const disabled = !emailEnabled.checked;
    recipientsField.disabled = disabled;
    scheduleField.disabled = disabled;
    testEmailButton.disabled = disabled;
    if (!disabled) refreshNextEmail();
  }

  emailEnabled.addEventListener('change', updateEmailSettingsState);
  scheduleField.addEventListener('input', function () {
    clearTimeout(timer);
    timer = setTimeout(refreshNextEmail, 400);
  });
  updateEmailSettingsState();
})();
