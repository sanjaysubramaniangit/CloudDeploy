import React, { useState, useEffect, useRef } from 'react';
import {
  Sparkles,
  Shield,
  ShieldAlert,
  Info,
  Terminal,
  Copy,
  Check,
  Plus,
  Trash2,
  Paperclip,
  ChevronDown,
  ChevronUp,
  AlertTriangle,
  CheckCircle,
  Server,
  HardDrive,
  Send,
  X,
  Lock
} from 'lucide-react';
import api from '../../api/axios';
import { useToast } from '../../context/ToastContext';
import { Spinner } from '../../components/ui/Spinner';
import './CloudAssistant.css';

// 5 Quick diagnostic starters
const QUICK_STARTERS = [
  {
    title: 'CrashLoopBackOff diagnosis',
    prompt: 'Investigate Kubernetes pod stuck in CrashLoopBackOff state. What are the common root causes and how do I debug the container entrypoint?'
  },
  {
    title: '502 Bad Gateway debugging',
    prompt: 'Application is returning 502 Bad Gateway intermittently behind Nginx / ALB reverse proxy. How do I isolate whether the upstream Spring Boot process died or timed out?'
  },
  {
    title: 'IAM AccessDenied remediation',
    prompt: 'Troubleshoot AWS IAM AccessDeniedException when the service attempts to access a private S3 bucket. What IAM policies or trust relationships should be checked?'
  },
  {
    title: 'OOMKilled memory analysis',
    prompt: 'Container was terminated with exit code 137 (OOMKilled). How do I configure JVM heap limits (-Xmx, -XX:MaxRAMPercentage) and container resource limits to prevent this?'
  },
  {
    title: 'Database connection pool timeout',
    prompt: 'HikariPool connection timeout: Connection is not available, request timed out after 30000ms. How do I diagnose connection leaks and tune pool settings?'
  }
];

