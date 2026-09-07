import React, { useState } from 'react';
import { Navigate, Link } from 'react-router-dom';
import { Cloud } from 'lucide-react';
import { useAuth } from '../../context/AuthContext';
import { Card, CardContent } from '../../components/ui/Card';
import { Input } from '../../components/ui/Input';
import { Button } from '../../components/ui/Button';
import './Auth.css';

export const Register = () => {
  const { user, register } = useAuth();
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  if (user) {
    return <Navigate to="/dashboard" replace />;
  }

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    
    if (password !== confirmPassword) {
      setError('Passwords do not match');
      return;
    }
    
    if (password.length < 8) {
      setError('Password must be at least 8 characters');
      return;
    }

    setLoading(true);
    await register(name, email, password);
    setLoading(false);
  };

  return (
    <div className="auth-page">
      <div className="auth-hero">
        <div className="auth-hero-content">
          <div className="auth-brand">
            <Cloud size={48} className="auth-brand-icon" />
            <h1>CloudDeploy AI</h1>
          </div>
          <p className="auth-tagline">AI-Powered Cloud Deployment & Intelligence Platform</p>
        </div>
      </div>
      
      <div className="auth-form-container">
        <Card className="auth-card">
          <CardContent>
            <h2 className="auth-title">Create Account</h2>
            <p className="auth-subtitle">Sign up for your new account</p>
            
            <form onSubmit={handleSubmit} className="auth-form">
              <Input 
                label="Full Name" 
                id="name" 
                value={name}
                onChange={e => setName(e.target.value)}
                placeholder="John Doe"
                required
              />
              <Input 
                label="Email" 
                id="email" 
                type="email" 
                value={email}
                onChange={e => setEmail(e.target.value)}
                placeholder="you@example.com"
                required
              />
              <Input 
                label="Password" 
                id="password" 
                type="password" 
                value={password}
                onChange={e => setPassword(e.target.value)}
                placeholder="••••••••"
                required
                error={error}
              />
              <Input 
                label="Confirm Password" 
                id="confirmPassword" 
                type="password" 
                value={confirmPassword}
                onChange={e => setConfirmPassword(e.target.value)}
                placeholder="••••••••"
                required
              />
              
              <div className="auth-actions">
                <Button type="submit" fullWidth disabled={loading}>
                  {loading ? 'Creating account...' : 'Create Account'}
                </Button>
              </div>
            </form>
            
            <div className="auth-footer">
              <p>Already have an account? <Link to="/login">Sign in</Link></p>
            </div>
          </CardContent>
        </Card>
      </div>
    </div>
  );
};
