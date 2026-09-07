import React from 'react';
import './Card.css';

export const Card = ({ children, className = '' }) => {
  return <div className={`card-component ${className}`}>{children}</div>;
};

export const CardHeader = ({ children, className = '' }) => {
  return <div className={`card-header ${className}`}>{children}</div>;
};

export const CardContent = ({ children, className = '' }) => {
  return <div className={`card-content ${className}`}>{children}</div>;
};
