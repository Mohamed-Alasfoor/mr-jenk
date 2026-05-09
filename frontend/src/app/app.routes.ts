import { Routes } from '@angular/router';
import { authGuard } from './core/guards/auth.guard';
import { roleGuard } from './core/guards/role.guard';
import { publicOnlyGuard } from './core/guards/public-only.guard';

export const routes: Routes = [
  // Public Routes
  { 
    path: '', 
    loadComponent: () => import('./features/home/home.component').then(m => m.HomeComponent)
  },
  { 
    path: 'products', 
    loadComponent: () => import('./features/products/product-list/product-list.component').then(m => m.ProductListComponent)
  },
  { 
    path: 'products/:id', 
    loadComponent: () => import('./features/products/product-detail/product-detail.component').then(m => m.ProductDetailComponent)
  },
  {
    path: 'sellers/:id',
    loadComponent: () => import('./features/sellers/seller-storefront/seller-storefront.component').then(m => m.SellerStorefrontComponent)
  },
  { 
    path: 'auth/login', 
    canActivate: [publicOnlyGuard],
    loadComponent: () => import('./features/auth/auth.component').then(m => m.AuthComponent)
  },
  { 
    path: 'auth/register', 
    canActivate: [publicOnlyGuard],
    loadComponent: () => import('./features/auth/auth.component').then(m => m.AuthComponent)
  },

  // Buyer Routes (CLIENT)
  { 
    path: 'profile', 
    canActivate: [authGuard, roleGuard],
    data: { roles: ['CLIENT'] },
    loadComponent: () => import('./features/buyer-profile/buyer-profile.component').then(m => m.BuyerProfileComponent)
  },

  // Seller Routes (SELLER)
  { 
    path: 'seller',
    canActivate: [authGuard, roleGuard],
    data: { roles: ['SELLER'] },
    children: [
      { path: 'dashboard', loadComponent: () => import('./features/seller/dashboard/seller-dashboard/seller-dashboard.component').then(m => m.SellerDashboardComponent) },
      { path: 'products', loadComponent: () => import('./features/seller/products/seller-products/seller-products.component').then(m => m.SellerProductsComponent) },
      { path: 'products/new', loadComponent: () => import('./features/seller/products/product-form/product-form.component').then(m => m.ProductFormComponent) },
      { path: 'products/:id/edit', loadComponent: () => import('./features/seller/products/product-form/product-form.component').then(m => m.ProductFormComponent) },
      { path: 'media', loadComponent: () => import('./features/seller/media/seller-media/seller-media.component').then(m => m.SellerMediaComponent) },
      { path: 'profile', loadComponent: () => import('./features/seller/profile/seller-profile/seller-profile.component').then(m => m.SellerProfileComponent) },
      { path: '', redirectTo: 'dashboard', pathMatch: 'full' }
    ]
  },

  // Error Pages
  { path: '404', loadComponent: () => import('./core/components/not-found/not-found.component').then(m => m.NotFoundComponent) },
  { path: '403', loadComponent: () => import('./core/components/forbidden/forbidden.component').then(m => m.ForbiddenComponent) },
  
  // Fallback
  { path: '**', redirectTo: '/404' }
];
