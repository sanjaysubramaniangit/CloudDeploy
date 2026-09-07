import React from 'react';
import './Button.css';

export const Button = ({ 
  children, 
  variant = 'primary', 
  type = 'button', 
  fullWidth, 
  disabled, 
  onClick, 
  className = '' 
}) => {
  const classes = `btn btn-${variant} ${fullWidth ? 'w-full' : ''} ${className}`;
  return (
    <button type={type} className={classes} disabled={disabled} onClick={onClick}>
      {children}
    </button>
  );
};
