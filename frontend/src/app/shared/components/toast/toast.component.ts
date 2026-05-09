import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ToastService, Toast } from '../../../core/services/toast.service';
import { LucideAngularModule, CheckCircle2, AlertCircle, Info, XCircle } from 'lucide-angular';

@Component({
  selector: 'app-toast',
  standalone: true,
  imports: [CommonModule, LucideAngularModule],
  templateUrl: './toast.component.html',
  styleUrl: './toast.component.scss'
})
export class ToastComponent {
  toastService = inject(ToastService);
  toasts$ = this.toastService.toasts$;

  readonly CheckCircle2Icon = CheckCircle2;
  readonly AlertCircleIcon = AlertCircle;
  readonly InfoIcon = Info;
  readonly XCircleIcon = XCircle;

  remove(id: number) {
    this.toastService.remove(id);
  }

  getIcon(type: Toast['type']) {
    switch (type) {
      case 'success': return this.CheckCircle2Icon;
      case 'error': return this.XCircleIcon;
      case 'warning': return this.AlertCircleIcon;
      case 'info': return this.InfoIcon;
    }
  }
}
