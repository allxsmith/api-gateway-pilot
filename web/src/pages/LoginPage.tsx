import { LogIn } from 'lucide-react'
import { useAuth } from 'react-oidc-context'

import { GatewayLogo } from '@/components/GatewayLogo'
import { Button } from '@/components/ui/button'
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card'

export function LoginPage() {
  const auth = useAuth()
  return (
    <div className="flex min-h-screen items-center justify-center bg-gradient-to-br from-indigo-600 to-indigo-900 p-4">
      <Card className="w-full max-w-md">
        <CardHeader className="items-center text-center">
          <div className="mb-2 flex h-14 w-14 items-center justify-center rounded-xl bg-primary text-primary-foreground">
            <GatewayLogo className="h-8 w-8" />
          </div>
          <CardTitle className="text-2xl">API Gateway Pilot</CardTitle>
          <CardDescription>
            Sign in to view your profile and devices.
          </CardDescription>
        </CardHeader>
        <CardContent className="flex flex-col gap-4">
          <Button
            size="lg"
            className="w-full"
            onClick={() => void auth.signinRedirect()}
          >
            <LogIn />
            Sign in
          </Button>
          <p className="text-center text-xs text-muted-foreground">
            Demo users <code className="font-mono">alice</code> /{' '}
            <code className="font-mono">bob</code> — password{' '}
            <code className="font-mono">password</code>
          </p>
        </CardContent>
      </Card>
    </div>
  )
}
