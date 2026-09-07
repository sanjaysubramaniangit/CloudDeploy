import React from 'react';
import { GitCommit, Calendar } from 'lucide-react';
import { StatusBadge } from '../ui/StatusBadge';
import './DeploymentTable.css';

export const DeploymentTable = ({ deployments = [] }) => {
  if (!deployments || deployments.length === 0) {
    return null;
  }

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

  return (
    <div className="table-responsive">
      <table className="deployment-table">
        <thead>
          <tr>
            <th>Version</th>
            <th>Commit</th>
            <th>Status</th>
            <th>Message</th>
            <th>Deployed At</th>
          </tr>
        </thead>
        <tbody>
          {deployments.map((dep) => (
            <tr key={dep.id} className="deployment-row">
              <td className="version-cell">
                <span className="version-tag">{dep.version}</span>
              </td>
              <td>
                {dep.commitHash ? (
                  <span className="commit-hash-pill">
                    <GitCommit size={12} className="commit-icon" />
                    <code>{dep.commitHash.substring(0, 7)}</code>
                  </span>
                ) : (
                  <span className="text-muted">—</span>
                )}
              </td>
              <td>
                <StatusBadge status={dep.status} />
              </td>
              <td className="message-cell">
                <span className="message-text" title={dep.deploymentMessage}>
                  {dep.deploymentMessage || <span className="text-muted">No message provided</span>}
                </span>
              </td>
              <td className="date-cell">
                <span className="date-wrapper">
                  <Calendar size={13} className="date-icon" />
                  {formatDate(dep.deployedAt)}
                </span>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
};
