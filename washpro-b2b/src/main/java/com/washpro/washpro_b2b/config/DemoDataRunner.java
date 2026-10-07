package com.washpro.washpro_b2b.config;

import com.washpro.washpro_b2b.entity.CustomerPrice;
import com.washpro.washpro_b2b.entity.CustomerType;
import com.washpro.washpro_b2b.entity.ItemCategory;
import com.washpro.washpro_b2b.entity.Order;
import com.washpro.washpro_b2b.entity.OrderHistory;
import com.washpro.washpro_b2b.entity.OrderItem;
import com.washpro.washpro_b2b.entity.OrderStatus;
import com.washpro.washpro_b2b.entity.Payment;
import com.washpro.washpro_b2b.entity.PickupRequest;
import com.washpro.washpro_b2b.entity.PickupStatus;
import com.washpro.washpro_b2b.entity.PriceUnit;
import com.washpro.washpro_b2b.entity.Restaurant;
import com.washpro.washpro_b2b.entity.Role;
import com.washpro.washpro_b2b.entity.ServicePrice;
import com.washpro.washpro_b2b.entity.ServiceType;
import com.washpro.washpro_b2b.entity.TimeSlot;
import com.washpro.washpro_b2b.entity.User;
import com.washpro.washpro_b2b.repository.CustomerPriceRepository;
import com.washpro.washpro_b2b.repository.OrderRepository;
import com.washpro.washpro_b2b.repository.PaymentRepository;
import com.washpro.washpro_b2b.repository.PickupRequestRepository;
import com.washpro.washpro_b2b.repository.RestaurantRepository;
import com.washpro.washpro_b2b.repository.ServicePriceRepository;
import com.washpro.washpro_b2b.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

/**
 * Sinh dữ liệu DEMO để xem trực quan: bảng giá, 8 khách hàng nhiều loại, tài xế, kế toán, tài khoản phía khách,
 * khoảng 6 tháng đơn hàng ở mọi trạng thái, thanh toán (có khách trả chậm để thấy tuổi nợ) và yêu cầu lấy đồ.
 *
 * Chỉ chạy khi đặt DEMO_DATA=true. Chạy lại không tạo trùng (đã có khách demo thì bỏ qua).
 * Xóa toàn bộ dữ liệu demo: docs/xoa-du-lieu-demo.sql. Mật khẩu mọi tài khoản demo: Demo@1234
 */
