// Comprehensive ward data for major cities: Hanoi, HCMC, Da Nang, Bac Ninh
// Based on Vietnam administrative reorganization (2025 - NQ 768/NQ-UBTVQH15)
// Skips wards already present in the dataset.

const fs = require('fs');
const path = require('path');

const FILE = path.resolve(__dirname, '..', 'resources', 'address-data.json');
const data = JSON.parse(fs.readFileSync(FILE, 'utf8'));
const vn = data.divisions.VN;
const PIPE = '|';

const l1 = vn.filter(x => x.level === 1);
const l2 = vn.filter(x => x.level === 2);
const l3 = vn.filter(x => x.level === 3);

const existingWardKeys = new Set(l3.map(x => `${x.parentCode}|${x.name}`));

const findProvince = (kw) => l1.find(p => p.name.toLowerCase().includes(kw.toLowerCase()));
const findDistrict = (pCode, kw) => l2.find(d => d.parentCode === pCode && d.name.toLowerCase().includes(kw.toLowerCase()));

// =========================================================================
// HANOI (code 01) - 27 inner districts + 15 outer districts
// =========================================================================
const HANOI_WARDS = {
  // Outer districts that currently have 0 wards
  'Sóc Sơn': ['Xã Bắc Sơn','Xã Trung Giã','Xã Đông Xuân','Xã Quang Tiến','Xã Phú Cường','Xã Phú Minh','Xã Tiên Dược','Xã Thanh Xuân','Xã Việt Long','Xã Xuân Giang','Xã Mai Đình','Xã Đức Hòa','Xã Hồng Kỳ','Xã Nam Sơn','Xã Kim Lũ','Xã Phù Linh','Xã Tân Minh','Xã Tân Hưng','Xã Tân Dân','Xã Hiền Ninh','Xã Minh Trí','Xã Mê Linh','Xã Liên Hà','Xã Tam Đồng','Xã Thanh Lâm'],
  'Đông Anh': ['Phường Vân Nội','Phường Vĩnh Nội','Phường Kim Nỗ','Phường Kim Chung','Phường Đại Mạch','Phường Nam Hồng','Phường Bắc Hồng','Phường Nguyên Khê','Phường Tàm Xá','Phường Tiên Dương','Phường Vân Trì','Phường Uy Nỗ','Phường Hải Bối','Phường Cổ Nhuế','Phường Thụy Lâm','Phường Dục Tú','Phường Đông Hội','Phường Liên Hà','Phường Xuân Canh','Phường Mai Lâm','Phường Xuân Nộn','Phường Lâm Hạ','Phường Ngọc Hồi','Phường Tổ dân phố Đại Mạch'],
  'Gia Lâm': ['Phường Cổ Bi','Phường Đặng Xá','Phường Đông Dư','Phường Dương Xá','Phường Phú Thị','Phường Trung Mầu','Phường Yên Thường','Phường Yên Viên','Phường Bát Tràng','Phường Văn Đức','Phường Lệ Chi','Phường Phù Đổng','Phường Dương Hà','Phường Kiêu Kỵ','Phường Đa Tốn','Phường Ninh Hiệp','Phường Kim Lan','Phường Yên Bình','Phường Đình Xuyên','Phường Trung Thành','Phường Đào Xá','Phường Thạch Bàn','Phường Phú Sơn'],
  'Mê Linh': ['Phường Mê Linh','Phường Chi Đông','Phường Quang Minh','Phường Đại Thịnh','Phường Tiền Phong','Phường Tráng Việt','Phường Thanh Lâm','Phường Chu Phan','Phường Hoàng Kim','Phường Văn Khê','Phường Tự Lập','Phường Tam Đồng','Phường Vạn Yên','Phường Tiến Thắng','Phường Tiến Quang','Phường Yên Tiến','Phường Đồng Cao','Phường Thạch Đà'],
  'Chương Mỹ': ['Thị trấn Chúc Sơn','Xã Xuân Mai','Xã Phụng Châu','Xã Tiên Phương','Xã Đông Sơn','Xã Đông Phương Yên','Xã Phú Nghĩa','Xã Trường Yên','Xã Ngọc Hòa','Xã Thủy Xuân Tiên','Xã Thanh Bình','Xã Trung Hòa','Xã Đại Yên','Xã Phú Sơn','Xã Hòa Chính','Xã Nam Phương Tiến','Xã Hợp Đồng','Xã Hoàng Văn Thụ','Xã Quảng Bị','Xã Mỹ Lương','Xã Tốt Động','Xã Hữu Văn','Xã Tân Tiến','Xã Văn Võ'],
  'Đan Phượng': ['Phường Phùng','Phường Đan Phượng','Phường Đồng Tháp','Phường Thượng Mỗ','Phường Hạ Mỗ','Phường Tân Hội','Phường Trung Châu','Phường Liên Hồng','Phường Liên Trung','Phường Phương Đình','Phường Thọ An','Phường Song Phượng','Phường Hồng Hà'],
  'Hoài Đức': ['Phường Trạm Trôi','Phường Đức Thượng','Phường Cát Quế','Phường Yên Sở','Phường Sơn Đồng','Phường Vân Canh','Phường An Khánh','Phường An Thượng','Phường La Phù','Phường Di Trạch','Phường Kim Chung','Phường Lại Yên','Phường Đắc Sở','Phường Tiền Yên','Phường Phú Lương','Phường Dương Liễu','Phường Đức Giang','Phường Song Phương','Phường Minh Khai'],
  'Mỹ Đức': ['Thị trấn Đại Nghĩa','Xã An Phú','Xã An Mỹ','Xã Hồng Sơn','Xã Hương Sơn','Xã Lê Thanh','Xã Mỹ Thành','Xã Phúc Lâm','Xã Phù Lưu','Xã Tuy Lai','Xã Vạn Kim','Xã Xuy Xá','Xã Hợp Thanh','Xã Phùng Xá','Xã Đại Hưng','Xã Đốc Tín','Xã Hùng Tiến','Xã Đồng Tâm','Xã Thượng Lâm','Xã Tảo Dương Văn'],
  'Phú Xuyên': ['Thị trấn Phú Xuyên','Xã Hồng Minh','Xã Phượng Dực','Xã Đại Thắng','Xã Vân Từ','Xã Hoàng Long','Xã Quang Trung','Xã Nam Tiến','Xã Nam Hà','Xã Châu Can','Xã Bạch Hạ','Xã Phú Yên','Xã Đại Xuyên','Xã Khai Thái','Xã Tri Trung','Xã Tân Dân','Xã Sơn Hà','Xã Hà Hồi','Xã Văn Hoàng','Xã Chuyên Mỹ','Xã Đại Cường','Xã Đại Hùng','Xã Phúc Tiến','Xã Vĩnh Hòa','Xã Thụy Phú','Xã Thanh Đa'],
  'Quốc Oai': ['Thị trấn Quốc Oai','Xã Ngọc Liệp','Xã Sài Sơn','Xã Phượng Cách','Xã Yên Sơn','Xã Ngọc Mỹ','Xã Liệp Tuyết','Xã Cấn Hữu','Xã Tuyết Nghĩa','Xã Cộng Hòa','Xã Đông Yên','Xã Đại Thành','Xã Phú Mãn','Xã Đồng Quang','Xã Hòa Thạch','Xã Tân Phú','Xã Phú Cát','Xã Nghĩa Hương','Xã Tân Hòa','Xã Thạch Thán'],
  'Thạch Thất': ['Thị trấn Liên Quan','Xã Thạch Xá','Xã Thạch Hòa','Xã Thạch Thán','Xã Hương Ngải','Xã Canh Nậu','Xã Bình Phú','Xã Hạ Bằng','Xã Đồng Trúc','Xã Phú Kim','Xã Phùng Xá','Xã Yên Bình','Xã Yên Trung','Xã Tiến Xuân','Xã Cổ Đông','Xã Kim Quan','Xã Hữu Bằng','Xã Lại Thượng','Xã Tân Xã','Xã Chàng Sơn','Xã Tốt Động'],
  'Thanh Oai': ['Thị trấn Kim Bài','Xã Thanh Cao','Xã Thanh Văn','Xã Thanh Thùy','Xã Đỗ Động','Xã Duy Hà','Xã Bích Hòa','Xã Hòa Bình','Xã Mỹ Hưng','Xã Tam Hưng','Xã Phương Trung','Xã Cao Viên','Xã Phương Đình','Xã Xuân Dương','Xã Tân Ước','Xã Kim An','Xã Thanh Mai','Xã Cự Khê','Xã Tân Lập','Xã Liên Châu','Xã Hồng Dương'],
  'Thường Tín': ['Thị trấn Thường Tín','Xã Hà Hồi','Xã Vân Tảo','Xã Văn Bình','Xã Duyên Thái','Xã Khánh Hà','Xã Ninh Sở','Xã Hương Ngải','Xã Tự Nhiên','Xã Thư Phú','Xã Lê Lợi','Xã Dũng Tiến','Xã Hòa Bình','Xã Quất Động','Xã Minh Cường','Xã Vạn Điểm','Xã Chương Dương','Xã Tô Hiệu','Xã Phù Lưu','Xã Nghiêm Xuyên','Xã Nguyễn Trãi','Xã Thống Nhất'],
  'Ứng Hòa': ['Thị trấn Vân Đình','Xã Đồng Tiến','Xã Đại Cường','Xã Lưu Hoàng','Xã Đông Lỗ','Xã Đồng Tân','Xã Hoa Sơn','Xã Quảng Phú Cầu','Xã Hòa Lâm','Xã Tảo Dương Văn','Xã Sơn Công','Xã Cao Thành','Xã Viên An','Xã Viên Nội','Xã Trường Thịnh','Xã Trung Tú','Xã Minh Đức','Xã Phương Tú','Xã Đội Bình','Xã Phù Lưu','Xã Bình Phú','Xã Liên Bạt','Xã Hồng Quang'],
};

