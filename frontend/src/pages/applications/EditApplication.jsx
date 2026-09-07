import React, { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { Card, CardContent, CardHeader } from '../../components/ui/Card';
import { Input } from '../../components/ui/Input';
import { Button } from '../../components/ui/Button';
import { Spinner } from '../../components/ui/Spinner';
import { ErrorState } from '../../components/ui/ErrorState';
import { useToast } from '../../context/ToastContext';
import { ArrowLeft } from 'lucide-react';
import api from '../../api/axios';
import './Applications.css';

export const EditApplication = () => {
  const { id } = useParams();
  const navigate = useNavigate();
  const { addToast } = useToast();

  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [repositoryUrl, setRepositoryUrl] = useState('');
  const [initialLoading, setInitialLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState(null);
  const [errors, setErrors] = useState({});

  const fetchApplication = async () => {
    setInitialLoading(true);
    setError(null);
    try {
      const response = await api.get(`/applications/${id}`);
      setName(response.data.name || '');
      setDescription(response.data.description || '');
      setRepositoryUrl(response.data.repositoryUrl || '');
    } catch (err) {
      console.error('Failed to load application for editing', err);
      if (err.response?.status === 404) {
        setError('Application not found.');
      } else if (err.response?.status === 403) {
        setError('You do not have permission to edit this application.');
      } else {
        setError('Unable to load application details.');
      }
    } finally {
      setInitialLoading(false);
    }
  };

  useEffect(() => {
    fetchApplication();
  }, [id]);

  const validate = () => {
    const errs = {};
    if (!name.trim()) {
      errs.name = 'Application name is required';
    } else if (name.trim().length < 2 || name.trim().length > 100) {
      errs.name = 'Application name must be between 2 and 100 characters';
    }

    if (description && description.length > 500) {
      errs.description = 'Description cannot exceed 500 characters';
    }

    if (repositoryUrl && repositoryUrl.trim()) {
      const urlPattern = /^(https?:\/\/.+|git@.+)$/;
      if (!urlPattern.test(repositoryUrl.trim())) {
        errs.repositoryUrl = 'Repository URL must be a valid HTTP/HTTPS or Git SSH URL';
      }
    }

    setErrors(errs);
    return Object.keys(errs).length === 0;
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!validate() || saving) return;

    setSaving(true);
    try {
      const payload = {
        name: name.trim(),
        description: description.trim() || null,
        repositoryUrl: repositoryUrl.trim() || null,
      };

      await api.put(`/applications/${id}`, payload);
      addToast('Application updated successfully', 'success');
      navigate(`/applications/${id}`);
    } catch (err) {
      const msg = err.response?.data?.message || 'Failed to update application';
      addToast(msg, 'error');
    } finally {
      setSaving(false);
    }
  };

  if (initialLoading) {
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
          title="Error Loading Application"
          message={error}
          onRetry={fetchApplication}
        />
      </div>
    );
  }

  return (
    <div className="applications-page">
      <div className="page-header">
        <button 
          className="btn-back-link"
          onClick={() => navigate(`/applications/${id}`)}
        >
          <ArrowLeft size={16} />
          <span>Back to Details</span>
        </button>
        <h1 className="page-title" style={{ marginTop: '0.5rem' }}>Edit Application</h1>
        <p className="page-description">
          Update application configuration and repository settings.
        </p>
      </div>

      <div className="app-form-container">
        <Card>
          <CardHeader>Update Information</CardHeader>
          <CardContent>
            <form onSubmit={handleSubmit} noValidate>
              <Input
                label="Application Name *"
                id="name"
                placeholder="e.g. Payments Gateway API"
                value={name}
                onChange={(e) => setName(e.target.value)}
                error={errors.name}
                required
                disabled={saving}
              />

              <div className="input-group">
                <label htmlFor="description" className="input-label">
                  Description (optional)
                </label>
                <textarea
                  id="description"
                  className="textarea-field"
                  rows={3}
                  placeholder="Brief summary of this service or microservice"
                  value={description}
                  onChange={(e) => setDescription(e.target.value)}
                  disabled={saving}
                />
                {errors.description && (
                  <span className="error-message">{errors.description}</span>
                )}
              </div>

              <Input
                label="Repository URL (optional)"
                id="repositoryUrl"
                placeholder="e.g. https://github.com/organization/repo.git"
                value={repositoryUrl}
                onChange={(e) => setRepositoryUrl(e.target.value)}
                error={errors.repositoryUrl}
                disabled={saving}
              />

              <div className="form-actions">
                <Button
                  variant="secondary"
                  onClick={() => navigate(`/applications/${id}`)}
                  disabled={saving}
                >
                  Cancel
                </Button>
                <Button type="submit" disabled={saving}>
                  {saving ? (
                    <span className="btn-loading-content">
                      <Spinner size={16} />
                      <span>Saving Changes...</span>
                    </span>
                  ) : (
                    'Save Changes'
                  )}
                </Button>
              </div>
            </form>
          </CardContent>
        </Card>
      </div>
    </div>
  );
};
