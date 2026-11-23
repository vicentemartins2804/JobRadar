import { HttpInterceptorFn } from '@angular/common/http';

import { environment } from '../../environments/environment';

/** Sends the session cookie on every request to our backend. */
export const credentialsInterceptor: HttpInterceptorFn = (req, next) => {
  if (req.url.startsWith(environment.backendUrl)) {
    return next(req.clone({ withCredentials: true }));
  }
  return next(req);
};
