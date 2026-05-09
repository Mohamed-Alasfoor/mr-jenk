import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpEventType, HttpParams, HttpRequest } from '@angular/common/http';
import { Observable, filter, map } from 'rxjs';
import { environment } from '../../../environments/environment';
import { MediaItem, MediaUploadEvent } from '../models/media.model';
import { normalizeManagedMediaUrl } from '../utils/media-url';

interface MediaItemResponse {
  id: string;
  productId?: string | null;
  imageUrl: string;
  contentType: string;
  sizeBytes: number;
  originalFilename: string;
  createdAt: string;
}

@Injectable({
  providedIn: 'root'
})
export class MediaService {
  private http = inject(HttpClient);
  private apiUrl = environment.apiUrl;

  getMyMedia(productId?: string | null): Observable<MediaItem[]> {
    let params: HttpParams | undefined;
    if (productId) {
      params = new HttpParams().set('productId', productId);
    }

    return this.http
      .get<MediaItemResponse[]>(`${this.apiUrl}/media/images`, { params })
      .pipe(map((items) => items.map((item) => this.normalizeMediaItem(item))));
  }

  upload(file: File, productId?: string | null): Observable<MediaUploadEvent> {
    const formData: FormData = new FormData();
    formData.append('file', file);
    if (productId) {
      formData.append('productId', productId);
    }

    const req = new HttpRequest<FormData>('POST', `${this.apiUrl}/media/images`, formData, {
      reportProgress: true,
      responseType: 'json'
    });

    return this.http.request<MediaItemResponse>(req).pipe(
      map((event) => {
        if (event.type === HttpEventType.UploadProgress) {
          const progress = event.total ? Math.round((event.loaded / event.total) * 100) : 0;
          return {
            kind: 'progress',
            progress
          } satisfies MediaUploadEvent;
        }

        if (event.type === HttpEventType.Response && event.body) {
          return {
            kind: 'complete',
            media: this.normalizeMediaItem(event.body)
          } satisfies MediaUploadEvent;
        }

        return null;
      }),
      filter((event): event is MediaUploadEvent => event !== null)
    );
  }

  updateAssignment(id: string, productId?: string | null): Observable<MediaItem> {
    return this.http
      .put<MediaItemResponse>(`${this.apiUrl}/media/images/${id}/assignment`, {
        productId: productId || null
      })
      .pipe(map((item) => this.normalizeMediaItem(item)));
  }

  delete(id: string): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/media/images/${id}`);
  }

  private normalizeMediaItem(item: MediaItemResponse): MediaItem {
    return {
      id: item.id,
      filename: item.originalFilename,
      url: normalizeManagedMediaUrl(item.imageUrl) ?? item.imageUrl,
      contentType: item.contentType,
      sizeBytes: item.sizeBytes,
      productId: item.productId ?? null,
      uploadedAt: item.createdAt
    };
  }
}
