'use client'

import { useEffect, useRef, useState } from 'react'

interface LiveViewEmbedProps {
  sessionCode: string
  className?: string
}

/**
 * Embeds the SquadX Live viewer in an iframe.
 * Communicates with the parent frame via postMessage.
 */
export default function LiveViewEmbed({ sessionCode, className = '' }: LiveViewEmbedProps) {
  const iframeRef = useRef<HTMLIFrameElement>(null)
  const [status, setStatus] = useState<'loading' | 'connected' | 'error'>('loading')

  const liveUrl = process.env.NEXT_PUBLIC_LIVE_URL || 'http://localhost:3100'
  const embedUrl = `${liveUrl}/embed/${sessionCode}`

  useEffect(() => {
    const handleMessage = (event: MessageEvent) => {
      // Only accept messages from the live view origin
      if (!event.origin.startsWith(liveUrl)) return

      const { type, data } = event.data || {}
      switch (type) {
        case 'session:connected':
          setStatus('connected')
          break
        case 'session:ended':
          setStatus('error')
          break
        case 'session:error':
          setStatus('error')
          break
      }
    }

    window.addEventListener('message', handleMessage)
    return () => window.removeEventListener('message', handleMessage)
  }, [liveUrl])

  return (
    <div className={`relative w-full h-full ${className}`}>
      {status === 'loading' && (
        <div className="absolute inset-0 flex items-center justify-center bg-gray-900 text-gray-400 z-10">
          <p>Connecting to live session...</p>
        </div>
      )}
      <iframe
        ref={iframeRef}
        src={embedUrl}
        className="w-full h-full border-0"
        allow="camera; microphone; display-capture"
        sandbox="allow-scripts allow-same-origin allow-forms allow-popups"
        onLoad={() => {
          if (status === 'loading') {
            // Give the embedded page a moment to initialize
            setTimeout(() => setStatus('connected'), 2000)
          }
        }}
        onError={() => setStatus('error')}
      />
    </div>
  )
}
