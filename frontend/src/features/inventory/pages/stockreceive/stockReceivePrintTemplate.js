const formatDateParts = (value) => {
  const date = value ? new Date(value) : new Date();
  if (Number.isNaN(date.getTime())) {
    return { day: '...', month: '...', year: '...' };
  }
  return {
    day: String(date.getDate()).padStart(2, '0'),
    month: String(date.getMonth() + 1).padStart(2, '0'),
    year: String(date.getFullYear()),
  };
};

const formatNumber = (value) => new Intl.NumberFormat('vi-VN').format(Number(value ?? 0));

const formatMoney = (value) => new Intl.NumberFormat('vi-VN', {
  maximumFractionDigits: 0,
}).format(Number(value ?? 0));

const escapeHtml = (value) => String(value ?? '')
  .replaceAll('&', '&amp;')
  .replaceAll('<', '&lt;')
  .replaceAll('>', '&gt;')
  .replaceAll('"', '&quot;')
  .replaceAll("'", '&#039;');

const digitWords = ['không', 'một', 'hai', 'ba', 'bốn', 'năm', 'sáu', 'bảy', 'tám', 'chín'];
const scaleWords = ['', 'nghìn', 'triệu', 'tỷ'];

const readTriple = (number, forceHundreds = false) => {
  const hundred = Math.floor(number / 100);
  const ten = Math.floor((number % 100) / 10);
  const unit = number % 10;
  const words = [];

  if (hundred > 0 || forceHundreds) {
    words.push(`${digitWords[hundred]} trăm`);
  }

  if (ten > 1) {
    words.push(`${digitWords[ten]} mươi`);
    if (unit === 1) words.push('mốt');
    else if (unit === 5) words.push('lăm');
    else if (unit > 0) words.push(digitWords[unit]);
  } else if (ten === 1) {
    words.push('mười');
    if (unit === 5) words.push('lăm');
    else if (unit > 0) words.push(digitWords[unit]);
  } else if (unit > 0) {
    if (hundred > 0 || forceHundreds) words.push('lẻ');
    words.push(digitWords[unit]);
  }

  return words.join(' ');
};

const numberToVietnameseWords = (value) => {
  const amount = Math.round(Math.abs(Number(value ?? 0)));
  if (amount === 0) return 'Không đồng';

  const groups = [];
  let remaining = amount;
  while (remaining > 0) {
    groups.push(remaining % 1000);
    remaining = Math.floor(remaining / 1000);
  }

  const words = [];
  for (let index = groups.length - 1; index >= 0; index -= 1) {
    const group = groups[index];
    if (group === 0) continue;
    const forceHundreds = index < groups.length - 1 && group < 100;
    words.push(`${readTriple(group, forceHundreds)} ${scaleWords[index]}`.trim());
  }

  const text = words.join(' ').replace(/\s+/g, ' ').trim();
  return `${text.charAt(0).toUpperCase()}${text.slice(1)} đồng`;
};

const getReceiptItems = (receipt) => Array.isArray(receipt?.items) ? receipt.items : [];

const getItemName = (item) =>
  item?.productName
  ?? item?.productVariantName
  ?? item?.variantName
  ?? item?.variantSku
  ?? item?.sku
  ?? '';

const getItemSku = (item) => item?.sku ?? item?.variantSku ?? '';

const buildItemRows = (items) => {
  if (items.length === 0) {
    return `
      <tr>
        <td class="center">1</td>
        <td colspan="7" class="muted center">Không có sản phẩm</td>
      </tr>
    `;
  }

  return items.map((item, index) => {
    const quantity = Number(item.quantity ?? 0);
    const unitCost = Number(item.unitCost ?? item.unitPrice ?? 0);
    const lineTotal = Number(item.totalCost ?? quantity * unitCost);
    return `
      <tr>
        <td class="center">${index + 1}</td>
        <td>
          <div>${escapeHtml(getItemName(item))}</div>
          ${item.variantName ? `<div class="item-note">${escapeHtml(item.variantName)}</div>` : ''}
          ${item.notes ? `<div class="item-note">${escapeHtml(item.notes)}</div>` : ''}
        </td>
        <td class="center">${escapeHtml(getItemSku(item))}</td>
        <td class="center">${escapeHtml(item.unitName ?? item.unit ?? 'Cái')}</td>
        <td class="number">${formatNumber(quantity)}</td>
        <td class="number">${formatNumber(quantity)}</td>
        <td class="number">${formatMoney(unitCost)}</td>
        <td class="number">${formatMoney(lineTotal)}</td>
      </tr>
    `;
  }).join('');
};

