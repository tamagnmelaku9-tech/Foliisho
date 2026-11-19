/**
 * Hawassa Transit & Fuel Queue - PWA Registration, Install Prompt & Notification Controller
 */
(function (window) {
  let deferredPrompt = null;
  let swRegistration = null;

  function isStandalone() {
    return (
      window.matchMedia('(display-mode: standalone)').matches ||
      window.navigator.standalone === true
    );
  }

  function isIOS() {
    return /iphone|ipad|ipod/i.test(window.navigator.userAgent || '');
  }

  function updateInstallButtons() {
    const buttons = document.querySelectorAll('[data-pwa-install-btn]');
    const banners = document.querySelectorAll('[data-pwa-install-banner]');

    if (isStandalone()) {
      buttons.forEach((btn) => btn.classList.add('hidden'));
      banners.forEach((banner) => banner.classList.add('hidden'));
      return;
    }

    if (deferredPrompt || isIOS()) {
      buttons.forEach((btn) => btn.classList.remove('hidden'));
      banners.forEach((banner) => banner.classList.remove('hidden'));
    } else {
      // Keep fallback install button visible so users can view manual instructions if browser suppresses prompt
      buttons.forEach((btn) => btn.classList.remove('hidden'));
    }
  }

  async function triggerInstall() {
    if (isStandalone()) {
      return;
    }
    if (deferredPrompt) {
      deferredPrompt.prompt();
      try {
        const choice = await deferredPrompt.userChoice;
        if (choice && choice.outcome === 'accepted') {
          deferredPrompt = null;
          updateInstallButtons();
        }
      } catch (e) {}
      return;
    }

    const modal = document.getElementById('pwaHelpModal');
    if (modal) {
      modal.classList.remove('hidden');
      return;
    }

    if (isIOS()) {
      alert('To install this app on iPhone/iPad: tap the Share button in Safari, then choose "Add to Home Screen".');
    } else {
      alert('To install this app: open your browser menu (⋮) and tap "Install app" or "Add to Home screen".');
    }
  }

  async function requestNotificationPermission() {
    if (!('Notification' in window)) return 'unsupported';
    if (Notification.permission === 'default') {
      try {
        return await Notification.requestPermission();
      } catch (e) {
        return Notification.permission;
      }
    }
    return Notification.permission;
  }

  async function notifyTurnAlert(title, body, targetUrl) {
    if (!('Notification' in window)) return;
    if (Notification.permission !== 'granted') return;

    const options = {
      body: body,
      icon: '/icons/icon-192.png',
      badge: '/icons/icon-192.png',
      vibrate: [400, 200, 400, 200, 600],
      tag: 'hawassa-turn-alert',
      renotify: true,
      data: { url: targetUrl || '/driver' }
    };

    try {
      if (swRegistration && swRegistration.showNotification) {
        await swRegistration.showNotification(title, options);
      } else {
        new Notification(title, options);
      }
    } catch (e) {}
  }

  window.addEventListener('beforeinstallprompt', (e) => {
    e.preventDefault();
    deferredPrompt = e;
    updateInstallButtons();
  });

  window.addEventListener('appinstalled', () => {
    deferredPrompt = null;
    updateInstallButtons();
  });

  window.addEventListener('load', () => {
    if ('serviceWorker' in navigator) {
      navigator.serviceWorker
        .register('/sw.js', { scope: '/' })
        .then((reg) => {
          swRegistration = reg;
          if (reg.update) reg.update().catch(() => {});
        })
        .catch((err) => {
          console.warn('Service Worker registration failed:', err);
        });
    }
    updateInstallButtons();
  });

  window.HawassaPWA = {
    isStandalone,
    isIOS,
    triggerInstall,
    requestNotificationPermission,
    notifyTurnAlert
  };
})(window);