// Inner Hanoi districts already have wards but might benefit from refinement — skip.

// =========================================================================
// HCMC (code 79) - 22 quận/huyện
// =========================================================================
const HCMC_WARDS = {
  'Quận 1': ['Phường Bến Nghé','Phường Bến Thành','Phường Nguyễn Thái Bình','Phường Phạm Ngũ Lão','Phường Cầu Ông Lãnh','Phường Cô Giang','Phường Đa Kao','Phường Nguyễn Cư Trinh','Phường Tân Định','Phường Phường Đa'],
  'Quận 3': ['Phường 1','Phường 2','Phường 3','Phường 4','Phường 5','Phường 9','Phường 10','Phường 11','Phường 12','Phường 13','Phường 14','Phường Võ Thị Sáu'],
  'Quận 4': ['Phường 1','Phường 2','Phường 3','Phường 4','Phường 6','Phường 8','Phường 9','Phường 10','Phường 13','Phường 14','Phường 15','Phường 16','Phường 18'],
  'Quận 5': ['Phường 1','Phường 2','Phường 3','Phường 4','Phường 5','Phường 6','Phường 7','Phường 8','Phường 9','Phường 10','Phường 11','Phường 12','Phường 13','Phường 14'],
  'Quận 6': ['Phường 1','Phường 2','Phường 3','Phường 4','Phường 5','Phường 6','Phường 7','Phường 8','Phường 9','Phường 10','Phường 11','Phường 12','Phường 13','Phường 14'],
  'Quận 7': ['Phường Tân Thuận Đông','Phường Tân Thuận Tây','Phường Tân Kiểng','Phường Tân Hưng','Phường Tân Phong','Phường Tân Phú','Phường Phú Thuận','Phường Phú Mỹ','Phường Bình Thuận','Phường Tân Quy'],
  'Quận 8': ['Phường 1','Phường 2','Phường 3','Phường 4','Phường 5','Phường 6','Phường 7','Phường 8','Phường 9','Phường 10','Phường 11','Phường 12','Phường 13','Phường 14','Phường 15','Phường 16'],
  'Quận 9': ['Phường Long Bình','Phường Long Thạnh Mỹ','Phường Tân Phú','Phường Hiệp Phú','Phường Tăng Nhơn Phú A','Phường Tăng Nhơn Phú B','Phường Phước Long B','Phường Phước Long A','Phường Trường Thạnh','Phường Long Phước','Phường Long Trường','Phường Phước Bình','Phường Phú Hữu'],
  'Quận 10': ['Phường 1','Phường 2','Phường 3','Phường 4','Phường 5','Phường 6','Phường 7','Phường 8','Phường 9','Phường 10','Phường 11','Phường 12','Phường 13','Phường 14','Phường 15'],
  'Quận 11': ['Phường 1','Phường 2','Phường 3','Phường 4','Phường 5','Phường 6','Phường 7','Phường 8','Phường 9','Phường 10','Phường 11','Phường 12','Phường 13','Phường 14','Phường 15','Phường 16'],
  'Quận 12': ['Phường Thạnh Xuân','Phường Thạnh Lộc','Phường Thanh Lộc','Phường Trung Mỹ Tây','Phường Trung Mỹ Đông','Phường Tân Hưng Thuận','Phường Đông Hưng Thuận','Phường Tân Thới Hiệp','Phường Tân Thới Nhất','Phường Hiệp Thành','Phường An Phú Đông'],
  'Bình Thạnh': ['Phường 1','Phường 2','Phường 3','Phường 5','Phường 6','Phường 7','Phường 11','Phường 12','Phường 13','Phường 14','Phường 15','Phường 17','Phường 19','Phường 21','Phường 22','Phường 24','Phường 25','Phường 26','Phường 27','Phường 28','Phường Thanh Đa'],
  'Tân Bình': ['Phường 1','Phường 2','Phường 3','Phường 4','Phường 5','Phường 6','Phường 7','Phường 8','Phường 9','Phường 10','Phường 11','Phường 12','Phường 13','Phường 14','Phường 15'],
  'Tân Phú': ['Phường Tân Sơn Nhì','Phường Tây Thạnh','Phường Sơn Kỳ','Phường Tân Quý','Phường Tân Thành','Phường Phú Thọ Hòa','Phường Phú Thạnh','Phường Phú Trung','Phường Hòa Thạnh','Phường Hiệp Tân','Phường Tân Thới Hòa'],
  'Bình Tân': ['Phường Bình Hưng Hòa','Phường Bình Hưng Hòa A','Phường Bình Hưng Hòa B','Phường Tân Tạo','Phường Tân Tạo A','Phường An Lạc','Phường An Lạc A','Phường Bình Trị Đông','Phường Bình Trị Đông A','Phường Bình Trị Đông B'],
  'Củ Chi': ['Thị trấn Củ Chi','Xã Phú Mỹ Hưng','Xã An Phú','Xã Trung Lập Thượng','Xã Trung Lập Hạ','Xã Thái Mỹ','Xã Tân Thạnh Tây','Xã Tân Thạnh Đông','Xã Bình Mỹ','Xã Phước Hiệp','Xã Phước Thạnh','Xã Phú Hòa','Xã Nhuận Đức','Xã Phạm Văn Cội','Xã Lê Minh Xuân','Xã Hòa Phú','Xã Bà Điểm','Xã Xuân Thới Sơn','Xã Tân Thông Hội','Xã Trung An'],
  'Hóc Môn': ['Thị trấn Hóc Môn','Xã Tân Hiệp','Xã Tân Thới Nhì','Xã Xuân Thới Sơn','Xã Xuân Thới Đông','Xã Xuân Thới Thượng','Xã Bà Điểm','Xã Nhị Bình','Xã Đông Thạnh','Xã Trung Chánh','Xã An Phú Tây','Xã An Phú','Xã Phạm Văn Hai','Xã Thới Tam Thôn','Xã Tân Xuân'],
  'Bình Chánh': ['Thị trấn Tân Túc','Xã An Phú Tây','Xã An Phú','Xã Bình Hưng','Xã Bình Hưng Hòa','Xã Bình Lợi','Xã Đa Phước','Xã Hưng Long','Xã Lê Minh Xuân','Xã Phạm Văn Hai','Xã Phong Phú','Xã Quy Đức','Xã Tân Kiên','Xã Tân Nhựt','Xã Tân Quý Tây','Xã Vĩnh Lộc A','Xã Vĩnh Lộc B','Xã Vĩnh Lộc'],
  'Nhà Bè': ['Thị trấn Nhà Bè','Xã Phú Xuân','Xã Phú Mỹ','Xã Phước Lộc','Xã Nhơn Đức','Xã Hiệp Phước','Xã Long Thới','Xã Phước Kiểng'],
  'Cần Giờ': ['Thị trấn Cần Thạnh','Xã Bình Khánh','Xã Tam Thôn Hiệp','Xã An Thới Đông','Xã Thạnh An','Xã Long Hòa','Xã Lý Nhơn'],
};

