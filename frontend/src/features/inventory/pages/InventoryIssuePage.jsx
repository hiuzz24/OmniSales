import { PackageMinus } from 'lucide-react';

const InventoryIssuePage = () => (
  <main className="product-workspace product-workspace--flow">
    <header>
      <div style={{ display: 'flex', alignItems: 'center', gap: 14 }}>
        <span style={{ width: 44, height: 44, display: 'grid', placeItems: 'center', border: '1px solid #dce5ed', borderRadius: 11, color: '#b45309', background: '#fffbeb' }}>
          <PackageMinus size={20} aria-hidden="true" />
        </span>
        <div><h1 style={{ margin: 0, fontSize: 26 }}>Phiếu xuất kho</h1><p style={{ margin: '4px 0 0', color: '#64748b', fontSize: 14 }}>Theo dõi và xử lý hàng xuất khỏi kho</p></div>
      </div>
    </header>
    <section style={{ padding: 48, border: '1px solid #dfe7ef', borderRadius: 14, background: '#fff', color: '#64748b', textAlign: 'center' }}>
      Nội dung phiếu xuất kho được quản lý tại màn danh sách phiếu xuất.
    </section>
  </main>
);

export default InventoryIssuePage;
