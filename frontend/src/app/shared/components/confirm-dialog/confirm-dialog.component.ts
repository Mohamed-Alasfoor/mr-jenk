import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-confirm-dialog',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="modal-backdrop" *ngIf="isOpen" (click)="close()">
      <div class="modal-card glass" (click)="$event.stopPropagation()">
        <span class="modal-kicker">Confirmation</span>
        <h3 class="modal-title">{{ title }}</h3>
        <p class="modal-body">{{ message }}</p>
        
        <div class="modal-actions">
          <button class="btn-ghost" (click)="close()">{{ cancelText }}</button>
          <button class="btn-primary danger" (click)="onConfirm()">{{ confirmText }}</button>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .modal-backdrop {
      position: fixed;
      inset: 0;
      background: rgba(15, 23, 47, 0.34);
      backdrop-filter: blur(12px);
      z-index: 9999;
      display: flex;
      align-items: center;
      justify-content: center;
      padding: 1rem;
      animation: fadeIn 200ms ease;
    }
    
    .modal-card {
      width: 100%;
      max-width: 460px;
      padding: 1.6rem;
      border-radius: var(--radius-lg);
      display: flex;
      flex-direction: column;
      gap: 0.9rem;
      animation: popIn 300ms var(--ease-spring);
    }

    .modal-kicker {
      color: var(--color-accent-primary);
      font-size: 0.72rem;
      font-weight: 800;
      letter-spacing: 0.22em;
      text-transform: uppercase;
    }
    
    .modal-title {
      margin: 0;
      font-size: clamp(1.8rem, 1.5rem + 1vw, 2.2rem);
      color: var(--color-text-primary);
    }
    
    .modal-body {
      margin: 0;
      color: var(--color-text-secondary);
      font-size: 0.92rem;
      line-height: 1.7;
    }
    
    .modal-actions {
      display: flex;
      justify-content: flex-end;
      gap: 0.8rem;
      margin-top: 1rem;
    }
    
    .btn-primary.danger {
      background: linear-gradient(135deg, #ff6b7f, #f24ca7);
    }
    
    @keyframes fadeIn {
      from { opacity: 0; }
      to { opacity: 1; }
    }
    
    @keyframes popIn {
      from { transform: scale(0.95); opacity: 0; }
      to { transform: scale(1); opacity: 1; }
    }
  `]
})
export class ConfirmDialogComponent {
  @Input() isOpen = false;
  @Input() title = 'Are you sure?';
  @Input() message = 'This action cannot be undone.';
  @Input() confirmText = 'Confirm';
  @Input() cancelText = 'Cancel';
  
  @Output() confirm = new EventEmitter<void>();
  @Output() cancel = new EventEmitter<void>();

  close() {
    this.isOpen = false;
    this.cancel.emit();
  }

  onConfirm() {
    this.isOpen = false;
    this.confirm.emit();
  }
}