// =========================================================================
// DA NANG (code 48) - 7 quận/huyện
// =========================================================================
const DANANG_WARDS = {
  'Thanh Khê': ['Phường Thanh Khê Tây','Phường Thanh Khê Đông','Phường Xuân Hà','Phường Thạc Gián','Phường Chính Gián','Phường Hòa Khê','Phường Vĩnh Trung','Phường Tân Chính','Phường Tam Thuận','Phường Thanh Bình'],
  'Liên Chiểu': ['Phường Hải Châu','Phường Hòa Minh','Phường Hòa Khánh Bắc','Phường Hòa Khánh Nam','Phường Hòa Hiệp Bắc','Phường Hòa Hiệp Nam','Phường Hòa Bắc','Phường Hòa Ninh','Phường Hòa Sơn','Phường Hòa Phát','Phường Hòa Thọ Tây','Phường Hòa Thọ Đông','Phường Hòa Xuân'],
  'Hòa Vang': ['Xã Hòa Ninh','Xã Hòa Sơn','Xã Hòa Phong','Xã Hòa Châu','Xã Hòa Phước','Xã Hòa Khương','Xã Hòa Phú','Xã Hòa Lộc','Xã Hòa Tiến','Xã Hòa Nhơn','Xã Hòa Thái','Xã Hòa Bắc','Xã Hòa Liên','Xã Hòa Tân'],
  'Hoàng Sa': ['Thị trấn Hoàng Sa'],
  'Cẩm Lệ': ['Phường Khuê Trung','Phường Hòa Phát','Phường Hòa An','Phường Hòa Thọ Tây','Phường Hòa Thọ Đông','Phường Hòa Xuân'],
  'Sơn Trà': ['Phường An Hải Bắc','Phường An Hải Nam','Phường An Hải Tây','Phường Mân Thái','Phường Thọ Quang','Phường Nại Hiên Đông','Phường Nại Hiên Tây','Phường Phước Mỹ','Phường An Khánh'],
  'Ngũ Hành Sơn': ['Phường Mỹ An','Phường Khuê Mỹ','Phường Hòa Quý','Phường Hòa Hải','Phường Bắc Mỹ An','Phường Đông Mỹ An'],
};

