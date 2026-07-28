import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { CommerceService } from './commerce.service';
import { environment } from '../../../environments/environment';

describe('CommerceService', () => {
  let service: CommerceService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({providers:[CommerceService,provideHttpClient(),provideHttpClientTesting()]});
    service=TestBed.inject(CommerceService);http=TestBed.inject(HttpTestingController);
  });
  afterEach(()=>http.verify());

  it('adds a product with its selected quantity', () => {
    service.setItem('product-1',3).subscribe(cart=>expect(cart.items[0].quantity).toBe(3));
    const request=http.expectOne(`${environment.apiUrl}/cart/items`);
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual({productId:'product-1',quantity:3});
    request.flush({userId:'buyer',items:[{productId:'product-1',quantity:3}],updatedAt:'2026-01-01'});
  });

  it('checks out exclusively with pay on delivery', () => {
    service.checkout('Manama').subscribe(order=>expect(order.paymentMethod).toBe('PAY_ON_DELIVERY'));
    const request=http.expectOne(`${environment.apiUrl}/orders`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({address:'Manama',paymentMethod:'PAY_ON_DELIVERY'});
    request.flush({id:'o1',buyerId:'b',sellerIds:['s'],items:[],address:'Manama',paymentMethod:'PAY_ON_DELIVERY',status:'PENDING',total:0,createdAt:'',updatedAt:''});
  });

  it('sends order search and date filters to the server', () => {
    service.orders('keyboard','SHIPPED','2026-01-01','2026-01-31').subscribe();
    const request=http.expectOne(req=>req.url===`${environment.apiUrl}/orders`);
    expect(request.request.params.get('query')).toBe('keyboard');
    expect(request.request.params.get('status')).toBe('SHIPPED');
    expect(request.request.params.get('from')).toBe('2026-01-01');
    expect(request.request.params.get('to')).toBe('2026-01-31');
    request.flush([]);
  });
});
