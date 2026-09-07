import React from 'react';
import { Loader2 } from 'lucide-react';

export const Spinner = ({ size = 24, className = '' }) => {
  return (
    <Loader2 
      size={size} 
      className={`animate-spin ${className}`} 
      style={{ animation: 'spin 1s linear infinite' }}
    />
  );
};
