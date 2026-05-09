import { Component } from '@angular/core';
import { Router } from '@angular/router';
import { EmptyStateComponent } from '../../../shared/components/empty-state/empty-state.component';
import { LucideAngularModule, Ghost } from 'lucide-angular';

@Component({
  selector: 'app-not-found',
  standalone: true,
  imports: [EmptyStateComponent, LucideAngularModule],
  template: `
    <div class="error-page">
      <app-empty-state 
        [icon]="GhostIcon"
        heading="404 - Not Found"
        description="The artifact or page you are looking for has been moved, removed, or never existed."
        actionLabel="Return to Catalog"
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
export class NotFoundComponent {
  readonly GhostIcon = Ghost;
  constructor(private router: Router) {}
  goHome() { this.router.navigate(['/']); }
}
