export default function FormField({
  label,
  name,
  type = 'text',
  value,
  onChange,
  autoFocus = false,
  required = false,
  autoComplete,
  placeholder,
  hint,
  disabled = false,
}) {
  return (
    <label htmlFor={name} className="block space-y-1.5">
      <span className="text-sm font-medium text-slate-300">{label}</span>
      <input
        id={name}
        name={name}
        type={type}
        value={value}
        onChange={(e) => onChange(e.target.value)}
        autoFocus={autoFocus}
        required={required}
        autoComplete={autoComplete}
        placeholder={placeholder}
        disabled={disabled}
        className="w-full rounded-md border border-slate-700 bg-slate-950 px-3 py-2 text-slate-100 placeholder:text-slate-600 focus:border-emerald-500 focus:outline-none focus:ring-1 focus:ring-emerald-500 disabled:opacity-50 disabled:cursor-not-allowed"
      />
      {hint && <span className="block text-xs text-slate-500">{hint}</span>}
    </label>
  );
}
