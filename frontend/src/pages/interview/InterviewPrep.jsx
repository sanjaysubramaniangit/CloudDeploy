import React, { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import {
  Sparkles,
  HelpCircle,
  Briefcase,
  FileText,
  AlertTriangle,
  CheckCircle,
  ChevronLeft,
  ChevronRight,
  Eye,
  EyeOff,
  Trash2,
  Plus,
  PlayCircle,
  Layers,
  Calendar,
  X,
  Clock,
  ShieldAlert
} from 'lucide-react';
import api from '../../api/axios';
import { useToast } from '../../context/ToastContext';
import { Spinner } from '../../components/ui/Spinner';
import './InterviewPrep.css';

export const InterviewPrep = () => {
  const { addToast } = useToast();

  // Data states
  const [jobs, setJobs] = useState([]);
  const [resumes, setResumes] = useState([]);
  const [sessions, setSessions] = useState([]);
  const [loadingData, setLoadingData] = useState(true);

  // Setup form states
  const [selectedResumeId, setSelectedResumeId] = useState('');
  const [selectedJobId, setSelectedJobId] = useState('');
  const [difficulty, setDifficulty] = useState('INTERMEDIATE');
  const [generating, setGenerating] = useState(false);

  // Active Session & Practice states
  const [activeSession, setActiveSession] = useState(null);
  const [currentQuestionIndex, setCurrentQuestionIndex] = useState(0);
  const [showConcepts, setShowConcepts] = useState(false);
  const [visitedQuestions, setVisitedQuestions] = useState(new Set([0]));
  const [loadingSession, setLoadingSession] = useState(false);

  // Controlled AI unconfigured / error state
  const [aiError, setAiError] = useState(null);

  // Modal states: New Job
  const [showNewJobModal, setShowNewJobModal] = useState(false);
  const [newJobTitle, setNewJobTitle] = useState('');
  const [newJobCompany, setNewJobCompany] = useState('');
  const [newJobSourceUrl, setNewJobSourceUrl] = useState('');
  const [newJobDescription, setNewJobDescription] = useState('');
  const [savingJob, setSavingJob] = useState(false);

  // Modal state: Delete Confirmation
  const [sessionToDelete, setSessionToDelete] = useState(null);
  const [deletingSession, setDeletingSession] = useState(false);

  // Load initial data
  const loadData = async () => {
    setLoadingData(true);
    try {
      const [jobsRes, resumesRes, sessionsRes] = await Promise.all([
        api.get('/jobs'),
        api.get('/resumes'),
        api.get('/ai/interview')
      ]);

      const loadedJobs = jobsRes.data || [];
      const loadedResumes = resumesRes.data || [];
      const loadedSessions = sessionsRes.data || [];

      setJobs(loadedJobs);
      setResumes(loadedResumes);
      setSessions(loadedSessions);

      // Select default analyzed resume if available
      const analyzed = loadedResumes.find((r) => r.hasAnalysis);
      if (analyzed && !selectedResumeId) {
        setSelectedResumeId(String(analyzed.id));
      } else if (loadedResumes.length > 0 && !selectedResumeId) {
        setSelectedResumeId(String(loadedResumes[0].id));
      }

      // Select default job if available
      if (loadedJobs.length > 0 && !selectedJobId) {
        setSelectedJobId(String(loadedJobs[0].id));
      }

      // If user has past sessions, load the most recent session into practice view
      if (loadedSessions.length > 0 && !activeSession) {
        loadSessionDetails(loadedSessions[0].id, false);
      }
    } catch (err) {
      console.error('Failed to load initial interview prep data', err);
      addToast('Failed to load initial interview preparation data', 'error');
    } finally {
      setLoadingData(false);
    }
  };

  useEffect(() => {
    loadData();
  }, []);

  // Load a full session with all 10 questions
  const loadSessionDetails = async (sessionId, showNotification = true) => {
    setLoadingSession(true);
    try {
      const res = await api.get(`/ai/interview/${sessionId}`);
      setActiveSession(res.data);
      setCurrentQuestionIndex(0);
      setShowConcepts(false);
      setVisitedQuestions(new Set([0]));
      if (showNotification) {
        addToast('Interview session loaded', 'success');
      }
    } catch (err) {
      console.error('Failed to load interview session details', err);
      addToast('Failed to load interview session questions', 'error');
    } finally {
      setLoadingSession(false);
    }
  };

  // Generate new interview session
  const handleGenerate = async (e) => {
    e.preventDefault();
    setAiError(null);

    if (!selectedResumeId) {
      addToast('Please select a resume with completed AI analysis', 'error');
      return;
    }
    if (!selectedJobId) {
      addToast('Please select a target job description', 'error');
      return;
    }

    setGenerating(true);
    try {
      const response = await api.post('/ai/interview/generate', {
        resumeId: Number(selectedResumeId),
        jobDescriptionId: Number(selectedJobId),
        difficulty: difficulty
      });

      const newSession = response.data;
      setActiveSession(newSession);
      setCurrentQuestionIndex(0);
      setShowConcepts(false);
      setVisitedQuestions(new Set([0]));

      // Refresh sessions history
      const historyRes = await api.get('/ai/interview');
      setSessions(historyRes.data || []);

      addToast(`Generated 10 personalized ${difficulty.toLowerCase()} interview questions!`, 'success');
    } catch (err) {
      console.error('Interview generation failed', err);
      if (err.response?.status === 503) {
        const errorMsg = err.response?.data?.message ||
          'AI interview generation is unavailable because the AI provider is not configured.';
        setAiError(errorMsg);
        addToast(errorMsg, 'error');
      } else {
        const msg = err.response?.data?.message || 'Failed to generate interview questions';
        addToast(msg, 'error');
      }
    } finally {
      setGenerating(false);
    }
  };

  // Create Job Description modal handler
  const handleCreateJob = async (e) => {
    e.preventDefault();
    if (!newJobTitle.trim() || !newJobDescription.trim()) {
      addToast('Please fill in Job Title and Job Description', 'error');
      return;
    }

    setSavingJob(true);
    try {
      const res = await api.post('/jobs', {
        title: newJobTitle.trim(),
        company: newJobCompany.trim(),
        sourceUrl: newJobSourceUrl.trim() || null,
        description: newJobDescription.trim()
      });

      const created = res.data;
      setJobs((prev) => [created, ...prev]);
      setSelectedJobId(String(created.id));
      setShowNewJobModal(false);
      setNewJobTitle('');
      setNewJobCompany('');
      setNewJobSourceUrl('');
      setNewJobDescription('');
      addToast(`Job "${created.title}" saved successfully`, 'success');
    } catch (err) {
      console.error('Failed to create job', err);
      addToast(err.response?.data?.message || 'Failed to save job description', 'error');
    } finally {
      setSavingJob(false);
    }
  };

  // Delete session handler
  const handleDeleteSession = async () => {
    if (!sessionToDelete) return;
    setDeletingSession(true);
    try {
      await api.delete(`/ai/interview/${sessionToDelete.id}`);
      setSessions((prev) => prev.filter((s) => s.id !== sessionToDelete.id));

      if (activeSession && activeSession.id === sessionToDelete.id) {
        setActiveSession(null);
        setCurrentQuestionIndex(0);
      }

      addToast('Interview session deleted', 'success');
      setSessionToDelete(null);
    } catch (err) {
      console.error('Failed to delete session', err);
      addToast(err.response?.data?.message || 'Failed to delete interview session', 'error');
    } finally {
      setDeletingSession(false);
    }
  };

  // Question navigation
  const navigateToQuestion = (index) => {
    if (!activeSession?.questions || index < 0 || index >= activeSession.questions.length) return;
    setCurrentQuestionIndex(index);
    setShowConcepts(false);
    setVisitedQuestions((prev) => new Set([...prev, index]));
  };

  const analyzedResumes = resumes.filter((r) => r.hasAnalysis);
  const activeQuestion = activeSession?.questions?.[currentQuestionIndex];

  // Helper for difficulty badge class
  const getDifficultyBadgeClass = (diff) => {
    switch (diff?.toUpperCase()) {
      case 'BEGINNER':
        return 'badge-diff-beginner';
      case 'ADVANCED':
        return 'badge-diff-advanced';
      default:
        return 'badge-diff-intermediate';
    }
  };

  // Helper for category formatting
  const formatCategory = (cat) => {
    if (!cat) return 'GENERAL';
    return cat.replace(/_/g, ' ');
  };

  if (loadingData) {
    return (
      <div className="interview-page">
        <div className="empty-state-box">
          <Spinner size={36} />
          <p style={{ marginTop: '1rem' }}>Loading interview preparation workspace...</p>
        </div>
      </div>
    );
  }

  return (
    <div className="interview-page">
      {/* Page Header */}
      <div className="interview-header">
        <div className="interview-header-badge">
          <Sparkles size={14} />
          <span>AI Interview Preparation</span>
        </div>
        <h1 className="interview-title">Technical Interview Preparation</h1>
        <p className="interview-subtitle">
          Generate structured, personalized interview questions grounded in your analyzed resume, target job description,
          and deterministic skill gaps. Questions are tailored across 13 core categories and 3 difficulty tiers.
        </p>
      </div>

      {/* Controlled AI Unconfigured Alert Banner */}
      {aiError && (
        <div className="ai-unconfigured-banner" role="alert">
          <ShieldAlert size={24} />
          <div className="banner-content">
            <h4>AI Provider Unavailable (HTTP 503)</h4>
            <p>{aiError}</p>
          </div>
        </div>
      )}

      {/* Setup & Generation Section */}
      <section className="interview-setup-card" aria-label="Interview Generation Setup">
        <h3 className="setup-card-title">
          <HelpCircle size={20} />
          <span>Configure New Practice Session</span>
        </h3>

        <form onSubmit={handleGenerate} className="setup-grid">
          {/* Resume Selector */}
          <div className="setup-field">
            <label htmlFor="resume-select">
              <span>Candidate Resume</span>
              {analyzedResumes.length === 0 && (
                <Link to="/resume-ai" className="field-action-link">
                  Upload & Analyze in Resume AI &rarr;
                </Link>
              )}
            </label>
            <select
              id="resume-select"
              className="setup-select"
              value={selectedResumeId}
              onChange={(e) => setSelectedResumeId(e.target.value)}
              disabled={generating || analyzedResumes.length === 0}
            >
              {analyzedResumes.length === 0 ? (
                <option value="">No analyzed resumes available</option>
              ) : (
                analyzedResumes.map((r) => (
                  <option key={r.id} value={r.id}>
                    {r.fileName} (Analyzed)
                  </option>
                ))
              )}
            </select>
          </div>

          {/* Job Description Selector */}
          <div className="setup-field">
            <label htmlFor="job-select">
              <span>Target Job Description</span>
              <button
                type="button"
                className="field-action-link"
                onClick={() => setShowNewJobModal(true)}
              >
                <Plus size={12} /> Add New Job
              </button>
            </label>
            <select
              id="job-select"
              className="setup-select"
              value={selectedJobId}
              onChange={(e) => setSelectedJobId(e.target.value)}
              disabled={generating || jobs.length === 0}
            >
              {jobs.length === 0 ? (
                <option value="">No saved jobs available</option>
              ) : (
                jobs.map((j) => (
                  <option key={j.id} value={j.id}>
                    {j.title} — {j.company}
                  </option>
                ))
              )}
            </select>
          </div>

          {/* Difficulty Selector */}
          <div className="setup-field" style={{ gridColumn: '1 / -1' }}>
            <label>Select Question Difficulty</label>
            <div className="difficulty-selector">
              <div
                className={`difficulty-pill beginner ${difficulty === 'BEGINNER' ? 'active' : ''}`}
                onClick={() => setDifficulty('BEGINNER')}
              >
                <span className="pill-title">Beginner</span>
                <span className="pill-desc">Fundamentals, core syntax & concepts</span>
              </div>
              <div
                className={`difficulty-pill intermediate ${difficulty === 'INTERMEDIATE' ? 'active' : ''}`}
                onClick={() => setDifficulty('INTERMEDIATE')}
              >
                <span className="pill-title">Intermediate</span>
                <span className="pill-desc">Real-world scenarios, frameworks & trade-offs</span>
              </div>
              <div
                className={`difficulty-pill advanced ${difficulty === 'ADVANCED' ? 'active' : ''}`}
                onClick={() => setDifficulty('ADVANCED')}
              >
                <span className="pill-title">Advanced</span>
                <span className="pill-desc">System design, internals & scalability</span>
              </div>
            </div>
          </div>

          {/* Action Row */}
          <div className="setup-actions" style={{ gridColumn: '1 / -1' }}>
            <button
              type="submit"
              className="btn-generate"
              disabled={generating || analyzedResumes.length === 0 || jobs.length === 0}
            >
              {generating ? (
                <>
                  <Spinner size={16} />
                  <span>Synthesizing Interview Questions...</span>
                </>
              ) : (
                <>
                  <Sparkles size={18} />
                  <span>Generate 10 Interview Questions</span>
                </>
              )}
            </button>
          </div>
        </form>
      </section>

      {/* Active Session / Practice Mode */}
      {loadingSession ? (
        <div className="empty-state-box">
          <Spinner size={28} />
          <p style={{ marginTop: '0.75rem' }}>Loading questions...</p>
        </div>
      ) : activeSession && activeSession.questions && activeSession.questions.length > 0 ? (
        <section className="practice-container" aria-label="Interactive Interview Practice Workspace">
          {/* Header Card */}
          <div className="practice-header-card">
            <div className="practice-meta-row">
              <div className="practice-title-group">
                <h2>
                  <Briefcase size={22} style={{ color: '#38bdf8' }} />
                  <span>{activeSession.jobTitle}</span>
                </h2>
                <div className="practice-meta-badges">
                  <span>at <strong>{activeSession.company}</strong></span>
                  <span>•</span>
                  <span>Resume: <strong>{activeSession.resumeFileName}</strong></span>
                  <span>•</span>
                  <span className={`practice-badge ${getDifficultyBadgeClass(activeSession.difficulty)}`}>
                    {activeSession.difficulty}
                  </span>
                  <span>•</span>
                  <span>{activeSession.questions.length} Questions</span>
                </div>
              </div>
            </div>

            {/* Question Navigator Strip (1..10) */}
            <div className="question-nav-strip">
              {activeSession.questions.map((q, idx) => {
                const isActive = idx === currentQuestionIndex;
                const isVisited = visitedQuestions.has(idx);
                return (
                  <button
                    key={q.id || idx}
                    type="button"
                    className={`nav-dot-btn ${isActive ? 'active' : ''} ${isVisited ? 'visited' : ''}`}
                    onClick={() => navigateToQuestion(idx)}
                    title={`Go to Question ${idx + 1} (${formatCategory(q.category)})`}
                  >
                    {idx + 1}
                  </button>
                );
              })}
            </div>
          </div>

          {/* Active Question Card */}
          {activeQuestion && (
            <div className="question-card">
              <div className="question-card-top">
                <span className="question-indicator">
                  Question {currentQuestionIndex + 1} of {activeSession.questions.length}
                </span>
                <div className="question-badges">
                  <span className="practice-badge badge-category">
                    {formatCategory(activeQuestion.category)}
                  </span>
                  <span className={`practice-badge ${getDifficultyBadgeClass(activeQuestion.difficulty)}`}>
                    {activeQuestion.difficulty}
                  </span>
                </div>
              </div>

              {/* Question Text */}
              <p className="question-text">{activeQuestion.question}</p>

              {/* Expected Concepts / Answer Guide Toggle */}
              <div className="expected-concepts-wrapper">
                <button
                  type="button"
                  className="expected-concepts-toggle"
                  onClick={() => setShowConcepts(!showConcepts)}
                >
                  {showConcepts ? <EyeOff size={16} /> : <Eye size={16} />}
                  <span>{showConcepts ? 'Hide Key Discussion Points' : 'Show Key Discussion Points'}</span>
                </button>

                {showConcepts && (
                  <div className="concepts-content">
                    <h4>Expected Concepts & Evaluation Criteria:</h4>
                    <ul className="concepts-list">
                      {activeQuestion.expectedConcepts && activeQuestion.expectedConcepts.length > 0 ? (
                        activeQuestion.expectedConcepts.map((concept, cIdx) => (
                          <li key={cIdx} className="concept-tag">
                            {concept}
                          </li>
                        ))
                      ) : (
                        <li className="concept-tag">General proficiency and clear reasoning</li>
                      )}
                    </ul>
                  </div>
                )}
              </div>

              {/* Navigation Actions */}
              <div className="question-nav-actions">
                <button
                  type="button"
                  className="btn-nav-action"
                  onClick={() => navigateToQuestion(currentQuestionIndex - 1)}
                  disabled={currentQuestionIndex === 0}
                >
                  <ChevronLeft size={18} />
                  <span>Previous</span>
                </button>

                <span style={{ fontSize: '0.85rem', color: '#64748b' }}>
                  {currentQuestionIndex + 1} / {activeSession.questions.length}
                </span>

                <button
                  type="button"
                  className="btn-nav-action"
                  onClick={() => navigateToQuestion(currentQuestionIndex + 1)}
                  disabled={currentQuestionIndex === activeSession.questions.length - 1}
                >
                  <span>Next</span>
                  <ChevronRight size={18} />
                </button>
              </div>
            </div>
          )}
        </section>
      ) : null}

      {/* History Section */}
      <section className="interview-history-card" aria-label="Past Interview Sessions History">
        <div className="history-card-header">
          <h3 className="history-card-title">
            <Clock size={20} style={{ color: '#818cf8' }} />
            <span>Interview Practice History</span>
          </h3>
          <span style={{ fontSize: '0.85rem', color: '#94a3b8' }}>
            {sessions.length} {sessions.length === 1 ? 'Session' : 'Sessions'} Recorded
          </span>
        </div>

        {sessions.length === 0 ? (
          <div className="empty-state-box">
            <Layers size={36} />
            <p>No practice sessions generated yet. Configure a session above to start practicing!</p>
          </div>
        ) : (
          <div className="history-table-container">
            <table className="history-table">
              <thead>
                <tr>
                  <th>Target Job</th>
                  <th>Resume</th>
                  <th>Difficulty</th>
                  <th>Questions</th>
                  <th>Created</th>
                  <th style={{ textAlign: 'right' }}>Actions</th>
                </tr>
              </thead>
              <tbody>
                {sessions.map((sess) => (
                  <tr key={sess.id}>
                    <td>
                      <div className="history-job-cell">
                        <span className="history-job-title">{sess.jobTitle}</span>
                        <span className="history-job-company">{sess.company}</span>
                      </div>
                    </td>
                    <td>{sess.resumeFileName}</td>
                    <td>
                      <span className={`practice-badge ${getDifficultyBadgeClass(sess.difficulty)}`}>
                        {sess.difficulty}
                      </span>
                    </td>
                    <td>{sess.questionCount || 10}</td>
                    <td>
                      {sess.createdAt
                        ? new Date(sess.createdAt).toLocaleDateString(undefined, {
                            month: 'short',
                            day: 'numeric',
                            year: 'numeric'
                          })
                        : 'Recently'}
                    </td>
                    <td style={{ textAlign: 'right' }}>
                      <div className="history-actions" style={{ justifyContent: 'flex-end' }}>
                        <button
                          type="button"
                          className="btn-history-open"
                          onClick={() => loadSessionDetails(sess.id)}
                          title="Open in Practice Workspace"
                        >
                          <PlayCircle size={14} />
                          <span>Practice</span>
                        </button>
                        <button
                          type="button"
                          className="btn-history-delete"
                          onClick={() => setSessionToDelete(sess)}
                          title="Delete Session"
                        >
                          <Trash2 size={16} />
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>

      {/* Modal: New Job Description */}
      {showNewJobModal && (
        <div className="modal-overlay" onClick={() => setShowNewJobModal(false)}>
          <div className="modal-content" onClick={(e) => e.stopPropagation()}>
            <div className="modal-header">
              <h3>Add Target Job Description</h3>
              <button
                type="button"
                className="btn-modal-close"
                onClick={() => setShowNewJobModal(false)}
              >
                <X size={20} />
              </button>
            </div>
            <form onSubmit={handleCreateJob} className="modal-form">
              <div className="form-group">
                <label>Job Title *</label>
                <input
                  type="text"
                  placeholder="e.g. Senior Cloud & DevOps Engineer"
                  value={newJobTitle}
                  onChange={(e) => setNewJobTitle(e.target.value)}
                  required
                />
              </div>
              <div className="form-group">
                <label>Company (Optional)</label>
                <input
                  type="text"
                  placeholder="e.g. Acme Cloud Corp"
                  value={newJobCompany}
                  onChange={(e) => setNewJobCompany(e.target.value)}
                />
              </div>
              <div className="form-group">
                <label>Source URL (Optional)</label>
                <input
                  type="url"
                  placeholder="https://company.com/careers/role"
                  value={newJobSourceUrl}
                  onChange={(e) => setNewJobSourceUrl(e.target.value)}
                />
              </div>
              <div className="form-group">
                <label>Job Description Requirements & Scope *</label>
                <textarea
                  rows={5}
                  placeholder="Paste role requirements, required skills, cloud infrastructure duties, etc."
                  value={newJobDescription}
                  onChange={(e) => setNewJobDescription(e.target.value)}
                  required
                />
              </div>
              <div className="modal-actions">
                <button
                  type="button"
                  className="btn-modal-cancel"
                  onClick={() => setShowNewJobModal(false)}
                  disabled={savingJob}
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="btn-modal-submit"
                  disabled={savingJob}
                >
                  {savingJob ? 'Saving...' : 'Save Job Description'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Modal: Delete Confirmation */}
      {sessionToDelete && (
        <div className="modal-overlay" onClick={() => setSessionToDelete(null)}>
          <div className="modal-content" onClick={(e) => e.stopPropagation()}>
            <div className="modal-header">
              <h3>Delete Interview Session?</h3>
              <button
                type="button"
                className="btn-modal-close"
                onClick={() => setSessionToDelete(null)}
              >
                <X size={20} />
              </button>
            </div>
            <p style={{ color: '#cbd5e1', fontSize: '0.9rem', lineHeight: 1.5, margin: 0 }}>
              Are you sure you want to delete the interview session for{' '}
              <strong>{sessionToDelete.jobTitle}</strong> ({sessionToDelete.company})?
              All 10 generated questions and practice progress will be permanently deleted.
            </p>
            <div className="modal-actions">
              <button
                type="button"
                className="btn-modal-cancel"
                onClick={() => setSessionToDelete(null)}
                disabled={deletingSession}
              >
                Cancel
              </button>
              <button
                type="button"
                className="btn-history-delete"
                style={{
                  background: '#ef4444',
                  color: '#ffffff',
                  padding: '0.6rem 1.25rem',
                  fontWeight: 600
                }}
                onClick={handleDeleteSession}
                disabled={deletingSession}
              >
                {deletingSession ? 'Deleting...' : 'Delete Session'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
