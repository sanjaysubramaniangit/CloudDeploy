import React, { useState, useEffect, useMemo } from 'react';
import { Link } from 'react-router-dom';
import {
  Sparkles,
  Briefcase,
  FileText,
  Plus,
  CheckCircle2,
  XCircle,
  AlertCircle,
  Search,
  Check,
  Layers,
  Cloud,
  Cpu,
  Database,
  Calendar,
  History,
  ExternalLink,
  ChevronRight,
  TrendingUp,
  Award,
  Terminal,
  Server
} from 'lucide-react';
import api from '../../api/axios';
import { useToast } from '../../context/ToastContext';
import { Spinner } from '../../components/ui/Spinner';
import './JobMatch.css';

export const JobMatch = () => {
  const { addToast } = useToast();

  // Data states
  const [jobs, setJobs] = useState([]);
  const [resumes, setResumes] = useState([]);
  const [historyMatches, setHistoryMatches] = useState([]);
  const [loadingData, setLoadingData] = useState(true);

  // Selection states
  const [selectedJobId, setSelectedJobId] = useState('');
  const [selectedResumeId, setSelectedResumeId] = useState('');

  // Execution & Active Match states
  const [matchingLoading, setMatchingLoading] = useState(false);
  const [activeMatch, setActiveMatch] = useState(null);

  // Filter & Search states for skills table
  const [skillFilter, setSkillFilter] = useState('ALL'); // ALL, MATCHED, MISSING, REQUIRED
  const [searchQuery, setSearchQuery] = useState('');

  // New Job Modal state
  const [showNewJobModal, setShowNewJobModal] = useState(false);
  const [newJobTitle, setNewJobTitle] = useState('');
  const [newJobCompany, setNewJobCompany] = useState('');
  const [newJobSourceUrl, setNewJobSourceUrl] = useState('');
  const [newJobDescription, setNewJobDescription] = useState('');
  const [savingJob, setSavingJob] = useState(false);

  // Fetch initial data: jobs, resumes, prior matches
  const fetchData = async () => {
    setLoadingData(true);
    try {
      const [jobsRes, resumesRes, historyRes] = await Promise.all([
        api.get('/jobs'),
        api.get('/resumes'),
        api.get('/ai/job-match')
      ]);

      const loadedJobs = jobsRes.data || [];
      const loadedResumes = resumesRes.data || [];
      const loadedHistory = historyRes.data || [];

      setJobs(loadedJobs);
      setResumes(loadedResumes);
      setHistoryMatches(loadedHistory);

      // Auto-select first job and first analyzed resume if available
      if (loadedJobs.length > 0 && !selectedJobId) {
        setSelectedJobId(String(loadedJobs[0].id));
      }
      const firstAnalyzed = loadedResumes.find((r) => r.hasAnalysis);
      if (firstAnalyzed && !selectedResumeId) {
        setSelectedResumeId(String(firstAnalyzed.id));
      } else if (loadedResumes.length > 0 && !selectedResumeId) {
        setSelectedResumeId(String(loadedResumes[0].id));
      }

      // If user has previous matches, show the most recent one by default
      if (loadedHistory.length > 0 && !activeMatch) {
        setActiveMatch(loadedHistory[0]);
      }
    } catch (err) {
      console.error('Failed to load job match initial data', err);
      addToast('Failed to load jobs or resumes', 'error');
    } finally {
      setLoadingData(false);
    }
  };

  useEffect(() => {
    fetchData();
  }, []);

  const selectedJob = useMemo(() => {
    return jobs.find((j) => String(j.id) === String(selectedJobId));
  }, [jobs, selectedJobId]);

  const selectedResume = useMemo(() => {
    return resumes.find((r) => String(r.id) === String(selectedResumeId));
  }, [resumes, selectedResumeId]);

  // Create new job description
  const handleCreateJob = async (e) => {
    e.preventDefault();
    if (!newJobTitle.trim() || !newJobDescription.trim()) {
      addToast('Job title and description are required', 'warning');
      return;
    }

    setSavingJob(true);
    try {
      const res = await api.post('/jobs', {
        title: newJobTitle.trim(),
        company: newJobCompany.trim() || null,
        sourceUrl: newJobSourceUrl.trim() || null,
        description: newJobDescription.trim()
      });

      addToast(`Job "${res.data.title}" saved successfully!`, 'success');
      setJobs((prev) => [res.data, ...prev]);
      setSelectedJobId(String(res.data.id));
      setShowNewJobModal(false);
      setNewJobTitle('');
      setNewJobCompany('');
      setNewJobSourceUrl('');
      setNewJobDescription('');
    } catch (err) {
      console.error('Failed to create job', err);
      addToast(err.response?.data?.message || 'Failed to save job description', 'error');
    } finally {
      setSavingJob(false);
    }
  };

  // Run Match Analysis
  const handleRunMatch = async () => {
    if (!selectedJobId || !selectedResumeId) {
      addToast('Please select both a job description and a resume', 'warning');
      return;
    }

    if (selectedResume && !selectedResume.hasAnalysis) {
      addToast('This resume has not been analyzed yet. Please run Resume Intelligence first.', 'warning');
      return;
    }

    setMatchingLoading(true);
    try {
      const res = await api.post('/ai/job-match', {
        jobDescriptionId: Number(selectedJobId),
        resumeId: Number(selectedResumeId)
      });

      setActiveMatch(res.data);
      setHistoryMatches((prev) => [res.data, ...prev.filter((m) => m.id !== res.data.id)]);
      addToast(`Job match completed! Overall Score: ${res.data.overallScore}%`, 'success');
    } catch (err) {
      console.error('Match execution failed', err);
      addToast(err.response?.data?.message || 'Job match failed to complete', 'error');
    } finally {
      setMatchingLoading(false);
    }
  };

  // Filter skills in detailed breakdown table
  const filteredDetails = useMemo(() => {
    if (!activeMatch || !activeMatch.details) return [];
    return activeMatch.details.filter((item) => {
      // Filter tab
      if (skillFilter === 'MATCHED' && !item.matched) return false;
      if (skillFilter === 'MISSING' && item.matched) return false;
      if (skillFilter === 'REQUIRED' && !item.required) return false;

      // Search query
      if (searchQuery.trim()) {
        const query = searchQuery.toLowerCase();
        const matchesName = item.skill.toLowerCase().includes(query);
        const matchesCat = item.category.toLowerCase().includes(query);
        const matchesEvidence = item.evidence ? item.evidence.toLowerCase().includes(query) : false;
        if (!matchesName && !matchesCat && !matchesEvidence) return false;
      }

      return true;
    });
  }, [activeMatch, skillFilter, searchQuery]);

  // Classification for Gauge
  const getScoreClass = (score) => {
    if (score >= 80) return 'high';
    if (score >= 60) return 'medium';
    return 'low';
  };

  const getScoreLabel = (score) => {
    if (score >= 80) return 'Strong Match';
    if (score >= 60) return 'Moderate Match';
    return 'Significant Gaps';
  };

  if (loadingData) {
    return (
      <div className="job-match-page" style={{ alignItems: 'center', justifyContent: 'center', minHeight: '50vh' }}>
        <Spinner size={36} />
        <p style={{ color: 'var(--text-muted, #94a3b8)', marginTop: '1rem' }}>Loading Job Match Intelligence...</p>
      </div>
    );
  }

  return (
    <div className="job-match-page">
      {/* Header */}
      <header className="job-match-header">
        <div className="job-match-badge-row">
          <span className="job-match-feature-tag">
            <Sparkles size={14} className="tag-sparkle" />
            AI Intelligence Suite
          </span>
          <span className="ai-provider-badge">
            Deterministic Engine + AI Reasoning
          </span>
        </div>
        <h1 className="job-match-title">AI Job Match Intelligence & Transparent Scoring</h1>
        <p className="job-match-subtitle">
          Evaluate candidate qualifications against target job descriptions using a 100% deterministic, 
          transparent mathematical scoring algorithm coupled with AI-powered contextual recommendations.
        </p>
      </header>

      {/* Setup & Selection Grid */}
      <div className="job-match-setup-grid">
        {/* Job Selection Card */}
        <div className="setup-card">
          <div className="setup-card-header">
            <div className="setup-card-title-group">
              <div className="setup-icon-box">
                <Briefcase size={20} />
              </div>
              <h3 className="setup-card-title">1. Target Job Description</h3>
            </div>
            <button
              type="button"
              className="run-match-btn"
              style={{ padding: '0.4rem 0.85rem', fontSize: '0.8rem', background: '#3b82f6' }}
              onClick={() => setShowNewJobModal(true)}
            >
              <Plus size={14} />
              New Job
            </button>
          </div>

          <div className="select-wrapper">
            <select
              value={selectedJobId}
              onChange={(e) => setSelectedJobId(e.target.value)}
              disabled={jobs.length === 0}
            >
              {jobs.length === 0 ? (
                <option value="">No jobs created yet</option>
              ) : (
                jobs.map((j) => (
                  <option key={j.id} value={j.id}>
                    {j.title} {j.company ? `(${j.company})` : ''}
                  </option>
                ))
              )}
            </select>
          </div>

          {selectedJob ? (
            <div className="job-preview-box">
              {selectedJob.company && (
                <span className="job-preview-company">
                  <Briefcase size={13} /> {selectedJob.company}
                </span>
              )}
              <p className="job-preview-text">{selectedJob.description}</p>
            </div>
          ) : (
            <p style={{ fontSize: '0.8125rem', color: 'var(--text-muted, #94a3b8)', margin: 0 }}>
              Click "New Job" to paste and save a target position.
            </p>
          )}
        </div>

        {/* Resume Selection Card */}
        <div className="setup-card">
          <div className="setup-card-header">
            <div className="setup-card-title-group">
              <div className="setup-icon-box" style={{ background: 'rgba(16, 185, 129, 0.12)', color: '#10b981' }}>
                <FileText size={20} />
              </div>
              <h3 className="setup-card-title">2. Candidate Resume</h3>
            </div>
            <Link
              to="/resume-ai"
              style={{ fontSize: '0.8rem', color: '#818cf8', display: 'inline-flex', alignItems: 'center', gap: '0.25rem', textDecoration: 'none' }}
            >
              Manage Resumes <ChevronRight size={14} />
            </Link>
          </div>

          <div className="select-wrapper">
            <select
              value={selectedResumeId}
              onChange={(e) => setSelectedResumeId(e.target.value)}
              disabled={resumes.length === 0}
            >
              {resumes.length === 0 ? (
                <option value="">No resumes uploaded yet</option>
              ) : (
                resumes.map((r) => (
                  <option key={r.id} value={r.id}>
                    {r.fileName} {r.hasAnalysis ? '(Analyzed ✓)' : '(Unanalyzed ⚠)'}
                  </option>
                ))
              )}
            </select>
          </div>

          {selectedResume ? (
            <div className="resume-selected-info">
              <div className="resume-status-badge-row">
                <span style={{ fontSize: '0.8125rem', color: 'var(--text-muted, #94a3b8)' }}>Analysis Status:</span>
                {selectedResume.hasAnalysis ? (
                  <span className="analyzed-chip ready">
                    <CheckCircle2 size={12} /> Ready for Match
                  </span>
                ) : (
                  <span className="analyzed-chip unanalyzed">
                    <AlertCircle size={12} /> Unanalyzed
                  </span>
                )}
              </div>

              {!selectedResume.hasAnalysis && (
                <div className="unanalyzed-alert">
                  <AlertCircle size={18} style={{ flexShrink: 0, marginTop: '2px' }} />
                  <div>
                    This resume has not been analyzed by Resume Intelligence yet.
                    <Link to="/resume-ai">Go to Resume AI to run analysis</Link>
                  </div>
                </div>
              )}
            </div>
          ) : (
            <p style={{ fontSize: '0.8125rem', color: 'var(--text-muted, #94a3b8)', margin: 0 }}>
              Please upload a resume on the <Link to="/resume-ai" style={{ color: '#818cf8' }}>Resume AI page</Link>.
            </p>
          )}
        </div>
      </div>

      {/* Match Action Bar */}
      <div className="match-action-bar">
        <div className="match-action-info">
          <div className="setup-icon-box" style={{ background: 'rgba(99, 102, 241, 0.15)', color: '#818cf8' }}>
            <Sparkles size={20} />
          </div>
          <div className="match-action-info-text">
            <h4>Ready to calculate role alignment?</h4>
            <p>Runs deterministic 4-factor scoring algorithm & retrieves qualitative AI coaching.</p>
          </div>
        </div>

        <button
          type="button"
          className="run-match-btn"
          disabled={!selectedJobId || !selectedResumeId || (selectedResume && !selectedResume.hasAnalysis) || matchingLoading}
          onClick={handleRunMatch}
        >
          {matchingLoading ? (
            <>
              <Spinner size={16} /> Computing Match...
            </>
          ) : (
            <>
              <Sparkles size={16} /> Run Match Analysis
            </>
          )}
        </button>
      </div>

      {/* MATCH RESULTS PRESENTATION */}
      {activeMatch && (
        <>
          {/* Score Overview: Overall Gauge + 6 Category Cards */}
          <section className="score-overview-grid">
            {/* Overall Score Gauge Card */}
            <div className="overall-gauge-card">
              <span style={{ fontSize: '0.8125rem', fontWeight: 600, color: 'var(--text-muted, #94a3b8)', textTransform: 'uppercase', letterSpacing: '0.05em' }}>
                Overall Role Alignment
              </span>

              {/* SVG Circular Gauge */}
              <div className="gauge-circle-container">
                <svg className="gauge-svg" viewBox="0 0 120 120">
                  <circle className="gauge-bg" cx="60" cy="60" r="50" />
                  <circle
                    className={`gauge-progress ${getScoreClass(activeMatch.overallScore)}`}
                    cx="60"
                    cy="60"
                    r="50"
                    style={{
                      strokeDasharray: 314.159,
                      strokeDashoffset: 314.159 - (314.159 * activeMatch.overallScore) / 100
                    }}
                  />
                </svg>
                <div className="gauge-text-box">
                  <span className="gauge-score-number">{activeMatch.overallScore}%</span>
                  <span className={`gauge-score-label ${getScoreClass(activeMatch.overallScore)}`}>
                    {getScoreLabel(activeMatch.overallScore)}
                  </span>
                </div>
              </div>

              {/* Formula & Transparency Explanation */}
              <div className="formula-explainer-box">
                <span className="formula-tag">Deterministic Formula:</span>
                <br />
                60% Required + 20% Preferred + 10% Experience + 10% Category Balance
              </div>

              <div style={{ marginTop: '0.75rem' }}>
                <span className="ai-provider-badge">
                  AI: {activeMatch.aiProvider} ({activeMatch.aiModel || 'active'})
                </span>
              </div>
            </div>

            {/* Category Score Cards Grid */}
            <div className="category-cards-grid">
              {/* Programming */}
              <div className="category-score-card">
                <div className="category-card-top">
                  <div className="category-title-group">
                    <Terminal size={16} className="category-card-icon" />
                    <span className="category-card-name">Programming</span>
                  </div>
                  <span className="category-score-val">{activeMatch.programmingScore}%</span>
                </div>
                <div className="cat-progress-bar-bg">
                  <div className="cat-progress-bar-fill prog" style={{ width: `${activeMatch.programmingScore}%` }} />
                </div>
              </div>

              {/* Cloud Architecture */}
              <div className="category-score-card">
                <div className="category-card-top">
                  <div className="category-title-group">
                    <Cloud size={16} className="category-card-icon" />
                    <span className="category-card-name">Cloud Architecture</span>
                  </div>
                  <span className="category-score-val">{activeMatch.cloudScore}%</span>
                </div>
                <div className="cat-progress-bar-bg">
                  <div className="cat-progress-bar-fill cloud" style={{ width: `${activeMatch.cloudScore}%` }} />
                </div>
              </div>

              {/* DevOps & CI/CD */}
              <div className="category-score-card">
                <div className="category-card-top">
                  <div className="category-title-group">
                    <Cpu size={16} className="category-card-icon" />
                    <span className="category-card-name">DevOps & CI/CD</span>
                  </div>
                  <span className="category-score-val">{activeMatch.devopsScore}%</span>
                </div>
                <div className="cat-progress-bar-bg">
                  <div className="cat-progress-bar-fill devops" style={{ width: `${activeMatch.devopsScore}%` }} />
                </div>
              </div>

              {/* Backend & Microservices */}
              <div className="category-score-card">
                <div className="category-card-top">
                  <div className="category-title-group">
                    <Server size={16} className="category-card-icon" />
                    <span className="category-card-name">Backend Services</span>
                  </div>
                  <span className="category-score-val">{activeMatch.backendScore}%</span>
                </div>
                <div className="cat-progress-bar-bg">
                  <div className="cat-progress-bar-fill backend" style={{ width: `${activeMatch.backendScore}%` }} />
                </div>
              </div>

              {/* Database & Storage */}
              <div className="category-score-card">
                <div className="category-card-top">
                  <div className="category-title-group">
                    <Database size={16} className="category-card-icon" />
                    <span className="category-card-name">Database Systems</span>
                  </div>
                  <span className="category-score-val">{activeMatch.databaseScore}%</span>
                </div>
                <div className="cat-progress-bar-bg">
                  <div className="cat-progress-bar-fill database" style={{ width: `${activeMatch.databaseScore}%` }} />
                </div>
              </div>

              {/* Years of Experience */}
              <div className="category-score-card">
                <div className="category-card-top">
                  <div className="category-title-group">
                    <TrendingUp size={16} className="category-card-icon" />
                    <span className="category-card-name">Experience Match</span>
                  </div>
                  <span className="category-score-val">{activeMatch.experienceScore}%</span>
                </div>
                <div className="cat-progress-bar-bg">
                  <div className="cat-progress-bar-fill experience" style={{ width: `${activeMatch.experienceScore}%` }} />
                </div>
              </div>
            </div>
          </section>

          {/* Skill-by-Skill Detailed Table */}
          <section className="skill-table-card">
            <div className="table-controls-bar">
              <div className="filter-pills-group">
                <button
                  type="button"
                  className={`filter-pill ${skillFilter === 'ALL' ? 'active' : ''}`}
                  onClick={() => setSkillFilter('ALL')}
                >
                  All Skills ({activeMatch.details?.length || 0})
                </button>
                <button
                  type="button"
                  className={`filter-pill ${skillFilter === 'MATCHED' ? 'active' : ''}`}
                  onClick={() => setSkillFilter('MATCHED')}
                >
                  Matched ({activeMatch.details?.filter((d) => d.matched).length || 0})
                </button>
                <button
                  type="button"
                  className={`filter-pill ${skillFilter === 'MISSING' ? 'active' : ''}`}
                  onClick={() => setSkillFilter('MISSING')}
                >
                  Missing ({activeMatch.details?.filter((d) => !d.matched).length || 0})
                </button>
                <button
                  type="button"
                  className={`filter-pill ${skillFilter === 'REQUIRED' ? 'active' : ''}`}
                  onClick={() => setSkillFilter('REQUIRED')}
                >
                  Required Only ({activeMatch.details?.filter((d) => d.required).length || 0})
                </button>
              </div>

              <div className="search-box-wrap">
                <Search size={14} className="search-icon-pos" />
                <input
                  type="text"
                  placeholder="Filter skills..."
                  value={searchQuery}
                  onChange={(e) => setSearchQuery(e.target.value)}
                />
              </div>
            </div>

            <div className="table-responsive">
              <table className="job-skills-table">
                <thead>
                  <tr>
                    <th>Skill</th>
                    <th>Category</th>
                    <th>Requirement</th>
                    <th>Status</th>
                    <th>Match Type</th>
                    <th>Evidence / Analysis Notes</th>
                  </tr>
                </thead>
                <tbody>
                  {filteredDetails.length === 0 ? (
                    <tr>
                      <td colSpan="6" style={{ textAlign: 'center', padding: '2rem', color: 'var(--text-muted, #94a3b8)' }}>
                        No skills match your active filter.
                      </td>
                    </tr>
                  ) : (
                    filteredDetails.map((item) => (
                      <tr key={item.id || item.skill}>
                        <td className="skill-name-cell">{item.skill}</td>
                        <td>
                          <span className={`category-chip ${item.category?.toLowerCase() || 'cloud'}`}>
                            {item.category}
                          </span>
                        </td>
                        <td>
                          <span className={`level-chip ${item.required ? 'required' : 'preferred'}`}>
                            {item.required ? 'Required' : 'Preferred'}
                          </span>
                        </td>
                        <td>
                          {item.matched ? (
                            <span className="match-status-badge matched">
                              <CheckCircle2 size={15} /> Matched
                            </span>
                          ) : (
                            <span className="match-status-badge missing">
                              <XCircle size={15} /> Missing
                            </span>
                          )}
                        </td>
                        <td>
                          <span className="match-type-pill">{item.matchType || 'NONE'}</span>
                        </td>
                        <td className="evidence-text-cell">{item.evidence || '—'}</td>
                      </tr>
                    ))
                  )}
                </tbody>
              </table>
            </div>
          </section>

          {/* AI Qualitative Coaching & Recommendations */}
          <section className="ai-insights-grid">
            {/* Executive Summary */}
            <div className="insight-card full-width">
              <div className="insight-card-header">
                <div className="insight-title-group">
                  <div className="insight-icon-box summary">
                    <Sparkles size={18} />
                  </div>
                  <h3 className="insight-title">Executive Role Match Summary</h3>
                </div>
              </div>
              <p className="insight-summary-text">{activeMatch.summary}</p>
            </div>

            {/* Candidate Strengths */}
            <div className="insight-card">
              <div className="insight-card-header">
                <div className="insight-title-group">
                  <div className="insight-icon-box strengths">
                    <Award size={18} />
                  </div>
                  <h3 className="insight-title">Core Alignment Strengths</h3>
                </div>
              </div>
              <ul className="insight-list">
                {activeMatch.strengths && activeMatch.strengths.length > 0 ? (
                  activeMatch.strengths.map((s, idx) => (
                    <li key={idx} className="insight-list-item">
                      <CheckCircle2 size={16} className="item-bullet-icon strengths" />
                      <span>{s}</span>
                    </li>
                  ))
                ) : (
                  <li className="insight-list-item" style={{ color: 'var(--text-muted, #94a3b8)' }}>
                    No specific strengths identified.
                  </li>
                )}
              </ul>
            </div>

            {/* Missing Skills & Gaps */}
            <div className="insight-card">
              <div className="insight-card-header">
                <div className="insight-title-group">
                  <div className="insight-icon-box missing">
                    <AlertCircle size={18} />
                  </div>
                  <h3 className="insight-title">Identified Missing Skills & Gaps</h3>
                </div>
              </div>
              <ul className="insight-list">
                {activeMatch.missingSkills && activeMatch.missingSkills.length > 0 ? (
                  activeMatch.missingSkills.map((m, idx) => (
                    <li key={idx} className="insight-list-item">
                      <XCircle size={16} className="item-bullet-icon missing" />
                      <span>{m}</span>
                    </li>
                  ))
                ) : (
                  <li className="insight-list-item" style={{ color: '#34d399' }}>
                    Candidate covers all identified requirements!
                  </li>
                )}
              </ul>
            </div>

            {/* Actionable Recommendations */}
            <div className="insight-card">
              <div className="insight-card-header">
                <div className="insight-title-group">
                  <div className="insight-icon-box recommendations">
                    <TrendingUp size={18} />
                  </div>
                  <h3 className="insight-title">Actionable Portfolio Recommendations</h3>
                </div>
              </div>
              <ul className="insight-list">
                {activeMatch.recommendations && activeMatch.recommendations.length > 0 ? (
                  activeMatch.recommendations.map((r, idx) => (
                    <li key={idx} className="insight-list-item">
                      <ChevronRight size={16} className="item-bullet-icon recommendations" />
                      <span>{r}</span>
                    </li>
                  ))
                ) : (
                  <li className="insight-list-item" style={{ color: 'var(--text-muted, #94a3b8)' }}>
                    No actionable recommendations available.
                  </li>
                )}
              </ul>
            </div>

            {/* Interview Prep Focus */}
            <div className="insight-card">
              <div className="insight-card-header">
                <div className="insight-title-group">
                  <div className="insight-icon-box prep">
                    <Terminal size={18} />
                  </div>
                  <h3 className="insight-title">Interview Deep-Dive Topics</h3>
                </div>
              </div>
              <ul className="insight-list">
                {activeMatch.preparationAreas && activeMatch.preparationAreas.length > 0 ? (
                  activeMatch.preparationAreas.map((p, idx) => (
                    <li key={idx} className="insight-list-item">
                      <ChevronRight size={16} className="item-bullet-icon prep" />
                      <span>{p}</span>
                    </li>
                  ))
                ) : (
                  <li className="insight-list-item" style={{ color: 'var(--text-muted, #94a3b8)' }}>
                    No specific preparation areas specified.
                  </li>
                )}
              </ul>
            </div>
          </section>
        </>
      )}

      {/* Match History Drawer / Section */}
      {historyMatches.length > 0 && (
        <section className="history-card">
          <div className="setup-card-header">
            <div className="setup-card-title-group">
              <div className="setup-icon-box" style={{ background: 'rgba(99, 102, 241, 0.12)', color: '#818cf8' }}>
                <History size={18} />
              </div>
              <h3 className="setup-card-title">Match History ({historyMatches.length})</h3>
            </div>
          </div>

          <div className="history-grid">
            {historyMatches.map((m) => (
              <div
                key={m.id}
                className="history-item-card"
                onClick={() => {
                  setActiveMatch(m);
                  if (m.jobDescriptionId) setSelectedJobId(String(m.jobDescriptionId));
                  if (m.resumeId) setSelectedResumeId(String(m.resumeId));
                  window.scrollTo({ top: 400, behavior: 'smooth' });
                }}
              >
                <div className="history-item-top">
                  <h4 className="history-role-title">{m.jobTitle}</h4>
                  <span className={`history-score-badge ${getScoreClass(m.overallScore)}`}>
                    {m.overallScore}%
                  </span>
                </div>
                {m.company && <span className="history-company-name">{m.company}</span>}
                <div className="history-footer">
                  <Calendar size={12} />
                  <span>{new Date(m.createdAt).toLocaleDateString()}</span>
                  <span>•</span>
                  <span>{m.resumeFileName}</span>
                </div>
              </div>
            ))}
          </div>
        </section>
      )}

      {/* Modal: New Job Description */}
      {showNewJobModal && (
        <div className="job-modal-overlay" onClick={() => setShowNewJobModal(false)}>
          <div className="job-modal-box" onClick={(e) => e.stopPropagation()}>
            <div className="job-modal-header">
              <h3 className="job-modal-title">Create Target Job Description</h3>
              <button
                type="button"
                className="close-modal-btn"
                onClick={() => setShowNewJobModal(false)}
              >
                ✕
              </button>
            </div>

            <form onSubmit={handleCreateJob} className="job-modal-form">
              <div className="form-field-group">
                <label className="form-label">
                  Job Title <span className="req">*</span>
                </label>
                <input
                  type="text"
                  className="job-input-control"
                  placeholder="e.g. Senior Cloud Architect / DevOps Lead"
                  value={newJobTitle}
                  onChange={(e) => setNewJobTitle(e.target.value)}
                  required
                />
              </div>

              <div className="form-field-group">
                <label className="form-label">Company Name</label>
                <input
                  type="text"
                  className="job-input-control"
                  placeholder="e.g. Stripe, Netflix, AWS"
                  value={newJobCompany}
                  onChange={(e) => setNewJobCompany(e.target.value)}
                />
              </div>

              <div className="form-field-group">
                <label className="form-label">Job Posting Source URL</label>
                <input
                  type="url"
                  className="job-input-control"
                  placeholder="https://company.com/careers/..."
                  value={newJobSourceUrl}
                  onChange={(e) => setNewJobSourceUrl(e.target.value)}
                />
              </div>

              <div className="form-field-group">
                <label className="form-label">
                  Job Description / Requirements <span className="req">*</span>
                </label>
                <textarea
                  className="job-input-control"
                  rows={8}
                  placeholder={`Paste the job description or requirements here...\n\nExample:\nRequirements:\n- 5+ years Java and Spring Boot\n- Production AWS experience\n- Docker containerization\n\nNice to have:\n- Kubernetes\n- Redis caching`}
                  value={newJobDescription}
                  onChange={(e) => setNewJobDescription(e.target.value)}
                  required
                />
              </div>

              <div className="modal-actions-row">
                <button
                  type="button"
                  className="filter-pill"
                  onClick={() => setShowNewJobModal(false)}
                  disabled={savingJob}
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="run-match-btn"
                  disabled={savingJob}
                >
                  {savingJob ? 'Saving Job...' : 'Save Job Description'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};

export default JobMatch;
