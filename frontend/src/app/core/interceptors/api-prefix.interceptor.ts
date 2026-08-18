import { HttpInterceptorFn } from '@angular/common/http';

import { environment } from '../../../environments/environment';

/** Prefixes relative API requests (e.g. `users`) with the configured `apiUrl`. */
export const apiPrefixInterceptor: HttpInterceptorFn = (req, next) => {
  if (/^https?:\/\//i.test(req.url)) {
    return next(req);
  }

  const path = req.url.startsWith('/') ? req.url.slice(1) : req.url;
  return next(req.clone({ url: `${environment.apiUrl}/${path}` }));
};