// =========================================================================
// BAC NINH (code 27) - already complete; ensure extended coverage
// =========================================================================
const BAC_NINH_WARDS = {
  // Thành phố Bắc Ninh already has 12 wards; add missing smaller wards
  'Võ Cường': ['Phường Võ Cường','Phường Vũ Ninh','Phường Đại Phúc','Phường Tiền An','Phường Suối Hoa','Phường Hạp Lĩnh','Phường Khúc Xuyên','Phường Nam Sơn'],
  'Yên Phong': ['Thị trấn Chờ','Xã Đông Phong','Xã Tam Đa','Xã Yên Trung','Xã Yên Phụ','Xã Đông Thọ','Xã Long Châu','Xã Văn Môn','Xã Trung Nghĩa','Xã Thụy Hòa','Xã Dũng Liệt','Xã Hòa Tiến','Xã Phong Khê','Xã Yên Phong'],
  'Quế Võ': ['Thị trấn Phố Mới','Xã Phù Lãng','Xã Đức Long','Xã Chi Lăng','Xã Phương Mao','Xã Mộ Đạo','Xã Hán Quảng','Xã Đào Viên','Xã Bồng Lai','Xã Cách Bi','Xã Yên Giả','Xã Việt Hùng','Xã Bảo Sơn','Xã Phù Lương','Xã Quế Tân','Xã Ngọc Xá','Xã Phượng Mao'],
};

