import { WebStorageStateStore } from 'oidc-client-ts'
import type { AuthProviderProps } from 'react-oidc-context'

const origin = window.location.origin

/**
 * OIDC configuration for the Authorization Code + PKCE flow against the
 * auth-server (reached through the nginx reverse proxy).
 */
export const oidcConfig: AuthProviderProps = {
  authority: import.meta.env.VITE_OIDC_AUTHORITY ?? 'http://localhost:8088',
  client_id: import.meta.env.VITE_OIDC_CLIENT_ID ?? 'spa-client',
  redirect_uri: `${origin}/callback`,
  post_logout_redirect_uri: origin,
  scope: 'openid profile read',
  userStore: new WebStorageStateStore({ store: window.localStorage }),
  onSigninCallback: () => {
    // Drop the ?code=... query string after a successful sign-in.
    window.history.replaceState({}, document.title, '/')
  },
}
