import React from 'react';
import { AlertOctagon, RotateCw } from 'lucide-react';
import { Button } from './Button';
import './ErrorState.css';

export const ErrorState = ({
  title = 'Failed to load data',
  message = 'An unexpected error occurred while communicating with the server.',
  onRetry,
  className = '',
}) => {
  return (
    <div className={`error-state-container ${className}`} role="alert">
      <div className="error-state-icon-wrapper">
        <AlertOctagon size={28} className="text-danger" />
      </div>
      <h3 className="error-state-title">{title}</h3>
      <p className="error-state-message">{message}</p>
      {onRetry && (
        <div className="error-state-action">
          <Button variant="secondary" onClick={onRetry} className="btn-retry">
            <RotateCw size={14} className="icon-mr" />
            <span>Try Again</span>
          </Button>
        </div>
      )}
    </div>
  );
};