export const CloudAssistant = () => {
  const { addToast } = useToast();
  const chatBottomRef = useRef(null);

  // Global / initial data states
  const [applications, setApplications] = useState([]);
  const [deployments, setDeployments] = useState([]);
  const [sessions, setSessions] = useState([]);
  const [loadingInitial, setLoadingInitial] = useState(true);

  // Current session & context states
  const [activeSessionId, setActiveSessionId] = useState(null);
  const [activeSessionDetail, setActiveSessionDetail] = useState(null);
  const [selectedAppId, setSelectedAppId] = useState('');
  const [selectedDepId, setSelectedDepId] = useState('');

  // Chat message feed
  const [messages, setMessages] = useState([]);

  // Prompt input states
  const [query, setQuery] = useState('');
  const [logSnippet, setLogSnippet] = useState('');
  const [showLogsDrawer, setShowLogsDrawer] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  // Controlled 503 / Error state
  const [aiError, setAiError] = useState(null);

  // Delete modal state
  const [sessionToDelete, setSessionToDelete] = useState(null);
  const [deletingSession, setDeletingSession] = useState(false);

  // Copied command tracking
  const [copiedIndex, setCopiedIndex] = useState(null);

  // Expanded log snippets in user bubbles
  const [expandedLogs, setExpandedLogs] = useState({});

  // Auto scroll to bottom
  const scrollToBottom = () => {
    chatBottomRef.current?.scrollIntoView({ behavior: 'smooth' });
  };

  useEffect(() => {
    scrollToBottom();
  }, [messages, submitting]);

  // Load initial applications and session history
  const loadInitialData = async () => {
    setLoadingInitial(true);
    try {
      const [appsRes, sessionsRes] = await Promise.all([
        api.get('/applications'),
        api.get('/ai/assistant/sessions')
      ]);
      setApplications(appsRes.data || []);
      setSessions(sessionsRes.data || []);
    } catch (err) {
      console.error('Failed to load initial assistant data', err);
      addToast('Failed to load troubleshooting data', 'error');
    } finally {
      setLoadingInitial(false);
    }
  };

  useEffect(() => {
    loadInitialData();
  }, []);

  // Fetch deployments when selectedAppId changes (only if not locked in an active session)
  useEffect(() => {
    if (!selectedAppId) {
      setDeployments([]);
      setSelectedDepId('');
      return;
    }

    const fetchDeployments = async () => {
      try {
        const res = await api.get(`/applications/${selectedAppId}/deployments`);
        setDeployments(res.data || []);
      } catch (err) {
        console.error('Failed to load deployments for application', err);
        setDeployments([]);
      }
    };

    fetchDeployments();
  }, [selectedAppId]);

  // Load full session detail
  const handleSelectSession = async (sessionId) => {
    if (activeSessionId === sessionId) return;
    try {
      const res = await api.get(`/ai/assistant/sessions/${sessionId}`);
      const detail = res.data;
      setActiveSessionId(detail.id);
      setActiveSessionDetail(detail);
      setMessages(detail.messages || []);
      setSelectedAppId(detail.applicationId ? String(detail.applicationId) : '');
      setSelectedDepId(detail.deploymentId ? String(detail.deploymentId) : '');
      setAiError(null);
    } catch (err) {
      console.error('Failed to load session detail', err);
      addToast('Failed to load troubleshooting thread', 'error');
    }
  };

  // Start fresh thread
  const handleNewThread = () => {
    setActiveSessionId(null);
    setActiveSessionDetail(null);
    setMessages([]);
    setSelectedAppId('');
    setSelectedDepId('');
    setQuery('');
    setLogSnippet('');
    setShowLogsDrawer(false);
    setAiError(null);
  };

  // Clear context
  const handleClearContext = () => {
    if (activeSessionId) return; // Locked in session
    setSelectedAppId('');
    setSelectedDepId('');
    setDeployments([]);
  };

  // Submit prompt / turn
  const handleSubmitPrompt = async (e) => {
    if (e) e.preventDefault();
    if (!query.trim() || submitting) return;

    const trimmedQuery = query.trim();
    const trimmedLogs = logSnippet.trim() ? logSnippet.trim() : null;

    // Optimistic user message representation
    const tempUserMessage = {
      id: Date.now(),
      role: 'USER',
      content: trimmedQuery,
      logSnippet: trimmedLogs,
      createdAt: new Date().toISOString()
    };

    setMessages((prev) => [...prev, tempUserMessage]);
    setQuery('');
    setLogSnippet('');
    setShowLogsDrawer(false);
    setSubmitting(true);
    setAiError(null);

    try {
      const payload = {
        query: trimmedQuery,
        logSnippet: trimmedLogs,
        applicationId: selectedAppId ? Number(selectedAppId) : null,
        deploymentId: selectedDepId ? Number(selectedDepId) : null,
        sessionId: activeSessionId || null
      };

      const res = await api.post('/ai/assistant/chat', payload);
      const data = res.data;

      // Update active session info
      setActiveSessionId(data.sessionId);

      // Append assistant message
      const assistantMsg = data.message || data.assistantMessage;
      if (assistantMsg) {
        setMessages((prev) => [...prev, assistantMsg]);
      }

      // Refresh sessions history list
      const sessionsRes = await api.get('/ai/assistant/sessions');
      setSessions(sessionsRes.data || []);

      addToast('Diagnostic analysis complete', 'success');
    } catch (err) {
      console.error('Troubleshooting chat failed', err);
      if (err.response?.status === 503) {
        const errorMsg =
          err.response?.data?.message ||
          'AI cloud troubleshooting assistant is unavailable because the AI provider is not configured. Configure the AI provider to enable cloud troubleshooting assistance.';
        setAiError(errorMsg);
        addToast(errorMsg, 'error');
      } else {
        const msg = err.response?.data?.message || 'Troubleshooting request failed';
        addToast(msg, 'error');
      }
    } finally {
      setSubmitting(false);
    }
  };

  // Delete session
  const handleDeleteSession = async () => {
    if (!sessionToDelete) return;
    setDeletingSession(true);
    try {
      await api.delete(`/ai/assistant/sessions/${sessionToDelete.id}`);
      setSessions((prev) => prev.filter((s) => s.id !== sessionToDelete.id));

      if (activeSessionId === sessionToDelete.id) {
        handleNewThread();
      }

      addToast('Troubleshooting thread deleted', 'success');
      setSessionToDelete(null);
    } catch (err) {
      console.error('Failed to delete session', err);
      addToast(err.response?.data?.message || 'Failed to delete thread', 'error');
    } finally {
      setDeletingSession(false);
    }
  };

  // Copy command to clipboard
  const handleCopyCommand = (cmdText, uniqueKey) => {
    navigator.clipboard.writeText(cmdText);
    setCopiedIndex(uniqueKey);
    setTimeout(() => setCopiedIndex(null), 2000);
    addToast('Advisory command copied to clipboard', 'info');
  };

  // Toggle attached logs viewer in user bubble
  const toggleAttachedLogs = (msgId) => {
    setExpandedLogs((prev) => ({
      ...prev,
      [msgId]: !prev[msgId]
    }));
  };

  // Helper for severity styling
  const getSeverityBadgeClass = (severity) => {
    switch (severity?.toUpperCase()) {
      case 'CRITICAL':
        return 'severity-critical';
      case 'HIGH':
        return 'severity-high';
      case 'MEDIUM':
        return 'severity-medium';
      case 'LOW':
        return 'severity-low';
      case 'INFO':
      default:
        return 'severity-info';
    }
  };

  // Helper for selected application details
  const selectedApp = applications.find((a) => String(a.id) === String(selectedAppId));
  const isContextLocked = Boolean(activeSessionId);

  if (loadingInitial) {
    return (
      <div className="cloud-assistant-page">
        <div style={{ textAlign: 'center', padding: '4rem 1rem' }}>
          <Spinner size={36} />
          <p style={{ marginTop: '1rem', color: '#94a3b8' }}>Loading Cloud Troubleshooting Assistant...</p>
        </div>
      </div>
    );
  }

  return (
    <div className="cloud-assistant-page">
      {/* Page Header */}
      <div className="assistant-header">
        <div className="assistant-header-badge">
          <Sparkles size={14} />
          <span>AI Cloud Troubleshooting Assistant</span>
        </div>
        <h1 className="assistant-title">Cloud Infrastructure Troubleshooting</h1>
        <p className="assistant-subtitle">
          AI-powered, context-aware cloud troubleshooting assistant grounded in application/deployment metadata and user-provided logs.
        </p>

        {/* Truthful Architecture Disclaimer Banner */}
        <div className="assistant-disclaimer-banner" role="note">
          <Info size={18} />
          <div>
            Assistant provides diagnostic guidance grounded strictly in stored application metadata and user-provided diagnostic logs.
            Live infrastructure telemetry (AWS, Kubernetes, Docker, CloudWatch) is not connected.
          </div>
        </div>
      </div>

      {/* Controlled 503 Alert Banner */}
      {aiError && (
        <div className="assistant-unconfigured-banner" role="alert">
          <ShieldAlert size={24} />
          <div>
            <h4>AI Provider Unavailable (HTTP 503)</h4>
            <p>{aiError}</p>
          </div>
        </div>
      )}

      {/* Main Workspace: Sidebar + Chat Feed */}
      <div className="assistant-workspace-layout">
        {/* Left: Sessions History Sidebar */}
        <aside className="assistant-sessions-sidebar" aria-label="Troubleshooting Threads">
          <div className="sidebar-header-row">
            <h3>
              <Shield size={18} />
              <span>Threads</span>
            </h3>
            <button
              type="button"
              className="btn-new-thread"
              onClick={handleNewThread}
              title="Start a new troubleshooting thread"
            >
              <Plus size={14} />
              <span>New</span>
            </button>
          </div>

          <div className="sessions-scroll-list">
            {sessions.length === 0 ? (
              <div className="empty-sessions-note">
                No past troubleshooting threads yet. Ask a question to begin.
              </div>
            ) : (
              sessions.map((s) => {
                const isActive = s.id === activeSessionId;
                return (
                  <div
                    key={s.id}
                    className={`session-item ${isActive ? 'active' : ''}`}
                    onClick={() => handleSelectSession(s.id)}
                  >
                    <div className="session-info">
                      <span className="session-title-text" title={s.title}>
                        {s.title}
                      </span>
                      <span className="session-meta-text">
                        {s.applicationName ? (
                          <span>{s.applicationName} • </span>
                        ) : null}
                        <span>{s.messageCount || 0} msgs</span>
                      </span>
                    </div>
                    <button
                      type="button"
                      className="btn-delete-session"
                      onClick={(e) => {
                        e.stopPropagation();
                        setSessionToDelete(s);
                      }}
                      title="Delete thread"
                    >
                      <Trash2 size={14} />
                    </button>
                  </div>
                );
              })
            )}
          </div>
        </aside>

        {/* Right: Main Chat & Diagnostic Pane */}
        <main className="assistant-chat-pane">
          {/* Top Context Grounding Bar */}
          <div className="context-grounding-bar">
            <div className="context-selectors">
              {/* Application Context */}
              <div className="context-group">
                <span className="context-label">
                  <Server size={14} /> App:
                </span>
                <select
                  className="context-select"
                  value={selectedAppId}
                  onChange={(e) => setSelectedAppId(e.target.value)}
                  disabled={isContextLocked || submitting}
                >
                  <option value="">No Application Context</option>
                  {applications.map((app) => (
                    <option key={app.id} value={app.id}>
                      {app.name} ({app.deploymentStatus || 'UNKNOWN'})
                    </option>
                  ))}
                </select>
              </div>

              {/* Deployment Context */}
              <div className="context-group">
                <span className="context-label">
                  <HardDrive size={14} /> Deploy:
                </span>
                <select
                  className="context-select"
                  value={selectedDepId}
                  onChange={(e) => setSelectedDepId(e.target.value)}
                  disabled={isContextLocked || !selectedAppId || submitting}
                >
                  <option value="">
                    {!selectedAppId ? 'Select App First' : 'No Deployment Context'}
                  </option>
                  {deployments.map((dep) => (
                    <option key={dep.id} value={dep.id}>
                      {dep.version} ({dep.commitHash ? dep.commitHash.substring(0, 7) : 'no hash'}) - {dep.status}
                    </option>
                  ))}
                </select>
              </div>

              {/* Locked Context Badge */}
              {isContextLocked && (
                <div className="context-locked-badge" title="Context is bound to active thread">
                  <Lock size={12} />
                  <span>Session Context Locked</span>
                </div>
              )}
            </div>

            {/* Clear Context Button */}
            {!isContextLocked && (selectedAppId || selectedDepId) && (
              <button
                type="button"
                className="btn-clear-context"
                onClick={handleClearContext}
                disabled={submitting}
              >
                Clear Context
              </button>
            )}
          </div>

          {/* Chat Messages Feed */}
          <div className="chat-messages-scroll">
            {messages.length === 0 ? (
              <div className="chat-welcome-box">
                <div className="chat-welcome-icon">
                  <Shield size={28} />
                </div>
                <h3>Cloud Diagnostic & SRE Assistant</h3>
                <p>
                  Ground your troubleshooting session in an existing CloudDeploy application and deployment,
                  or ask general cloud questions. Attach stack traces or container logs for deep root-cause analysis.
                </p>

                {/* Quick Diagnostic Starters */}
                <div className="quick-starters-section">
                  <span className="quick-starters-title">Common Diagnostic Inquiries:</span>
                  <div className="starters-grid">
                    {QUICK_STARTERS.map((starter, idx) => (
                      <button
                        key={idx}
                        type="button"
                        className="btn-starter-chip"
                        onClick={() => {
                          setQuery(starter.prompt);
                        }}
                      >
                        <Sparkles size={13} />
                        <span>{starter.title}</span>
                      </button>
                    ))}
                  </div>
                </div>
              </div>
            ) : (
              messages.map((msg, mIdx) => {
                const isUser = msg.role === 'USER';

                if (isUser) {
                  return (
                    <div key={msg.id || mIdx} className="message-bubble-user">
                      <div className="user-text-bubble">{msg.content}</div>

                      {/* If user attached logs */}
                      {msg.logSnippet && (
                        <div className="user-attached-logs">
                          <div
                            className="logs-header-toggle"
                            onClick={() => toggleAttachedLogs(msg.id || mIdx)}
                          >
                            <span>
                              Attached Log Snippet ({msg.logSnippet.length} chars)
                            </span>
                            {expandedLogs[msg.id || mIdx] ? (
                              <ChevronUp size={14} />
                            ) : (
                              <ChevronDown size={14} />
                            )}
                          </div>
                          {expandedLogs[msg.id || mIdx] && (
                            <pre className="attached-logs-pre">{msg.logSnippet}</pre>
                          )}
                        </div>
                      )}
                    </div>
                  );
                }

                // Assistant Message Card
                const uniqueKey = msg.id || mIdx;
                return (
                  <div key={uniqueKey} className="message-card-assistant">
                    {/* Header with Severity Badge */}
                    <div className="assistant-message-header">
                      <div className="assistant-identity">
                        <Shield size={16} />
                        <span>CloudDeploy SRE Assistant</span>
                      </div>
                      <span className={`severity-badge ${getSeverityBadgeClass(msg.severity)}`}>
                        {msg.severity || 'INFO'} Severity
                      </span>
                    </div>

                    {/* Content / Analysis */}
                    <div className="assistant-analysis-text">{msg.content}</div>

                    {/* Root Cause Callout Box */}
                    {msg.rootCause && (
                      <div className="root-cause-box">
                        <div className="root-cause-title">
                          <AlertTriangle size={15} />
                          <span>Identified Root Cause</span>
                        </div>
                        <p className="root-cause-text">{msg.rootCause}</p>
                      </div>
                    )}

                    {/* Step-by-Step Remediation Plan */}
                    {msg.remediationSteps && msg.remediationSteps.length > 0 && (
                      <div className="remediation-section">
                        <h4 className="remediation-heading">
                          <CheckCircle size={15} />
                          <span>Step-by-Step Remediation Plan</span>
                        </h4>
                        <ul className="remediation-list">
                          {msg.remediationSteps.map((step, sIdx) => (
                            <li key={sIdx} className="remediation-item">
                              <span className="step-number-badge">{sIdx + 1}</span>
                              <span>{step}</span>
                            </li>
                          ))}
                        </ul>
                      </div>
                    )}

                    {/* Advisory Suggested CLI Commands */}
                    {msg.suggestedCommands && msg.suggestedCommands.length > 0 && (
                      <div className="advisory-commands-section">
                        <div className="advisory-commands-header">
                          <h4 className="advisory-heading">
                            <Terminal size={15} />
                            <span>Advisory Diagnostic Commands</span>
                          </h4>
                          <span className="advisory-disclaimer-pill">
                            ⚠️ Advisory text only — run in your trusted shell
                          </span>
                        </div>

                        {msg.suggestedCommands.map((cmd, cIdx) => {
                          const cmdKey = `${uniqueKey}-${cIdx}`;
                          const isCopied = copiedIndex === cmdKey;
                          return (
                            <div key={cIdx} className="command-card">
                              <span className="command-code-text">{cmd}</span>
                              <button
                                type="button"
                                className={`btn-copy-command ${isCopied ? 'copied' : ''}`}
                                onClick={() => handleCopyCommand(cmd, cmdKey)}
                                title="Copy command to clipboard"
                              >
                                {isCopied ? <Check size={12} /> : <Copy size={12} />}
                                <span>{isCopied ? 'Copied' : 'Copy'}</span>
                              </button>
                            </div>
                          );
                        })}
                      </div>
                    )}
                  </div>
                );
              })
            )}

            {/* Submitting Loading Indicator */}
            {submitting && (
              <div className="message-card-assistant" style={{ opacity: 0.85 }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
                  <Spinner size={16} />
                  <span style={{ fontSize: '0.9rem', color: '#38bdf8', fontWeight: 500 }}>
                    Grounding diagnostic context and generating remediation plan...
                  </span>
                </div>
              </div>
            )}

            <div ref={chatBottomRef} />
          </div>

          {/* Bottom Chat Input Form */}
          <div className="chat-input-container">
            {/* Logs Attachment Collapsible Drawer */}
            <div className="logs-attach-toggle-bar">
              <button
                type="button"
                className="btn-toggle-logs"
                onClick={() => setShowLogsDrawer(!showLogsDrawer)}
              >
                <Paperclip size={14} />
                <span>
                  {showLogsDrawer ? 'Hide Logs Drawer' : 'Attach Diagnostic Logs / Stack Trace'}
                </span>
                {logSnippet.trim() && (
                  <span className="logs-badge-attached">
                    {logSnippet.length} chars
                  </span>
                )}
              </button>
              <span style={{ fontSize: '0.75rem', color: '#64748b' }}>
                Credentials automatically scrubbed
              </span>
            </div>

            {showLogsDrawer && (
              <div className="logs-attach-drawer">
                <textarea
                  className="logs-textarea"
                  placeholder="Paste raw container stdout/stderr, stack traces, cloud-init output, or curl outputs here..."
                  value={logSnippet}
                  onChange={(e) => setLogSnippet(e.target.value)}
                  maxLength={8000}
                />
                <div className="logs-helper-row">
                  <span>Sensitive tokens (AWS keys, passwords, bearer tokens) are sanitized.</span>
                  <span>{logSnippet.length} / 8,000</span>
                </div>
              </div>
            )}

            {/* Prompt Textarea & Send Button */}
            <form onSubmit={handleSubmitPrompt} className="prompt-input-form">
              <textarea
                className="prompt-textarea"
                rows={2}
                placeholder="Ask about deployment failures, pod crashes, ingress 502s, or cloud IAM issues..."
                value={query}
                onChange={(e) => setQuery(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === 'Enter' && !e.shiftKey) {
                    e.preventDefault();
                    handleSubmitPrompt();
                  }
                }}
                disabled={submitting}
                maxLength={4000}
              />
              <button
                type="submit"
                className="btn-send-prompt"
                disabled={submitting || !query.trim()}
              >
                {submitting ? (
                  <Spinner size={16} />
                ) : (
                  <>
                    <Send size={16} />
                    <span>Send</span>
                  </>
                )}
              </button>
            </form>
          </div>
        </main>
      </div>

      {/* Delete Thread Confirmation Modal */}
      {sessionToDelete && (
        <div className="modal-overlay" onClick={() => setSessionToDelete(null)}>
          <div className="modal-content" onClick={(e) => e.stopPropagation()}>
            <div className="modal-header">
              <h3>Delete Troubleshooting Thread?</h3>
              <button
                type="button"
                className="btn-modal-close"
                onClick={() => setSessionToDelete(null)}
              >
                <X size={20} />
              </button>
            </div>
            <p style={{ color: '#cbd5e1', fontSize: '0.9rem', lineHeight: 1.5, margin: 0 }}>
              Are you sure you want to delete the thread <strong>"{sessionToDelete.title}"</strong>?
              All messages, diagnostic outputs, and suggested commands will be permanently removed.
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
                className="btn-modal-delete"
                onClick={handleDeleteSession}
                disabled={deletingSession}
              >
                {deletingSession ? 'Deleting...' : 'Delete Thread'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
export default CloudAssistant;