@Component
@org.springframework.core.annotation.Order(10) // sau LegacyDataRunner (gỡ ràng buộc trạng thái cũ)
public class DemoDataRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataRunner.class);
    static final String PASSWORD = "Demo@1234";
    private static final String MARKER_CUSTOMER = "Khách sạn Hoa Sen";

    private final ServicePriceRepository priceRepository;
    private final CustomerPriceRepository customerPriceRepository;
    private final RestaurantRepository restaurantRepository;
    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final PickupRequestRepository pickupRepository;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbc;

    /** false (mặc định, không làm gì) | true (sinh nếu chưa có) | reset (xóa dữ liệu demo cũ rồi sinh lại). */
    private final String mode;

    /** Cố định hạt giống để mỗi lần sinh ra cùng một bộ dữ liệu. */
    private final Random rnd = new Random(20261006L);

    /** Tên đăng nhập Quản lý xưởng thật (ghi vào nhật ký đơn). */
    private String adminUser = "admin";
    /** Khách demo → tài khoản quản lý phía khách; đơn demo đang xử lý (để gắn yêu cầu lấy đồ). */
    private final Map<Long, String> managerOf = new HashMap<>();
    private final List<Order> activeOrders = new ArrayList<>();

    public DemoDataRunner(ServicePriceRepository priceRepository, CustomerPriceRepository customerPriceRepository,
            RestaurantRepository restaurantRepository, UserRepository userRepository, OrderRepository orderRepository,
            PaymentRepository paymentRepository, PickupRequestRepository pickupRepository,
            PasswordEncoder passwordEncoder, JdbcTemplate jdbc,
            @Value("${app.demo-data.enabled:false}") String mode) {
        this.mode = mode == null ? "false" : mode.trim();
        this.priceRepository = priceRepository;
        this.customerPriceRepository = customerPriceRepository;
        this.restaurantRepository = restaurantRepository;
        this.userRepository = userRepository;
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.pickupRepository = pickupRepository;
        this.passwordEncoder = passwordEncoder;
        this.jdbc = jdbc;
    }

    /** Khách demo: loại khách, thói quen trả tiền và những món hay gửi. */
    private record Profile(String name, CustomerType type, String address, String phone, String contact,
            int turnaround, int term, int ordersPerWeek, PayHabit habit, String managerUser, String staffUser,
            List<String> items) {
    }

    /** Trả đúng hạn / trả chậm ~1 tháng / nợ dây dưa (để thấy đủ các mức tuổi nợ). */
    private enum PayHabit { ON_TIME, LATE, BAD }

    @Override
    public void run(ApplicationArguments args) {
        if (!"true".equalsIgnoreCase(mode) && !"reset".equalsIgnoreCase(mode)) {
            return;
        }
        if ("reset".equalsIgnoreCase(mode)) {
            deleteDemoData();
        } else if (restaurantRepository.findByNameIgnoreCase(MARKER_CUSTOMER).isPresent()) {
            log.info("Dữ liệu demo đã có sẵn, bỏ qua. Đặt DEMO_DATA=reset để xóa và sinh lại.");
            return;
        }
        log.info("Đang sinh dữ liệu demo...");
        userRepository.findAllByOrderByUsernameAsc().stream()
                .filter(u -> u.getRole() == Role.WORKSHOP_ADMIN)
                .findFirst()
                .ifPresent(u -> adminUser = u.getUsername());
        Map<String, ServicePrice> catalog = seedCatalog();
        List<User> drivers = seedStaff();
        List<Profile> profiles = profiles();

        LocalDateTime now = LocalDateTime.now().withSecond(0).withNano(0);
        int orderCount = 0;
        int paymentCount = 0;
        for (Profile p : profiles) {
            Restaurant r = seedCustomer(p, catalog);
            List<Order> completed = new ArrayList<>();
            orderCount += seedOrders(p, r, catalog, drivers, now, completed);
            paymentCount += seedPayments(p, r, completed, now.toLocalDate());
        }
        int pickups = seedPickups(drivers, now);
        log.info("Đã sinh dữ liệu demo: {} khách hàng, {} đơn, {} khoản thanh toán, {} yêu cầu lấy đồ. Mật khẩu: {}",
                profiles.size(), orderCount, paymentCount, pickups, PASSWORD);
    }

    /** Xóa dữ liệu demo cũ (khách demo và mọi thứ gắn với họ, tài khoản demo). Không đụng dữ liệu thật. */
    private void deleteDemoData() {
        List<String> names = profiles().stream().map(Profile::name).toList();
        String placeholders = String.join(",", names.stream().map(n -> "?").toList());
        List<Long> customers = jdbc.queryForList("SELECT id FROM restaurants WHERE name IN (" + placeholders + ")",
                Long.class, names.toArray());
        if (!customers.isEmpty()) {
            String ids = String.join(",", customers.stream().map(String::valueOf).toList());
            String orders = "SELECT id FROM orders WHERE restaurant_id IN (" + ids + ")";
            jdbc.update("DELETE FROM pickup_requests WHERE restaurant_id IN (" + ids + ")");
            jdbc.update("DELETE FROM order_history WHERE order_id IN (" + orders + ")");
            jdbc.update("DELETE FROM order_items WHERE order_id IN (" + orders + ")");
            jdbc.update("DELETE FROM orders WHERE restaurant_id IN (" + ids + ")");
            jdbc.update("DELETE FROM payments WHERE restaurant_id IN (" + ids + ")");
            jdbc.update("DELETE FROM customer_prices WHERE restaurant_id IN (" + ids + ")");
            jdbc.update("DELETE FROM users WHERE restaurant_id IN (" + ids + ")");
            jdbc.update("DELETE FROM restaurants WHERE id IN (" + ids + ")");
        }
        jdbc.update("DELETE FROM users WHERE username IN ('taixe.tai', 'taixe.hung', 'taixe.phuc', 'ketoan.mai')");
        log.info("Đã xóa dữ liệu demo cũ ({} khách hàng)", customers.size());
    }

    // ===================== Bảng giá =====================

    private Map<String, ServicePrice> seedCatalog() {
        Object[][] rows = {
                { "Khăn trải bàn", ItemCategory.TABLE_LINEN, ServiceType.WASH_IRON, PriceUnit.PIECE, 12000 },
                { "Khăn ăn", ItemCategory.TABLE_LINEN, ServiceType.WASH_IRON, PriceUnit.PIECE, 3000 },
                { "Tạp dề", ItemCategory.TABLE_LINEN, ServiceType.WASH, PriceUnit.PIECE, 5000 },
                { "Khăn lau bếp", ItemCategory.TABLE_LINEN, ServiceType.WASH, PriceUnit.PIECE, 2000 },
                { "Ga giường đơn", ItemCategory.BEDDING, ServiceType.WASH_IRON, PriceUnit.PIECE, 15000 },
                { "Ga giường đôi", ItemCategory.BEDDING, ServiceType.WASH_IRON, PriceUnit.PIECE, 20000 },
                { "Vỏ chăn", ItemCategory.BEDDING, ServiceType.WASH_IRON, PriceUnit.PIECE, 25000 },
                { "Vỏ gối", ItemCategory.BEDDING, ServiceType.WASH_IRON, PriceUnit.PIECE, 5000 },
                { "Ruột chăn", ItemCategory.BEDDING, ServiceType.WASH, PriceUnit.PIECE, 60000 },
                { "Chăn ga gối (tính cân)", ItemCategory.BEDDING, ServiceType.WASH, PriceUnit.KG, 22000 },
                { "Khăn tắm", ItemCategory.TOWEL, ServiceType.WASH, PriceUnit.PIECE, 6000 },
                { "Khăn mặt", ItemCategory.TOWEL, ServiceType.WASH, PriceUnit.PIECE, 2500 },
                { "Áo choàng tắm", ItemCategory.TOWEL, ServiceType.WASH, PriceUnit.PIECE, 20000 },
                { "Thảm chân", ItemCategory.TOWEL, ServiceType.WASH, PriceUnit.PIECE, 8000 },
                { "Áo đồng phục", ItemCategory.UNIFORM, ServiceType.WASH_IRON, PriceUnit.PIECE, 12000 },
                { "Quần đồng phục", ItemCategory.UNIFORM, ServiceType.WASH_IRON, PriceUnit.PIECE, 12000 },
                { "Bộ đồng phục", ItemCategory.UNIFORM, ServiceType.WASH_IRON, PriceUnit.SET, 22000 },
                { "Áo đầu bếp", ItemCategory.UNIFORM, ServiceType.STAIN_REMOVAL, PriceUnit.PIECE, 18000 },
                { "Áo blouse y tế", ItemCategory.UNIFORM, ServiceType.WASH_IRON, PriceUnit.PIECE, 15000 },
                { "Áo vest lễ tân", ItemCategory.UNIFORM, ServiceType.DRY_CLEAN, PriceUnit.PIECE, 45000 },
                { "Đồng phục (tính cân)", ItemCategory.UNIFORM, ServiceType.WASH, PriceUnit.KG, 25000 },
        };
        Map<String, ServicePrice> map = new HashMap<>();
        for (Object[] row : rows) {
            String name = (String) row[0];
            ServiceType service = (ServiceType) row[2];
            ServicePrice price = priceRepository.findByNameIgnoreCaseAndServiceType(name, service)
                    .orElseGet(() -> priceRepository.save(ServicePrice.builder()
                            .name(name)
                            .category((ItemCategory) row[1])
                            .serviceType(service)
                            .unit((PriceUnit) row[3])
                            .unitPrice(money((Integer) row[4]))
                            .build()));
            map.put(name, price);
        }
        return map;
    }

    // ===================== Nhân sự xưởng =====================

    private List<User> seedStaff() {
        List<User> drivers = new ArrayList<>();
        drivers.add(user("taixe.tai", "Trần Văn Tài", "0905111222", Role.DRIVER, null));
        drivers.add(user("taixe.hung", "Lê Minh Hùng", "0905333444", Role.DRIVER, null));
        drivers.add(user("taixe.phuc", "Phạm Hồng Phúc", "0905555666", Role.DRIVER, null));
        user("ketoan.mai", "Đỗ Thị Mai", "0905777888", Role.WORKSHOP_ACCOUNTANT, null);
        return drivers;
    }

    private User user(String username, String fullName, String phone, Role role, Restaurant restaurant) {
        return userRepository.findByUsername(username).orElseGet(() -> userRepository.save(User.builder()
                .username(username)
                .password(passwordEncoder.encode(PASSWORD))
                .fullName(fullName)
                .phone(phone)
                .role(role)
                .restaurant(restaurant)
                .build()));
    }

    // ===================== Khách hàng =====================

    private List<Profile> profiles() {
        return List.of(
                new Profile(MARKER_CUSTOMER, CustomerType.HOTEL, "25 Nguyễn Huệ, Q.1, TP.HCM", "0283822111",
                        "Chị Lan - buồng phòng", 24, 15, 3, PayHabit.ON_TIME, "hoasen.ql", "hoasen.nv",
                        List.of("Ga giường đôi", "Ga giường đơn", "Vỏ gối", "Vỏ chăn", "Khăn tắm", "Khăn mặt")),
                new Profile("Khách sạn Biển Ngọc", CustomerType.HOTEL, "102 Trần Phú, Nha Trang", "0258352999",
                        "Anh Quân - giám sát buồng", 24, 30, 2, PayHabit.LATE, "bienngoc.ql", null,
                        List.of("Chăn ga gối (tính cân)", "Khăn tắm", "Áo choàng tắm", "Thảm chân")),
                new Profile("Nhà hàng Biển Xanh", CustomerType.RESTAURANT, "88 Võ Văn Kiệt, Q.5, TP.HCM", "0909888777",
                        "Anh Minh - bếp trưởng", 48, 0, 2, PayHabit.ON_TIME, "bienxanh.ql", "bienxanh.nv",
                        List.of("Khăn trải bàn", "Khăn ăn", "Áo đầu bếp", "Tạp dề")),
                new Profile("Nhà hàng Phố Cổ", CustomerType.RESTAURANT, "15 Hàng Bè, Hoàn Kiếm, Hà Nội", "0243826555",
                        "Chị Hà - quản lý sảnh", 48, 7, 1, PayHabit.ON_TIME, "phoco.ql", null,
                        List.of("Khăn trải bàn", "Khăn ăn", "Áo đồng phục", "Khăn lau bếp")),
                new Profile("Spa Ngọc Lan", CustomerType.SPA, "7 Pasteur, Q.3, TP.HCM", "0912345678",
                        "Chị Ngọc - chủ spa", 48, 7, 2, PayHabit.LATE, "ngoclan.ql", null,
                        List.of("Khăn tắm", "Khăn mặt", "Áo choàng tắm", "Bộ đồng phục")),
                new Profile("Công ty May Sao Mai", CustomerType.COMPANY, "KCN Tân Bình, TP.HCM", "0283815777",
                        "Anh Dũng - hành chính", 72, 30, 1, PayHabit.BAD, "saomai.ql", null,
                        List.of("Đồng phục (tính cân)", "Bộ đồng phục")),
                new Profile("Phòng khám Đa khoa An Tâm", CustomerType.HOSPITAL, "210 Lý Thường Kiệt, Q.10, TP.HCM",
                        "0283866123", "Chị Thảo - điều dưỡng trưởng", 24, 15, 2, PayHabit.ON_TIME, "antam.ql", null,
                        List.of("Áo blouse y tế", "Ga giường đơn", "Vỏ gối")),
                new Profile("Homestay Đồi Thông", CustomerType.HOTEL, "5 Đường Hoa Hồng, Đà Lạt", "0263382456",
                        "Anh Khoa - chủ homestay", 48, 0, 1, PayHabit.ON_TIME, "doithong.ql", null,
                        List.of("Ga giường đôi", "Vỏ chăn", "Vỏ gối", "Khăn tắm", "Áo vest lễ tân")));
    }

    private Restaurant seedCustomer(Profile p, Map<String, ServicePrice> catalog) {
        Restaurant r = restaurantRepository.save(Restaurant.builder()
                .name(p.name())
                .customerType(p.type())
                .address(p.address())
                .phone(p.phone())
                .contactName(p.contact())
                .turnaroundHours(p.turnaround())
                .paymentTermDays(p.term())
                .build());
        user(p.managerUser(), p.contact().split(" - ")[0], p.phone(), Role.RESTAURANT_MANAGER, r);
        managerOf.put(r.getId(), p.managerUser());
        if (p.staffUser() != null) {
            user(p.staffUser(), "Nhân viên " + p.name(), null, Role.RESTAURANT_STAFF, r);
        }
        // Khách lớn có giá hợp đồng riêng (giảm ~10%) cho món gửi nhiều nhất
        if (p.ordersPerWeek() >= 2 && p.type() != CustomerType.RESTAURANT) {
            ServicePrice main = catalog.get(p.items().get(0));
            customerPriceRepository.save(CustomerPrice.builder()
                    .restaurant(r)
                    .servicePrice(main)
                    .unitPrice(main.getUnitPrice().multiply(BigDecimal.valueOf(0.9)).setScale(-2, RoundingMode.HALF_UP)
                            .setScale(2, RoundingMode.HALF_UP))
                    .updatedBy("ketoan.mai")
                    .build());
        }
        return r;
    }

    // ===================== Đơn hàng =====================

    /** Đơn 6 tháng gần nhất: đơn cũ đã hoàn thành, vài ngày gần đây đủ mọi trạng thái (có cả đơn trễ hạn). */
    private int seedOrders(Profile p, Restaurant r, Map<String, ServicePrice> catalog, List<User> drivers,
            LocalDateTime now, List<Order> completed) {
        Map<Long, BigDecimal> custom = new HashMap<>();
        customerPriceRepository.findByRestaurantId(r.getId())
                .forEach(c -> custom.put(c.getServicePrice().getId(), c.getUnitPrice()));

        int count = 0;
        double gapDays = 7.0 / p.ordersPerWeek();
        // Đơn đã hoàn thành: từ 180 ngày trước đến 4 ngày trước
        for (double ago = 180; ago > 4; ago -= gapDays * (0.7 + rnd.nextDouble() * 0.6)) {
            LocalDateTime pickup = now.minusMinutes((long) (ago * 24 * 60)).withHour(7 + rnd.nextInt(4)).withMinute(rnd.nextInt(4) * 15);
            completed.add(createOrder(p, r, catalog, custom, drivers, pickup, OrderStatus.COMPLETED, false));
            count++;
        }
        // Đơn đang xử lý: mỗi khách vài đơn ở các trạng thái khác nhau
        OrderStatus[] active = switch (p.habit()) {
            case ON_TIME -> new OrderStatus[] { OrderStatus.WASHING, OrderStatus.DELIVERING, OrderStatus.PENDING_CONFIRM };
            case LATE -> new OrderStatus[] { OrderStatus.WASHING, OrderStatus.DISCREPANCY };
            case BAD -> new OrderStatus[] { OrderStatus.DELIVERY_ISSUE, OrderStatus.WASHING };
        };
        for (int i = 0; i < active.length; i++) {
            OrderStatus s = active[i];
            boolean late = s == OrderStatus.WASHING && i == 0 && p.turnaround() <= 24; // khách sạn 24h: 1 đơn trễ hạn
            double hoursAgo = switch (s) {
                case PENDING_CONFIRM, DISCREPANCY -> 1 + rnd.nextInt(5);
                case WASHING -> late ? p.turnaround() + 6 : 6 + rnd.nextInt(10);
                case DELIVERING -> p.turnaround() - 4;
                default -> p.turnaround() + 10;
            };
            activeOrders.add(createOrder(p, r, catalog, custom, drivers, now.minusMinutes((long) (hoursAgo * 60)), s, late));
            count++;
        }
        return count;
    }

    private Order createOrder(Profile p, Restaurant r, Map<String, ServicePrice> catalog, Map<Long, BigDecimal> custom,
            List<User> drivers, LocalDateTime pickup, OrderStatus target, boolean late) {
        User driver = drivers.get(rnd.nextInt(drivers.size()));
        String admin = adminUser;
        String manager = p.managerUser();

        Order order = Order.builder()
                .driver(driver)
                .restaurant(r)
                .status(target)
                .totalBags(2 + rnd.nextInt(7))
                .dueAt(pickup.plusHours(p.turnaround()))
                .build();

        // 2–4 món trong danh sách món hay gửi của khách
        List<String> pool = new ArrayList<>(p.items());
        int lines = Math.min(pool.size(), 2 + rnd.nextInt(3));
        List<String> summary = new ArrayList<>();
        for (int i = 0; i < lines; i++) {
            ServicePrice price = catalog.get(pool.remove(rnd.nextInt(pool.size())));
            BigDecimal qty = price.getUnit() == PriceUnit.KG
                    ? BigDecimal.valueOf(8 + rnd.nextInt(40) + rnd.nextInt(10) / 10.0).setScale(2, RoundingMode.HALF_UP)
                    : BigDecimal.valueOf(price.getUnitPrice().compareTo(BigDecimal.valueOf(20000)) >= 0
                            ? 5 + rnd.nextInt(30) : 20 + rnd.nextInt(120)).setScale(2, RoundingMode.HALF_UP);
            order.addItem(OrderItem.builder()
                    .servicePrice(price)
                    .itemName(price.getName())
                    .category(price.getCategory())
                    .serviceType(price.getServiceType())
                    .unit(price.getUnit())
                    .quantity(qty)
                    .unitPrice(custom.getOrDefault(price.getId(), price.getUnitPrice()))
                    .build());
            summary.add(price.getName() + " " + qty.stripTrailingZeros().toPlainString() + " " + price.getUnit().getLabel());
        }
        String items = String.join(", ", summary);

        // Nhật ký theo đúng quy trình, kèm mốc thời gian
        List<LocalDateTime> times = new ArrayList<>();
        order.addHistory(null, OrderStatus.PENDING_CONFIRM, driver.getUsername(),
                "Tài xế tạo đơn (" + order.getTotalBags() + " túi): " + items);
        times.add(pickup);
        LocalDateTime t = pickup;
        if (target == OrderStatus.DISCREPANCY) {
            order.addHistory(OrderStatus.PENDING_CONFIRM, OrderStatus.DISCREPANCY, manager,
                    "Báo sai lệch: " + summary.get(0).split(" ")[0] + " thiếu 2 cái so với phiếu. Kiểm lại kho thấy thiếu");
            order.setDescription("Khách báo thiếu 2 món so với phiếu, chờ đối chiếu");
            times.add(t.plusMinutes(40));
        }
        if (target.ordinal() >= OrderStatus.WASHING.ordinal() && target != OrderStatus.DISCREPANCY) {
            t = t.plusMinutes(30 + rnd.nextInt(90));
            order.addHistory(OrderStatus.PENDING_CONFIRM, OrderStatus.WASHING, manager,
                    "Khách hàng xác nhận số lượng: " + order.getTotalBags() + " túi, " + items + ". Chuyển xưởng giặt");
            times.add(t);
        }
        if (target.ordinal() >= OrderStatus.DELIVERING.ordinal()) {
            t = pickup.plusHours(Math.max(4, p.turnaround() - 6 - rnd.nextInt(6)));
            order.addHistory(OrderStatus.WASHING, OrderStatus.DELIVERING, admin, "Chuyển sang \"Đang giao\"");
            times.add(t);
        }
        if (target == OrderStatus.DELIVERY_ISSUE) {
            t = t.plusHours(2);
            String note = "Thiếu 3 bộ đồng phục, 1 bộ còn vết dầu";
            order.addHistory(OrderStatus.DELIVERING, OrderStatus.DELIVERY_ISSUE, manager, "Khách báo sự cố khi nhận đồ: " + note);
            order.setDescription("Sự cố giao trả: " + note);
            times.add(t);
        }
        if (target == OrderStatus.COMPLETED) {
            t = t.plusMinutes(60 + rnd.nextInt(240));
            boolean byCustomer = rnd.nextInt(10) < 8;
            order.addHistory(OrderStatus.DELIVERING, OrderStatus.COMPLETED, byCustomer ? manager : admin,
                    byCustomer ? "Khách hàng xác nhận đã nhận đủ đồ" : "Xưởng chốt hoàn thành đơn");
            times.add(t);
            order.setCompletedAt(t);
        }

        Order saved = orderRepository.save(order);
        // @PrePersist luôn ghi "bây giờ": đặt lại mốc thời gian thật của đơn và nhật ký
        LocalDateTime updated = times.get(times.size() - 1);
        jdbc.update("UPDATE orders SET created_at = ?, updated_at = ?, due_at = ?, completed_at = ? WHERE id = ?",
                pickup, updated, saved.getDueAt(), saved.getCompletedAt(), saved.getId());
        List<OrderHistory> history = saved.getHistory();
        for (int i = 0; i < history.size(); i++) {
            jdbc.update("UPDATE order_history SET created_at = ? WHERE id = ?", times.get(i), history.get(i).getId());
        }
        if (late) {
            log.debug("Đơn #{} cố ý trễ hạn để demo", saved.getId());
        }
        return saved;
    }

    // ===================== Thanh toán =====================

    /**
     * Khách thanh toán theo tháng: tổng các đơn hoàn thành trong tháng, trả sau kỳ thanh toán.
     * Khách trả chậm / nợ dây dưa để lại các khoản quá hạn ở nhiều mức tuổi nợ.
     */
    private int seedPayments(Profile p, Restaurant r, List<Order> completed, LocalDate today) {
        Map<YearMonth, BigDecimal> byMonth = new TreeMap<>();
        for (Order o : completed) {
            byMonth.merge(YearMonth.from(o.getCompletedAt()), o.getTotalAmount(), BigDecimal::add);
        }
        int delay = switch (p.habit()) {
            case ON_TIME -> 3;
            case LATE -> 28;
            case BAD -> 75;
        };
        int count = 0;
        for (Map.Entry<YearMonth, BigDecimal> e : byMonth.entrySet()) {
            LocalDate paid = e.getKey().atEndOfMonth().plusDays(p.term() + delay);
            if (paid.isAfter(today)) {
                continue;
            }
            // Khách nợ dây dưa chỉ trả một phần
            BigDecimal amount = p.habit() == PayHabit.BAD
                    ? e.getValue().multiply(BigDecimal.valueOf(0.6)).setScale(-3, RoundingMode.DOWN).setScale(2)
                    : e.getValue().setScale(2, RoundingMode.HALF_UP);
            Payment payment = paymentRepository.save(Payment.builder()
                    .restaurant(r)
                    .amount(amount)
                    .paidDate(paid)
                    .note("Chuyển khoản thanh toán tháng " + e.getKey().getMonthValue() + "/" + e.getKey().getYear())
                    .createdBy("ketoan.mai")
                    .build());
            jdbc.update("UPDATE payments SET created_at = ? WHERE id = ?", paid.atTime(10, 30), payment.getId());
            count++;
        }
        // Một khoản ghi nhầm đã hủy, để thấy nghiệp vụ hủy thanh toán
        if (p.habit() == PayHabit.ON_TIME && p.ordersPerWeek() >= 3) {
            Payment wrong = paymentRepository.save(Payment.builder()
                    .restaurant(r)
                    .amount(money(1500000))
                    .paidDate(today.minusDays(20))
                    .note("Tiền mặt")
                    .createdBy("ketoan.mai")
                    .voided(true)
                    .voidedBy("ketoan.mai")
                    .voidedAt(LocalDateTime.now().minusDays(19))
                    .voidReason("Ghi nhầm khách hàng, đã nhập lại cho đúng")
                    .build());
            jdbc.update("UPDATE payments SET created_at = ? WHERE id = ?", today.minusDays(20).atTime(9, 0), wrong.getId());
            count++;
        }
        return count;
    }

    // ===================== Yêu cầu lấy đồ =====================

    private int seedPickups(List<User> drivers, LocalDateTime now) {
        Restaurant hoaSen = restaurantRepository.findByNameIgnoreCase(MARKER_CUSTOMER).orElseThrow();
        Restaurant bienXanh = restaurantRepository.findByNameIgnoreCase("Nhà hàng Biển Xanh").orElseThrow();
        Restaurant ngocLan = restaurantRepository.findByNameIgnoreCase("Spa Ngọc Lan").orElseThrow();
        Restaurant anTam = restaurantRepository.findByNameIgnoreCase("Phòng khám Đa khoa An Tâm").orElseThrow();
        Restaurant doiThong = restaurantRepository.findByNameIgnoreCase("Homestay Đồi Thông").orElseThrow();
        LocalDate today = now.toLocalDate();

        List<PickupRequest> list = new ArrayList<>();
        list.add(pickup(hoaSen, "hoasen.nv", today, TimeSlot.MORNING, 8, "Lấy ở cửa sau, có 3 bao chăn ga lớn",
                PickupStatus.ACCEPTED, drivers.get(0)));
        list.add(pickup(anTam, "antam.ql", today, TimeSlot.AFTERNOON, 5, "Đồ y tế đã đóng túi riêng, vui lòng mang găng tay",
                PickupStatus.ACCEPTED, drivers.get(1)));
        list.add(pickup(bienXanh, "bienxanh.ql", today.plusDays(1), TimeSlot.AFTERNOON, 4, null, PickupStatus.OPEN, null));
        list.add(pickup(ngocLan, "ngoclan.ql", today, TimeSlot.ANYTIME, null, "Gọi trước 15 phút", PickupStatus.OPEN, null));
        list.add(pickup(doiThong, "doithong.ql", today.minusDays(1), TimeSlot.MORNING, 3, null, PickupStatus.OPEN, null));
        PickupRequest cancelled = pickup(bienXanh, "bienxanh.nv", today.minusDays(3), TimeSlot.EVENING, 2, null,
                PickupStatus.CANCELLED, null);
        cancelled.setCancelledBy("bienxanh.nv");
        cancelled.setCancelReason("Đã gửi chung với đơn hôm trước");
        list.add(cancelled);

        // Một số đơn demo đang xử lý được tạo từ yêu cầu lấy đồ (chỉ đơn demo, không đụng đơn thật)
        for (Order o : activeOrders.stream().filter(o -> o.getStatus() != OrderStatus.PENDING_CONFIRM).limit(6).toList()) {
            PickupRequest done = pickup(o.getRestaurant(), managerOf.get(o.getRestaurant().getId()),
                    o.getDueAt().minusHours(o.getRestaurant().getTurnaroundHours()).toLocalDate(), TimeSlot.MORNING,
                    o.getTotalBags(), null, PickupStatus.DONE, o.getDriver());
            done.setOrder(o);
            list.add(done);
        }

        for (PickupRequest p : pickupRepository.saveAll(list)) {
            jdbc.update("UPDATE pickup_requests SET created_at = ?, updated_at = ? WHERE id = ?",
                    p.getPreferredDate().minusDays(1).atTime(16, 0), now, p.getId());
        }
        return list.size();
    }

    private PickupRequest pickup(Restaurant r, String by, LocalDate date, TimeSlot slot, Integer bags, String note,
            PickupStatus status, User driver) {
        return PickupRequest.builder()
                .restaurant(r)
                .requestedBy(by)
                .preferredDate(date)
                .timeSlot(slot)
                .estimatedBags(bags)
                .note(note)
                .status(status)
                .driver(driver)
                .build();
    }

    private static BigDecimal money(long value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }
}
