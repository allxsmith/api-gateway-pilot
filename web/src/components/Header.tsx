import { LogOut } from 'lucide-react'
import { useAuth } from 'react-oidc-context'

import { GatewayLogo } from '@/components/GatewayLogo'
import { Button } from '@/components/ui/button'

export function Header() {
  const auth = useAuth()
  return (
    <header className="border-b bg-card">
      <div className="mx-auto flex max-w-4xl items-center justify-between p-4">
        <div className="flex items-center gap-2 text-primary">
          <GatewayLogo className="h-7 w-7" />
          <span className="font-semibold text-foreground">API Gateway Pilot</span>
        </div>
        <Button variant="outline" size="sm" onClick={() => void auth.removeUser()}>
          <LogOut />
          Sign out
        </Button>
      </div>
    </header>
  )
}
