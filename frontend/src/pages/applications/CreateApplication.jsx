import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Card, CardContent, CardHeader } from '../../components/ui/Card';
import { Input } from '../../components/ui/Input';
import { Button } from '../../components/ui/Button';
import { Spinner } from '../../components/ui/Spinner';
import { useToast } from '../../context/ToastContext';
import { ArrowLeft } from 'lucide-react';
import api from '../../api/axios';
import './Applications.css';

export const CreateApplication = () => {
  const navigate = useNavigate();
  const { addToast } = useToast();

  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [repositoryUrl, setRepositoryUrl] = useState('');
  const [loading, setLoading] = useState(false);
  const [errors, setErrors] = useState({});

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

    if (repositoryUrl.trim()) {
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
    if (!validate() || loading) return;

    setLoading(true);
    try {
      const payload = {
        name: name.trim(),
        description: description.trim() || null,
        repositoryUrl: repositoryUrl.trim() || null,
      };

      const response = await api.post('/applications', payload);
      addToast(`Application "${response.data.name}" created successfully`, 'success');
      navigate(`/applications/${response.data.id}`);
    } catch (err) {
      const msg = err.response?.data?.message || 'Failed to create application';
      addToast(msg, 'error');
    } finally {
      setLoading(false);
    }
  };

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
        <h1 className="page-title" style={{ marginTop: '0.5rem' }}>Create New Application</h1>
        <p className="page-description">
          Define your application service and connect your version control repository.
        </p>
      </div>

      <div className="app-form-container">
        <Card>
          <CardHeader>Application Details</CardHeader>
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
                disabled={loading}
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
                  disabled={loading}
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
                disabled={loading}
              />

              <div className="form-actions">
                <Button
                  variant="secondary"
                  onClick={() => navigate('/applications')}
                  disabled={loading}
                >
                  Cancel
                </Button>
                <Button type="submit" disabled={loading}>
                  {loading ? (
                    <span className="btn-loading-content">
                      <Spinner size={16} />
                      <span>Creating...</span>
                    </span>
                  ) : (
                    'Create Application'
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
