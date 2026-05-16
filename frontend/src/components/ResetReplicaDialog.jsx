import { useState } from 'react';
import Modal from './Modal.jsx';
import ErrorBanner from './ErrorBanner.jsx';
import { resetReplica } from '../lib/deployments.js';
import { extractErrorMessage, extractErrorDetails } from '../lib/format.js';

export default function ResetReplicaDialog({ deploymentId, replica, onClose, onResetDone }) {
  const [error, setError] = useState(null);
  const [details, setDetails] = useState([]);
  const [submitting, setSubmitting] = useState(false);

  async function handleConfirm() {
    setError(null);
    setDetails([]);
    setSubmitting(true);
    try {
      const updated = await resetReplica(deploymentId, replica.replicaIndex);
      onResetDone(updated);
      onClose();
    } catch (err) {
      setError(extractErrorMessage(err, 'reset failed'));
      setDetails(extractErrorDetails(err));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <Modal title={`Reset replica #${replica.replicaIndex}`} onClose={submitting ? () => {} : onClose} size="sm">
      <div className="space-y-4">
        <p className="text-sm text-slate-300">
          This replica is in <span className="font-mono text-amber-300">CRASHLOOP_BACKOFF</span>.
          Reset clears the failure window and restart count and flips the status back to{' '}
          <span className="font-mono">PENDING</span>. The reconciliation loop picks it up on the
          next pass and starts a fresh container.
        </p>
        <p className="text-xs text-slate-500">
          If the underlying problem is not fixed, the replica will trip back into CrashLoopBackOff
          within a few minutes.
        </p>
        <ErrorBanner message={error} details={details} />
        <div className="flex items-center justify-end gap-2">
          <button
            type="button"
            onClick={onClose}
            disabled={submitting}
            className="rounded-md border border-slate-700 px-4 py-2 text-sm text-slate-300 transition hover:border-slate-600 hover:bg-slate-900 disabled:opacity-50"
          >
            Cancel
          </button>
          <button
            type="button"
            onClick={handleConfirm}
            disabled={submitting}
            className="rounded-md bg-amber-500 px-4 py-2 text-sm font-medium text-slate-950 transition hover:bg-amber-400 disabled:opacity-50"
          >
            {submitting ? 'Resetting…' : 'Reset replica'}
          </button>
        </div>
      </div>
    </Modal>
  );
}
