import React from 'react';
import { Clock, Loader2, CheckCircle2, XCircle, CircleSlash } from 'lucide-react';
import './StatusBadge.css';

export const StatusBadge = ({ status, className = '' }) => {
  const normalized = (status || 'OFFLINE').toUpperCase();

  const getStatusConfig = () => {
    switch (normalized) {
      case 'SUCCESS':
        return {
          icon: <CheckCircle2 size={13} aria-hidden="true" />,
          label: 'Success',
          className: 'badge-success-modern',
        };
      case 'RUNNING':
        return {
          icon: <Loader2 size={13} className="badge-spin" aria-hidden="true" />,
          label: 'Running',
          className: 'badge-running-modern',
        };
      case 'PENDING':
        return {
          icon: <Clock size={13} aria-hidden="true" />,
          label: 'Pending',
          className: 'badge-pending-modern',
        };
      case 'FAILED':
        return {
          icon: <XCircle size={13} aria-hidden="true" />,
          label: 'Failed',
          className: 'badge-failed-modern',
        };
      case 'OFFLINE':
      default:
        return {
          icon: <CircleSlash size={13} aria-hidden="true" />,
          label: 'Offline',
          className: 'badge-offline-modern',
        };
    }
  };

  const config = getStatusConfig();

  return (
    <span
      className={`status-badge-container ${config.className} ${className}`}
      role="status"
      aria-label={`Deployment status: ${config.label}`}
    >
      <span className="status-badge-icon">{config.icon}</span>
      <span className="status-badge-label">{config.label}</span>
    </span>
  );
};
