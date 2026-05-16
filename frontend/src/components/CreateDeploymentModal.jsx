import { useState } from 'react';
import { Plus, Trash2 } from 'lucide-react';
import Modal from './Modal.jsx';
import FormField from './FormField.jsx';
import ErrorBanner from './ErrorBanner.jsx';
import { createDeployment } from '../lib/deployments.js';
import { extractErrorDetails, extractErrorMessage } from '../lib/format.js';

const NAME_PATTERN = /^[a-z0-9]([-a-z0-9]*[a-z0-9])?$/;

function validateLocal({ name, image, tag, desiredReplicas }) {
  if (!NAME_PATTERN.test(name) || name.length > 40) {
    return 'name must be lowercase alphanumeric with optional hyphens, 1-40 chars';
  }
  if (!image.trim()) return 'image is required';
  if (!tag.trim()) return 'tag is required';
  if (desiredReplicas < 1 || desiredReplicas > 10) {
    return 'desiredReplicas must be between 1 and 10';
  }
  return null;
}

function buildProbe(probe) {
  if (probe.type === 'DOCKER') {
    return {
      type: 'DOCKER',
      intervalSeconds: probe.intervalSeconds,
      timeoutSeconds: probe.timeoutSeconds,
      failureThreshold: probe.failureThreshold,
    };
  }
  if (probe.type === 'TCP') {
    return {
      type: 'TCP',
      port: probe.port,
      intervalSeconds: probe.intervalSeconds,
      timeoutSeconds: probe.timeoutSeconds,
      failureThreshold: probe.failureThreshold,
    };
  }
  return {
    type: 'HTTP',
    path: probe.path || '/',
    port: probe.port,
    intervalSeconds: probe.intervalSeconds,
    timeoutSeconds: probe.timeoutSeconds,
    failureThreshold: probe.failureThreshold,
  };
}

