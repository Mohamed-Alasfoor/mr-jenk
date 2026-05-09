import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, Validators, ReactiveFormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { finalize, of, switchMap, tap } from 'rxjs';
import { AuthService } from '../../core/services/auth.service';
import { ToastService } from '../../core/services/toast.service';
import { LucideAngularModule, ShoppingBag, Store, Eye, EyeOff, AlertCircle } from 'lucide-angular';
import { FileUploadComponent } from '../../shared/components/file-upload/file-upload.component';
import { MediaImageComponent } from '../../shared/components/media-image/media-image.component';

@Component({
  selector: 'app-auth',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, LucideAngularModule, FileUploadComponent, MediaImageComponent],
  templateUrl: './auth.component.html',
  styleUrl: './auth.component.scss'
})
export class AuthComponent implements OnInit, OnDestroy {
  fb = inject(FormBuilder);
  authService = inject(AuthService);
  router = inject(Router);
  toast = inject(ToastService);

  activeTab: 'login' | 'register' = 'login';
  
  loginForm!: FormGroup;
  registerForm!: FormGroup;
  
  isSubmitting = false;
  showPassword = false;
  selectedAvatarFile: File | null = null;
  avatarPreviewUrl: string | null = null;

  readonly avatarMaxSizeBytes = 2 * 1024 * 1024;

  readonly ShoppingBagIcon = ShoppingBag;
  readonly StoreIcon = Store;
  readonly EyeIcon = Eye;
  readonly EyeOffIcon = EyeOff;
  readonly AlertCircleIcon = AlertCircle;

  ngOnInit() {
    this.syncTabWithRoute();

    this.loginForm = this.fb.group({
      email: ['', [Validators.required, Validators.email]],
      password: ['', [Validators.required]]
    });

    this.registerForm = this.fb.group({
      fullName: ['', [Validators.required, Validators.minLength(3)]],
      email: ['', [Validators.required, Validators.email]],
      password: ['', [Validators.required, Validators.minLength(8)]],
      confirmPassword: ['', [Validators.required]],
      role: ['CLIENT', [Validators.required]]
    }, { validators: this.passwordMatchValidator });
  }

  setTab(tab: 'login' | 'register') {
    if (this.activeTab === tab) {
      return;
    }

    this.activeTab = tab;
    this.showPassword = false;
    this.isSubmitting = false;
    this.clearSelectedAvatar();
    this.loginForm.reset();
    this.registerForm.reset({ role: 'CLIENT' });
    this.router.navigate([tab === 'login' ? '/auth/login' : '/auth/register']);
  }

  togglePassword() {
    this.showPassword = !this.showPassword;
  }

  setRole(role: 'CLIENT' | 'SELLER') {
    this.registerForm.patchValue({ role });
    if (role !== 'SELLER') {
      this.clearSelectedAvatar();
    }
  }

  passwordMatchValidator(g: FormGroup) {
    return g.get('password')?.value === g.get('confirmPassword')?.value
      ? null : { mismatch: true };
  }

  onLoginSubmit() {
    if (this.loginForm.invalid) {
      this.loginForm.markAllAsTouched();
      return;
    }
    
    this.isSubmitting = true;
    this.authService.login(this.loginForm.value).subscribe({
      next: (res: any) => {
        this.toast.show('Welcome back!', 'success');
        this.redirectAfterAuth();
      },
      error: (err: any) => {
        this.isSubmitting = false;
        this.toast.show(err.error?.message || 'Login failed', 'error');
      }
    });
  }

  onRegisterSubmit() {
    if (this.registerForm.invalid) {
      this.registerForm.markAllAsTouched();
      return;
    }
    
    this.isSubmitting = true;
    let accountCreated = false;
    const { confirmPassword, ...data } = this.registerForm.value;
    const avatarFile = data.role === 'SELLER' ? this.selectedAvatarFile : null;
    
    this.authService.register(data).pipe(
      tap(() => {
        accountCreated = true;
      }),
      switchMap(() => {
        if (!avatarFile) {
          return of(false);
        }

        return this.authService.updateProfileWithAvatar(data.fullName, avatarFile).pipe(
          switchMap(() => of(true))
        );
      }),
      finalize(() => {
        this.isSubmitting = false;
      })
    ).subscribe({
      next: (avatarUploaded) => {
        this.toast.show(
          avatarUploaded
            ? 'Account created and avatar uploaded.'
            : 'Account created successfully!',
          'success'
        );
        this.clearSelectedAvatar();
        this.redirectAfterAuth();
      },
      error: (err: any) => {
        if (accountCreated) {
          this.toast.show(
            'Account created, but avatar upload failed. You can retry from your seller profile.',
            'warning'
          );
          this.clearSelectedAvatar();
          this.redirectAfterAuth();
          return;
        }

        this.toast.show(err.error?.message || 'Registration failed', 'error');
      }
    });
  }

  onAvatarSelected(file: File) {
    this.selectedAvatarFile = file;
    this.updateAvatarPreview(file);
  }

  clearSelectedAvatar() {
    if (this.avatarPreviewUrl?.startsWith('blob:')) {
      URL.revokeObjectURL(this.avatarPreviewUrl);
    }

    this.selectedAvatarFile = null;
    this.avatarPreviewUrl = null;
  }

  private redirectAfterAuth() {
    if (this.authService.isSeller()) {
      this.router.navigate(['/seller/dashboard']);
    } else {
      this.router.navigate(['/products']);
    }
  }

  isInvalid(form: FormGroup, field: string): boolean {
    const control = form.get(field);
    return !!(control && control.invalid && (control.dirty || control.touched));
  }

  private syncTabWithRoute() {
    this.activeTab = this.router.url.includes('/register') ? 'register' : 'login';
  }

  private updateAvatarPreview(file: File) {
    if (this.avatarPreviewUrl?.startsWith('blob:')) {
      URL.revokeObjectURL(this.avatarPreviewUrl);
    }

    this.avatarPreviewUrl = URL.createObjectURL(file);
  }

  ngOnDestroy(): void {
    this.clearSelectedAvatar();
  }
}
