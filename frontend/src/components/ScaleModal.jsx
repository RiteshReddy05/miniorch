import { useState } from 'react';
import Modal from './Modal.jsx';
import FormField from './FormField.jsx';
import ErrorBanner from './ErrorBanner.jsx';
import { scaleDeployment } from '../lib/deployments.js';
import { extractErrorDetails, extractErrorMessage } from '../lib/format.js';
import { useToast } from './Toast.jsx';

export default function ScaleModal({ deployment, onClose, onScaled }) {
  const toast = useToast();
  const [desiredReplicas, setDesiredReplicas] = useState(deployment.desiredReplicas);
  const [error, setError] = useState(null);
  const [details, setDetails] = useState([]);
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(event) {
    event.preventDefault();
    setError(null);
    setDetails([]);
    const value = Number(desiredReplicas);
    if (!Number.isFinite(value) || value < 1 || value > 10) {
      setError('desiredReplicas must be between 1 and 10');
      return;
    }
    setSubmitting(true);
    try {
      const updated = await scaleDeployment(deployment.id, value);
      onScaled(updated);
      toast.push({
        tone: 'success',
        title: 'Scale applied',
        message: `${deployment.name} -> ${value} replica${value === 1 ? '' : 's'}`,
      });
      onClose();
    } catch (err) {
      setError(extractErrorMessage(err, 'scale failed'));
      setDetails(extractErrorDetails(err));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <Modal title={`Scale ${deployment.name}`} onClose={submitting ? () => {} : onClose} size="sm">
      <form onSubmit={handleSubmit} className="space-y-4">
        <p className="text-sm text-slate-400">
          Current: <span className="text-slate-200">{deployment.desiredReplicas}</span>. The
          reconciliation loop will converge container count on its next pass.
        </p>
        <FormField
          label="Desired replicas"
          name="desiredReplicas"
          type="number"
          value={desiredReplicas}
          onChange={setDesiredReplicas}
          autoFocus
          required
          disabled={submitting}
          hint="1 to 10"
        />
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
            className="rounded-md bg-emerald-500 px-4 py-2 text-sm font-medium text-slate-950 transition hover:bg-emerald-400 disabled:opacity-50"
          >
            {submitting ? 'Scaling…' : 'Apply'}
          </button>
        </div>
      </form>
    </Modal>
  );
}