export const buildStockReceivePrintHtml = (receipt) => {
  const items = getReceiptItems(receipt);
  const totalAmount = items.reduce((sum, item) => {
    const quantity = Number(item.quantity ?? 0);
    const unitCost = Number(item.unitCost ?? item.unitPrice ?? 0);
    return sum + Number(item.totalCost ?? quantity * unitCost);
  }, 0);
  const totalQuantity = items.reduce((sum, item) => sum + Number(item.quantity ?? 0), 0);
  const date = formatDateParts(receipt?.receivedAt ?? receipt?.confirmedAt ?? receipt?.createdAt);
  const printDate = formatDateParts(new Date());

  return `<!doctype html>
<html lang="vi">
<head>
  <meta charset="utf-8" />
  <title>Phiếu nhập kho ${escapeHtml(receipt?.receiptCode ?? '')}</title>
  <style>
    @page { size: A4 portrait; margin: 16mm 14mm; }
    * { box-sizing: border-box; }
    body {
      margin: 0;
      color: #000;
      font-family: "Times New Roman", Times, serif;
      font-size: 13px;
      line-height: 1.25;
      background: #fff;
    }
    .sheet { width: 100%; }
    .top-grid {
      display: grid;
      grid-template-columns: 1fr 1fr;
      align-items: start;
      gap: 20px;
      margin-bottom: 10px;
    }
    .unit-block div { min-height: 20px; }
    .template-note { text-align: center; font-weight: 700; }
    .template-note div:last-child { font-weight: 400; font-style: italic; font-size: 12px; }
    h1 {
      margin: 10px 0 4px;
      text-align: center;
      font-size: 22px;
      font-weight: 700;
      letter-spacing: 0;
    }
    .debit-credit {
      width: 210px;
      margin-left: auto;
      margin-bottom: 8px;
      line-height: 1.6;
    }
    .line { margin: 7px 0; }
    .form-line {
      display: flex;
      align-items: baseline;
      gap: 6px;
      margin: 8px 0;
      width: 100%;
    }
    .form-line .fill {
      flex: 1 1 auto;
      min-width: 70px;
      border-bottom: 1px dotted #000;
      min-height: 18px;
      padding: 0 4px 1px;
    }
    .form-line .fill.short { flex: 0 0 150px; }
    .form-line .fill.medium { flex: 0 0 230px; }
    .form-line .fill.long { flex: 1 1 360px; }
    .form-line .label { white-space: nowrap; }
    .dots {
      display: inline-block;
      min-width: 180px;
      border-bottom: 1px dotted #000;
      padding: 0 4px 1px;
    }
    .long { min-width: 420px; }
    .medium { min-width: 260px; }
    table {
      width: 100%;
      border-collapse: collapse;
      margin-top: 10px;
      table-layout: fixed;
    }
    th, td {
      border: 1px solid #000;
      padding: 5px 6px;
      vertical-align: middle;
      overflow-wrap: anywhere;
    }
    th { text-align: center; font-weight: 700; }
    .code-row td {
      text-align: center;
      font-weight: 700;
      padding: 3px 6px;
    }
    .center { text-align: center; }
    .number { text-align: right; white-space: nowrap; }
    .muted { color: #444; }
    .item-note { font-size: 11px; color: #333; margin-top: 2px; }
    .total-row td { font-weight: 700; }
    .footer-line { margin-top: 8px; }
    .date-line { margin-top: 18px; text-align: right; font-style: italic; }
    .signatures {
      display: grid;
      grid-template-columns: repeat(4, 1fr);
      gap: 12px;
      margin-top: 10px;
      text-align: center;
      page-break-inside: avoid;
    }
    .signature-title { font-weight: 700; }
    .signature-note { font-style: italic; font-size: 12px; }
    .signature-space { height: 72px; }
    @media print {
      body { -webkit-print-color-adjust: exact; print-color-adjust: exact; }
      .sheet { break-inside: avoid; }
    }
  </style>
</head>
<body>
  <main class="sheet">
    <section class="top-grid">
      <div class="unit-block">
        <div>Đơn vị: <span class="dots medium">${escapeHtml(receipt?.companyName ?? '')}</span></div>
        <div>Bộ phận: <span class="dots medium">${escapeHtml(receipt?.departmentName ?? receipt?.warehouseName ?? '')}</span></div>
      </div>
      <div class="template-note">
        <div>Mẫu số 01 - VT</div>
        <div>(Ban hành theo Thông tư số 200/2014/TT-BTC<br/>ngày 22/12/2014 của Bộ Tài chính)</div>
      </div>
    </section>

    <h1>PHIẾU NHẬP KHO</h1>
    <div class="debit-credit">
      <div>Nợ <span class="dots">${escapeHtml(receipt?.debitAccount ?? '')}</span></div>
      <div>Có <span class="dots">${escapeHtml(receipt?.creditAccount ?? '')}</span></div>
    </div>

    <div class="form-line">
      <span class="label">- Họ và tên người giao:</span>
      <span class="fill long"></span>
    </div>
    <div class="form-line">
      <span class="label">- Theo</span>
      <span class="fill medium"></span>
      <span class="label">số</span>
      <span class="fill short">${escapeHtml(receipt?.invoiceNumber ?? receipt?.receiptCode ?? '')}</span>
      <span class="label">ngày ${date.day} tháng ${date.month} năm ${date.year} của</span>
    </div>
    <div class="form-line">
      <span class="fill long"></span>
    </div>
    <div class="form-line">
      <span class="label">- Nhập tại kho:</span>
      <span class="fill medium">${escapeHtml(receipt?.warehouseName ?? '')}</span>
      <span class="label">địa điểm</span>
      <span class="fill long">${escapeHtml(receipt?.warehouseAddress ?? '')}</span>
    </div>

    <table>
      <colgroup>
        <col style="width: 5%" />
        <col style="width: 28%" />
        <col style="width: 12%" />
        <col style="width: 8%" />
        <col style="width: 10%" />
        <col style="width: 10%" />
        <col style="width: 12%" />
        <col style="width: 15%" />
      </colgroup>
      <thead>
        <tr>
          <th rowspan="2">STT</th>
          <th rowspan="2">Tên, nhãn hiệu, quy cách, phẩm chất vật tư, dụng cụ sản phẩm, hàng hoá</th>
          <th rowspan="2">Mã số</th>
          <th rowspan="2">Đơn vị tính</th>
          <th colspan="2">Số lượng</th>
          <th rowspan="2">Đơn giá</th>
          <th rowspan="2">Thành tiền</th>
        </tr>
        <tr>
          <th>Theo chứng từ</th>
          <th>Thực nhập</th>
        </tr>
      </thead>
      <tbody>
        <tr class="code-row">
          <td>A</td><td>B</td><td>C</td><td>D</td><td>1</td><td>2</td><td>3</td><td>4</td>
        </tr>
        ${buildItemRows(items)}
        <tr class="total-row">
          <td></td>
          <td>Cộng</td>
          <td></td>
          <td></td>
          <td class="number">${formatNumber(totalQuantity)}</td>
          <td class="number">${formatNumber(totalQuantity)}</td>
          <td></td>
          <td class="number">${formatMoney(receipt?.totalCost ?? totalAmount)}</td>
        </tr>
      </tbody>
    </table>

    <div class="footer-line">- Tổng số tiền (viết bằng chữ): <span>${escapeHtml(numberToVietnameseWords(receipt?.totalCost ?? totalAmount))}</span></div>
    <div class="footer-line">- Số chứng từ gốc kèm theo: <span class="dots long">${escapeHtml(receipt?.invoiceNumber ?? '')}</span></div>
    ${receipt?.notes ? `<div class="footer-line">- Ghi chú: <span>${escapeHtml(receipt.notes)}</span></div>` : ''}

    <div class="date-line">Ngày ${printDate.day} tháng ${printDate.month} năm ${printDate.year}</div>
    <section class="signatures">
      <div>
        <div class="signature-title">Người lập phiếu</div>
        <div class="signature-note">(Ký, họ tên)</div>
        <div class="signature-space"></div>
        <div></div>
      </div>
      <div>
        <div class="signature-title">Người giao hàng</div>
        <div class="signature-note">(Ký, họ tên)</div>
        <div class="signature-space"></div>
      </div>
      <div>
        <div class="signature-title">Thủ kho</div>
        <div class="signature-note">(Ký, họ tên)</div>
        <div class="signature-space"></div>
      </div>
      <div>
        <div class="signature-title">Kế toán trưởng</div>
        <div class="signature-note">(Hoặc bộ phận có nhu cầu nhập)<br/>(Ký, họ tên)</div>
        <div class="signature-space"></div>
        <div>${escapeHtml(receipt?.approvedByName ?? '')}</div>
      </div>
    </section>
  </main>
</body>
</html>`;
};

export const printStockReceiveReceipt = (receipt, targetWindow = null) => {
  const printWindow = targetWindow ?? window.open('', '_blank');
  if (!printWindow) {
    throw new Error('Không thể mở cửa sổ in. Vui lòng cho phép popup cho trang này.');
  }

  printWindow.document.open();
  printWindow.document.write(buildStockReceivePrintHtml(receipt));
  printWindow.document.close();
  printWindow.focus();
  printWindow.setTimeout(() => {
    printWindow.print();
  }, 250);
};
