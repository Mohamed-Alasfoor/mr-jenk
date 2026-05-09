// Toast Service
import { Injectable } from '@angular/core';
import { BehaviorSubject } from 'rxjs';

export interface Toast {
  id: number;
  message: string;
  type: 'success' | 'error' | 'warning' | 'info';
}

@Injectable({ providedIn: 'root' })
export class ToastService {
  private toastsSubject = new BehaviorSubject<Toast[]>([]);
  public toasts$ = this.toastsSubject.asObservable();
  private counter = 0;

  show(message: string, type: Toast['type'] = 'info') {
    const id = this.counter++;
    const currentToasts = this.toastsSubject.value;
    
    this.toastsSubject.next([...currentToasts, { id, message, type }]);

    // Auto dismiss after 4s
    setTimeout(() => this.remove(id), 4000);
  }

  remove(id: number) {
    const currentToasts = this.toastsSubject.value;
    this.toastsSubject.next(currentToasts.filter(t => t.id !== id));
  }
}
