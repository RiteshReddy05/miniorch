import { useEffect, useRef } from 'react';
import { X } from 'lucide-react';

const SIZES = {
  sm: 'max-w-sm',
  md: 'max-w-lg',
  lg: 'max-w-2xl',
};

export default function Modal({ title, onClose, children, size = 'md' }) {
  const dialogRef = useRef(null);

  useEffect(() => {
    function handleKey(event) {
      if (event.key === 'Escape') onClose();
    }
    document.addEventListener('keydown', handleKey);
    const previouslyFocused = document.activeElement;
    dialogRef.current?.focus();
    return () => {
      document.removeEventListener('keydown', handleKey);
      if (previouslyFocused instanceof HTMLElement) {
        previouslyFocused.focus();
      }
    };
  }, [onClose]);

  return (
    <div
      className="fixed inset-0 z-50 flex items-start justify-center overflow-y-auto bg-slate-950/70 p-4 backdrop-blur-sm sm:p-8"
      onClick={onClose}
      role="presentation"
    >
      <div
        ref={dialogRef}
        tabIndex={-1}
        role="dialog"
        aria-modal="true"
        aria-label={title}
        onClick={(e) => e.stopPropagation()}
        className={`w-full ${SIZES[size]} rounded-xl border border-slate-800 bg-slate-900 shadow-2xl outline-none`}
      >
        <header className="flex items-center justify-between border-b border-slate-800 px-5 py-3">
          <h2 className="text-base font-semibold text-slate-100">{title}</h2>
          <button
            type="button"
            onClick={onClose}
            className="rounded text-slate-400 transition hover:text-slate-200"
            aria-label="Close dialog"
          >
            <X className="h-4 w-4" />
          </button>
        </header>
        <div className="p-5">{children}</div>
      </div>
    </div>
  );
}
