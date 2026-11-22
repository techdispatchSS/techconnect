// Shows job alerts while the app is closed (#69). Deliberately small, and has no fetch handler:
// this is not an offline cache, so it can't serve anyone a stale version of the app.

self.addEventListener('install', () => self.skipWaiting());
self.addEventListener('activate', (event) => event.waitUntil(self.clients.claim()));

function readMessage(event) {
  try {
    return event.data.json().data || {};
  } catch {
    return {};
  }
}

self.addEventListener('push', (event) => {
  const message = readMessage(event);
  // Always shows something: browsers punish a push that shows no notification, and on iPhone
  // can stop delivering them.
  event.waitUntil(
    self.registration.showNotification(message.title || 'TechConnect', {
      body: message.body || 'You have a job waiting.',
      icon: '/icon-192.png',
      badge: '/icon-192.png',
      // An offer lapses in minutes, so it stays up until it has been seen.
      requireInteraction: true,
      data: { url: message.url || '/jobs' },
    }),
  );
});

self.addEventListener('notificationclick', (event) => {
  event.notification.close();
  // Only ever a page of this app, whatever the message said.
  const target = new URL(event.notification.data?.url || '/jobs', self.location.origin);
  const url = target.origin === self.location.origin ? target.href : `${self.location.origin}/jobs`;
  event.waitUntil(
    self.clients.matchAll({ type: 'window', includeUncontrolled: true }).then((windows) => {
      const open = windows.find((client) => client.url.startsWith(self.location.origin));
      if (open) {
        return open.navigate(url).then((client) => (client ?? open).focus());
      }
      return self.clients.openWindow(url);
    }),
  );
});
