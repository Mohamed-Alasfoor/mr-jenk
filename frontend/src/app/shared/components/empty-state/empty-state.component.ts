import { Component, Input, Output, EventEmitter } from '@angular/core';
import { CommonModule } from '@angular/common';
import { LucideAngularModule, Archive } from 'lucide-angular';

@Component({
  selector: 'app-empty-state',
  standalone: true,
  imports: [CommonModule, LucideAngularModule],
  template: `
    <div class="empty-state">
      <div class="icon-wrapper glass">
        <lucide-icon [img]="icon" size="48"></lucide-icon>
      </div>
      <h3 class="heading">{{ heading }}</h3>
      <p class="description">{{ description }}</p>
      <button *ngIf="actionLabel" class="btn-primary" (click)="action.emit()">
        {{ actionLabel }}
      </button>
    </div>
  `,
  styles: [`
    .empty-state {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      padding: 4rem 2rem;
      text-align: center;
      max-width: 400px;
      margin: 0 auto;
    }
    
    .icon-wrapper {
      width: 96px;
      height: 96px;
      border-radius: 2rem;
      display: flex;
      align-items: center;
      justify-content: center;
      margin-bottom: 1.5rem;
      color: var(--color-accent-primary);
      border: 1px solid rgba(255, 255, 255, 0.72);
      box-shadow: var(--shadow-neu-sm), var(--shadow-inset);
    }
    
    .heading {
      font-size: 1.5rem;
      margin-bottom: 0.5rem;
      color: var(--color-text-primary);
    }
    
    .description {
      font-size: 0.94rem;
      color: var(--color-text-secondary);
      margin-bottom: 2rem;
      line-height: 1.6;
    }
  `]
})
export class EmptyStateComponent {
  @Input() icon: any = Archive;
  @Input() heading: string = 'No Data Found';
  @Input() description: string = 'There is currently no data to display here.';
  @Input() actionLabel: string = '';
  @Output() action = new EventEmitter<void>();
}
