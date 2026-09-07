import React, { useState, useEffect, useRef } from 'react';
import { Card, CardContent, CardHeader } from '../ui/Card';
import { Button } from '../ui/Button';
import { Spinner } from '../ui/Spinner';
import { EmptyState } from '../ui/EmptyState';
import { ErrorState } from '../ui/ErrorState';
import { ConfirmDialog } from '../ui/ConfirmDialog';
import { useToast } from '../../context/ToastContext';
import {
  UploadCloud,
  File,
  FileText,
  FileCode,
  FileArchive,
  Image as ImageIcon,
  Download,
  Trash2,
  X,
  Calendar,
  User,
  AlertCircle,
  ExternalLink
} from 'lucide-react';
import api from '../../api/axios';
import './ApplicationFilesSection.css';

export const ApplicationFilesSection = ({ applicationId }) => {
  const { addToast } = useToast();
  const fileInputRef = useRef(null);

  const [files, setFiles] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  // Selected file for upload
  const [selectedFile, setSelectedFile] = useState(null);
  const [uploadLoading, setUploadLoading] = useState(false);
  const [uploadError, setUploadError] = useState(null);
  const [isDragOver, setIsDragOver] = useState(false);

  // Deletion modal
  const [deleteTarget, setDeleteTarget] = useState(null);
  const [deleteLoading, setDeleteLoading] = useState(false);

  // Access URL loading state by file ID
  const [urlLoadingId, setUrlLoadingId] = useState(null);

  const fetchFiles = async () => {
    setLoading(true);
    setError(null);
    try {
      const response = await api.get(`/applications/${applicationId}/files`);
      setFiles(response.data);
    } catch (err) {
      console.error('Failed to load application files', err);
      if (err.response?.status === 403) {
        setError('You do not have permission to view files for this application.');
      } else if (err.response?.status === 404) {
        setError('Application not found.');
      } else {
        setError('Unable to load files from storage service.');
      }
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (applicationId) {
      fetchFiles();
    }
  }, [applicationId]);

  const formatFileSize = (bytes) => {
    if (!bytes && bytes !== 0) return '—';
    if (bytes < 1024) return `${bytes} B`;
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
    return `${(bytes / (1024 * 1024)).toFixed(2)} MB`;
  };

  const formatDate = (dateString) => {
    if (!dateString) return '—';
    try {
      const date = new Date(dateString);
      return new Intl.DateTimeFormat('en-US', {
        month: 'short',
        day: 'numeric',
        year: 'numeric',
        hour: '2-digit',
        minute: '2-digit',
      }).format(date);
    } catch {
      return dateString;
    }
  };

  const getFileIcon = (fileName = '', contentType = '') => {
    const ext = fileName.split('.').pop().toLowerCase();
    if (['zip', 'tar', 'gz', 'tgz', 'rar', '7z'].includes(ext)) {
      return <FileArchive size={16} className="file-type-icon archive-icon" />;
    }
    if (['json', 'yaml', 'yml', 'xml', 'js', 'jsx', 'ts', 'tsx', 'py', 'java', 'sql', 'sh'].includes(ext)) {
      return <FileCode size={16} className="file-type-icon code-icon" />;
    }
    if (['png', 'jpg', 'jpeg', 'gif', 'svg', 'webp'].includes(ext) || contentType.startsWith('image/')) {
      return <ImageIcon size={16} className="file-type-icon image-icon" />;
    }
    if (['txt', 'md', 'pdf', 'csv', 'log', 'doc', 'docx'].includes(ext) || contentType.startsWith('text/')) {
      return <FileText size={16} className="file-type-icon text-icon" />;
    }
    return <File size={16} className="file-type-icon default-icon" />;
  };

  const handleFileSelect = (file) => {
    setUploadError(null);
    if (!file) return;

    if (file.size > 10 * 1024 * 1024) {
      setUploadError('File size exceeds the 10MB limit.');
      addToast('File size exceeds the 10MB limit.', 'error');
      return;
    }

    const dangerous = ['exe', 'sh', 'bat', 'cmd', 'jar', 'jsp', 'dll', 'so'];
    const ext = file.name.split('.').pop().toLowerCase();
    if (dangerous.includes(ext)) {
      setUploadError(`File extension .${ext} is restricted and cannot be uploaded.`);
      addToast(`File extension .${ext} is restricted.`, 'error');
      return;
    }

    setSelectedFile(file);
  };

  const handleDrop = (e) => {
    e.preventDefault();
    setIsDragOver(false);
    if (e.dataTransfer.files && e.dataTransfer.files[0]) {
      handleFileSelect(e.dataTransfer.files[0]);
    }
  };

  const handleUploadSubmit = async () => {
    if (!selectedFile || uploadLoading) return;

    setUploadLoading(true);
    setUploadError(null);

    const formData = new FormData();
    formData.append('file', selectedFile);

    try {
      const response = await api.post(`/applications/${applicationId}/files`, formData, {
        headers: { 'Content-Type': 'multipart/form-data' },
      });
      addToast(`File "${response.data.fileName}" uploaded successfully`, 'success');
      setFiles((prev) => [response.data, ...prev]);
      setSelectedFile(null);
      if (fileInputRef.current) {
        fileInputRef.current.value = '';
      }
    } catch (err) {
      console.error('File upload failed', err);
      const msg = err.response?.data?.message || 'Failed to upload file to storage.';
      setUploadError(msg);
      addToast(msg, 'error');
    } finally {
      setUploadLoading(false);
    }
  };

  const handleDownload = async (file) => {
    setUrlLoadingId(file.id);
    try {
      const response = await api.get(`/files/${file.id}/access-url`);
      const accessUrl = response.data.accessUrl;
      // Open in a new tab securely
      window.open(accessUrl, '_blank', 'noopener,noreferrer');
      addToast('Secure pre-signed URL generated (valid for 15m)', 'info');
    } catch (err) {
      console.error('Failed to generate pre-signed URL', err);
      const msg = err.response?.data?.message || 'Failed to generate download link';
      addToast(msg, 'error');
    } finally {
      setUrlLoadingId(null);
    }
  };

  const handleDeleteConfirm = async () => {
    if (!deleteTarget) return;
    setDeleteLoading(true);
    try {
      await api.delete(`/files/${deleteTarget.id}`);
      addToast(`File "${deleteTarget.fileName}" deleted`, 'success');
      setFiles((prev) => prev.filter((f) => f.id !== deleteTarget.id));
      setDeleteTarget(null);
    } catch (err) {
      console.error('Failed to delete file', err);
      const msg = err.response?.data?.message || 'Failed to delete file from storage';
      addToast(msg, 'error');
    } finally {
      setDeleteLoading(false);
    }
  };

  return (
    <div className="application-files-section">
      <div className="section-header-row">
        <div>
          <h2 className="section-title">Files & Artifacts</h2>
          <p className="page-description" style={{ margin: 0 }}>
            Secure S3-backed storage for build artifacts, configs, and deployment assets.
          </p>
        </div>
      </div>

      {/* Upload Zone Card */}
      <Card className="upload-card">
        <CardContent>
          <div
            className={`file-dropzone ${isDragOver ? 'dropzone-active' : ''}`}
            onDragOver={(e) => { e.preventDefault(); setIsDragOver(true); }}
            onDragLeave={() => setIsDragOver(false)}
            onDrop={handleDrop}
            onClick={() => !selectedFile && fileInputRef.current?.click()}
          >
            <input
              type="file"
              ref={fileInputRef}
              style={{ display: 'none' }}
              onChange={(e) => handleFileSelect(e.target.files[0])}
            />

            {!selectedFile ? (
              <div className="dropzone-empty-content">
                <div className="upload-icon-wrapper">
                  <UploadCloud size={24} className="upload-icon" />
                </div>
                <div className="dropzone-text">
                  <span className="dropzone-primary-text">
                    Click to browse or drag and drop files here
                  </span>
                  <span className="dropzone-sub-text">
                    Max file size: 10MB • Private S3 storage with short-lived pre-signed access
                  </span>
                </div>
              </div>
            ) : (
              <div className="selected-file-preview" onClick={(e) => e.stopPropagation()}>
                <div className="preview-info-row">
                  <div className="preview-icon-wrapper">
                    {getFileIcon(selectedFile.name, selectedFile.type)}
                  </div>
                  <div className="preview-details">
                    <span className="preview-name">{selectedFile.name}</span>
                    <span className="preview-meta">
                      {formatFileSize(selectedFile.size)} • {selectedFile.type || 'application/octet-stream'}
                    </span>
                  </div>
                  <button
                    className="btn-clear-selection"
                    onClick={() => {
                      setSelectedFile(null);
                      setUploadError(null);
                      if (fileInputRef.current) fileInputRef.current.value = '';
                    }}
                    disabled={uploadLoading}
                    title="Remove selection"
                  >
                    <X size={16} />
                  </button>
                </div>

                {uploadError && (
                  <div className="upload-error-banner">
                    <AlertCircle size={14} className="error-icon" />
                    <span>{uploadError}</span>
                  </div>
                )}

                <div className="preview-actions">
                  <Button
                    variant="secondary"
                    onClick={() => {
                      setSelectedFile(null);
                      setUploadError(null);
                      if (fileInputRef.current) fileInputRef.current.value = '';
                    }}
                    disabled={uploadLoading}
                  >
                    Cancel
                  </Button>
                  <Button
                    onClick={handleUploadSubmit}
                    disabled={uploadLoading}
                  >
                    {uploadLoading ? (
                      <span className="btn-loading-content">
                        <Spinner size={16} />
                        <span>Uploading to S3...</span>
                      </span>
                    ) : (
                      'Upload File'
                    )}
                  </Button>
                </div>
              </div>
            )}
          </div>
        </CardContent>
      </Card>

      {/* Files List Table */}
      <Card className="files-table-card">
        {loading ? (
          <div className="files-loading-wrapper">
            <Spinner size={32} />
            <p className="loading-text">Loading application files...</p>
          </div>
        ) : error ? (
          <CardContent>
            <ErrorState
              title="Storage Error"
              message={error}
              onRetry={fetchFiles}
            />
          </CardContent>
        ) : files.length === 0 ? (
          <CardContent>
            <EmptyState
              icon={File}
              title="No files uploaded yet"
              description="Upload build artifacts, configuration files, or release assets to this application."
              actionText="Upload File"
              onAction={() => fileInputRef.current?.click()}
            />
          </CardContent>
        ) : (
          <div className="table-responsive">
            <table className="files-table">
              <thead>
                <tr>
                  <th>File</th>
                  <th>Type</th>
                  <th>Size</th>
                  <th>Uploaded</th>
                  <th>Uploaded By</th>
                  <th className="th-actions">Actions</th>
                </tr>
              </thead>
              <tbody>
                {files.map((file) => (
                  <tr key={file.id} className="file-row">
                    <td className="file-name-cell">
                      <div className="file-title-wrapper">
                        {getFileIcon(file.fileName, file.contentType)}
                        <span className="file-name-text" title={file.fileName}>
                          {file.fileName}
                        </span>
                      </div>
                    </td>
                    <td>
                      <span className="content-type-badge">
                        {file.contentType ? file.contentType.split('/').pop() : 'binary'}
                      </span>
                    </td>
                    <td>
                      <span className="file-size-text">{formatFileSize(file.fileSize)}</span>
                    </td>
                    <td className="date-cell">
                      <span className="date-wrapper">
                        <Calendar size={13} className="date-icon" />
                        {formatDate(file.uploadedAt)}
                      </span>
                    </td>
                    <td className="uploader-cell">
                      <span className="uploader-wrapper">
                        <User size={13} className="user-icon" />
                        <span>{file.uploadedByName || file.uploadedByEmail || '—'}</span>
                      </span>
                    </td>
                    <td className="td-actions">
                      <div className="action-buttons-group">
                        <Button
                          variant="secondary"
                          className="btn-download-action"
                          onClick={() => handleDownload(file)}
                          disabled={urlLoadingId === file.id}
                          title="Generate pre-signed download link"
                        >
                          {urlLoadingId === file.id ? (
                            <Spinner size={14} />
                          ) : (
                            <>
                              <Download size={14} className="btn-icon-mr" />
                              <span>Download</span>
                            </>
                          )}
                        </Button>
                        <button
                          className="btn-icon-action btn-icon-danger"
                          title="Delete File"
                          onClick={() => setDeleteTarget(file)}
                          aria-label={`Delete ${file.fileName}`}
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
      </Card>

      {/* Delete Confirmation Dialog */}
      <ConfirmDialog
        isOpen={Boolean(deleteTarget)}
        title="Delete File?"
        message={`This will permanently remove "${deleteTarget?.fileName}" from this application and AWS S3 storage. This action cannot be undone.`}
        confirmText="Delete File"
        confirmVariant="danger"
        isLoading={deleteLoading}
        onConfirm={handleDeleteConfirm}
        onCancel={() => !deleteLoading && setDeleteTarget(null)}
      />
    </div>
  );
};
