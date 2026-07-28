import { CommonModule } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Analytics, Order, OrderStatus } from '../../core/models/commerce.model';
import { AuthService } from '../../core/services/auth.service';
import { CommerceService } from '../../core/services/commerce.service';
import { ToastService } from '../../core/services/toast.service';
@Component({standalone:true,selector:'app-orders',imports:[CommonModule,FormsModule,RouterLink],templateUrl:'./orders.component.html',styleUrl:'./orders.component.scss'})
export class OrdersComponent implements OnInit {
  private commerce=inject(CommerceService);private route=inject(ActivatedRoute);private toast=inject(ToastService);
  auth=inject(AuthService);orders:Order[]=[];selected?:Order;analytics?:Analytics;query='';status='';from='';to='';loading=true;
  readonly statuses:OrderStatus[]=['PENDING','CONFIRMED','SHIPPED','DELIVERED','CANCELLED'];
  ngOnInit(){const id=this.route.snapshot.paramMap.get('id');if(id)this.commerce.order(id).subscribe({next:o=>{this.selected=o;this.loading=false;},error:()=>this.loading=false});else this.load();this.commerce.analytics().subscribe(a=>this.analytics=a);}
  load(){this.loading=true;this.commerce.orders(this.query,this.status,this.from,this.to).subscribe({next:o=>{this.orders=o;this.loading=false;},error:()=>{this.loading=false;this.toast.show('Could not load orders','error');}});}
  cancel(o:Order){this.commerce.cancel(o.id).subscribe(x=>{o.status=x.status;this.toast.show('Order cancelled','success');});}
  redo(o:Order){this.commerce.redo(o.id).subscribe(()=>this.toast.show('Items restored to your cart','success'));}
  remove(o:Order){this.commerce.removeOrder(o.id).subscribe(()=>{this.orders=this.orders.filter(item=>item.id!==o.id);this.toast.show('Cancelled order removed','success');});}
  advance(o:Order){const next:Partial<Record<OrderStatus,OrderStatus>>={PENDING:'CONFIRMED',CONFIRMED:'SHIPPED',SHIPPED:'DELIVERED'};const status=next[o.status];if(status)this.commerce.updateStatus(o.id,status).subscribe(x=>o.status=x.status);}
  canCancel(o:Order){return o.status==='PENDING'||o.status==='CONFIRMED';}
}
