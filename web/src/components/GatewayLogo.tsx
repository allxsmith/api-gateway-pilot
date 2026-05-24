/** The gateway-arch mark, sized by its container's font size / explicit class. */
export function GatewayLogo({ className }: { className?: string }) {
  return (
    <svg
      viewBox="0 0 40 40"
      fill="none"
      className={className}
      aria-hidden="true"
    >
      <path
        d="M7 35 V18 a13 13 0 0 1 26 0 V35"
        stroke="currentColor"
        strokeWidth="5"
        strokeLinecap="round"
      />
      <path
        d="M15 35 V19 a5 5 0 0 1 10 0 V35"
        stroke="currentColor"
        strokeOpacity="0.5"
        strokeWidth="3"
        strokeLinecap="round"
      />
      <circle cx="20" cy="27" r="3.6" fill="#06b6d4" />
    </svg>
  )
}
