import React, { useState, useEffect } from 'react';
import { X, Info } from 'lucide-react';
import { Input } from '../ui/Input';
import { Button } from '../ui/Button';
import { Spinner } from '../ui/Spinner';
import { useToast } from '../../context/ToastContext';
import api from '../../api/axios';
import './RecordDeploymentModal.css';

export const RecordDeploymentModal = ({
  isOpen,
  applicationId,
  applicationName,
  onClose,
  onSuccess,
}) => {
  const { addToast } = useToast();
  const [version, setVersion] = useState('');
  const [commitHash, setCommitHash] = useState('');
  const [status, setStatus] = useState('SUCCESS');
  const [deploymentMessage, setDeploymentMessage] = useState('');
  const [loading, setLoading] = useState(false);
  const [errors, setErrors] = useState({});

  useEffect(() => {
    if (isOpen) {
      setVersion('');
      setCommitHash('');
      setStatus('SUCCESS');
      setDeploymentMessage('');
      setErrors({});
      document.body.style.overflow = 'hidden';
    }
    return () => {
      document.body.style.overflow = '';
    };
  }, [isOpen]);

  if (!isOpen) return null;

  const validate = () => {
    const errs = {};
    if (!version.trim()) {
      errs.version = 'Version is required (e.g. v1.0.0)';
    } else if (version.length > 50) {
      errs.version = 'Version must be 50 characters or less';
    }
    if (commitHash && commitHash.length > 40) {
      errs.commitHash = 'Commit hash must be 40 characters or less';
    }
    if (deploymentMessage && deploymentMessage.length > 1000) {
      errs.deploymentMessage = 'Message must be 1000 characters or less';
    }
    setErrors(errs);
    return Object.keys(errs).length === 0;
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!validate() || loading) return;

    setLoading(true);
    try {
      const payload = {
        version: version.trim(),
        commitHash: commitHash.trim() || null,
        status,
        deploymentMessage: deploymentMessage.trim() || null,
      };

      const response = await api.post(`/applications/${applicationId}/deployments`, payload);
      addToast(`Deployment record ${response.data.version} created successfully`, 'success');
      onSuccess(response.data);
      onClose();
    } catch (err) {
      const msg = err.response?.data?.message || 'Failed to record deployment';
      addToast(msg, 'error');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="modal-overlay" role="dialog" aria-modal="true" aria-labelledby="record-deployment-title">
      <div className="modal-backdrop" onClick={!loading ? onClose : undefined} />
      <div className="modal-card deployment-modal-card">
        <div className="modal-header">
          <div>
            <h3 id="record-deployment-title" className="modal-title">
              Record Deployment
            </h3>
            <p className="modal-subtitle">
              for <strong>{applicationName}</strong>
            </p>
          </div>
          <button
            className="modal-close-btn"
            onClick={onClose}
            disabled={loading}
            aria-label="Close modal"
          >
            <X size={18} />
          </button>
        </div>

        <div className="deployment-notice-banner">
          <Info size={16} className="notice-icon" />
          <div className="notice-text">
            <strong>Audit History Record:</strong> This logs a deployment state in your history table. It does not trigger automated AWS infrastructure provisioning.
          </div>
        </div>

        <form onSubmit={handleSubmit} className="deployment-form">
          <Input
            label="Version *"
            id="version"
            placeholder="e.g. v1.0.0"
            value={version}
            onChange={(e) => setVersion(e.target.value)}
            error={errors.version}
            required
            disabled={loading}
          />

          <Input
            label="Commit Hash (optional)"
            id="commitHash"
            placeholder="e.g. 7a8b9c0"
            value={commitHash}
            onChange={(e) => setCommitHash(e.target.value)}
            error={errors.commitHash}
            disabled={loading}
          />

          <div className="input-group">
            <label htmlFor="status" className="input-label">
              Deployment Status *
            </label>
            <select
              id="status"
              className="select-field"
              value={status}
              onChange={(e) => setStatus(e.target.value)}
              disabled={loading}
            >
              <option value="SUCCESS">SUCCESS</option>
              <option value="RUNNING">RUNNING</option>
              <option value="PENDING">PENDING</option>
              <option value="FAILED">FAILED</option>
            </select>
          </div>

          <div className="input-group">
            <label htmlFor="deploymentMessage" className="input-label">
              Deployment Message (optional)
            </label>
            <textarea
              id="deploymentMessage"
              className="textarea-field"
              rows={3}
              placeholder="Release notes, fixes, or deployment summary"
              value={deploymentMessage}
              onChange={(e) => setDeploymentMessage(e.target.value)}
              disabled={loading}
            />
            {errors.deploymentMessage && (
              <span className="error-message">{errors.deploymentMessage}</span>
            )}
          </div>

          <div className="modal-actions">
            <Button variant="secondary" onClick={onClose} disabled={loading}>
              Cancel
            </Button>
            <Button type="submit" disabled={loading}>
              {loading ? (
                <span className="btn-loading-content">
                  <Spinner size={16} />
                  <span>Recording...</span>
                </span>
              ) : (
                'Save Deployment Record'
              )}
            </Button>
          </div>
        </form>
      </div>
    </div>
  );
};
