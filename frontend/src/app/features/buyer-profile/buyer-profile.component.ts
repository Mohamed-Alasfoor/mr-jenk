import { Component, DestroyRef, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { filter } from 'rxjs';
import { AuthService } from '../../core/services/auth.service';
import { ToastService } from '../../core/services/toast.service';
import { User } from '../../core/models/user.model';

@Component({
  selector: 'app-buyer-profile',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './buyer-profile.component.html',
  styleUrl: './buyer-profile.component.scss'
})
export class BuyerProfileComponent implements OnInit {
  authService = inject(AuthService);
  toastService = inject(ToastService);
  fb = inject(FormBuilder);
  router = inject(Router);
  destroyRef = inject(DestroyRef);

  profileForm!: FormGroup;
  user: User | null = null;
  isSubmitting = false;

  ngOnInit() {
    this.profileForm = this.fb.group({
      fullName: ['', [Validators.required, Validators.minLength(3)]],
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
    this.authService.updateProfile({ fullName: this.profileForm.get('fullName')?.value }).subscribe({
      next: (updatedUser: any) => {
        this.user = updatedUser;
        this.isSubmitting = false;
        this.toastService.show('Profile updated successfully', 'success');
      },
      error: (err: any) => {
        this.isSubmitting = false;
        this.toastService.show('Failed to update profile', 'error');
      }
    });
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
}
