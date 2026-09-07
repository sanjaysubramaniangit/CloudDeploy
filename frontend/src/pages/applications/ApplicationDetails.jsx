import React, { useState, useEffect } from 'react';
import { useParams, useNavigate, Link } from 'react-router-dom';
import { Card, CardContent, CardHeader } from '../../components/ui/Card';
import { Button } from '../../components/ui/Button';
import { StatusBadge } from '../../components/ui/StatusBadge';
import { Spinner } from '../../components/ui/Spinner';
import { EmptyState } from '../../components/ui/EmptyState';
import { ErrorState } from '../../components/ui/ErrorState';
import { ConfirmDialog } from '../../components/ui/ConfirmDialog';
import { DeploymentTable } from '../../components/deployments/DeploymentTable';
import { RecordDeploymentModal } from '../../components/deployments/RecordDeploymentModal';
import { ApplicationFilesSection } from '../../components/files/ApplicationFilesSection';
import { useToast } from '../../context/ToastContext';
import { 
  ArrowLeft, 
  Edit3, 
  Trash2, 
  Plus, 
  ExternalLink, 
  Calendar, 
  Layers,
  HardDrive
} from 'lucide-react';
import api from '../../api/axios';
import './Applications.css';

export const ApplicationDetails = () => {
  const { id } = useParams();
  const navigate = useNavigate();
  const { addToast } = useToast();

  const [application, setApplication] = useState(null);
  const [deployments, setDeployments] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  // Modals state
  const [isRecordModalOpen, setIsRecordModalOpen] = useState(false);
  const [isDeleteModalOpen, setIsDeleteModalOpen] = useState(false);
  const [deleteLoading, setDeleteLoading] = useState(false);

  const fetchApplicationData = async () => {
    setLoading(true);
    setError(null);
    try {
      const [appRes, depRes] = await Promise.all([
        api.get(`/applications/${id}`),
        api.get(`/applications/${id}/deployments`),
      ]);
      setApplication(appRes.data);
      setDeployments(depRes.data);
    } catch (err) {
      console.error('Failed to load application details', err);
      if (err.response?.status === 404) {
        setError('Application not found. It may have been removed.');
      } else if (err.response?.status === 403) {
        setError('You do not have permission to view this application.');
      } else {
        setError('An unexpected error occurred while loading application details.');
      }
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchApplicationData();
  }, [id]);

  const handleDeleteConfirm = async () => {
    setDeleteLoading(true);
    try {
      await api.delete(`/applications/${id}`);
      addToast(`Application "${application.name}" deleted`, 'success');
      navigate('/applications');
    } catch (err) {
      const msg = err.response?.data?.message || 'Failed to delete application';
      addToast(msg, 'error');
      setDeleteLoading(false);
    }
  };

  const handleDeploymentSuccess = (newDeployment) => {
    setDeployments((prev) => [newDeployment, ...prev]);
    // update application status matching latest deployment
    setApplication((prev) => ({
      ...prev,
      deploymentStatus: newDeployment.status,
      totalDeployments: (prev.totalDeployments || 0) + 1,
    }));
  };

  const formatDate = (dateString) => {
    if (!dateString) return '—';
    try {
      const date = new Date(dateString);
      return new Intl.DateTimeFormat('en-US', {
        month: 'short',
        day: 'numeric',
        year: 'numeric',
        hour: '2-digit',
        minute: '2-digit',
      }).format(date);
    } catch {
      return dateString;
    }
  };

  if (loading) {
    return (
      <div className="page-loading-center">
        <Spinner size={36} />
        <p className="loading-text">Loading application details...</p>
      </div>
    );
  }

  if (error) {
    return (
      <div className="applications-page">
        <button 
          className="btn-back-link"
          onClick={() => navigate('/applications')}
        >
          <ArrowLeft size={16} />
          <span>Back to Applications</span>
        </button>
        <ErrorState
          title="Error"
          message={error}
          onRetry={fetchApplicationData}
        />
      </div>
    );
  }

  return (
    <div className="applications-page">
      <div className="page-header">
        <button 
          className="btn-back-link"
          onClick={() => navigate('/applications')}
        >
          <ArrowLeft size={16} />
          <span>Back to Applications</span>
        </button>

        <div className="detail-header" style={{ marginTop: '0.75rem' }}>
          <div>
            <div className="detail-title-group">
              <h1 className="page-title" style={{ margin: 0 }}>
                {application?.name}
              </h1>
              <StatusBadge status={application?.deploymentStatus} />
            </div>
            <p className="page-description">
              {application?.description || 'No description provided for this application.'}
            </p>
          </div>

          <div className="detail-actions-group">
            <Button
              variant="secondary"
              onClick={() => navigate(`/applications/${id}/edit`)}
            >
              <Edit3 size={15} className="btn-icon-mr" />
              Edit Application
            </Button>
            <Button onClick={() => setIsRecordModalOpen(true)}>
              <Plus size={16} className="btn-icon-mr" />
              Record Deployment
            </Button>
            <Button
              variant="secondary"
              className="btn-icon-danger"
              onClick={() => setIsDeleteModalOpen(true)}
              title="Delete Application"
            >
              <Trash2 size={16} />
            </Button>
          </div>
        </div>
      </div>

      {/* Overview Metadata Cards */}
      <div className="detail-grid">
        <Card>
          <CardHeader>Repository Configuration</CardHeader>
          <CardContent>
            <div className="meta-field-label">Repository URL</div>
            {application?.repositoryUrl ? (
              <a
                href={application.repositoryUrl}
                target="_blank"
                rel="noopener noreferrer"
                className="repo-url-link"
                style={{ maxWidth: '100%' }}
              >
                <ExternalLink size={14} />
                <span>{application.repositoryUrl}</span>
              </a>
            ) : (
              <p className="meta-field-value text-muted">No repository configured</p>
            )}
          </CardContent>
        </Card>

        <Card>
          <CardHeader>Application Timeline</CardHeader>
          <CardContent>
            <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
              <div>
                <div className="meta-field-label">Created Date</div>
                <p className="meta-field-value">
                  <Calendar size={13} style={{ display: 'inline', marginRight: '0.35rem' }} />
                  {formatDate(application?.createdAt)}
                </p>
              </div>
              <div>
                <div className="meta-field-label">Last Updated</div>
                <p className="meta-field-value">
                  <Calendar size={13} style={{ display: 'inline', marginRight: '0.35rem' }} />
                  {formatDate(application?.updatedAt)}
                </p>
              </div>
            </div>
          </CardContent>
        </Card>
      </div>

      {/* Deployment History Section */}
      <div className="section-header-row">
        <div>
          <h2 className="section-title">Deployment History</h2>
          <p className="page-description" style={{ margin: 0 }}>
            Log of recorded releases and execution states.
          </p>
        </div>
        <Button 
          variant="secondary" 
          onClick={() => setIsRecordModalOpen(true)}
        >
          <Plus size={14} className="btn-icon-mr" />
          Record Deployment
        </Button>
      </div>

      <Card>
        {deployments.length === 0 ? (
          <CardContent>
            <EmptyState
              icon={HardDrive}
              title="No deployment history yet"
              description="Record your first deployment to build deployment history and track software releases."
              actionText="Record First Deployment"
              onAction={() => setIsRecordModalOpen(true)}
            />
          </CardContent>
        ) : (
          <DeploymentTable deployments={deployments} />
        )}
      </Card>

      {/* Files & Artifacts Section */}
      <ApplicationFilesSection applicationId={id} />

      {/* Record Deployment Modal */}
      <RecordDeploymentModal
        isOpen={isRecordModalOpen}
        applicationId={id}
        applicationName={application?.name}
        onClose={() => setIsRecordModalOpen(false)}
        onSuccess={handleDeploymentSuccess}
      />

      {/* Delete Application Dialog */}
      <ConfirmDialog
        isOpen={isDeleteModalOpen}
        title="Delete Application?"
        message={`This will permanently delete "${application?.name}" and all ${deployments.length} deployment records. This action cannot be reversed.`}
        confirmText="Delete"
        confirmVariant="danger"
        isLoading={deleteLoading}
        onConfirm={handleDeleteConfirm}
        onCancel={() => !deleteLoading && setIsDeleteModalOpen(false)}
      />
    </div>
  );
};
