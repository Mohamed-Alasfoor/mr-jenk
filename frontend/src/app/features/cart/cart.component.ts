import { CommonModule } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { forkJoin } from 'rxjs';
import { Cart } from '../../core/models/commerce.model';
import { Product } from '../../core/models/product.model';
import { CommerceService } from '../../core/services/commerce.service';
import { ProductService } from '../../core/services/product.service';
import { ToastService } from '../../core/services/toast.service';
@Component({standalone:true,selector:'app-cart',imports:[CommonModule,FormsModule],templateUrl:'./cart.component.html',styleUrl:'./cart.component.scss'})
export class CartComponent implements OnInit {
  private commerce=inject(CommerceService);private products=inject(ProductService);private toast=inject(ToastService);private router=inject(Router);
  cart?:Cart; catalog=new Map<string,Product>(); loading=true; address=''; step:1|2=1; submitting=false;
  ngOnInit(){forkJoin([this.commerce.getCart(),this.products.getAll()]).subscribe({next:([c,p])=>{this.cart=c;p.forEach(x=>this.catalog.set(x.id,x));this.loading=false;},error:()=>{this.loading=false;this.toast.show('Could not load cart','error');}});}
  product(id:string){return this.catalog.get(id);}
  get total(){return (this.cart?.items||[]).reduce((sum,i)=>sum+(this.product(i.productId)?.price||0)*i.quantity,0);}
  update(id:string,qty:number){if(qty<1)return;this.commerce.setItem(id,qty).subscribe(c=>this.cart=c);}
  remove(id:string){this.commerce.removeItem(id).subscribe(c=>this.cart=c);}
  review(){if(!this.address.trim()){this.toast.show('Delivery address is required','error');return;}this.step=2;}
  checkout(){this.submitting=true;this.commerce.checkout(this.address).subscribe({next:o=>{this.toast.show('Order placed — pay on delivery','success');this.router.navigate(['/orders',o.id]);},error:()=>{this.submitting=false;this.toast.show('Checkout failed','error');}});}
}