export default function CreateDeploymentModal({ onClose, onCreated }) {
  const [name, setName] = useState('');
  const [image, setImage] = useState('');
  const [tag, setTag] = useState('');
  const [desiredReplicas, setDesiredReplicas] = useState(1);
  const [envEntries, setEnvEntries] = useState([]);
  const [portEntries, setPortEntries] = useState([]);
  const [probeExpanded, setProbeExpanded] = useState(false);
  const [probe, setProbe] = useState({
    type: 'DOCKER',
    path: '/',
    port: 80,
    intervalSeconds: 10,
    timeoutSeconds: 2,
    failureThreshold: 3,
  });
  const [error, setError] = useState(null);
  const [details, setDetails] = useState([]);
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(event) {
    event.preventDefault();
    setError(null);
    setDetails([]);

    const local = validateLocal({ name, image, tag, desiredReplicas });
    if (local) {
      setError(local);
      return;
    }

    const env = {};
    for (const { key, value } of envEntries) {
      if (key.trim()) env[key.trim()] = value;
    }

    const ports = portEntries
      .filter((p) => p.hostPort && p.containerPort)
      .map((p) => ({
        hostPort: Number(p.hostPort),
        containerPort: Number(p.containerPort),
        protocol: p.protocol || 'tcp',
      }));

    const body = {
      name,
      image,
      tag,
      desiredReplicas: Number(desiredReplicas),
      env: Object.keys(env).length > 0 ? env : undefined,
      ports: ports.length > 0 ? ports : undefined,
      probe: probeExpanded ? buildProbe(probe) : undefined,
    };

    setSubmitting(true);
    try {
      const created = await createDeployment(body);
      onCreated(created);
      onClose();
    } catch (err) {
      setError(extractErrorMessage(err, 'create failed'));
      setDetails(extractErrorDetails(err));
    } finally {
      setSubmitting(false);
    }
  }

  function addEnvRow() {
    setEnvEntries((prev) => [...prev, { key: '', value: '' }]);
  }

  function updateEnvRow(idx, field, value) {
    setEnvEntries((prev) => prev.map((row, i) => (i === idx ? { ...row, [field]: value } : row)));
  }

  function removeEnvRow(idx) {
    setEnvEntries((prev) => prev.filter((_, i) => i !== idx));
  }

  function addPortRow() {
    setPortEntries((prev) => [
      ...prev,
      { hostPort: '', containerPort: '', protocol: 'tcp' },
    ]);
  }

  function updatePortRow(idx, field, value) {
    setPortEntries((prev) => prev.map((row, i) => (i === idx ? { ...row, [field]: value } : row)));
  }

  function removePortRow(idx) {
    setPortEntries((prev) => prev.filter((_, i) => i !== idx));
  }

  return (
    <Modal title="New deployment" onClose={submitting ? () => {} : onClose} size="lg">
      <form onSubmit={handleSubmit} className="space-y-5">
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <FormField
            label="Name"
            name="name"
            value={name}
            onChange={setName}
            autoFocus
            required
            disabled={submitting}
            hint="lowercase alphanumeric, hyphens allowed"
          />
          <FormField
            label="Desired replicas"
            name="desiredReplicas"
            type="number"
            value={desiredReplicas}
            onChange={setDesiredReplicas}
            required
            disabled={submitting}
            hint="1 to 10"
          />
          <FormField
            label="Image"
            name="image"
            value={image}
            onChange={setImage}
            required
            disabled={submitting}
            placeholder="nginx"
          />
          <FormField
            label="Tag"
            name="tag"
            value={tag}
            onChange={setTag}
            required
            disabled={submitting}
            placeholder="1.27-alpine"
          />
        </div>

        <fieldset className="space-y-2 rounded-lg border border-slate-800 p-4">
          <div className="flex items-center justify-between">
            <legend className="text-sm font-medium text-slate-300">Environment</legend>
            <button
              type="button"
              onClick={addEnvRow}
              disabled={submitting}
              className="inline-flex items-center gap-1 text-xs text-emerald-400 hover:text-emerald-300"
            >
              <Plus className="h-3.5 w-3.5" /> Add variable
            </button>
          </div>
          {envEntries.length === 0 ? (
            <p className="text-xs text-slate-500">No environment variables.</p>
          ) : (
            <ul className="space-y-2">
              {envEntries.map((row, idx) => (
                <li key={idx} className="flex items-center gap-2">
                  <input
                    placeholder="KEY"
                    value={row.key}
                    onChange={(e) => updateEnvRow(idx, 'key', e.target.value)}
                    disabled={submitting}
                    className="w-1/3 rounded-md border border-slate-700 bg-slate-950 px-2 py-1 text-sm text-slate-100 placeholder:text-slate-600 focus:border-emerald-500 focus:outline-none focus:ring-1 focus:ring-emerald-500"
                  />
                  <input
                    placeholder="value"
                    value={row.value}
                    onChange={(e) => updateEnvRow(idx, 'value', e.target.value)}
                    disabled={submitting}
                    className="flex-1 rounded-md border border-slate-700 bg-slate-950 px-2 py-1 text-sm text-slate-100 placeholder:text-slate-600 focus:border-emerald-500 focus:outline-none focus:ring-1 focus:ring-emerald-500"
                  />
                  <button
                    type="button"
                    onClick={() => removeEnvRow(idx)}
                    disabled={submitting}
                    className="text-slate-400 hover:text-rose-300"
                    aria-label="Remove env row"
                  >
                    <Trash2 className="h-4 w-4" />
                  </button>
                </li>
              ))}
            </ul>
          )}
        </fieldset>

        <fieldset className="space-y-2 rounded-lg border border-slate-800 p-4">
          <div className="flex items-center justify-between">
            <legend className="text-sm font-medium text-slate-300">Ports</legend>
            <button
              type="button"
              onClick={addPortRow}
              disabled={submitting}
              className="inline-flex items-center gap-1 text-xs text-emerald-400 hover:text-emerald-300"
            >
              <Plus className="h-3.5 w-3.5" /> Add port
            </button>
          </div>
          {portEntries.length === 0 ? (
            <p className="text-xs text-slate-500">No port bindings.</p>
          ) : (
            <ul className="space-y-2">
              {portEntries.map((row, idx) => (
                <li key={idx} className="flex items-center gap-2">
                  <input
                    placeholder="host"
                    type="number"
                    value={row.hostPort}
                    onChange={(e) => updatePortRow(idx, 'hostPort', e.target.value)}
                    disabled={submitting}
                    className="w-24 rounded-md border border-slate-700 bg-slate-950 px-2 py-1 text-sm text-slate-100 placeholder:text-slate-600 focus:border-emerald-500 focus:outline-none focus:ring-1 focus:ring-emerald-500"
                  />
                  <span className="text-slate-600">→</span>
                  <input
                    placeholder="container"
                    type="number"
                    value={row.containerPort}
                    onChange={(e) => updatePortRow(idx, 'containerPort', e.target.value)}
                    disabled={submitting}
                    className="w-24 rounded-md border border-slate-700 bg-slate-950 px-2 py-1 text-sm text-slate-100 placeholder:text-slate-600 focus:border-emerald-500 focus:outline-none focus:ring-1 focus:ring-emerald-500"
                  />
                  <select
                    value={row.protocol}
                    onChange={(e) => updatePortRow(idx, 'protocol', e.target.value)}
                    disabled={submitting}
                    className="rounded-md border border-slate-700 bg-slate-950 px-2 py-1 text-sm text-slate-100 focus:border-emerald-500 focus:outline-none focus:ring-1 focus:ring-emerald-500"
                  >
                    <option value="tcp">tcp</option>
                    <option value="udp">udp</option>
                  </select>
                  <div className="flex-1" />
                  <button
                    type="button"
                    onClick={() => removePortRow(idx)}
                    disabled={submitting}
                    className="text-slate-400 hover:text-rose-300"
                    aria-label="Remove port row"
                  >
                    <Trash2 className="h-4 w-4" />
                  </button>
                </li>
              ))}
            </ul>
          )}
        </fieldset>

        <fieldset className="space-y-3 rounded-lg border border-slate-800 p-4">
          <label className="flex items-center gap-2 text-sm font-medium text-slate-300">
            <input
              type="checkbox"
              checked={probeExpanded}
              onChange={(e) => setProbeExpanded(e.target.checked)}
              disabled={submitting}
              className="h-4 w-4 rounded border-slate-700 bg-slate-950 text-emerald-500 focus:ring-emerald-500"
            />
            Custom probe
            {!probeExpanded && (
              <span className="ml-1 text-xs font-normal text-slate-500">
                (defaults to Docker state, 10s/2s/3)
              </span>
            )}
          </label>
          {probeExpanded && (
            <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
              <label className="block space-y-1.5">
                <span className="text-xs font-medium text-slate-400">Type</span>
                <select
                  value={probe.type}
                  onChange={(e) => setProbe({ ...probe, type: e.target.value })}
                  disabled={submitting}
                  className="w-full rounded-md border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-slate-100 focus:border-emerald-500 focus:outline-none focus:ring-1 focus:ring-emerald-500"
                >
                  <option value="HTTP">HTTP</option>
                  <option value="TCP">TCP</option>
                  <option value="DOCKER">DOCKER</option>
                </select>
              </label>
              {probe.type === 'HTTP' && (
                <FormField
                  label="Path"
                  name="probePath"
                  value={probe.path}
                  onChange={(v) => setProbe({ ...probe, path: v })}
                  disabled={submitting}
                  placeholder="/healthz"
                />
              )}
              {probe.type !== 'DOCKER' && (
                <FormField
                  label="Port"
                  name="probePort"
                  type="number"
                  value={probe.port}
                  onChange={(v) => setProbe({ ...probe, port: Number(v) })}
                  disabled={submitting}
                />
              )}
              <FormField
                label="Interval (s)"
                name="probeInterval"
                type="number"
                value={probe.intervalSeconds}
                onChange={(v) => setProbe({ ...probe, intervalSeconds: Number(v) })}
                disabled={submitting}
                hint="5-300"
              />
              <FormField
                label="Timeout (s)"
                name="probeTimeout"
                type="number"
                value={probe.timeoutSeconds}
                onChange={(v) => setProbe({ ...probe, timeoutSeconds: Number(v) })}
                disabled={submitting}
                hint="1-60, < interval"
              />
              <FormField
                label="Failure threshold"
                name="probeThreshold"
                type="number"
                value={probe.failureThreshold}
                onChange={(v) => setProbe({ ...probe, failureThreshold: Number(v) })}
                disabled={submitting}
                hint="1-10"
              />
            </div>
          )}
        </fieldset>

        <ErrorBanner message={error} details={details} />

        <div className="flex items-center justify-end gap-2 pt-2">
          <button
            type="button"
            onClick={onClose}
            disabled={submitting}
            className="rounded-md border border-slate-700 px-4 py-2 text-sm text-slate-300 transition hover:border-slate-600 hover:bg-slate-900 disabled:opacity-50"
          >
            Cancel
          </button>
          <button
            type="submit"
            disabled={submitting}
            className="rounded-md bg-emerald-500 px-4 py-2 text-sm font-medium text-slate-950 transition hover:bg-emerald-400 disabled:opacity-50 disabled:cursor-not-allowed"
          >
            {submitting
              ? 'Creating deployment (may take up to 60 seconds for first-time image pulls)…'
              : 'Create deployment'}
          </button>
        </div>
      </form>
    </Modal>
  );
}
