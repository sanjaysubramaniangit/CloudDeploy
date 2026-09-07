import React, { useState } from 'react';
import { Navigate, Link } from 'react-router-dom';
import { Cloud, Lock } from 'lucide-react';
import { useAuth } from '../../context/AuthContext';
import { Card, CardContent } from '../../components/ui/Card';
import { Input } from '../../components/ui/Input';
import { Button } from '../../components/ui/Button';
import './Auth.css';

export const Login = () => {
  const { user, login } = useAuth();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [loading, setLoading] = useState(false);

  if (user) {
    return <Navigate to="/dashboard" replace />;
  }

  const handleSubmit = async (e) => {
    e.preventDefault();
    setLoading(true);
    await login(email, password);
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
          <div className="auth-features">
            <div className="auth-feature">
              <Lock size={20} />
              <span>Secure, enterprise-grade deployments</span>
            </div>
          </div>
        </div>
      </div>
      
      <div className="auth-form-container">
        <Card className="auth-card">
          <CardContent>
            <h2 className="auth-title">Welcome back</h2>
            <p className="auth-subtitle">Enter your credentials to access your account</p>
            
            <form onSubmit={handleSubmit} className="auth-form">
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
              />
              
              <div className="auth-actions">
                <Button type="submit" fullWidth disabled={loading}>
                  {loading ? 'Signing in...' : 'Sign In'}
                </Button>
              </div>
            </form>
            
            <div className="auth-footer">
              <p>Don't have an account? <Link to="/register">Create account</Link></p>
            </div>
          </CardContent>
        </Card>
      </div>
    </div>
  );
};
