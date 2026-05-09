import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { LucideAngularModule, UploadCloud } from 'lucide-angular';
import { ToastService } from '../../../core/services/toast.service';

@Component({
  selector: 'app-file-upload',
  standalone: true,
  imports: [CommonModule, LucideAngularModule],
  templateUrl: './file-upload.component.html',
  styleUrl: './file-upload.component.scss'
})
export class FileUploadComponent {
  @Input() accept = 'image/*';
  @Input() maxSizeBytes = 2 * 1024 * 1024;
  @Input() maxFiles = 1;
  @Input() multiple = false;
  @Input() disabled = false;
  @Input() isUploading = false;
  @Input() uploadProgress = 0;
  @Input() progressLabel = 'UPLOADING...';
  @Input() title = 'Upload image';
  @Input() description = 'Drag and drop an image here or choose one from your device.';
  @Input() buttonLabel = 'Select file';
  
  @Output() fileSelected = new EventEmitter<File>();
  @Output() filesSelected = new EventEmitter<File[]>();
  
  readonly UploadCloudIcon = UploadCloud;
  isDragging = false;
  
  constructor(private toast: ToastService) {}

  onDragOver(event: DragEvent) {
    event.preventDefault();

    if (this.isInteractionDisabled) {
      return;
    }

    this.isDragging = true;
  }
  
  onDragLeave(event: DragEvent) {
    event.preventDefault();
    this.isDragging = false;
  }
  
  onDrop(event: DragEvent) {
    event.preventDefault();
    this.isDragging = false;

    if (this.isInteractionDisabled) {
      return;
    }
    
    if (event.dataTransfer?.files?.length) {
      this.handleFiles(Array.from(event.dataTransfer.files));
    }
  }
  
  onFileChange(event: Event) {
    const input = event.target as HTMLInputElement;
    if (input.files?.length) {
      this.handleFiles(Array.from(input.files));
    }
    input.value = '';
  }

  get isInteractionDisabled(): boolean {
    return this.disabled || this.isUploading;
  }
  
  private handleFiles(files: File[]) {
    const selectedFiles = this.multiple ? files : files.slice(0, 1);
    const allowedFileCount = Math.max(this.maxFiles, 1);

    if (selectedFiles.length > allowedFileCount) {
      this.toast.show(
        `You can select up to ${allowedFileCount} ${allowedFileCount === 1 ? 'file' : 'files'} at a time.`,
        'warning'
      );
      return;
    }

    const validFiles = selectedFiles.filter((file) => this.validateFile(file));
    if (validFiles.length === 0) {
      return;
    }

    if (this.multiple) {
      this.filesSelected.emit(validFiles);
      return;
    }

    this.fileSelected.emit(validFiles[0]);
  }

  private validateFile(file: File): boolean {
    if (!this.matchesAccept(file)) {
      this.toast.show(`"${file.name}" is not supported. Please select ${this.accept}.`, 'error');
      return false;
    }

    if (file.size > this.maxSizeBytes) {
      this.toast.show(
        `"${file.name}" exceeds the ${(this.maxSizeBytes / 1024 / 1024).toFixed(1)}MB limit.`,
        'error'
      );
      return false;
    }

    return true;
  }

  private matchesAccept(file: File): boolean {
    if (!this.accept.trim()) {
      return true;
    }

    return this.accept
      .split(',')
      .map((value) => value.trim())
      .filter((value) => value.length > 0)
      .some((pattern) => {
        if (pattern.endsWith('/*')) {
          return file.type.startsWith(pattern.slice(0, -1));
        }

        return file.type === pattern;
      });
  }
}
