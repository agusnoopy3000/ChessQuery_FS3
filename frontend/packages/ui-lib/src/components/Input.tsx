import { InputHTMLAttributes, ReactNode, forwardRef, useId } from 'react';
import { cn } from '../utils/cn';

export interface InputProps extends InputHTMLAttributes<HTMLInputElement> {
  label?: string;
  error?: string;
  hint?: string;
  leftIcon?: ReactNode;
  rightIcon?: ReactNode;
}

export const Input = forwardRef<HTMLInputElement, InputProps>(
  ({ label, error, hint, leftIcon, rightIcon, className, id, style, 'aria-label': ariaLabel, ...rest }, ref) => {
    const reactId = useId();
    const inputId = id || (label ? `input-${label.replace(/\s+/g, '-').toLowerCase()}` : `input-${reactId}`);
    const msgId = error || hint ? `${inputId}-msg` : undefined;
    return (
      <div style={{ display: 'flex', flexDirection: 'column' }}>
        {label && (
          <label htmlFor={inputId} className="label">
            {label}
          </label>
        )}
        <div style={{ position: 'relative' }}>
          {leftIcon && (
            <span
              aria-hidden="true"
              style={{
                position: 'absolute',
                left: 10,
                top: 0,
                bottom: 0,
                display: 'flex',
                alignItems: 'center',
                color: 'var(--text-dim)',
                pointerEvents: 'none',
              }}
            >
              {leftIcon}
            </span>
          )}
          <input
            ref={ref}
            id={inputId}
            className={cn('input', className)}
            aria-label={!label ? ariaLabel : undefined}
            aria-invalid={error ? true : undefined}
            aria-describedby={msgId}
            style={{
              paddingLeft: leftIcon ? 34 : undefined,
              paddingRight: rightIcon ? 34 : undefined,
              borderColor: error ? 'var(--red)' : undefined,
              ...style,
            }}
            {...rest}
          />
          {rightIcon && (
            <span
              aria-hidden="true"
              style={{
                position: 'absolute',
                right: 10,
                top: 0,
                bottom: 0,
                display: 'flex',
                alignItems: 'center',
                color: 'var(--text-dim)',
              }}
            >
              {rightIcon}
            </span>
          )}
        </div>
        {error ? (
          <span id={msgId} role="alert" style={{ fontSize: 11, color: 'var(--red)', marginTop: 4 }}>
            {error}
          </span>
        ) : hint ? (
          <span id={msgId} style={{ fontSize: 11, color: 'var(--text-muted)', marginTop: 4 }}>
            {hint}
          </span>
        ) : null}
      </div>
    );
  },
);
Input.displayName = 'Input';
