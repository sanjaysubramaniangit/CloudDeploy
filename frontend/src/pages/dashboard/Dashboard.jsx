import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import { Card, CardContent, CardHeader } from '../../components/ui/Card';
import { Button } from '../../components/ui/Button';
import { StatusBadge } from '../../components/ui/StatusBadge';
import { Spinner } from '../../components/ui/Spinner';
import { ErrorState } from '../../components/ui/ErrorState';
import { EmptyState } from '../../components/ui/EmptyState';
import { 
  AppWindow, 
  CheckCircle2, 
  XCircle, 
  HardDrive, 
  Plus, 
  ArrowRight,
  GitCommit,
  ExternalLink,
  Calendar
} from 'lucide-react';
import api from '../../api/axios';
import './Dashboard.css';

export const Dashboard = () => {
  const { user } = useAuth();
  const navigate = useNavigate();
  const [stats, setStats] = useState(null);
  const [healthStatus, setHealthStatus] = useState('Checking...');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const fetchDashboardData = async () => {
    setLoading(true);
    setError(null);
    try {
      const [statsRes, healthRes] = await Promise.all([
        api.get('/dashboard'),
        api.get('/health').catch(() => ({ data: { status: 'DOWN' } })),
      ]);
      setStats(statsRes.data);
      setHealthStatus(healthRes.data?.status || 'DOWN');
    } catch (err) {
      console.error('Failed to load dashboard statistics', err);
      setError('Unable to retrieve dashboard statistics from the server.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchDashboardData();
  }, []);

  const formatDate = (dateString) => {
    if (!dateString) return '—';
    try {
      const date = new Date(dateString);
      return new Intl.DateTimeFormat('en-US', {
        month: 'short',
        day: 'numeric',
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
        <p className="loading-text">Loading dashboard metrics...</p>
      </div>
    );
  }

  if (error) {
    return (
      <div className="dashboard-page">
        <ErrorState
          title="Dashboard Error"
          message={error}
          onRetry={fetchDashboardData}
        />
      </div>
    );
  }

  return (
    <div className="dashboard-page">
      <div className="page-header dashboard-header">
        <div>
          <h1 className="page-title">Good day, {user?.name}</h1>
          <p className="page-description">
            Overview of your applications, deployments, and live system metrics.
          </p>
        </div>
        <div className="dashboard-quick-actions">
          <Button variant="secondary" onClick={() => navigate('/applications')}>
            View Applications
          </Button>
          <Button onClick={() => navigate('/applications/new')}>
            <Plus size={16} className="btn-icon-mr" />
            Create Application
          </Button>
        </div>
      </div>

      {/* Metrics Cards */}
      <div className="stats-grid">
        <Card className="stat-card-wrapper">
          <CardContent className="stat-card">
            <div className="stat-icon-wrapper bg-blue">
              <AppWindow size={24} className="text-blue" />
            </div>
            <div className="stat-content">
              <p className="stat-label">Total Applications</p>
              <h3 className="stat-value">{stats?.totalApplications ?? 0}</h3>
            </div>
          </CardContent>
        </Card>

        <Card className="stat-card-wrapper">
          <CardContent className="stat-card">
            <div className="stat-icon-wrapper bg-purple">
              <HardDrive size={24} className="text-purple" />
            </div>
            <div className="stat-content">
              <p className="stat-label">Total Deployments</p>
              <h3 className="stat-value">{stats?.totalDeployments ?? 0}</h3>
            </div>
          </CardContent>
        </Card>

        <Card className="stat-card-wrapper">
          <CardContent className="stat-card">
            <div className="stat-icon-wrapper bg-green">
              <CheckCircle2 size={24} className="text-green" />
            </div>
            <div className="stat-content">
              <p className="stat-label">Successful Deployments</p>
              <h3 className="stat-value">{stats?.successfulDeployments ?? 0}</h3>
            </div>
          </CardContent>
        </Card>

        <Card className="stat-card-wrapper">
          <CardContent className="stat-card">
            <div className="stat-icon-wrapper bg-red">
              <XCircle size={24} className="text-red" />
            </div>
            <div className="stat-content">
              <p className="stat-label">Failed Deployments</p>
              <h3 className="stat-value">{stats?.failedDeployments ?? 0}</h3>
            </div>
          </CardContent>
        </Card>
      </div>

      {/* Main Panels */}
      <div className="dashboard-panels-grid">
        {/* Recent Applications */}
        <Card className="dashboard-panel-card">
          <CardHeader className="panel-header-with-link">
            <span>Recent Applications</span>
            {stats?.recentApplications?.length > 0 && (
              <button 
                className="panel-view-all-link"
                onClick={() => navigate('/applications')}
              >
                <span>View all</span>
                <ArrowRight size={14} />
              </button>
            )}
          </CardHeader>
          <CardContent className="panel-card-content">
            {stats?.recentApplications && stats.recentApplications.length > 0 ? (
              <div className="dashboard-list">
                {stats.recentApplications.map((app) => (
                  <div 
                    key={app.id} 
                    className="dashboard-list-item"
                    onClick={() => navigate(`/applications/${app.id}`)}
                  >
                    <div className="list-item-main">
                      <h4 className="list-item-title">{app.name}</h4>
                      <div className="list-item-meta">
                        {app.repositoryUrl ? (
                          <span className="repo-link-preview">
                            <ExternalLink size={12} />
                            {app.repositoryUrl.replace(/^https?:\/\//, '')}
                          </span>
                        ) : (
                          <span>No repository linked</span>
                        )}
                        <span>•</span>
                        <span>{app.totalDeployments} deploy{app.totalDeployments !== 1 ? 's' : ''}</span>
                      </div>
                    </div>
                    <div className="list-item-end">
                      <StatusBadge status={app.deploymentStatus} />
                    </div>
                  </div>
                ))}
              </div>
            ) : (
              <EmptyState
                icon={AppWindow}
                title="No applications yet"
                description="Create your first application to start tracking deployments."
                actionText="Create Application"
                onAction={() => navigate('/applications/new')}
              />
            )}
          </CardContent>
        </Card>

        {/* Recent Deployments */}
        <Card className="dashboard-panel-card">
          <CardHeader className="panel-header-with-link">
            <span>Recent Deployments</span>
            <div className="system-health-pill">
              <span className={`health-dot ${healthStatus === 'UP' ? 'bg-success' : 'bg-danger'}`} />
              <span className="health-text">API {healthStatus}</span>
            </div>
          </CardHeader>
          <CardContent className="panel-card-content">
            {stats?.recentDeployments && stats.recentDeployments.length > 0 ? (
              <div className="dashboard-list">
                {stats.recentDeployments.map((dep) => (
                  <div 
                    key={dep.id} 
                    className="dashboard-list-item"
                    onClick={() => navigate(`/applications/${dep.applicationId}`)}
                  >
                    <div className="list-item-main">
                      <div className="deployment-item-header">
                        <span className="deployment-app-name">{dep.applicationName}</span>
                        <span className="deployment-version-tag">{dep.version}</span>
                        {dep.commitHash && (
                          <span className="deployment-commit-tag">
                            <GitCommit size={11} />
                            {dep.commitHash.substring(0, 7)}
                          </span>
                        )}
                      </div>
                      <p className="deployment-item-msg">
                        {dep.deploymentMessage || 'No message provided'}
                      </p>
                    </div>
                    <div className="list-item-end">
                      <StatusBadge status={dep.status} />
                      <span className="deployment-item-date">
                        <Calendar size={12} />
                        {formatDate(dep.deployedAt)}
                      </span>
                    </div>
                  </div>
                ))}
              </div>
            ) : (
              <EmptyState
                icon={HardDrive}
                title="No deployments recorded"
                description="Deployment history will appear here once recorded on an application."
              />
            )}
          </CardContent>
        </Card>
      </div>
    </div>
  );
};
