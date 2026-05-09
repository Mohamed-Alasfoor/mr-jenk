import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, Observable, catchError, finalize, map, of, tap } from 'rxjs';
import { User, Role } from '../models/user.model';
import { environment } from '../../../environments/environment';
import { normalizeManagedMediaUrl } from '../utils/media-url';
import { ProductService } from './product.service';

export interface LoginCredentials {
  email: string;
  password: string;
}

export interface RegisterPayload {
  fullName: string;
  email: string;
  password: string;
  role: Extract<Role, 'CLIENT' | 'SELLER'>;
}

export interface AuthResponse {
  token: string;
  user: User;
}

@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private http = inject(HttpClient);
  private productService = inject(ProductService);
  private apiUrl = environment.apiUrl;
  
  private currentUserSubject = new BehaviorSubject<User | null>(null);
  private authReadySubject = new BehaviorSubject(false);
  public currentUser$ = this.currentUserSubject.asObservable();
  public authReady$ = this.authReadySubject.asObservable();

  constructor() {
    this.restoreSession();
  }

  private restoreSession() {
    const token = localStorage.getItem('buy01_token');
    if (!token) {
      this.resetSessionState();
      this.authReadySubject.next(true);
      return;
    }

    this.getCurrentUser().pipe(
      catchError(() => {
        this.logout();
        return of(null);
      }),
      finalize(() => this.authReadySubject.next(true))
    ).subscribe();
  }

  get currentUserValue(): User | null {
    return this.currentUserSubject.value;
  }

  login(credentials: LoginCredentials): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${this.apiUrl}/auth/login`, credentials).pipe(
      map((res) => this.normalizeAuthResponse(res)),
      tap(res => {
        this.resetSessionState();
        localStorage.setItem('buy01_token', res.token);
        this.currentUserSubject.next(res.user);
      })
    );
  }

  register(data: RegisterPayload): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${this.apiUrl}/auth/register`, data).pipe(
      map((res) => this.normalizeAuthResponse(res)),
      tap((res) => {
        if (res.token) {
          this.resetSessionState();
          localStorage.setItem('buy01_token', res.token);
          if (res.user) this.currentUserSubject.next(res.user);
        }
      })
    );
  }

  getCurrentUser(): Observable<User> {
    return this.http.get<User>(`${this.apiUrl}/me`).pipe(
      map((user) => this.normalizeUser(user)),
      tap(user => this.currentUserSubject.next(user))
    );
  }

  updateProfile(data: {fullName: string}): Observable<User> {
    return this.http.put<User>(`${this.apiUrl}/me`, data).pipe(
      map((user) => this.normalizeUser(user)),
      tap(user => this.currentUserSubject.next(user))
    );
  }

  updateProfileWithAvatar(fullName: string, avatar?: File | null): Observable<User> {
    const formData = new FormData();
    formData.append('fullName', fullName.trim());
    if (avatar) {
      formData.append('avatar', avatar);
    }

    return this.http.put<User>(`${this.apiUrl}/me`, formData).pipe(
      map((user) => this.normalizeUser(user)),
      tap(user => this.currentUserSubject.next(user))
    );
  }

  logout() {
    localStorage.removeItem('buy01_token');
    this.resetSessionState();
    this.currentUserSubject.next(null);
    this.authReadySubject.next(true);
  }

  isLoggedIn(): boolean { return !!this.currentUserValue; }
  hasRole(role: Role): boolean { return this.currentUserValue?.role === role; }
  isSeller(): boolean { return this.hasRole('SELLER'); }
  isBuyer(): boolean { return this.hasRole('CLIENT'); }
  isGuest(): boolean { return !this.isLoggedIn(); }

  private normalizeAuthResponse(response: AuthResponse): AuthResponse {
    return {
      ...response,
      user: this.normalizeUser(response.user)
    };
  }

  private normalizeUser(user: User): User {
    return {
      ...user,
      avatarUrl: normalizeManagedMediaUrl(user.avatarUrl)
    };
  }

  private resetSessionState(): void {
    this.productService.invalidateCache();
  }
}
