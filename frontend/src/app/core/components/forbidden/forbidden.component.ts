import { Component } from '@angular/core';
import { Router } from '@angular/router';
import { EmptyStateComponent } from '../../../shared/components/empty-state/empty-state.component';
import { LucideAngularModule, ShieldAlert } from 'lucide-angular';

@Component({
  selector: 'app-forbidden',
  standalone: true,
  imports: [EmptyStateComponent, LucideAngularModule],
  template: `
    <div class="error-page">
      <app-empty-state 
        [icon]="ShieldAlertIcon"
        heading="403 - Access Forbidden"
        description="You do not have the necessary credentials to view this area. If you believe this is an error, check your active session."
        actionLabel="Return to Home"
        (action)="goHome()">
      </app-empty-state>
    </div>
  `,
  styles: [`
    .error-page {
      min-height: calc(100vh - 200px);
      display: flex;
      align-items: center;
      justify-content: center;
      padding: 2rem;
    }
  `]
})
export class ForbiddenComponent {
  readonly ShieldAlertIcon = ShieldAlert;
  constructor(private router: Router) {}
  goHome() { this.router.navigate(['/']); }
}
