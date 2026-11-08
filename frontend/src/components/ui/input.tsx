import type { ComponentProps } from 'react'
import { cn } from '../../lib/utils'
export function Input({ className, ...props }: ComponentProps<'input'>) {
  return <input data-slot="input" className={cn('h-11 w-full rounded-xl border border-slate-200 bg-white px-3 text-sm placeholder:text-slate-400 disabled:opacity-50', className)} {...props} />
}
