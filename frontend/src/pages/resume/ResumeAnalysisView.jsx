import React, { useState, useEffect } from 'react';
import {
  X,
  Sparkles,
  CheckCircle2,
  AlertTriangle,
  TrendingUp,
  Code2,
  Layers,
  Cloud,
  Database,
  Cpu,
  Award,
  BookOpen,
  ChevronDown,
  ChevronUp,
  Copy,
  Check,
  FileText
} from 'lucide-react';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import './ResumeAi.css';

export const ResumeAnalysisView = ({ isOpen, onClose, analysis, resumeFileName }) => {
  const [showRawJson, setShowRawJson] = useState(false);
  const [copied, setCopied] = useState(false);

  useEffect(() => {
    const handleKeyDown = (e) => {
      if (e.key === 'Escape' && isOpen) {
        onClose();
      }
    };
    if (isOpen) {
      document.body.style.overflow = 'hidden';
      window.addEventListener('keydown', handleKeyDown);
    }
    return () => {
      document.body.style.overflow = '';
      window.removeEventListener('keydown', handleKeyDown);
    };
  }, [isOpen, onClose]);

  if (!isOpen || !analysis) return null;

  const handleCopyJson = () => {
    if (analysis.rawJson) {
      navigator.clipboard.writeText(analysis.rawJson);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    }
  };

  const formatDate = (dateStr) => {
    if (!dateStr) return 'Just now';
    try {
      return new Intl.DateTimeFormat('en-US', {
        month: 'short',
        day: 'numeric',
        year: 'numeric',
        hour: '2-digit',
        minute: '2-digit',
      }).format(new Date(dateStr));
    } catch {
      return dateStr;
    }
  };

  return (
    <div className="modal-overlay" role="dialog" aria-modal="true" aria-labelledby="analysis-title">
      <div className="modal-backdrop" onClick={onClose} />
      <div className="analysis-modal-container">
        
        {/* Modal Header */}
        <div className="analysis-modal-header">
          <div className="analysis-header-info">
            <div className="analysis-icon-badge">
              <Sparkles size={22} className="text-primary" />
            </div>
            <div>
              <h2 id="analysis-title" className="analysis-title">Resume Intelligence Analysis</h2>
              <div className="analysis-meta">
                <span className="analysis-filename">
                  <FileText size={14} />
                  {resumeFileName || analysis.fileName || 'Candidate Resume'}
                </span>
                <span className="analysis-meta-sep">•</span>
                <span className="analysis-date">Analyzed: {formatDate(analysis.analyzedAt)}</span>
                {analysis.aiProvider && (
                  <>
                    <span className="analysis-meta-sep">•</span>
                    <Badge variant="info">
                      {analysis.aiProvider} ({analysis.aiModel || 'default'})
                    </Badge>
                  </>
                )}
              </div>
            </div>
          </div>
          <button
            className="modal-close-btn"
            onClick={onClose}
            aria-label="Close analysis dialog"
          >
            <X size={20} />
          </button>
        </div>

        {/* Modal Body */}
        <div className="analysis-modal-body">
          
          {/* Executive Summary Card */}
          <div className="analysis-summary-card">
            <h3 className="section-title">
              <Award size={18} className="text-primary" />
              Executive Summary
            </h3>
            <p className="summary-text">{analysis.summary || 'No summary available.'}</p>
          </div>

          {/* Categorized Skills Grid */}
          <div className="skills-grid-container">
            <h3 className="section-title">
              <Code2 size={18} className="text-primary" />
              Categorized Technical Competencies
            </h3>
            <div className="skills-categories-grid">
              
              {/* Programming Languages */}
              <div className="skill-category-card">
                <div className="category-header">
                  <Code2 size={16} className="category-icon text-indigo" />
                  <h4>Programming Languages</h4>
                </div>
                <div className="skills-tag-list">
                  {analysis.programmingLanguages && analysis.programmingLanguages.length > 0 ? (
                    analysis.programmingLanguages.map((lang, idx) => (
                      <span key={idx} className="skill-tag skill-tag-indigo">{lang}</span>
                    ))
                  ) : (
                    <span className="empty-tag-text">None detected</span>
                  )}
                </div>
              </div>

              {/* Frameworks */}
              <div className="skill-category-card">
                <div className="category-header">
                  <Layers size={16} className="category-icon text-violet" />
                  <h4>Frameworks & Libraries</h4>
                </div>
                <div className="skills-tag-list">
                  {analysis.frameworks && analysis.frameworks.length > 0 ? (
                    analysis.frameworks.map((fw, idx) => (
                      <span key={idx} className="skill-tag skill-tag-violet">{fw}</span>
                    ))
                  ) : (
                    <span className="empty-tag-text">None detected</span>
                  )}
                </div>
              </div>

              {/* Cloud Technologies */}
              <div className="skill-category-card">
                <div className="category-header">
                  <Cloud size={16} className="category-icon text-emerald" />
                  <h4>Cloud Platforms & Services</h4>
                </div>
                <div className="skills-tag-list">
                  {analysis.cloudTechnologies && analysis.cloudTechnologies.length > 0 ? (
                    analysis.cloudTechnologies.map((cloud, idx) => (
                      <span key={idx} className="skill-tag skill-tag-emerald">{cloud}</span>
                    ))
                  ) : (
                    <span className="empty-tag-text">None detected</span>
                  )}
                </div>
              </div>

              {/* Databases */}
              <div className="skill-category-card">
                <div className="category-header">
                  <Database size={16} className="category-icon text-amber" />
                  <h4>Databases & Caching</h4>
                </div>
                <div className="skills-tag-list">
                  {analysis.databases && analysis.databases.length > 0 ? (
                    analysis.databases.map((db, idx) => (
                      <span key={idx} className="skill-tag skill-tag-amber">{db}</span>
                    ))
                  ) : (
                    <span className="empty-tag-text">None detected</span>
                  )}
                </div>
              </div>

              {/* DevOps & Tools */}
              <div className="skill-category-card">
                <div className="category-header">
                  <Cpu size={16} className="category-icon text-cyan" />
                  <h4>DevOps, CI/CD & Infra</h4>
                </div>
                <div className="skills-tag-list">
                  {analysis.devopsTools && analysis.devopsTools.length > 0 ? (
                    analysis.devopsTools.map((tool, idx) => (
                      <span key={idx} className="skill-tag skill-tag-cyan">{tool}</span>
                    ))
                  ) : (
                    <span className="empty-tag-text">None detected</span>
                  )}
                </div>
              </div>

              {/* All Technical Skills */}
              <div className="skill-category-card">
                <div className="category-header">
                  <Award size={16} className="category-icon text-blue" />
                  <h4>Additional Technical Skills</h4>
                </div>
                <div className="skills-tag-list">
                  {analysis.technicalSkills && analysis.technicalSkills.length > 0 ? (
                    analysis.technicalSkills.map((skill, idx) => (
                      <span key={idx} className="skill-tag skill-tag-blue">{skill}</span>
                    ))
                  ) : (
                    <span className="empty-tag-text">None detected</span>
                  )}
                </div>
              </div>

            </div>
          </div>

          {/* Highlights & Achievements */}
          {analysis.experienceHighlights && analysis.experienceHighlights.length > 0 && (
            <div className="analysis-section">
              <h3 className="section-title">
                <TrendingUp size={18} className="text-primary" />
                Key Experience Highlights
              </h3>
              <ul className="highlights-list">
                {analysis.experienceHighlights.map((hl, idx) => (
                  <li key={idx} className="highlight-item">
                    <span className="highlight-bullet">•</span>
                    <span>{hl}</span>
                  </li>
                ))}
              </ul>
            </div>
          )}

          {/* Strengths & Areas to Improve Columns */}
          <div className="strengths-weaknesses-grid">
            
            {/* Strengths */}
            <div className="analysis-eval-card card-strengths">
              <div className="eval-card-header">
                <CheckCircle2 size={18} className="text-emerald" />
                <h4>Demonstrated Strengths</h4>
              </div>
              <ul className="eval-list">
                {analysis.strengths && analysis.strengths.length > 0 ? (
                  analysis.strengths.map((s, idx) => (
                    <li key={idx}>
                      <CheckCircle2 size={14} className="text-emerald list-icon" />
                      <span>{s}</span>
                    </li>
                  ))
                ) : (
                  <li className="empty-eval-item">No specific strengths noted</li>
                )}
              </ul>
            </div>

            {/* Areas to Improve */}
            <div className="analysis-eval-card card-improvements">
              <div className="eval-card-header">
                <AlertTriangle size={18} className="text-amber" />
                <h4>Areas for Growth & Missing Skills</h4>
              </div>
              <ul className="eval-list">
                {analysis.areasToImprove && analysis.areasToImprove.length > 0 ? (
                  analysis.areasToImprove.map((area, idx) => (
                    <li key={idx}>
                      <AlertTriangle size={14} className="text-amber list-icon" />
                      <span>{area}</span>
                    </li>
                  ))
                ) : (
                  <li className="empty-eval-item">No missing areas identified</li>
                )}
              </ul>
            </div>

          </div>

          {/* Recommended Next Skills */}
          {analysis.recommendedSkills && analysis.recommendedSkills.length > 0 && (
            <div className="analysis-section recommendations-card">
              <h3 className="section-title">
                <BookOpen size={18} className="text-purple" />
                Recommended Skills to Learn Next
              </h3>
              <p className="recommendations-hint">
                High-leverage industry technologies and certifications that will maximize this candidate's market competitive edge:
              </p>
              <div className="skills-tag-list">
                {analysis.recommendedSkills.map((rec, idx) => (
                  <span key={idx} className="skill-tag skill-tag-purple">
                    <Sparkles size={12} className="rec-sparkle" />
                    {rec}
                  </span>
                ))}
              </div>
            </div>
          )}

          {/* Raw JSON Developer Toggle */}
          {analysis.rawJson && (
            <div className="raw-json-accordion">
              <button
                type="button"
                className="raw-json-trigger"
                onClick={() => setShowRawJson(!showRawJson)}
                aria-expanded={showRawJson}
              >
                <span>Raw AI Response (Engineering / Debug Inspection)</span>
                {showRawJson ? <ChevronUp size={16} /> : <ChevronDown size={16} />}
              </button>

              {showRawJson && (
                <div className="raw-json-content">
                  <div className="raw-json-bar">
                    <span className="raw-json-note">Stored for technical audit and debugging. Not exposed in user workflows.</span>
                    <button
                      type="button"
                      className="copy-btn"
                      onClick={handleCopyJson}
                      aria-label="Copy raw JSON"
                    >
                      {copied ? <Check size={14} className="text-emerald" /> : <Copy size={14} />}
                      <span>{copied ? 'Copied!' : 'Copy JSON'}</span>
                    </button>
                  </div>
                  <pre className="raw-json-pre">
                    <code>{analysis.rawJson}</code>
                  </pre>
                </div>
              )}
            </div>
          )}

        </div>

        {/* Modal Footer */}
        <div className="analysis-modal-footer">
          <Button variant="secondary" onClick={onClose}>
            Close Analysis
          </Button>
        </div>

      </div>
    </div>
  );
};
