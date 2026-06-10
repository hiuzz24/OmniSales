// Mock data cho auth API
export const MOCK_USERS = [
  {
    id: 1,
    email: 'admin@osms.com',
    password: 'admin123',
    name: 'Admin User',
    role: 'SYSTEM_ADMIN',
    avatar: null,
    isActive: true,
    createdAt: '2024-01-01T00:00:00Z',
  },
  {
    id: 2,
    email: 'owner@osms.com',
    password: 'owner123',
    name: 'Nguyễn Văn Owner',
    role: 'OWNER',
    avatar: null,
    isActive: true,
    createdAt: '2024-01-15T00:00:00Z',
  },
  {
    id: 3,
    email: 'staff@osms.com',
    password: 'staff123',
    name: 'Trần Văn Staff',
    role: 'SALES',
    avatar: null,
    isActive: true,
    createdAt: '2024-02-01T00:00:00Z',
  },
];

export const MOCK_SHOPS = [
  {
    id: 1,
    name: 'Shop Thời Trang Nữ',
    platform: 'SHOPEE',
    shopId: 'SP001',
    ownerId: 2,
    isConnected: true,
    monthlyRevenue: 125000000,
  },
  {
    id: 2,
    name: 'Cosmetics Store',
    platform: 'TIKTOK_SHOP',
    shopId: 'TT001',
    ownerId: 2,
    isConnected: true,
    monthlyRevenue: 89000000,
  },
  {
    id: 3,
    name: 'Lazada Accessories',
    platform: 'LAZADA',
    shopId: 'LZ001',
    ownerId: 2,
    isConnected: true,
    monthlyRevenue: 45000000,
  },
];

export const MOCK_ORDERS = [
  {
    id: 1,
    orderCode: 'ORD-2024-0001',
    platform: 'SHOPEE',
    status: 'PENDING',
    totalAmount: 299000,
    customerName: 'Nguyễn Thị A',
    customerPhone: '0901234567',
    items: [
      { productName: 'Áo thun nam basic', quantity: 2, price: 149500 },
    ],
    createdAt: '2024-06-10T08:30:00Z',
  },
  {
    id: 2,
    orderCode: 'ORD-2024-0002',
    platform: 'TIKTOK_SHOP',
    status: 'CONFIRMED',
    totalAmount: 599000,
    customerName: 'Trần Văn B',
    customerPhone: '0912345678',
    items: [
      { productName: 'Giày thể thao', quantity: 1, price: 599000 },
    ],
    createdAt: '2024-06-10T09:15:00Z',
  },
  {
    id: 3,
    orderCode: 'ORD-2024-0003',
    platform: 'LAZADA',
    status: 'SHIPPING',
    totalAmount: 1250000,
    customerName: 'Lê Thị C',
    customerPhone: '0923456789',
    items: [
      { productName: 'Túi xách nữ', quantity: 1, price: 850000 },
      { productName: 'Ví nam da', quantity: 1, price: 400000 },
    ],
    createdAt: '2024-06-10T10:00:00Z',
  },
];

export const MOCK_PRODUCTS = [
  {
    id: 1,
    name: 'Áo thun nam basic',
    sku: 'ATN-BASIC-001',
    price: 149500,
    stock: 250,
    category: 'Thời trang nam',
    imageUrl: 'https://images.unsplash.com/photo-1521572163474-6864f9cf17ab?w=200',
    platforms: ['SHOPEE', 'TIKTOK_SHOP', 'LAZADA'],
  },
  {
    id: 2,
    name: 'Giày thể thao Nike Air',
    sku: 'GT-NIKE-001',
    price: 1599000,
    stock: 45,
    category: 'Giày dép',
    imageUrl: 'https://images.unsplash.com/photo-1542291026-7eec264c27ff?w=200',
    platforms: ['SHOPEE', 'LAZADA'],
  },
  {
    id: 3,
    name: 'Túi xách nữ hàng hiệu',
    sku: 'TX-NU-001',
    price: 850000,
    stock: 30,
    category: 'Túi xách',
    imageUrl: 'https://images.unsplash.com/photo-1548036328-c9fa89d128fa?w=200',
    platforms: ['SHOPEE', 'TIKTOK_SHOP'],
  },
];

export const MOCK_WAREHOUSES = [
  {
    id: 1,
    name: 'Kho chính HCM',
    address: '123 Nguyễn Trãi, Quận 1, TP.HCM',
    stock: 3250,
    capacity: 5000,
  },
  {
    id: 2,
    name: 'Kho phụ Hà Nội',
    address: '456 Trần Duy Hưng, Cầu Giấy, HN',
    stock: 1800,
    capacity: 3000,
  },
];

export const MOCK_STATS = {
  totalRevenue: 24580000,
  revenueGrowth: 18,
  totalOrders: 347,
  activeChannels: 3,
  totalProducts: 156,
  lowStockAlerts: 12,
  monthlyRevenue: [
    { month: 'Tháng 1', revenue: 18500000 },
    { month: 'Tháng 2', revenue: 21000000 },
    { month: 'Tháng 3', revenue: 19800000 },
    { month: 'Tháng 4', revenue: 23200000 },
    { month: 'Tháng 5', revenue: 25800000 },
    { month: 'Tháng 6', revenue: 24580000 },
  ],
  platformRevenue: [
    { platform: 'Shopee', revenue: 9800000, percentage: 40 },
    { platform: 'TikTok Shop', revenue: 7350000, percentage: 30 },
    { platform: 'Lazada', revenue: 7430000, percentage: 30 },
  ],
};
