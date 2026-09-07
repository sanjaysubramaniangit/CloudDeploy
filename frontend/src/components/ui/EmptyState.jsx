import React from 'react';
import { Layers } from 'lucide-react';
import { Button } from './Button';
import './EmptyState.css';

export const EmptyState = ({
  icon: Icon = Layers,
  title = 'No items found',
  description = 'Get started by creating a new entry.',
  actionText,
  onAction,
  className = '',
}) => {
  return (
    <div className={`empty-state-container ${className}`}>
      <div className="empty-state-icon-wrapper">
        <Icon size={32} className="empty-state-icon" aria-hidden="true" />
      </div>
      <h3 className="empty-state-title">{title}</h3>
      <p className="empty-state-description">{description}</p>
      {actionText && onAction && (
        <div className="empty-state-action">
          <Button onClick={onAction}>{actionText}</Button>
        </div>
      )}
    </div>
  );
};
