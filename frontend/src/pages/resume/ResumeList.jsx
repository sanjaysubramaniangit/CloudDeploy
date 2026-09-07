import React, { useState } from 'react';
import {
  FileText,
  Sparkles,
  Trash2,
  Calendar,
  HardDrive,
  Eye,
  AlertCircle,
  CheckCircle2,
  ChevronDown,
  ChevronUp
} from 'lucide-react';
import { Card, CardContent, CardHeader } from '../../components/ui/Card';
import { Button } from '../../components/ui/Button';
import { Badge } from '../../components/ui/Badge';
import { Spinner } from '../../components/ui/Spinner';
import { EmptyState } from '../../components/ui/EmptyState';
import { ConfirmDialog } from '../../components/ui/ConfirmDialog';
import { useToast } from '../../context/ToastContext';
import api from '../../api/axios';
import './ResumeAi.css';

export const ResumeList = ({
  resumes,
  loading,
  onRefresh,
  onOpenAnalysis,
  onDeleteSuccess
}) => {
  const { addToast } = useToast();

  const [expandedSnippetId, setExpandedSnippetId] = useState(null);
  const [analyzingId, setAnalyzingId] = useState(null);
  const [deleteTarget, setDeleteTarget] = useState(null);
  const [deleteLoading, setDeleteLoading] = useState(false);
  const [aiError, setAiError] = useState(null);

  const formatFileSize = (bytes) => {
    if (!bytes && bytes !== 0) return '—';
    if (bytes < 1024) return `${bytes} B`;
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
    return `${(bytes / (1024 * 1024)).toFixed(2)} MB`;
  };

  const formatDate = (dateStr) => {
    if (!dateStr) return '—';
    try {
      return new Intl.DateTimeFormat('en-US', {
        month: 'short',
        day: 'numeric',
        year: 'numeric',
      }).format(new Date(dateStr));
    } catch {
      return dateStr;
    }
  };

  const toggleSnippet = (id) => {
    setExpandedSnippetId(expandedSnippetId === id ? null : id);
  };

  const handleTriggerAnalysis = async (resume) => {
    setAnalyzingId(resume.id);
    setAiError(null);

    try {
      const response = await api.post('/ai/resume/analyze', { resumeId: resume.id });
      addToast(`Resume "${resume.fileName}" analyzed successfully!`, 'success');
      if (onRefresh) {
        onRefresh();
      }
      if (onOpenAnalysis) {
        onOpenAnalysis(response.data, resume.fileName);
      }
    } catch (err) {
      console.error('AI analysis error', err);
      if (err.response?.status === 503) {
        const msg = err.response?.data?.message || 'AI provider is not configured. Please configure AI_PROVIDER and AI_API_KEY.';
        setAiError(msg);
        addToast(msg, 'warning');
      } else {
        const msg = err.response?.data?.message || 'Failed to analyze resume with AI.';
        setAiError(msg);
        addToast(msg, 'error');
      }
    } finally {
      setAnalyzingId(null);
    }
  };

  const handleFetchExistingAnalysis = async (resume) => {
    try {
      const response = await api.get(`/ai/resume/${resume.id}/analysis`);
      if (onOpenAnalysis) {
        onOpenAnalysis(response.data, resume.fileName);
      }
    } catch (err) {
      console.error('Failed to fetch analysis', err);
      addToast(err.response?.data?.message || 'Could not load existing analysis.', 'error');
    }
  };

  const handleDeleteConfirm = async () => {
    if (!deleteTarget) return;

    setDeleteLoading(true);
    try {
      await api.delete(`/resumes/${deleteTarget.id}`);
      addToast(`Resume "${deleteTarget.fileName}" deleted successfully`, 'success');
      setDeleteTarget(null);
      if (onDeleteSuccess) {
        onDeleteSuccess(deleteTarget.id);
      }
    } catch (err) {
      console.error('Failed to delete resume', err);
      addToast(err.response?.data?.message || 'Failed to delete resume', 'error');
    } finally {
      setDeleteLoading(false);
    }
  };

  if (loading) {
    return (
      <Card className="resume-list-card">
        <CardContent className="resume-list-loading">
          <Spinner size={32} />
          <p className="loading-text">Loading candidate resumes...</p>
        </CardContent>
      </Card>
    );
  }

  return (
    <>
      <Card className="resume-list-card">
        <CardHeader>
          <div className="list-card-header-inner">
            <div className="list-header-left">
              <h3 className="list-title">My Uploaded Resumes</h3>
              <span className="resumes-count-badge">
                {resumes.length} {resumes.length === 1 ? 'Resume' : 'Resumes'}
              </span>
            </div>
          </div>
        </CardHeader>

        <CardContent>
          {/* AI Configuration Notice Alert (503 Error Banner) */}
          {aiError && (
            <div className="ai-warning-alert" role="alert">
              <div className="ai-warning-icon">
                <AlertCircle size={20} className="text-amber" />
              </div>
              <div className="ai-warning-content">
                <strong>AI Service Unavailable</strong>
                <p>{aiError}</p>
                <span className="ai-warning-hint">
                  To enable live resume intelligence, set <code>AI_PROVIDER=openai</code> and <code>AI_API_KEY=your_key</code> in your environment.
                </span>
              </div>
              <button
                type="button"
                className="ai-warning-dismiss"
                onClick={() => setAiError(null)}
                aria-label="Dismiss notice"
              >
                ✕
              </button>
            </div>
          )}

          {/* Empty State */}
          {resumes.length === 0 ? (
            <EmptyState
              icon={<FileText size={48} className="text-muted" />}
              title="No resumes uploaded yet"
              description="Upload your first candidate resume (PDF, DOCX, or TXT) above to extract text and generate structured AI intelligence."
            />
          ) : (
            <div className="resumes-list-container">
              {resumes.map((resume) => {
                const isAnalyzing = analyzingId === resume.id;
                const isSnippetOpen = expandedSnippetId === resume.id;

                return (
                  <div key={resume.id} className="resume-item-card">
                    <div className="resume-item-top">
                      
                      {/* Left: File Icon & Meta */}
                      <div className="resume-meta-group">
                        <div className="resume-type-icon">
                          <FileText size={22} className="text-primary" />
                        </div>
                        <div className="resume-title-box">
                          <h4 className="resume-filename">{resume.fileName}</h4>
                          <div className="resume-submeta">
                            <span className="submeta-item">
                              <Calendar size={13} />
                              {formatDate(resume.createdAt)}
                            </span>
                            <span className="submeta-sep">•</span>
                            <span className="submeta-item">
                              <HardDrive size={13} />
                              {formatFileSize(resume.fileSize)}
                            </span>
                          </div>
                        </div>
                      </div>

                      {/* Center: Status Badge */}
                      <div className="resume-status-col">
                        {resume.hasAnalysis ? (
                          <span className="status-pill status-analyzed">
                            <CheckCircle2 size={13} />
                            <span>Analyzed</span>
                          </span>
                        ) : (
                          <span className="status-pill status-ready">
                            <Sparkles size={13} />
                            <span>Ready to Analyze</span>
                          </span>
                        )}
                      </div>

                      {/* Right: Actions */}
                      <div className="resume-actions-group">
                        {resume.hasAnalysis ? (
                          <Button
                            variant="secondary"
                            className="btn-view-analysis"
                            onClick={() => handleFetchExistingAnalysis(resume)}
                            aria-label={`View analysis for ${resume.fileName}`}
                          >
                            <Eye size={15} />
                            <span>View Analysis</span>
                          </Button>
                        ) : (
                          <Button
                            variant="primary"
                            className="btn-analyze-ai"
                            onClick={() => handleTriggerAnalysis(resume)}
                            disabled={isAnalyzing}
                            aria-label={`Analyze ${resume.fileName} with AI`}
                          >
                            {isAnalyzing ? (
                              <span className="btn-loading-content">
                                <Spinner size={15} />
                                <span>Analyzing...</span>
                              </span>
                            ) : (
                              <>
                                <Sparkles size={15} />
                                <span>Analyze with AI</span>
                              </>
                            )}
                          </Button>
                        )}

                        <button
                          type="button"
                          className="resume-action-icon-btn delete-btn"
                          onClick={() => setDeleteTarget(resume)}
                          aria-label={`Delete resume ${resume.fileName}`}
                        >
                          <Trash2 size={16} />
                        </button>
                      </div>

                    </div>

                    {/* Snippet Collapsible Bar */}
                    {resume.extractedSnippet && (
                      <div className="resume-snippet-wrapper">
                        <button
                          type="button"
                          className="snippet-toggle-btn"
                          onClick={() => toggleSnippet(resume.id)}
                          aria-expanded={isSnippetOpen}
                        >
                          <span>{isSnippetOpen ? 'Hide extracted text snippet' : 'Show extracted text snippet'}</span>
                          {isSnippetOpen ? <ChevronUp size={14} /> : <ChevronDown size={14} />}
                        </button>

                        {isSnippetOpen && (
                          <div className="snippet-box">
                            <p className="snippet-content">"{resume.extractedSnippet}"</p>
                          </div>
                        )}
                      </div>
                    )}

                  </div>
                );
              })}
            </div>
          )}
        </CardContent>
      </Card>

      {/* Delete Confirmation Dialog */}
      <ConfirmDialog
        isOpen={!!deleteTarget}
        title="Delete Candidate Resume?"
        message={`Are you sure you want to delete "${deleteTarget?.fileName}"? This will permanently remove the file from private S3 storage and delete any associated AI analyses.`}
        confirmText="Delete Resume"
        cancelText="Keep Resume"
        confirmVariant="danger"
        isLoading={deleteLoading}
        onConfirm={handleDeleteConfirm}
        onCancel={() => setDeleteTarget(null)}
      />
    </>
  );
};
