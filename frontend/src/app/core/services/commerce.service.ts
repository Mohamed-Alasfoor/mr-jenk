import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Analytics, Cart, Order, OrderStatus } from '../models/commerce.model';
@Injectable({providedIn:'root'})
export class CommerceService {
  private http=inject(HttpClient); private api=environment.apiUrl;
  getCart():Observable<Cart>{return this.http.get<Cart>(`${this.api}/cart`);}
  setItem(productId:string,quantity:number):Observable<Cart>{return this.http.put<Cart>(`${this.api}/cart/items`,{productId,quantity});}
  removeItem(productId:string):Observable<Cart>{return this.http.delete<Cart>(`${this.api}/cart/items/${productId}`);}
  checkout(address:string):Observable<Order>{return this.http.post<Order>(`${this.api}/orders`,{address,paymentMethod:'PAY_ON_DELIVERY'});}
  orders(query='',status='',from='',to=''):Observable<Order[]>{let p=new HttpParams();if(query)p=p.set('query',query);if(status)p=p.set('status',status);if(from)p=p.set('from',from);if(to)p=p.set('to',to);return this.http.get<Order[]>(`${this.api}/orders`,{params:p});}
  order(id:string):Observable<Order>{return this.http.get<Order>(`${this.api}/orders/${id}`);}
  cancel(id:string):Observable<Order>{return this.http.post<Order>(`${this.api}/orders/${id}/cancel`,{});}
  redo(id:string):Observable<Order>{return this.http.post<Order>(`${this.api}/orders/${id}/redo`,{});}
  removeOrder(id:string):Observable<void>{return this.http.delete<void>(`${this.api}/orders/${id}`);}
  updateStatus(id:string,status:OrderStatus):Observable<Order>{return this.http.patch<Order>(`${this.api}/orders/${id}/status`,{status});}
  analytics():Observable<Analytics>{return this.http.get<Analytics>(`${this.api}/analytics/me`);}
}
