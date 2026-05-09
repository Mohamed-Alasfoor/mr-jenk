import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, catchError, map, shareReplay, tap, throwError } from 'rxjs';
import { Product } from '../models/product.model';
import { environment } from '../../../environments/environment';
import { normalizeManagedMediaUrls } from '../utils/media-url';

interface ProductResponse {
  id: string;
  name: string;
  description: string;
  price: number;
  quantity: number;
  sellerId: string;
  imageUrls?: string[] | null;
  createdAt: string;
  updatedAt: string;
}

@Injectable({
  providedIn: 'root'
})
export class ProductService {
  private http = inject(HttpClient);
  private apiUrl = environment.apiUrl;
  private allProducts$?: Observable<Product[]>;
  private myProducts$?: Observable<Product[]>;
  private productByIdCache = new Map<string, Observable<Product>>();
  private productsBySellerCache = new Map<string, Observable<Product[]>>();

  getAll(): Observable<Product[]> {
    if (this.allProducts$) {
      return this.allProducts$;
    }

    this.allProducts$ = this.http
      .get<ProductResponse[]>(`${this.apiUrl}/products`)
      .pipe(
        map((products) => products.map((product) => this.normalizeProduct(product))),
        catchError((error) => {
          this.allProducts$ = undefined;
          return throwError(() => error);
        }),
        shareReplay(1)
      );

    return this.allProducts$;
  }

  getById(id: string): Observable<Product> {
    const cachedProduct = this.productByIdCache.get(id);
    if (cachedProduct) {
      return cachedProduct;
    }

    const request$ = this.http
      .get<ProductResponse>(`${this.apiUrl}/products/${id}`)
      .pipe(
        map((product) => this.normalizeProduct(product)),
        catchError((error) => {
          this.productByIdCache.delete(id);
          return throwError(() => error);
        }),
        shareReplay(1)
      );

    this.productByIdCache.set(id, request$);
    return request$;
  }

  getMyProducts(): Observable<Product[]> {
    if (this.myProducts$) {
      return this.myProducts$;
    }

    this.myProducts$ = this.http
      .get<ProductResponse[]>(`${this.apiUrl}/products/me`)
      .pipe(
        map((products) => products.map((product) => this.normalizeProduct(product))),
        catchError((error) => {
          this.myProducts$ = undefined;
          return throwError(() => error);
        }),
        shareReplay(1)
      );

    return this.myProducts$;
  }

  getAllBySeller(): Observable<Product[]> {
    return this.getMyProducts();
  }

  getBySellerId(sellerId: string): Observable<Product[]> {
    const cachedProducts = this.productsBySellerCache.get(sellerId);
    if (cachedProducts) {
      return cachedProducts;
    }

    const request$ = this.http
      .get<ProductResponse[]>(`${this.apiUrl}/products/seller/${sellerId}`)
      .pipe(
        map((products) => products.map((product) => this.normalizeProduct(product))),
        catchError((error) => {
          this.productsBySellerCache.delete(sellerId);
          return throwError(() => error);
        }),
        shareReplay(1)
      );

    this.productsBySellerCache.set(sellerId, request$);
    return request$;
  }

  create(product: Partial<Product>): Observable<Product> {
    return this.http.post<ProductResponse>(`${this.apiUrl}/products`, product).pipe(
      map((createdProduct) => this.normalizeProduct(createdProduct)),
      tap(() => this.invalidateCache())
    );
  }

  update(id: string, product: Partial<Product>): Observable<Product> {
    return this.http.put<ProductResponse>(`${this.apiUrl}/products/${id}`, product).pipe(
      map((updatedProduct) => this.normalizeProduct(updatedProduct)),
      tap(() => this.invalidateCache(id))
    );
  }

  delete(id: string): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/products/${id}`).pipe(
      tap(() => this.invalidateCache(id))
    );
  }

  private normalizeProduct(product: ProductResponse): Product {
    return {
      ...product,
      imageUrls: normalizeManagedMediaUrls(product.imageUrls)
    };
  }

  invalidateCache(productId?: string): void {
    this.allProducts$ = undefined;
    this.myProducts$ = undefined;
    this.productsBySellerCache.clear();

    if (productId) {
      this.productByIdCache.delete(productId);
      return;
    }

    this.productByIdCache.clear();
  }
}
