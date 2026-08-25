import {
  ApplicationConfig,
  provideBrowserGlobalErrorListeners,
  provideZonelessChangeDetection,
} from '@angular/core';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { provideHttpClient, withFetch, withInterceptors } from '@angular/common/http';
import { provideRouter, withComponentInputBinding } from '@angular/router';

import { routes } from './app.routes';
import { authInterceptor } from './core/auth/auth.interceptor';
import { apiPrefixInterceptor } from './core/interceptors/api-prefix.interceptor';
import { errorInterceptor } from './core/interceptors/error.interceptor';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideZonelessChangeDetection(),
    provideRouter(routes, withComponentInputBinding()),
    provideAnimationsAsync(),
    provideHttpClient(
      withFetch(),
      // Order matters: the prefix runs first so the auth interceptor sees the final URL
      // when deciding whether a request is to a public endpoint, and the error interceptor
      // sits outermost to catch failures from everything inside it.
      withInterceptors([apiPrefixInterceptor, authInterceptor, errorInterceptor]),
    ),
  ],
};
