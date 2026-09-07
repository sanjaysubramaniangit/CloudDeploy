import React from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { ToastProvider } from './context/ToastContext';
import { AuthProvider } from './context/AuthContext';
import { AppLayout } from './components/layout/AppLayout';
import { Login } from './pages/auth/Login';
import { Register } from './pages/auth/Register';
import { Dashboard } from './pages/dashboard/Dashboard';
import { ApplicationsList } from './pages/applications/ApplicationsList';
import { CreateApplication } from './pages/applications/CreateApplication';
import { ApplicationDetails } from './pages/applications/ApplicationDetails';
import { EditApplication } from './pages/applications/EditApplication';
import { ResumeAi } from './pages/resume/ResumeAi';
import { JobMatch } from './pages/jobmatch/JobMatch';
import { InterviewPrep } from './pages/interview/InterviewPrep';
import './App.css';

function App() {
  return (
    <ToastProvider>
      <AuthProvider>
        <BrowserRouter>
          <Routes>
            <Route path="/" element={<Navigate to="/dashboard" replace />} />
            <Route path="/login" element={<Login />} />
            <Route path="/register" element={<Register />} />
            
            <Route element={<AppLayout />}>
              <Route path="/dashboard" element={<Dashboard />} />
              <Route path="/applications" element={<ApplicationsList />} />
              <Route path="/applications/new" element={<CreateApplication />} />
              <Route path="/applications/:id" element={<ApplicationDetails />} />
              <Route path="/applications/:id/edit" element={<EditApplication />} />
              <Route path="/resume-ai" element={<ResumeAi />} />
              <Route path="/resume" element={<Navigate to="/resume-ai" replace />} />
              <Route path="/job-match" element={<JobMatch />} />
              <Route path="/jobs" element={<Navigate to="/job-match" replace />} />
              <Route path="/interview-prep" element={<InterviewPrep />} />
              <Route path="/interview" element={<Navigate to="/interview-prep" replace />} />
              {/* Fallback for un-implemented authenticated pages */}
              <Route path="*" element={<Navigate to="/dashboard" replace />} />
            </Route>
          </Routes>
        </BrowserRouter>
      </AuthProvider>
    </ToastProvider>
  );
}

export default App;
