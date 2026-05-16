import { useState } from 'react';
import Modal from './Modal.jsx';
import ErrorBanner from './ErrorBanner.jsx';
import { deleteDeployment } from '../lib/deployments.js';
import { extractErrorMessage, extractErrorDetails } from '../lib/format.js';
import { useToast } from './Toast.jsx';

export default function DeleteDeploymentDialog({ deployment, onClose, onDeleted }) {
  const toast = useToast();
  const [typed, setTyped] = useState('');
  const [error, setError] = useState(null);
  const [details, setDetails] = useState([]);
  const [submitting, setSubmitting] = useState(false);
  const matches = typed === deployment.name;

  async function handleConfirm() {
    setError(null);
    setDetails([]);
    setSubmitting(true);
    try {
      await deleteDeployment(deployment.id);
      toast.push({
        tone: 'success',
        title: 'Deployment deleted',
        message: deployment.name,
      });
      onDeleted();
    } catch (err) {
      setError(extractErrorMessage(err, 'delete failed'));
      setDetails(extractErrorDetails(err));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <Modal title={`Delete ${deployment.name}`} onClose={submitting ? () => {} : onClose} size="sm">
      <div className="space-y-4">
        <p className="text-sm text-slate-300">
          This will stop and remove every container under{' '}
          <span className="font-mono text-rose-300">{deployment.name}</span> and delete the
          deployment record. The event history is also wiped.
        </p>
        <label htmlFor="confirm-name" className="block space-y-1.5">
          <span className="text-sm text-slate-300">
            Type the deployment name to confirm:
          </span>
          <input
            id="confirm-name"
            name="confirm-name"
            type="text"
            value={typed}
            autoFocus
            disabled={submitting}
            onChange={(e) => setTyped(e.target.value)}
            placeholder={deployment.name}
            className="w-full rounded-md border border-slate-700 bg-slate-950 px-3 py-2 font-mono text-sm text-slate-100 placeholder:text-slate-700 focus:border-rose-500 focus:outline-none focus:ring-1 focus:ring-rose-500"
          />
        </label>
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
            disabled={!matches || submitting}
            className="rounded-md bg-rose-500 px-4 py-2 text-sm font-medium text-slate-950 transition hover:bg-rose-400 disabled:opacity-50 disabled:cursor-not-allowed"
          >
            {submitting ? 'Deleting…' : 'Delete deployment'}
          </button>
        </div>
      </div>
    </Modal>
  );
}
