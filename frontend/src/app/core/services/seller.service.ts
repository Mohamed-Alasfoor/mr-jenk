import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { map, Observable } from 'rxjs';
import { PublicSellerProfile } from '../models/user.model';
import { environment } from '../../../environments/environment';
import { normalizeManagedMediaUrl } from '../utils/media-url';

interface PublicSellerProfileResponse {
  id: string;
  fullName: string;
  avatarUrl?: string | null;
}

@Injectable({
  providedIn: 'root'
})
export class SellerService {
  private http = inject(HttpClient);
  private apiUrl = environment.apiUrl;

  getById(id: string): Observable<PublicSellerProfile> {
    return this.http
      .get<PublicSellerProfileResponse>(`${this.apiUrl}/sellers/${id}`)
      .pipe(
        map((seller) => ({
          ...seller,
          avatarUrl: normalizeManagedMediaUrl(seller.avatarUrl)
        }))
      );
  }
}
