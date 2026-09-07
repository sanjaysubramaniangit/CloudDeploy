import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { Card, CardContent } from '../../components/ui/Card';
import { Button } from '../../components/ui/Button';
import { StatusBadge } from '../../components/ui/StatusBadge';
import { Spinner } from '../../components/ui/Spinner';
import { EmptyState } from '../../components/ui/EmptyState';
import { ErrorState } from '../../components/ui/ErrorState';
import { ConfirmDialog } from '../../components/ui/ConfirmDialog';
import { useToast } from '../../context/ToastContext';
import { 
  AppWindow, 
  Plus, 
  ExternalLink, 
  Calendar, 
  Eye, 
  Edit3, 
  Trash2,
  HardDrive
} from 'lucide-react';
import api from '../../api/axios';
import './Applications.css';

export const ApplicationsList = () => {
  const navigate = useNavigate();
  const { addToast } = useToast();
  const [applications, setApplications] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  // Deletion modal state
  const [deleteTarget, setDeleteTarget] = useState(null);
  const [deleteLoading, setDeleteLoading] = useState(false);

  const fetchApplications = async () => {
    setLoading(true);
    setError(null);
    try {
      const response = await api.get('/applications');
      setApplications(response.data);
    } catch (err) {
      console.error('Error fetching applications:', err);
      setError('Unable to load applications from the server.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchApplications();
  }, []);

  const handleDeleteConfirm = async () => {
    if (!deleteTarget) return;
    setDeleteLoading(true);
    try {
      await api.delete(`/applications/${deleteTarget.id}`);
      addToast(`Application "${deleteTarget.name}" deleted successfully`, 'success');
      setApplications((prev) => prev.filter((app) => app.id !== deleteTarget.id));
      setDeleteTarget(null);
    } catch (err) {
      const msg = err.response?.data?.message || 'Failed to delete application';
      addToast(msg, 'error');
    } finally {
      setDeleteLoading(false);
    }
  };

  const formatDate = (dateString) => {
    if (!dateString) return '—';
    try {
      const date = new Date(dateString);
      return new Intl.DateTimeFormat('en-US', {
        month: 'short',
        day: 'numeric',
        year: 'numeric',
      }).format(date);
    } catch {
      return dateString;
    }
  };

  return (
    <div className="applications-page">
      <div className="page-header header-with-action">
        <div>
          <h1 className="page-title">Applications</h1>
          <p className="page-description">Manage your applications and deployment history.</p>
        </div>
        <Button onClick={() => navigate('/applications/new')}>
          <Plus size={16} className="btn-icon-mr" />
          New Application
        </Button>
      </div>

      {loading ? (
        <div className="page-loading-center">
          <Spinner size={36} />
          <p className="loading-text">Loading applications...</p>
        </div>
      ) : error ? (
        <ErrorState
          title="Failed to load applications"
          message={error}
          onRetry={fetchApplications}
        />
      ) : applications.length === 0 ? (
        <Card>
          <CardContent>
            <EmptyState
              icon={AppWindow}
              title="No applications yet"
              description="Create your first application to begin tracking deployments and managing services."
              actionText="Create Application"
              onAction={() => navigate('/applications/new')}
            />
          </CardContent>
        </Card>
      ) : (
        <Card className="applications-card">
          <div className="table-responsive">
            <table className="applications-table">
              <thead>
                <tr>
                  <th>Application</th>
                  <th>Status</th>
                  <th>Repository</th>
                  <th>Deployments</th>
                  <th>Last Updated</th>
                  <th className="th-actions">Actions</th>
                </tr>
              </thead>
              <tbody>
                {applications.map((app) => (
                  <tr key={app.id} className="application-row">
                    <td className="app-primary-cell">
                      <span 
                        className="app-name-link"
                        onClick={() => navigate(`/applications/${app.id}`)}
                      >
                        {app.name}
                      </span>
                      {app.description && (
                        <span className="app-desc-preview">{app.description}</span>
                      )}
                    </td>
                    <td>
                      <StatusBadge status={app.deploymentStatus} />
                    </td>
                    <td>
                      {app.repositoryUrl ? (
                        <a
                          href={app.repositoryUrl}
                          target="_blank"
                          rel="noopener noreferrer"
                          className="repo-url-link"
                          onClick={(e) => e.stopPropagation()}
                        >
                          <ExternalLink size={12} />
                          <span>{app.repositoryUrl.replace(/^https?:\/\//, '')}</span>
                        </a>
                      ) : (
                        <span className="text-muted">—</span>
                      )}
                    </td>
                    <td>
                      <span className="deploy-count-pill">
                        <HardDrive size={13} />
                        <span>{app.totalDeployments}</span>
                      </span>
                    </td>
                    <td className="date-cell">
                      <span className="date-wrapper">
                        <Calendar size={13} className="date-icon" />
                        {formatDate(app.updatedAt)}
                      </span>
                    </td>
                    <td className="td-actions">
                      <div className="action-buttons-group">
                        <button
                          className="btn-icon-action"
                          title="View Details"
                          aria-label={`View ${app.name}`}
                          onClick={() => navigate(`/applications/${app.id}`)}
                        >
                          <Eye size={16} />
                        </button>
                        <button
                          className="btn-icon-action"
                          title="Edit Application"
                          aria-label={`Edit ${app.name}`}
                          onClick={() => navigate(`/applications/${app.id}/edit`)}
                        >
                          <Edit3 size={16} />
                        </button>
                        <button
                          className="btn-icon-action btn-icon-danger"
                          title="Delete Application"
                          aria-label={`Delete ${app.name}`}
                          onClick={() => setDeleteTarget(app)}
                        >
                          <Trash2 size={16} />
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </Card>
      )}

      {/* Delete Confirmation Modal */}
      <ConfirmDialog
        isOpen={Boolean(deleteTarget)}
        title="Delete Application?"
        message={`This will permanently remove "${deleteTarget?.name}" and its entire deployment history. This action cannot be undone.`}
        confirmText="Delete"
        confirmVariant="danger"
        isLoading={deleteLoading}
        onConfirm={handleDeleteConfirm}
        onCancel={() => !deleteLoading && setDeleteTarget(null)}
      />
    </div>
  );
};
