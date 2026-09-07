import React, { useState } from 'react';
import { User, LogOut, Settings } from 'lucide-react';
import { useAuth } from '../../context/AuthContext';
import './Layout.css';

export const Header = () => {
  const { user, logout } = useAuth();
  const [dropdownOpen, setDropdownOpen] = useState(false);

  return (
    <header className="app-header">
      <div className="header-search">
        {/* Placeholder for future search */}
      </div>
      
      <div className="header-actions">
        <div className="user-menu">
          <button 
            className="user-button"
            onClick={() => setDropdownOpen(!dropdownOpen)}
          >
            <div className="avatar">
              <User size={20} />
            </div>
            <span className="user-name">{user?.name}</span>
          </button>
          
          {dropdownOpen && (
            <div className="user-dropdown">
              <div className="dropdown-item">
                <Settings size={16} />
                <span>Settings</span>
              </div>
              <div className="dropdown-divider"></div>
              <div className="dropdown-item text-danger" onClick={logout}>
                <LogOut size={16} />
                <span>Sign out</span>
              </div>
            </div>
          )}
        </div>
      </div>
    </header>
  );
};
