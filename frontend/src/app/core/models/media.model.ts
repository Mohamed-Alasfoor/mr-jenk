export interface MediaItem {
  id: string;
  filename: string;
  url: string;
  contentType: string;
  sizeBytes: number;
  productId?: string | null;
  uploadedAt: string;
}

export interface MediaUploadProgress {
  kind: 'progress';
  progress: number;
}

export interface MediaUploadComplete {
  kind: 'complete';
  media: MediaItem;
}

export type MediaUploadEvent = MediaUploadProgress | MediaUploadComplete;
