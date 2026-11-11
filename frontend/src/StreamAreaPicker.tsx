import { useRef, type PointerEvent } from 'react'

type Point = { x: number; y: number }
export type Rectangle = Point & { width: number; height: number }

const canvasSize = 10000
const wholeCanvas = { x: 0, y: 0, width: canvasSize, height: canvasSize }

export function StreamAreaPicker({ area, onChange, zone, occupied = [], disabled = false }: {
  area: Rectangle | null
  onChange: (area: Rectangle | null) => void
  zone?: Rectangle | null
  occupied?: Rectangle[]
  disabled?: boolean
}) {
  const anchor = useRef<(Point & { pointerId: number }) | null>(null)
  const limits = zone ?? wholeCanvas

  function point(event: PointerEvent<SVGSVGElement>): Point {
    const bounds = event.currentTarget.getBoundingClientRect()
    return {
      x: Math.round((event.clientX - bounds.left) / bounds.width * canvasSize),
      y: Math.round((event.clientY - bounds.top) / bounds.height * canvasSize),
    }
  }

  function draw(event: PointerEvent<SVGSVGElement>) {
    const start = anchor.current
    if (!start || start.pointerId !== event.pointerId || disabled) return
    const end = point(event)
    const x = Math.max(limits.x, Math.min(limits.x + limits.width, end.x))
    const y = Math.max(limits.y, Math.min(limits.y + limits.height, end.y))
    const width = Math.abs(x - start.x)
    const height = Math.abs(y - start.y)
    onChange(width && height ? { x: Math.min(start.x, x), y: Math.min(start.y, y), width, height } : null)
  }

  return <svg viewBox="0 0 10000 10000" preserveAspectRatio="none" aria-label="Stream layout"
    className={`block aspect-video w-full touch-none rounded-lg ${disabled ? '' : 'cursor-crosshair'} ${zone ? 'bg-slate-200' : 'bg-slate-100'}`}
    onPointerDown={event => {
      if (disabled || !event.isPrimary || event.button !== 0) return
      const start = point(event)
      if (start.x < limits.x || start.y < limits.y ||
          start.x > limits.x + limits.width || start.y > limits.y + limits.height) return
      anchor.current = { ...start, pointerId: event.pointerId }
      event.currentTarget.setPointerCapture(event.pointerId)
    }}
    onPointerMove={draw}
    onPointerUp={draw}
    onLostPointerCapture={event => {
      if (anchor.current?.pointerId === event.pointerId) anchor.current = null
    }}>
    {zone && <rect {...zone} fill="#f0f9ff" stroke="#0369a1" strokeWidth="35" strokeDasharray="100 65" />}
    {occupied.map((reservation, index) => <rect key={index} {...reservation} fill="#fecaca" stroke="#b91c1c" strokeWidth="35" />)}
    {area && <rect {...area} fill="#7dd3fc" fillOpacity=".65" stroke="#0369a1" strokeWidth="35" />}
  </svg>
}
