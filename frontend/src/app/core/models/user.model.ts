export type Role = 'CLIENT' | 'SELLER' | 'ADMIN';

export interface User {
  id: string;
  email: string;
  fullName: string;
  role: Role;
  avatarUrl?: string;
}

export interface PublicSellerProfile {
  id: string;
  fullName: string;
  avatarUrl?: string;
}