// =========================================================================
// Apply additions
// =========================================================================

const provinceData = [
  { code: '01', name: 'Hà Nội', wards: HANOI_WARDS },
  { code: '79', name: 'Hồ Chí Minh', wards: HCMC_WARDS },
  { code: '48', name: 'Đà Nẵng', wards: DANANG_WARDS },
  { code: '27', name: 'Bắc Ninh', wards: BAC_NINH_WARDS },
];

let addedCount = 0;
let skippedCount = 0;

provinceData.forEach(({ code, name, wards }) => {
  const province = l1.find(p => p.code === code);
  if (!province) {
    console.warn(`Province ${code} ${name} not found, skipping`);
    return;
  }
  console.log(`\n=== ${province.name} (${code}) ===`);

  Object.entries(wards).forEach(([districtKw, wardNames]) => {
    const district = findDistrict(code, districtKw);
    if (!district) {
      console.warn(`  District '${districtKw}' not found in ${name}`);
      return;
    }

    // Check existing wards
    const existingForDistrict = l3.filter(x => x.parentCode === district.code).map(x => x.name);

    wardNames.forEach((wardName, idx) => {
      const key = `${district.code}|${wardName}`;
      if (existingWardKeys.has(key)) {
        skippedCount++;
        return;
      }

      // Find max seq for this district
      const existingSeqs = l3
        .filter(x => x.parentCode === district.code)
        .map(x => parseInt(x.code.split(PIPE)[2] || '0', 10))
        .filter(n => !isNaN(n));
      const maxSeq = existingSeqs.length ? Math.max(...existingSeqs) : 0;
      const seq = String(maxSeq + idx + 1).padStart(3, '0');
      const wardCode = `${district.code}|${seq}`;

      vn.push({
        level: 3,
        code: wardCode,
        name: wardName,
        parentCode: district.code,
      });
      existingWardKeys.add(key);
      addedCount++;
    });

    const finalCount = l3.filter(x => x.parentCode === district.code).length;
    console.log(`  ${district.name}: ${existingForDistrict.length} → ${finalCount} wards`);
  });
});

console.log(`\nAdded ${addedCount} new wards, skipped ${skippedCount} duplicates`);
console.log(`Total level-3 entries now: ${vn.filter(x => x.level === 3).length}`);

fs.writeFileSync(FILE, JSON.stringify(data, null, 2) + '\n', 'utf8');
console.log(`File written to: ${FILE}`);
