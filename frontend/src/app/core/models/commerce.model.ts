export interface CartItem { productId: string; quantity: number; }
export interface Cart { id?: string; userId: string; items: CartItem[]; updatedAt: string; }
export type OrderStatus = 'PENDING'|'CONFIRMED'|'SHIPPED'|'DELIVERED'|'CANCELLED';
export interface OrderItem { productId:string; sellerId:string; productName:string; unitPrice:number; quantity:number; }
export interface Order { id:string; buyerId:string; sellerIds:string[]; items:OrderItem[]; address:string; paymentMethod:string; status:OrderStatus; total:number; createdAt:string; updatedAt:string; }
export interface Analytics { totalSpent?:number; revenue?:number; orderCount:number; topProducts:{key:string;value:number}[]; topCategories:{key:string;value:number}[]; }
