import React, { useState, useRef } from 'react';
import { UploadCloud, FileText, X, AlertCircle, CheckCircle2 } from 'lucide-react';
import { Card, CardContent, CardHeader } from '../../components/ui/Card';
import { Button } from '../../components/ui/Button';
import { Spinner } from '../../components/ui/Spinner';
import { useToast } from '../../context/ToastContext';
import api from '../../api/axios';
import './ResumeAi.css';

const ALLOWED_EXTENSIONS = ['.pdf', '.docx', '.txt'];
const MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB

export const ResumeUpload = ({ onUploadSuccess }) => {
  const { addToast } = useToast();
  const fileInputRef = useRef(null);

  const [selectedFile, setSelectedFile] = useState(null);
  const [isDragOver, setIsDragOver] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [validationError, setValidationError] = useState(null);

  const validateFile = (file) => {
    if (!file) return 'No file selected';

    const fileName = file.name.toLowerCase();
    const hasValidExt = ALLOWED_EXTENSIONS.some((ext) => fileName.endsWith(ext));
    if (!hasValidExt) {
      return 'Invalid file type. Only PDF, DOCX, and TXT files are supported.';
    }

    if (file.size === 0) {
      return 'Selected file is empty.';
    }

    if (file.size > MAX_FILE_SIZE) {
      return 'File exceeds maximum upload size of 10MB.';
    }

    return null;
  };

  const handleFileSelect = (file) => {
    setValidationError(null);
    const error = validateFile(file);
    if (error) {
      setValidationError(error);
      setSelectedFile(null);
      return;
    }
    setSelectedFile(file);
  };

  const handleInputChange = (e) => {
    if (e.target.files && e.target.files[0]) {
      handleFileSelect(e.target.files[0]);
    }
  };

  const handleDragOver = (e) => {
    e.preventDefault();
    setIsDragOver(true);
  };

  const handleDragLeave = (e) => {
    e.preventDefault();
    setIsDragOver(false);
  };

  const handleDrop = (e) => {
    e.preventDefault();
    setIsDragOver(false);
    if (e.dataTransfer.files && e.dataTransfer.files[0]) {
      handleFileSelect(e.dataTransfer.files[0]);
    }
  };

  const handleClearSelected = () => {
    setSelectedFile(null);
    setValidationError(null);
    if (fileInputRef.current) {
      fileInputRef.current.value = '';
    }
  };

  const formatFileSize = (bytes) => {
    if (bytes < 1024) return `${bytes} B`;
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
    return `${(bytes / (1024 * 1024)).toFixed(2)} MB`;
  };

  const handleUpload = async () => {
    if (!selectedFile) return;

    setUploading(true);
    setValidationError(null);

    const formData = new FormData();
    formData.append('file', selectedFile);

    try {
      const response = await api.post('/resumes/upload', formData, {
        headers: {
          'Content-Type': 'multipart/form-data',
        },
      });

      addToast('Resume uploaded and text extracted successfully!', 'success');
      handleClearSelected();
      if (onUploadSuccess) {
        onUploadSuccess(response.data);
      }
    } catch (err) {
      console.error('Failed to upload resume', err);
      const serverMsg = err.response?.data?.message || 'Failed to upload resume. Please try again.';
      setValidationError(serverMsg);
      addToast(serverMsg, 'error');
    } finally {
      setUploading(false);
    }
  };

  return (
    <Card className="resume-upload-card">
      <CardHeader>
        <div className="upload-card-header-inner">
          <div className="upload-header-icon">
            <UploadCloud size={20} className="text-primary" />
          </div>
          <div>
            <h3 className="upload-title">Upload Candidate Resume</h3>
            <p className="upload-subtitle">
              Upload PDF, DOCX, or TXT format (max 10MB). Text will be extracted and prepared for AI analysis.
            </p>
          </div>
        </div>
      </CardHeader>
      <CardContent>
        {/* Hidden File Input */}
        <input
          ref={fileInputRef}
          type="file"
          accept=".pdf,.docx,.txt,application/pdf,application/vnd.openxmlformats-officedocument.wordprocessingml.document,text/plain"
          style={{ display: 'none' }}
          onChange={handleInputChange}
          aria-label="Upload resume file input"
        />

        {/* Drag & Drop Zone */}
        {!selectedFile && (
          <div
            className={`resume-dropzone ${isDragOver ? 'drag-over' : ''}`}
            onDragOver={handleDragOver}
            onDragLeave={handleDragLeave}
            onDrop={handleDrop}
            onClick={() => fileInputRef.current?.click()}
            role="button"
            tabIndex={0}
            onKeyDown={(e) => {
              if (e.key === 'Enter' || e.key === ' ') {
                e.preventDefault();
                fileInputRef.current?.click();
              }
            }}
            aria-label="Drag and drop resume here or click to select file"
          >
            <div className="dropzone-icon-circle">
              <UploadCloud size={32} className="dropzone-cloud" />
            </div>
            <div className="dropzone-text-group">
              <p className="dropzone-main-text">
                <span className="dropzone-browse-link">Click to browse</span> or drag and drop your resume
              </p>
              <p className="dropzone-hint-text">Supported formats: PDF, DOCX, TXT • Max size: 10MB</p>
            </div>
            <div className="format-chips">
              <span className="format-chip">PDF</span>
              <span className="format-chip">DOCX</span>
              <span className="format-chip">TXT</span>
            </div>
          </div>
        )}

        {/* Selected File Card */}
        {selectedFile && (
          <div className="selected-file-preview">
            <div className="preview-file-left">
              <div className="file-icon-box">
                <FileText size={24} className="text-primary" />
              </div>
              <div className="file-details">
                <span className="file-name-text">{selectedFile.name}</span>
                <span className="file-size-text">{formatFileSize(selectedFile.size)}</span>
              </div>
            </div>
            <button
              type="button"
              className="remove-file-btn"
              onClick={handleClearSelected}
              disabled={uploading}
              aria-label="Remove selected file"
            >
              <X size={18} />
            </button>
          </div>
        )}

        {/* Error Alert */}
        {validationError && (
          <div className="upload-error-alert" role="alert">
            <AlertCircle size={16} className="text-danger flex-shrink-0" />
            <span>{validationError}</span>
          </div>
        )}

        {/* Action Button */}
        {selectedFile && (
          <div className="upload-action-row">
            <Button
              variant="secondary"
              onClick={handleClearSelected}
              disabled={uploading}
            >
              Cancel
            </Button>
            <Button
              variant="primary"
              onClick={handleUpload}
              disabled={uploading}
            >
              {uploading ? (
                <span className="btn-loading-content">
                  <Spinner size={16} />
                  <span>Uploading & Extracting Text...</span>
                </span>
              ) : (
                <>
                  <UploadCloud size={16} />
                  <span>Upload Resume</span>
                </>
              )}
            </Button>
          </div>
        )}
      </CardContent>
    </Card>
  );
};
