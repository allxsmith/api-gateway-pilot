import { useAuth } from 'react-oidc-context'

import { DashboardPage } from '@/pages/DashboardPage'
import { LoginPage } from '@/pages/LoginPage'

export default function App() {
  const auth = useAuth()

  if (auth.isLoading) {
    return <CenteredMessage text="Loading…" />
  }
  if (auth.error) {
    return <CenteredMessage text={`Authentication error: ${auth.error.message}`} />
  }
  return auth.isAuthenticated ? <DashboardPage /> : <LoginPage />
}

function CenteredMessage({ text }: { text: string }) {
  return (
    <div className="flex min-h-screen items-center justify-center bg-background text-muted-foreground">
      {text}
    </div>
  )
}
