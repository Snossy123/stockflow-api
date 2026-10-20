package com.arafawood.api.service;

import com.arafawood.api.common.ApiException;
import com.arafawood.api.domain.Category;
import com.arafawood.api.domain.ExpenseType;
import com.arafawood.api.domain.Item;
import com.arafawood.api.domain.Unit;
import com.arafawood.api.dto.Dtos.ExpenseTypeDto;
import com.arafawood.api.dto.Dtos.IdName;
import com.arafawood.api.dto.Dtos.ItemDto;
import com.arafawood.api.dto.Dtos.ItemWrite;
import com.arafawood.api.repo.CategoryRepository;
import com.arafawood.api.repo.ExpenseRepository;
import com.arafawood.api.repo.ExpenseTypeRepository;
import com.arafawood.api.repo.ItemRepository;
import com.arafawood.api.repo.UnitRepository;
import com.arafawood.api.security.CurrentUserService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CatalogService {

    private final ItemRepository items;
    private final CategoryRepository categories;
    private final UnitRepository units;
    private final ExpenseTypeRepository expenseTypes;
    private final ExpenseRepository expenses;
    private final SettingService settings;
    private final StockService stock;
    private final CurrentUserService currentUser;

    public CatalogService(
            ItemRepository items,
            CategoryRepository categories,
            UnitRepository units,
            ExpenseTypeRepository expenseTypes,
            ExpenseRepository expenses,
            SettingService settings,
            StockService stock,
            CurrentUserService currentUser) {
        this.items = items;
        this.categories = categories;
        this.units = units;
        this.expenseTypes = expenseTypes;
        this.expenses = expenses;
        this.settings = settings;
        this.stock = stock;
        this.currentUser = currentUser;
    }

    @Transactional(readOnly = true)
    public List<ItemDto> items() {
        currentUser.requireUser();
        return items.findAll().stream().map(this::toItem).toList();
    }

    @Transactional
    public ItemDto createItem(ItemWrite write) {
        if (!currentUser.can("settings", "create")
                && !currentUser.can("purchases", "create")
                && !currentUser.can("sales", "create")) {
            throw ApiException.forbidden();
        }
        Item item = new Item();
        apply(item, write);
        item = items.save(item);
        BigDecimal openingQty = write.openingQty();
        if (openingQty != null && openingQty.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal cost = write.openingUnitCost() == null ? BigDecimal.ZERO : write.openingUnitCost();
            stock.moveIn(item, "opening", openingQty, cost, "item", item.getId());
        }
        return toItem(item);
    }

    @Transactional
    public ItemDto updateItem(Long id, ItemWrite write) {
        currentUser.require("settings", "create");
        Item item = items.findById(id).orElseThrow(() -> ApiException.notFound("الصنف غير موجود"));
        apply(item, write);
        return toItem(items.save(item));
    }

    @Transactional(readOnly = true)
    public List<IdName> categories() {
        currentUser.requireUser();
        return categories.findAll().stream().map(c -> new IdName(c.getId(), c.getName())).toList();
    }

    @Transactional
    public IdName createCategory(IdName write) {
        currentUser.require("settings", "create");
        String name = requireName(write.name());
        categories.findByName(name).ifPresent(existing -> {
            throw ApiException.conflict("الاسم مسجل بالفعل", "DUPLICATE_NAME");
        });
        Category category = new Category();
        category.setName(name);
        categories.save(category);
        return new IdName(category.getId(), category.getName());
    }

    @Transactional
    public IdName updateCategory(Long id, IdName write) {
        currentUser.require("settings", "create");
        Category category = categories.findById(id)
                .orElseThrow(() -> ApiException.notFound("التصنيف غير موجود"));
        String name = requireName(write.name());
        categories.findByName(name).ifPresent(existing -> {
            if (!existing.getId().equals(id)) {
                throw ApiException.conflict("الاسم مسجل بالفعل", "DUPLICATE_NAME");
            }
        });
        category.setName(name);
        categories.save(category);
        return new IdName(category.getId(), category.getName());
    }

    @Transactional
    public void deleteCategory(Long id) {
        currentUser.require("settings", "create");
        Category category = categories.findById(id)
                .orElseThrow(() -> ApiException.notFound("التصنيف غير موجود"));
        if (items.existsByCategoryId(id)) {
            throw ApiException.conflict("لا يمكن حذف التصنيف لوجود أصناف مرتبطة به", "CATEGORY_IN_USE");
        }
        categories.delete(category);
    }

    @Transactional(readOnly = true)
    public List<IdName> units() {
        currentUser.requireUser();
        return units.findAll().stream().map(u -> new IdName(u.getId(), u.getName())).toList();
    }

    @Transactional
    public IdName createUnit(IdName write) {
        currentUser.require("settings", "create");
        String name = requireName(write.name());
        units.findByName(name).ifPresent(existing -> {
            throw ApiException.conflict("الاسم مسجل بالفعل", "DUPLICATE_NAME");
        });
        Unit unit = new Unit();
        unit.setName(name);
        units.save(unit);
        return new IdName(unit.getId(), unit.getName());
    }

    @Transactional
    public IdName updateUnit(Long id, IdName write) {
        currentUser.require("settings", "create");
        Unit unit = units.findById(id)
                .orElseThrow(() -> ApiException.notFound("الوحدة غير موجودة"));
        String name = requireName(write.name());
        units.findByName(name).ifPresent(existing -> {
            if (!existing.getId().equals(id)) {
                throw ApiException.conflict("الاسم مسجل بالفعل", "DUPLICATE_NAME");
            }
        });
        unit.setName(name);
        units.save(unit);
        return new IdName(unit.getId(), unit.getName());
    }

    @Transactional
    public void deleteUnit(Long id) {
        currentUser.require("settings", "create");
        Unit unit = units.findById(id)
                .orElseThrow(() -> ApiException.notFound("الوحدة غير موجودة"));
        if (items.existsByUnitId(id)) {
            throw ApiException.conflict("لا يمكن حذف الوحدة لوجود أصناف مرتبطة بها", "UNIT_IN_USE");
        }
        units.delete(unit);
    }

    @Transactional(readOnly = true)
    public List<ExpenseTypeDto> expenseTypes() {
        currentUser.requireUser();
        return expenseTypes.findAll().stream()
                .map(t -> new ExpenseTypeDto(t.getId(), t.getName(), t.isAffectsStock()))
                .toList();
    }

    @Transactional
    public ExpenseTypeDto createExpenseType(ExpenseTypeDto write) {
        currentUser.require("settings", "create");
        String name = requireName(write.name());
        expenseTypes.findByName(name).ifPresent(existing -> {
            throw ApiException.conflict("الاسم مسجل بالفعل", "DUPLICATE_NAME");
        });
        ExpenseType type = new ExpenseType();
        type.setName(name);
        type.setAffectsStock(write.affectsStock());
        expenseTypes.save(type);
        return new ExpenseTypeDto(type.getId(), type.getName(), type.isAffectsStock());
    }

    @Transactional
    public ExpenseTypeDto updateExpenseType(Long id, ExpenseTypeDto write) {
        currentUser.require("settings", "create");
        ExpenseType type = expenseTypes.findById(id)
                .orElseThrow(() -> ApiException.notFound("نوع المصروف غير موجود"));
        String name = requireName(write.name());
        expenseTypes.findByName(name).ifPresent(existing -> {
            if (!existing.getId().equals(id)) {
                throw ApiException.conflict("الاسم مسجل بالفعل", "DUPLICATE_NAME");
            }
        });
        type.setName(name);
        type.setAffectsStock(write.affectsStock());
        expenseTypes.save(type);
        return new ExpenseTypeDto(type.getId(), type.getName(), type.isAffectsStock());
    }

    @Transactional
    public void deleteExpenseType(Long id) {
        currentUser.require("settings", "create");
        ExpenseType type = expenseTypes.findById(id)
                .orElseThrow(() -> ApiException.notFound("نوع المصروف غير موجود"));
        if (expenses.existsByTypeId(id)) {
            throw ApiException.conflict("لا يمكن حذف نوع المصروف لوجود مصروفات مسجلة به", "EXPENSE_TYPE_IN_USE");
        }
        expenseTypes.delete(type);
    }

    private String requireName(String name) {
        if (name == null || name.isBlank()) {
            throw ApiException.badRequest("الاسم مطلوب");
        }
        return name.trim();
    }

    @Transactional(readOnly = true)
    public Map<String, String> settings() {
        currentUser.require("settings", "view");
        return settings.all();
    }

    @Transactional
    public Map<String, String> saveSettings(Map<String, String> body) {
        currentUser.require("settings", "create");
        body.forEach(settings::put);
        return settings.all();
    }

    private void apply(Item item, ItemWrite write) {
        Category category = categories.findById(write.categoryId()).orElseThrow(() -> ApiException.badRequest("التصنيف غير موجود"));
        Unit unit = units.findById(write.unitId()).orElseThrow(() -> ApiException.badRequest("الوحدة غير موجودة"));
        item.setCode(write.code());
        item.setName(write.name());
        item.setCategory(category);
        item.setLength(write.length());
        item.setWidth(write.width());
        item.setThickness(write.thickness());
        item.setUnit(unit);
        item.setMinQty(write.minQty() == null ? new BigDecimal(settings.get("default_min_qty", "10")) : write.minQty());
    }

    private ItemDto toItem(Item item) {
        BigDecimal qty = stock.onHand(item.getId());
        BigDecimal avg = stock.averageCost(item.getId());
        return new ItemDto(
                item.getId(),
                item.getCode(),
                item.getName(),
                item.getCategory().getId(),
                item.getCategory().getName(),
                item.getLength(),
                item.getWidth(),
                item.getThickness(),
                item.getUnit().getId(),
                item.getUnit().getName(),
                item.getMinQty(),
                qty,
                avg,
                qty.multiply(avg));
    }
}
