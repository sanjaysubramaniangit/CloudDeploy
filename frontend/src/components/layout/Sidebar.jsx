import React from 'react';
import { NavLink } from 'react-router-dom';
import { Cloud, LayoutDashboard, AppWindow, HardDrive, FileText, Briefcase, MessageSquare, Shield, Activity } from 'lucide-react';
import './Layout.css';

export const Sidebar = () => {
  return (
    <aside className="sidebar">
      <div className="sidebar-brand">
        <Cloud className="brand-icon" size={28} />
        <span className="brand-text">CloudDeploy AI</span>
      </div>
      
      <nav className="sidebar-nav">
        <NavLink to="/dashboard" className={({ isActive }) => `nav-item ${isActive ? 'active' : ''}`}>
          <LayoutDashboard size={20} />
          <span>Dashboard</span>
        </NavLink>
        <NavLink to="/applications" className={({ isActive }) => `nav-item ${isActive ? 'active' : ''}`}>
          <AppWindow size={20} />
          <span>Applications</span>
        </NavLink>
        <NavLink to="/deployments" className="nav-item">
          <HardDrive size={20} />
          <span>Deployments</span>
        </NavLink>
        <div className="nav-section">AI Tools</div>
        <NavLink to="/resume-ai" className={({ isActive }) => `nav-item ${isActive ? 'active' : ''}`}>
          <FileText size={20} />
          <span>Resume AI</span>
        </NavLink>
        <NavLink to="/job-match" className={({ isActive }) => `nav-item ${isActive ? 'active' : ''}`}>
          <Briefcase size={20} />
          <span>Job Match</span>
        </NavLink>
        <NavLink to="/interview-prep" className={({ isActive }) => `nav-item ${isActive ? 'active' : ''}`}>
          <MessageSquare size={20} />
          <span>Interview Prep</span>
        </NavLink>
        <NavLink to="/assistant" className={({ isActive }) => `nav-item ${isActive ? 'active' : ''}`}>
          <Shield size={20} />
          <span>Cloud Assistant</span>
        </NavLink>
      </nav>
    </aside>
  );
};
