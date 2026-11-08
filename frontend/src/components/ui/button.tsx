import type { ComponentProps } from 'react'
import { Slot } from '@radix-ui/react-slot'
import { cva, type VariantProps } from 'class-variance-authority'
import { cn } from '../../lib/utils'

const buttonVariants = cva(
  'inline-flex items-center justify-center gap-2 rounded-xl text-sm font-semibold transition disabled:pointer-events-none disabled:opacity-50 [&_svg]:size-4',
  { variants: {
    variant: { default: 'bg-sky-600 text-white hover:bg-sky-700', outline: 'border border-sky-200 bg-white text-sky-800 hover:bg-sky-50', ghost: 'text-slate-600 hover:bg-sky-100' },
    size: { default: 'h-10 px-4', sm: 'h-9 px-3', lg: 'h-12 px-6' },
  }, defaultVariants: { variant: 'default', size: 'default' } },
)

type ButtonProps = ComponentProps<'button'> & VariantProps<typeof buttonVariants> & { asChild?: boolean }
export function Button({ className, variant, size, asChild = false, type = 'button', ...props }: ButtonProps) {
  const Component = asChild ? Slot : 'button'
  return <Component data-slot="button" type={asChild ? undefined : type} className={cn(buttonVariants({ variant, size, className }))} {...props} />
}
