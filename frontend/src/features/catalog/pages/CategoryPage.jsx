import { useState, useEffect, useMemo } from 'react';
import { toast } from 'react-toastify';
import {
  Tag, Plus, Search, ChevronDown, CheckCircle, XCircle,
  Folder, FolderOpen, Package, MoreHorizontal, ChevronLeft, ChevronRight,
  AlertTriangle, X, Check, Edit2, Trash2, Power, CornerDownRight,
  Maximize2, Minimize2
} from 'lucide-react';
import categoryApi from '../../../api/categoryApi';
import PageHeader from '../../../shared/components/PageHeader';
import Pagination from '../../../shared/components/Pagination';
import styles from './CategoryPage.module.css';

const CategoryPage = () => {
  const [data, setData] = useState({
    totalCategories: 0,
    totalActiveCategories: 0,
    totalParentCategories: 0,
    totalProducts: 0,
    categories: [],
    totalPages: 0,
    totalElements: 0
  });
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [page, setPage] = useState(0);
  const [size] = useState(50);

  // Filters
  const [searchInput, setSearchInput] = useState('');
  const [statusFilter, setStatusFilter] = useState('');

  // Modal State for Add Category
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [parentCategories, setParentCategories] = useState([]);
  const [categoryName, setCategoryName] = useState('');
  const [parentId, setParentId] = useState('');
  const [description, setDescription] = useState('');
  const [categoryStatus, setCategoryStatus] = useState('ACTIVE');
  const [submitting, setSubmitting] = useState(false);

  // Actions Dropdown state
  const [activeMenuId, setActiveMenuId] = useState(null);

  // Inline Editing State
  const [editingId, setEditingId] = useState(null);
  const [editName, setEditName] = useState('');
  const [editParentId, setEditParentId] = useState('');
  const [editDescription, setEditDescription] = useState('');
  const [editStatus, setEditStatus] = useState('ACTIVE');

  // Close actions menu on click outside
  useEffect(() => {
    const handleOutsideClick = () => {
      setActiveMenuId(null);
    };
    if (activeMenuId) {
      document.addEventListener('click', handleOutsideClick);
    }
    return () => {
      document.removeEventListener('click', handleOutsideClick);
    };
  }, [activeMenuId]);

  // Fetch Category Dashboard Data
  const fetchDashboardData = async () => {
    try {
      setLoading(true);
      setError(null);
      const res = await categoryApi.getDashboard(page, size);
      if (res) {
        const categories = res.categories ?? [];
        const nextTotalElements = res.totalElements ?? res.totalCategories ?? categories.length ?? 0;
        setData({
          totalCategories: res.totalCategories ?? 0,
          totalActiveCategories: res.totalActiveCategories ?? 0,
          totalParentCategories: res.totalParentCategories ?? 0,
          totalProducts: res.totalProducts ?? 0,
          categories,
          totalPages: res.totalPages ?? Math.ceil(nextTotalElements / size),
          totalElements: nextTotalElements
        });
      }
    } catch (err) {
      console.error('Error fetching categories dashboard data:', err);
      setError('Không thể tải dữ liệu danh mục sản phẩm. Vui lòng thử lại sau.');
      toast.error('Tải dữ liệu danh mục thất bại');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchDashboardData();
  }, [page, size]);

  // Load all parent categories for selects
  const loadParentCategories = async () => {
    try {
      const list = await categoryApi.getAll();
      // Filter active root categories (which don't have parentId)
      const roots = list.filter(cat => cat.parentId === null && (!cat.status || cat.status === 'ACTIVE'));
      setParentCategories(roots);
    } catch (err) {
      console.error('Error loading parent categories:', err);
    }
  };

  // Helpers
  const formatDate = (dateString) => {
    if (!dateString) return '';
    const date = new Date(dateString);
    const day = String(date.getDate()).padStart(2, '0');
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const year = date.getFullYear();
    return `${day}/${month}/${year}`;
  };

  const getDescription = (category) => {
    if (!category) return 'Phân loại danh mục';
    if (category.description) return category.description;
    if (category.name) return `Phân loại cho ${category.name.toLowerCase()}`;
    return 'Phân loại danh mục';
  };

  const slugify = (text) => {
    return text
      .toString()
      .toLowerCase()
      .normalize('NFD')
      .replace(/[\u0300-\u036f]/g, '')
      .replace(/[đĐ]/g, 'd')
      .replace(/[^a-z0-9 -]/g, '')
      .replace(/\s+/g, '-')
      .replace(/-+/g, '-')
      .trim();
  };

  const openModal = () => {
    setIsModalOpen(true);
    setCategoryName('');
    setParentId('');
    setDescription('');
    setCategoryStatus('ACTIVE');
    loadParentCategories();
  };

  const handleSubmitNewCategory = async (e) => {
    e.preventDefault();
    if (!categoryName.trim()) {
      toast.error('Vui lòng nhập tên danh mục');
      return;
    }
    try {
      setSubmitting(true);
      const payload = {
        name: categoryName,
        slug: slugify(categoryName),
        parentId: parentId ? parentId : null,
        status: categoryStatus,
        sortOrder: 0
      };
      await categoryApi.create(payload);
      toast.success('Thêm danh mục thành công');
      setIsModalOpen(false);
      fetchDashboardData();
    } catch (err) {
      console.error('Error creating category:', err);
      toast.error(err?.response?.data?.message || 'Có lỗi xảy ra khi tạo danh mục');
    } finally {
      setSubmitting(false);
    }
  };

  // Toggle Action Menu
  const handleToggleMenu = (e, id) => {
    e.stopPropagation();
    setActiveMenuId(activeMenuId === id ? null : id);
  };

  // Action Menu Handlers
  const startInlineEdit = (category) => {
    setEditingId(category.id);
    setEditName(category.name);
    setEditStatus(category.status);
    // Find parent UUID by searching from options or checking if category already has a parent id
    // We fetch parent categories list so that dropdown options are ready
    loadParentCategories();

    // We need to set editParentId. Since backend response list categories doesn't explicitly store parentId,
    // let's fetch categories or match category name with parentOptions. Or wait!
    // In categoryResponse, we have parentCategoryName. But in CategoryResponseDTO, it returns parentCategoryName
    // Let's find the parent category id by matching parentCategoryName in parentCategories list!
    // But since parentCategories is fetched asynchronously, let's load first and matching from fetched list.
    categoryApi.getAll().then(list => {
      const parent = list.find(cat => cat.name === category.parentCategoryName);
      setEditParentId(parent ? parent.id : '');
    }).catch(err => {
      console.error(err);
      setEditParentId('');
    });

    setEditDescription(getDescription(category));
    setActiveMenuId(null);
  };

  const cancelInlineEdit = () => {
    setEditingId(null);
  };

  const handleSaveEdit = async (id) => {
    if (!editName.trim()) {
      toast.error('Tên danh mục không được để trống');
      return;
    }
    try {
      setLoading(true);
      const payload = {
        name: editName,
        slug: slugify(editName),
        parentId: editParentId ? editParentId : null,
        status: editStatus,
        sortOrder: 0
      };
      await categoryApi.update(id, payload);
      toast.success('Cập nhật danh mục thành công');
      setEditingId(null);
      fetchDashboardData();
    } catch (err) {
      console.error('Error updating category:', err);
      toast.error(err?.response?.data?.message || 'Có lỗi xảy ra khi cập nhật danh mục');
    } finally {
      setLoading(false);
    }
  };

  const handleToggleStatus = async (category) => {
    const isCurrentlyActive = category.status === 'ACTIVE';
    const newStatus = isCurrentlyActive ? 'INACTIVE' : 'ACTIVE';
    const actionLabel = isCurrentlyActive ? 'ngưng hoạt động' : 'kích hoạt';

    if (window.confirm(`Bạn có chắc chắn muốn ${actionLabel} danh mục "${category.name}"?`)) {
      try {
        setLoading(true);
        const parentList = await categoryApi.getAll();
        const parent = parentList.find(cat => cat.name === category.parentCategoryName);
        const payload = {
          name: category.name,
          slug: category.slug || slugify(category.name),
          parentId: parent ? parent.id : null,
          status: newStatus,
          sortOrder: 0
        };
        await categoryApi.update(category.id, payload);
        toast.success(`Đã ${actionLabel} danh mục thành công`);
        setActiveMenuId(null);
        fetchDashboardData();
      } catch (err) {
        console.error('Error toggling category status:', err);
        toast.error(err?.response?.data?.message || 'Có lỗi xảy ra khi đổi trạng thái danh mục');
      } finally {
        setLoading(false);
      }
    }
  };

  const handleDelete = async (category) => {
    if (category.productCount > 0) {
      toast.error(`Không thể xóa danh mục "${category.name}" vì đang chứa ${category.productCount} sản phẩm.`);
      setActiveMenuId(null);
      return;
    }

    if (window.confirm(`Bạn có chắc chắn muốn XÓA VĨNH VIỄN danh mục "${category.name}" khỏi cơ sở dữ liệu?`)) {
      try {
        setLoading(true);
        await categoryApi.delete(category.id);
        toast.success('Đã xóa vĩnh viễn danh mục thành công');
        setActiveMenuId(null);
        fetchDashboardData();
      } catch (err) {
        console.error('Error deleting category:', err);
        toast.error(err?.response?.data?.message || 'Có lỗi xảy ra khi xóa danh mục');
      } finally {
        setLoading(false);
      }
    }
  };

  // Expanded Nodes State
  const [expandedIds, setExpandedIds] = useState(new Set());

  // Build tree hierarchy from data.categories with cycle protection & cumulative product counting
  const categoryTree = useMemo(() => {
    const categoriesList = data.categories || [];
    if (!Array.isArray(categoriesList) || categoriesList.length === 0) return [];

    const categoryByName = {};
    categoriesList.forEach((cat) => {
      if (cat && cat.name) {
        categoryByName[cat.name] = {
          ...cat,
          directProductCount: Number(cat.productCount || 0),
          children: []
        };
      }
    });

    const roots = [];
    categoriesList.forEach((cat) => {
      if (!cat || !cat.name) return;
      const node = categoryByName[cat.name];
      if (!node) return;

      const isRoot = !cat.parentCategoryName || cat.parentCategoryName === 'Danh mục gốc';
      if (isRoot) {
        roots.push(node);
      } else {
        const parent = categoryByName[cat.parentCategoryName];
        if (parent && Array.isArray(parent.children) && parent !== node) {
          if (!parent.children.some((c) => c.id === node.id)) {
            parent.children.push(node);
          }
        } else {
          roots.push(node);
        }
      }
    });

    // Compute cumulative total product count (direct products + all subcategories' products)
    const computeCumulativeProducts = (node, visited = new Set()) => {
      if (!node || !node.id || visited.has(node.id)) return 0;
      visited.add(node.id);

      let sum = Number(node.directProductCount || 0);
      if (Array.isArray(node.children)) {
        node.children.forEach((child) => {
          sum += computeCumulativeProducts(child, visited);
        });
      }
      node.productCount = sum;
      return sum;
    };

    roots.forEach((root) => computeCumulativeProducts(root));

    return roots;
  }, [data.categories]);

  // Check if filtering/search is active
  const isFiltering = Boolean(searchInput.trim() || statusFilter);

  // Filter tree nodes if search or status filter is active with cycle protection
  const filteredTree = useMemo(() => {
    if (!isFiltering) return categoryTree;

    const query = searchInput.toLowerCase().trim();

    const filterNodes = (nodes, visited = new Set()) => {
      if (!Array.isArray(nodes)) return [];
      return nodes
        .map((node) => {
          if (!node || !node.id || visited.has(node.id)) return null;

          const currentVisited = new Set(visited);
          currentVisited.add(node.id);

          const nodeName = node.name ? node.name.toLowerCase() : '';
          const nodeSlug = node.slug ? node.slug.toLowerCase() : '';
          const nodeDesc = getDescription(node).toLowerCase();

          const matchesSearch =
            !query ||
            nodeName.includes(query) ||
            nodeSlug.includes(query) ||
            nodeDesc.includes(query);
          const matchesStatus = !statusFilter || node.status === statusFilter;

          const matchingChildren = filterNodes(node.children || [], currentVisited);

          if ((matchesSearch && matchesStatus) || matchingChildren.length > 0) {
            return {
              ...node,
              children: matchingChildren
            };
          }
          return null;
        })
        .filter(Boolean);
    };

    return filterNodes(categoryTree);
  }, [categoryTree, searchInput, statusFilter, isFiltering]);

  // Flatten visible tree rows based on expandedIds with cycle protection
  const visibleRows = useMemo(() => {
    const flatten = (nodes, level = 0, visited = new Set()) => {
      if (!Array.isArray(nodes)) return [];
      let result = [];
      nodes.forEach((node) => {
        if (!node || !node.id || visited.has(node.id)) return;

        const currentVisited = new Set(visited);
        currentVisited.add(node.id);

        const hasChildren = Array.isArray(node.children) && node.children.length > 0;
        const isExpanded = isFiltering || expandedIds.has(node.id);

        result.push({
          ...node,
          level,
          hasChildren,
          isExpanded
        });

        if (hasChildren && isExpanded) {
          result = result.concat(flatten(node.children, level + 1, currentVisited));
        }
      });
      return result;
    };

    try {
      return flatten(filteredTree);
    } catch (e) {
      console.error('Error flattening category tree:', e);
      return data.categories || [];
    }
  }, [filteredTree, expandedIds, isFiltering, data.categories]);

  // Expand / Collapse Handlers
  const toggleExpand = (id, e) => {
    if (e) e.stopPropagation();
    setExpandedIds((prev) => {
      const next = new Set(prev);
      if (next.has(id)) {
        next.delete(id);
      } else {
        next.add(id);
      }
      return next;
    });
  };

  const handleExpandAll = () => {
    const allParentIds = new Set();
    const collectParentIds = (nodes) => {
      nodes.forEach((n) => {
        if (n.children && n.children.length > 0) {
          allParentIds.add(n.id);
          collectParentIds(n.children);
        }
      });
    };
    collectParentIds(categoryTree);
    setExpandedIds(allParentIds);
  };

  const handleCollapseAll = () => {
    setExpandedIds(new Set());
  };

  const actions = (
    <button className={styles.primaryBtn} onClick={openModal}>
      <Plus size={18} />
      Thêm danh mục
    </button>
  );

  return (
    <div className={`${styles.page} product-workspace`}>
      <div className={styles.pageHeader}>
        <PageHeader
          title="Danh mục sản phẩm"
          subtitle="Phân loại và tổ chức danh mục sản phẩm"
          icon={() => <Tag size={20} />}
          actions={actions}
        />
      </div>

      {/* Summary Cards */}
      <div className={styles.statsGrid}>
        <div className={styles.statCard}>
          <div className={styles.statInfo}>
            <span className={styles.statLabel}>Tổng danh mục</span>
            <span className={styles.statValue}>{data.totalCategories}</span>
          </div>
          <div className={`${styles.statIconWrapper} ${styles.themeBlue}`}>
            <Tag size={20} />
          </div>
        </div>

        <div className={styles.statCard}>
          <div className={styles.statInfo}>
            <span className={styles.statLabel}>Hoạt động</span>
            <span className={styles.statValue}>{data.totalActiveCategories}</span>
          </div>
          <div className={`${styles.statIconWrapper} ${styles.themeIndigo}`}>
            <CheckCircle size={20} />
          </div>
        </div>

        <div className={styles.statCard}>
          <div className={styles.statInfo}>
            <span className={styles.statLabel}>Danh mục gốc</span>
            <span className={styles.statValue}>{data.totalParentCategories}</span>
          </div>
          <div className={`${styles.statIconWrapper} ${styles.themeAmber}`}>
            <Folder size={20} />
          </div>
        </div>

        <div className={styles.statCard}>
          <div className={styles.statInfo}>
            <span className={styles.statLabel}>Tổng sản phẩm</span>
            <span className={styles.statValue}>{data.totalProducts}</span>
          </div>
          <div className={`${styles.statIconWrapper} ${styles.themeEmerald}`}>
            <Package size={20} />
          </div>
        </div>
      </div>

      {/* Filter and Search Bar */}
      <div className={styles.filterBar}>
        <div className={styles.searchWrapper}>
          <Search size={18} className={styles.searchIcon} />
          <input
            type="text"
            placeholder="Tìm theo tên, mô tả..."
            value={searchInput}
            onChange={(e) => setSearchInput(e.target.value)}
            className={styles.searchInput}
          />
        </div>

        <div className={styles.selectWrapper}>
          <select
            value={statusFilter}
            onChange={(e) => setStatusFilter(e.target.value)}
            className={styles.statusSelect}
          >
            <option value="">Tất cả trạng thái</option>
            <option value="ACTIVE">Hoạt động</option>
            <option value="INACTIVE">Ngưng hoạt động</option>
          </select>
          <ChevronDown size={16} className={styles.selectChevron} />
        </div>

        {/* Tree Control Buttons */}
        <div className={styles.treeControlButtons}>
          <button
            type="button"
            className={styles.treeActionBtn}
            onClick={handleExpandAll}
            title="Mở tất cả danh mục"
          >
            <Maximize2 size={14} />
            Mở tất cả
          </button>
          <button
            type="button"
            className={styles.treeActionBtn}
            onClick={handleCollapseAll}
            title="Thu gọn tất cả danh mục"
          >
            <Minimize2 size={14} />
            Thu gọn
          </button>
        </div>
      </div>

      {/* Categories Table Card */}
      <div className={styles.tableCard}>
        {loading && data.categories.length === 0 ? (
          <div className={styles.loadingState}>
            <div className={styles.loadingSpinner}></div>
            <p>Đang tải dữ liệu danh mục...</p>
          </div>
        ) : error ? (
          <div className={styles.emptyState}>
            <AlertTriangle size={40} className={styles.emptyIcon} style={{ color: '#ef4444' }} />
            <h3 className={styles.emptyTitle}>Đã xảy ra lỗi</h3>
            <p className={styles.emptyText}>{error}</p>
          </div>
        ) : visibleRows.length === 0 ? (
          <div className={styles.emptyState}>
            <Tag size={40} className={styles.emptyIcon} />
            <h3 className={styles.emptyTitle}>Không tìm thấy danh mục</h3>
            <p className={styles.emptyText}>Thử thay đổi từ khóa tìm kiếm hoặc bộ lọc.</p>
          </div>
        ) : (
          <div className={styles.tableWrapper}>
            <table className={styles.table}>
              <thead>
                <tr>
                  <th className={styles.th}>Tên danh mục</th>
                  <th className={styles.th}>Danh mục cha</th>
                  <th className={styles.th}>Mô tả</th>
                  <th className={styles.th} style={{ textAlign: 'right' }}>Sản phẩm</th>
                  <th className={styles.th}>Trạng thái</th>
                  <th className={styles.th}>Ngày tạo</th>
                  <th className={styles.th} style={{ width: '130px', textAlign: 'center' }}>Thao tác</th>
                </tr>
              </thead>
              <tbody>
                {visibleRows.map((category) => {
                  const isParentRoot = category.parentCategoryName === 'Danh mục gốc';
                  const isEditing = category.id === editingId;

                  return (
                    <tr key={category.id} className={styles.tr}>
                      {/* Name Column with Tree Indent */}
                      <td className={styles.td}>
                        <div
                          className={styles.treeNameCell}
                          style={{ paddingLeft: `${category.level * 24}px` }}
                        >
                          {/* Expand/Collapse Toggle or Branch Guide */}
                          {category.hasChildren ? (
                            <button
                              type="button"
                              className={styles.expandToggleBtn}
                              onClick={(e) => toggleExpand(category.id, e)}
                              title={category.isExpanded ? 'Thu gọn' : 'Mở rộng'}
                            >
                              {category.isExpanded ? <ChevronDown size={15} /> : <ChevronRight size={15} />}
                            </button>
                          ) : category.level > 0 ? (
                            <span className={styles.treeBranchGuide}>
                              <CornerDownRight size={14} />
                            </span>
                          ) : (
                            <span className={styles.expandTogglePlaceholder} />
                          )}

                          {/* Category Folder / Tag Icon */}
                          {category.hasChildren ? (
                            category.isExpanded ? (
                              <FolderOpen size={17} className={styles.folderIconActive} />
                            ) : (
                              <Folder size={17} className={styles.folderIcon} />
                            )
                          ) : (
                            <Tag size={15} className={styles.itemTagIcon} />
                          )}

                          {/* Category Name or Inline Edit Input */}
                          {isEditing ? (
                            <input
                              type="text"
                              value={editName}
                              onChange={(e) => setEditName(e.target.value)}
                              className={styles.inlineEditInput}
                            />
                          ) : (
                            <span className={styles.categoryNameText} title={category.name}>
                              {category.name}
                            </span>
                          )}

                          {/* Subcategory Count Badge */}
                          {category.hasChildren && !isEditing && (
                            <span className={styles.subCountBadge}>
                              {Array.isArray(category.children) ? category.children.length : 0} danh mục con
                            </span>
                          )}
                        </div>
                      </td>

                      {/* Parent Category Column */}
                      <td className={styles.td}>
                        {isEditing ? (
                          <div className={styles.selectWrapper} style={{ minWidth: '160px' }}>
                            <select
                              value={editParentId}
                              onChange={(e) => setEditParentId(e.target.value)}
                              className={styles.inlineEditSelect}
                            >
                              <option value="">— Không có (danh mục gốc) —</option>
                              {parentCategories
                                .filter(cat => cat.id !== category.id) // Prevent self-parenting
                                .map(cat => (
                                  <option key={cat.id} value={cat.id}>
                                    {cat.name}
                                  </option>
                                ))}
                            </select>
                            <ChevronDown size={14} className={styles.selectChevron} />
                          </div>
                        ) : (
                          <span className={isParentRoot ? styles.parentMuted : styles.parentActive} title={category.parentCategoryName}>
                            {category.parentCategoryName}
                          </span>
                        )}
                      </td>

                      {/* Description Column */}
                      <td className={styles.td}>
                        {isEditing ? (
                          <input
                            type="text"
                            value={editDescription}
                            onChange={(e) => setEditDescription(e.target.value)}
                            className={styles.inlineEditInput}
                          />
                        ) : (
                          <span className={styles.descriptionText} title={getDescription(category)}>{getDescription(category)}</span>
                        )}
                      </td>

                      {/* Product Count Column */}
                      <td className={styles.td} style={{ textAlign: 'right' }}>
                        <span className={styles.productCount}>{category.productCount}</span>
                      </td>

                      {/* Status Column */}
                      <td className={styles.td}>
                        {isEditing ? (
                          <div className={styles.selectWrapper} style={{ minWidth: '150px' }}>
                            <select
                              value={editStatus}
                              onChange={(e) => setEditStatus(e.target.value)}
                              className={styles.inlineEditSelect}
                            >
                              <option value="ACTIVE">Hoạt động</option>
                              <option value="INACTIVE">Ngưng hoạt động</option>
                            </select>
                            <ChevronDown size={14} className={styles.selectChevron} />
                          </div>
                        ) : (
                          <span
                            className={`${styles.statusBadge} ${category.status === 'ACTIVE'
                                ? styles.statusActive
                                : styles.statusInactive
                              }`}
                          >
                            {category.status === 'ACTIVE' ? (
                              <>
                                <CheckCircle size={13} />
                                Hoạt động
                              </>
                            ) : (
                              <>
                                <XCircle size={13} />
                                Ngưng hoạt động
                              </>
                            )}
                          </span>
                        )}
                      </td>

                      {/* Created At Column */}
                      <td className={styles.td}>
                        {formatDate(category.createdAt)}
                      </td>

                      {/* Actions Column */}
                      <td className={styles.td} style={{ textAlign: 'center' }}>
                        {isEditing ? (
                          <div className={styles.inlineEditButtons}>
                            <button
                              className={styles.saveBtn}
                              onClick={() => handleSaveEdit(category.id)}
                              title="Lưu"
                            >
                              <Check size={14} />
                            </button>
                            <button
                              className={styles.inlineCancelBtn}
                              onClick={cancelInlineEdit}
                              title="Hủy"
                            >
                              <X size={14} />
                            </button>
                          </div>
                        ) : (
                          <div className={styles.directActionButtons}>
                            <button
                              className={styles.iconActionBtn}
                              onClick={() => startInlineEdit(category)}
                              title="Sửa danh mục"
                            >
                              <Edit2 size={15} />
                            </button>
                            <button
                              className={`${styles.iconActionBtn} ${
                                category.status === 'ACTIVE'
                                  ? styles.statusDeactivateBtn
                                  : styles.statusActivateBtn
                              }`}
                              onClick={() => handleToggleStatus(category)}
                              title={category.status === 'ACTIVE' ? 'Ngưng hoạt động' : 'Kích hoạt'}
                            >
                              <Power size={15} />
                            </button>
                            <button
                              className={`${styles.iconActionBtn} ${styles.deleteActionBtn} ${
                                category.productCount > 0 ? styles.iconActionDisabled : ''
                              }`}
                              onClick={() => handleDelete(category)}
                              title={
                                category.productCount > 0
                                  ? `Không thể xóa vì đang chứa ${category.productCount} sản phẩm`
                                  : 'Xóa vĩnh viễn danh mục'
                              }
                            >
                              <Trash2 size={15} />
                            </button>
                          </div>
                        )}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}

        {/* Pagination Footer */}
        {!loading && !error && (
          <Pagination
            currentPage={page}
            totalPages={data.totalPages}
            totalElements={data.totalElements}
            pageSize={size}
            currentCount={visibleRows.length}
            itemLabel="danh mục"
            onPageChange={setPage}
          />
        )}
        {data.totalPages < 0 && (
          <div className={styles.pagination}>
            <span className={styles.paginationInfo}>
              Hiển thị {page * size + 1} - {Math.min((page + 1) * size, data.totalElements)} trong tổng số {data.totalElements} danh mục
            </span>
            <div className={styles.paginationButtons}>
              <button
                className={styles.paginationBtn}
                onClick={() => setPage((prev) => Math.max(0, prev - 1))}
                disabled={page === 0}
              >
                <ChevronLeft size={16} />
                Trước
              </button>
              <button
                className={styles.paginationBtn}
                onClick={() => setPage((prev) => Math.min(data.totalPages - 1, prev + 1))}
                disabled={page === data.totalPages - 1}
              >
                Sau
                <ChevronRight size={16} />
              </button>
            </div>
          </div>
        )}
      </div>

      {/* Add Category Modal */}
      {isModalOpen && (
        <div className={styles.modalOverlay}>
          <div className={styles.modalContent}>
            <div className={styles.modalHeader}>
              <div className={styles.modalTitleWrapper}>
                <Tag size={20} />
                <h3 className={styles.modalTitle}>Thêm danh mục</h3>
              </div>
              <button className={styles.modalCloseBtn} onClick={() => setIsModalOpen(false)}>
                <X size={20} />
              </button>
            </div>
            <form onSubmit={handleSubmitNewCategory}>
              <div className={styles.modalBody}>
                <div className={styles.formGroup}>
                  <label className={styles.formLabel}>
                    Tên danh mục <span className={styles.requiredStar}>*</span>
                  </label>
                  <input
                    type="text"
                    required
                    placeholder="VD: Áo thun, Quần jeans..."
                    value={categoryName}
                    onChange={(e) => setCategoryName(e.target.value)}
                    className={styles.formInput}
                  />
                </div>

                <div className={styles.formGroup}>
                  <label className={styles.formLabel}>Danh mục cha</label>
                  <div className={styles.selectWrapper} style={{ width: '100%' }}>
                    <select
                      value={parentId}
                      onChange={(e) => setParentId(e.target.value)}
                      className={styles.formSelect}
                      style={{ width: '100%' }}
                    >
                      <option value="">— Không có (danh mục gốc) —</option>
                      {parentCategories.map(cat => (
                        <option key={cat.id} value={cat.id}>
                          {cat.name}
                        </option>
                      ))}
                    </select>
                    <ChevronDown size={16} className={styles.selectChevron} />
                  </div>
                </div>

                <div className={styles.formGroup}>
                  <label className={styles.formLabel}>Mô tả</label>
                  <textarea
                    placeholder="Mô tả danh mục sản phẩm..."
                    value={description}
                    onChange={(e) => setDescription(e.target.value)}
                    className={styles.formInput}
                    style={{ minHeight: '80px', resize: 'vertical', fontFamily: 'inherit' }}
                  />
                </div>

                <div className={styles.formGroup}>
                  <label className={styles.formLabel}>Trạng thái</label>
                  <div className={styles.radioGroup}>
                    <div
                      className={`${styles.radioCard} ${categoryStatus === 'ACTIVE' ? styles.radioCardActive : ''}`}
                      onClick={() => setCategoryStatus('ACTIVE')}
                    >
                      <input
                        type="radio"
                        checked={categoryStatus === 'ACTIVE'}
                        onChange={() => setCategoryStatus('ACTIVE')}
                        className={styles.radioInput}
                      />
                      <span>Hoạt động</span>
                    </div>
                    <div
                      className={`${styles.radioCard} ${categoryStatus === 'INACTIVE' ? styles.radioCardActive : ''}`}
                      onClick={() => setCategoryStatus('INACTIVE')}
                    >
                      <input
                        type="radio"
                        checked={categoryStatus === 'INACTIVE'}
                        onChange={() => setCategoryStatus('INACTIVE')}
                        className={styles.radioInput}
                      />
                      <span>Ẩn</span>
                    </div>
                  </div>
                </div>
              </div>
              <div className={styles.modalFooter}>
                <button type="button" className={styles.cancelBtn} onClick={() => setIsModalOpen(false)} disabled={submitting}>
                  Hủy
                </button>
                <button type="submit" className={styles.primaryBtn} disabled={submitting}>
                  {submitting ? 'Đang lưu...' : 'Thêm danh mục'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};

export default CategoryPage;
