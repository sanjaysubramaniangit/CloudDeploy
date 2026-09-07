import React, { useState, useEffect } from 'react';
import { Sparkles, Brain, FileCheck } from 'lucide-react';
import { ResumeUpload } from './ResumeUpload';
import { ResumeList } from './ResumeList';
import { ResumeAnalysisView } from './ResumeAnalysisView';
import api from '../../api/axios';
import './ResumeAi.css';

export const ResumeAi = () => {
  const [resumes, setResumes] = useState([]);
  const [loading, setLoading] = useState(true);
  const [activeAnalysis, setActiveAnalysis] = useState(null);
  const [activeFileName, setActiveFileName] = useState('');

  const fetchResumes = async () => {
    setLoading(true);
    try {
      const response = await api.get('/resumes');
      setResumes(response.data || []);
    } catch (err) {
      console.error('Failed to fetch resumes', err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchResumes();
  }, []);

  const handleUploadSuccess = (newResume) => {
    setResumes((prev) => [newResume, ...prev]);
  };

  const handleOpenAnalysis = (analysisData, fileName) => {
    setActiveAnalysis(analysisData);
    setActiveFileName(fileName);
  };

  const handleCloseAnalysis = () => {
    setActiveAnalysis(null);
    setActiveFileName('');
  };

  const handleDeleteSuccess = (deletedId) => {
    setResumes((prev) => prev.filter((r) => r.id !== deletedId));
  };

  return (
    <div className="resume-ai-page">
      {/* Page Header */}
      <div className="resume-page-header">
        <div className="header-badge-row">
          <span className="ai-feature-tag">
            <Sparkles size={14} className="tag-sparkle" />
            AI Intelligence Suite
          </span>
        </div>
        <div className="header-title-row">
          <div>
            <h1 className="resume-page-title">AI-Powered Resume Intelligence</h1>
            <p className="resume-page-subtitle">
              Securely ingest candidate resumes into AWS S3, parse multi-format documents (PDF, DOCX, TXT), 
              scrub sensitive credentials, and generate deep structured technical competency profiles.
            </p>
          </div>
        </div>
      </div>

      {/* Upload Section */}
      <section className="resume-section-upload" aria-label="Resume upload section">
        <ResumeUpload onUploadSuccess={handleUploadSuccess} />
      </section>

      {/* Resumes List Section */}
      <section className="resume-section-list" aria-label="Resume management section">
        <ResumeList
          resumes={resumes}
          loading={loading}
          onRefresh={fetchResumes}
          onOpenAnalysis={handleOpenAnalysis}
          onDeleteSuccess={handleDeleteSuccess}
        />
      </section>

      {/* Analysis Details Modal */}
      <ResumeAnalysisView
        isOpen={!!activeAnalysis}
        onClose={handleCloseAnalysis}
        analysis={activeAnalysis}
        resumeFileName={activeFileName}
      />
    </div>
  );
};
