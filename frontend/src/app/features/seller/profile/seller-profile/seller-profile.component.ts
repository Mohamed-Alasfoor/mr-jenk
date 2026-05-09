import { Component, DestroyRef, OnDestroy, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { AuthService } from '../../../../core/services/auth.service';
import { ToastService } from '../../../../core/services/toast.service';
import { User } from '../../../../core/models/user.model';
import { LucideAngularModule, User as UserIcon, LogOut, CheckCircle2, Camera } from 'lucide-angular';
import { Router, RouterLink } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { filter } from 'rxjs';
import { FileUploadComponent } from '../../../../shared/components/file-upload/file-upload.component';
import { MediaImageComponent } from '../../../../shared/components/media-image/media-image.component';
import { SellerPortalShellComponent } from '../../../../shared/components/seller-portal-shell/seller-portal-shell.component';

@Component({
  selector: 'app-seller-profile',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, LucideAngularModule, RouterLink, FileUploadComponent, MediaImageComponent, SellerPortalShellComponent],
  templateUrl: './seller-profile.component.html',
  styleUrl: './seller-profile.component.scss'
})
export class SellerProfileComponent implements OnInit, OnDestroy {
  authService = inject(AuthService);
  toastService = inject(ToastService);
  fb = inject(FormBuilder);
  router = inject(Router);
  destroyRef = inject(DestroyRef);

  profileForm!: FormGroup;
  user: User | null = null;
  isSubmitting = false;
  selectedAvatarFile: File | null = null;
  avatarPreviewUrl: string | null = null;

  readonly avatarMaxSizeBytes = 2 * 1024 * 1024;

  readonly UserIcon = UserIcon;
  readonly LogOutIcon = LogOut;
  readonly CheckCircle2Icon = CheckCircle2;
  readonly CameraIcon = Camera;

  ngOnInit() {
    this.profileForm = this.fb.group({
      fullName: ['', [Validators.required]],
      email: [{ value: '', disabled: true }]
    });

    const currentUser = this.authService.currentUserValue;
    if (currentUser) {
      this.applyUser(currentUser);
    }

    this.authService.currentUser$
      .pipe(
        filter((user): user is User => user !== null),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe((user) => this.applyUser(user));
  }

  onSubmit() {
    if (this.profileForm.invalid) return;
    
    this.isSubmitting = true;
    const fullName = this.profileForm.get('fullName')?.value?.trim();
    const request$ = this.selectedAvatarFile
      ? this.authService.updateProfileWithAvatar(fullName, this.selectedAvatarFile)
      : this.authService.updateProfile({ fullName });

    request$.subscribe({
      next: (updatedUser: any) => {
        this.user = updatedUser;
        this.isSubmitting = false;
        this.clearSelectedAvatar();
        this.toastService.show('Profile updated successfully', 'success');
      },
      error: (err: any) => {
        this.isSubmitting = false;
        this.toastService.show(err.error?.message || 'Failed to update profile', 'error');
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

  logout() {
    this.authService.logout();
    this.router.navigate(['/auth/login']);
  }

  private applyUser(user: User) {
    this.user = user;
    this.profileForm.patchValue({
      fullName: user.fullName,
      email: user.email
    });
  }

  get displayedAvatarUrl(): string | null {
    return this.avatarPreviewUrl || this.user?.avatarUrl || null;
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
