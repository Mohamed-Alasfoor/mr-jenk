export interface Product {
  id: string;
  name: string;
  description: string;
  category?: string;
  price: number;
  quantity: number;
  imageUrls: string[];
  sellerId: string;
  createdAt: string;
  updatedAt: string;
}
