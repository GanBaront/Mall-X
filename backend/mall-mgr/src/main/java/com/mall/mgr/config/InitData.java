package com.mall.mgr.config;

import com.mall.mgr.entity.*;
import com.mall.mgr.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;

/**
 * 首次启动初始化种子数据（mall-mgr 平台管理服务配置类）
 * <p>实现 CommandLineRunner，在应用启动后执行；幂等设计：每个种子方法各自先判断对应表是否已有数据，
 * 非空则跳过，保证重复启动不会重复插入。用于为演示环境准备商户、商品、订单、统计看板等基础数据。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InitData implements CommandLineRunner {

    private final SysUserRepository sysUserRepository;
    private final MerchantRepository merchantRepository;
    private final PayAppRepository payAppRepository;
    private final PayChannelRepository payChannelRepository;
    private final PayWayRepository payWayRepository;
    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final IsvRepository isvRepository;
    private final PayOrderRepository payOrderRepository;
    private final RefundOrderRepository refundOrderRepository;
    private final BizOrderRepository bizOrderRepository;
    private final MchNoticeRepository mchNoticeRepository;
    private final SysLogRepository sysLogRepository;

    private static final Random R = new Random();
    private static final String[] WAYS = {"WECHAT", "ALIPAY", "UNIONPAY"};

    /** 演示交易数据开关（mall.init.demo-orders，见 application.yml）：false 时不生成演示订单 */
    @Value("${mall.init.demo-orders:true}")
    private boolean demoOrders;

    @Override
    public void run(String... args) {
        // 按顺序播种各维度演示数据：用户→商户→支付渠道/方式→类目→商品→服务商/通知→演示交易
        seedUsers();
        seedMerchants();
        seedChannels();
        seedCategories();
        seedProducts();
        seedOther();
        if (demoOrders) {
            seedDemoOrders();
        }
        log.info("Mall-X 初始化数据检查完成");
    }

    /** 种子方法：初始化平台管理员（admin）、运营（ops01）、审核（audit01）与普通用户（user） */
    private void seedUsers() {
        if (sysUserRepository.count() > 0) return;
        saveUser("admin", "admin123", "平台超级管理员", "ROLE_SUPER_ADMIN");
        saveUser("ops01", "123456", "运营专员-小李", "ROLE_OPS");
        saveUser("audit01", "123456", "审核专员-小王", "ROLE_AUDIT");
        saveUser("user", "123456", "普通用户", "ROLE_USER");
    }

    private void saveUser(String username, String pwd, String realName, String role) {
        SysUser u = new SysUser();
        u.setUsername(username);
        u.setPassword(pwd);
        u.setRealName(realName);
        u.setRole(role);
        u.setState(1);
        u.setCreatedAt(LocalDateTime.now());
        sysUserRepository.save(u);
    }

    /** 种子方法：初始化三个演示商户（含正常/待审核状态与差异化费率 mchRate） */
    private void seedMerchants() {
        if (merchantRepository.count() > 0) return;
        saveMerchant("M1649035675", "merchant", "123456", "支持小小数码专营店", "13800001111", "浙江省杭州市西湖区文一西路 100 号", 1, "0.6");
        saveMerchant("M1649035676", "yunshang", "123456", "云上服饰旗舰店", "13800002222", "广东省广州市天河区体育西路 88 号", 0, "0.6");
        saveMerchant("M1649035678", "yueji", "123456", "悦己美妆个护", "13800004444", "江苏省南京市玄武区中山东路 300 号", 1, "0.45");
    }

    private void saveMerchant(String shopNum, String username, String pwd, String name, String phone, String addr, int state, String rate) {
        Merchant m = new Merchant();
        m.setShopNum(shopNum);
        m.setUsername(username);
        m.setPassword(pwd);
        m.setShopName(name);
        m.setShopPhone(phone);
        m.setShopAddress(addr);
        m.setShopDesc(name + "，正品保障。");
        m.setShopState(state);
        m.setMchRate(rate);
        m.setApplyTime(LocalDateTime.now().minusDays(60));
        m.setTotalIncome(BigDecimal.ZERO);
        m.setTotalFee(BigDecimal.ZERO);
        m.setBalance(BigDecimal.ZERO);
        m.setWithdrawn(BigDecimal.ZERO);
        m.setCreatedAt(LocalDateTime.now().minusDays(60));
        merchantRepository.save(m);

        PayApp app = new PayApp();
        // appId 按商户号严格对应（与 PC 端 mock 数据一致）
        app.setAppId(switch (shopNum) {
            case "M1649035675" -> "62591053e4b05076b70116b4";   // 小小数码-收银台
            case "M1649035678" -> "8a77b20f8b2101c4018b2c3d4e5f0001"; // 悦己美妆-收银台
            default -> "a1b2c3d4e5f60718293a4b5c";              // 云上服饰-小程序
        });
        app.setAppName(name + (shopNum.equals("M1649035676") ? "-小程序" : "-收银台"));
        app.setShopNum(shopNum);
        app.setState(1);
        app.setCreatedAt(LocalDateTime.now().minusDays(59));
        payAppRepository.save(app);
    }

    /** 种子方法：初始化支付渠道（WECHAT/ALIPAY/UNIONPAY）及对应支付方式（均为跳转收银台支付） */
    private void seedChannels() {
        if (payChannelRepository.count() > 0) return;
        saveChannel("WECHAT", "微信支付", 1, "wx8886666aabbccdd", "1900006666", "0.2", "微信支付渠道");
        saveChannel("ALIPAY", "支付宝", 1, "2021003188612345", "2088123456789012", "0.3", "支付宝当面付+手机网站支付");
        saveChannel("UNIONPAY", "银联支付", 0, "unionpay-mer-0088", "89834018399UPPE", "0.15", "银联全渠道支付");

        saveWay("WECHAT", "微信支付", "跳转支付", 1, "0.2");
        saveWay("ALIPAY", "支付宝支付", "跳转支付", 1, "0.3");
        saveWay("UNIONPAY", "银联支付", "跳转支付", 0, "0.15");
    }

    private void saveChannel(String wayCode, String name, int state, String appId, String mchId, String rate, String remark) {
        PayChannel c = new PayChannel();
        c.setWayCode(wayCode);
        c.setName(name);
        c.setState(state);
        c.setAppId(appId);
        c.setAppSecret("channel-secret-" + wayCode.toLowerCase());
        c.setMchId(mchId);
        c.setRate(rate);
        c.setRemark(remark);
        payChannelRepository.save(c);
    }

    private void saveWay(String wayCode, String name, String type, int state, String rate) {
        PayWay w = new PayWay();
        w.setWayCode(wayCode);
        w.setName(name);
        w.setWayType(type);
        w.setState(state);
        w.setRate(rate);
        payWayRepository.save(w);
    }

    /** 种子方法：初始化多级商品类目（数码电器/服饰鞋包/美妆个护/食品生鲜 及其子级） */
    private void seedCategories() {
        if (categoryRepository.count() > 0) return;
        long g1 = saveCat("数码电器", 0L, 1);
        saveCat("手机通讯", g1, 1); saveCat("电脑办公", g1, 2); saveCat("影音设备", g1, 3);
        long g2 = saveCat("服饰鞋包", 0L, 2);
        saveCat("男装", g2, 1); saveCat("女装", g2, 2); saveCat("箱包配饰", g2, 3);
        long g3 = saveCat("美妆个护", 0L, 3);
        saveCat("护肤", g3, 1); saveCat("彩妆", g3, 2);
        long g4 = saveCat("食品生鲜", 0L, 4);
        saveCat("水果", g4, 1); saveCat("休闲零食", g4, 2);
    }

    private long saveCat(String name, Long parent, int sort) {
        Category c = new Category();
        c.setName(name);
        c.setParentId(parent);
        c.setSort(sort);
        c.setState(1);
        return categoryRepository.save(c).getId();
    }

    /**
     * 种子方法：初始化商品库（100 个互不相同的真实感商品，5 分类各 20 个）。
     * 图片使用 static/goods/real/real-001..100.jpg（与商品顺序一一对应，
     * 为按商品关键词从 LoremFlickr 下载的真实实拍图，脚本 .workbuddy/fetch_real_images.py）。
     * 以「青柑普洱茶」为精选库标记：记录不存在或仍指向旧版占位图
     * （首次建库/旧版组合词库/gen 占位图版）时清空商品表重建；已是新版则跳过（幂等）。
     * 订单里冗余了商品名/图片快照，重建不受影响。
     */
    private void seedProducts() {
        List<Product> marker = productRepository.findByNameContaining("青柑普洱茶");
        if (!marker.isEmpty() && marker.get(0).getImage() != null
                && marker.get(0).getImage().startsWith("/static/goods/real/")) return;
        productRepository.deleteAllInBatch(); // 重建商品库，避免新旧种子混存

        // 行格式：{名称, 价格, 库存, 销量, 分类, 图片编号(已废弃，图片按行序取 gen-%03d), 描述}；服装归悦己(M1649035678)，其余归小小数码(M1649035675)
        Object[][] rows = {
                // ===== 服装（20）=====
                {"轻量羽绒服", "99.0", 58, 120, "服装", 1, "90%白鸭绒填充，轻暖蓬松，三色可选。"},
                {"针织羊毛围巾", "59.0", 0, 210, "服装", 5, "柔软亲肤，加厚保暖，多色可选。"},
                {"窄管直筒牛仔裤", "149.0", 44, 64, "服装", 10, "弹力面料，显瘦版型。"},
                {"法式碎花连衣裙", "189.0", 66, 331, "服装", 1, "雪纺面料，收腰显瘦，度假必备。"},
                {"国潮印花卫衣", "129.0", 88, 275, "服装", 5, "重磅纯棉，国风印花，男女同款。"},
                {"冰丝防晒衬衫", "99.0", 120, 189, "服装", 10, "UPF50+ 防晒，凉感透气，通勤百搭。"},
                {"加绒保暖卫裤", "119.0", 75, 156, "服装", 1, "内里加绒，束脚版型，秋冬保暖。"},
                {"复古水洗夹克", "219.0", 32, 98, "服装", 5, "水洗做旧工艺，版型挺括，耐穿耐洗。"},
                {"运动速干Polo衫", "89.0", 140, 233, "服装", 10, "速干透气面料，商务运动两相宜。"},
                {"羊毛混纺大衣", "399.0", 21, 87, "服装", 1, "70%羊毛含量，廓形剪裁，气质通勤。"},
                {"雪纺收腰长裙", "159.0", 54, 176, "服装", 5, "轻盈飘逸，收腰显高，多色可选。"},
                {"工装多口袋短裤", "69.0", 160, 322, "服装", 10, "耐磨帆布，立体口袋，街头风。"},
                {"桑蚕丝睡衣套装", "299.0", 18, 65, "服装", 1, "100%桑蚕丝，亲肤滑爽，精致镶边。"},
                {"高腰阔腿西裤", "169.0", 62, 141, "服装", 5, "垂感面料，高腰提臀，遮肉显瘦。"},
                {"撞色拼接棒球服", "179.0", 47, 118, "服装", 10, "拼接撞色，罗纹收口，学院风。"},
                {"法兰绒格子衬衫", "109.0", 95, 264, "服装", 1, "磨毛法兰绒，柔软厚实，情侣款。"},
                {"弹力瑜伽健身裤", "89.0", 175, 489, "服装", 5, "裸感高弹，深蹲不透，蜜桃提臀。"},
                {"儿童卡通连帽卫衣", "79.0", 130, 208, "服装", 10, "A类纯棉，卡通印花，保暖舒适。"},
                {"中长款商务风衣", "289.0", 26, 73, "服装", 1, "防泼水面料，腰带收腰，气场全开。"},
                {"精梳罗纹打底衫", "59.0", 210, 397, "服装", 5, "精梳棉，罗纹弹力，内搭神器。"},
                // ===== 家电（20）=====
                {"家用空气炸锅", "199.0", 23, 86, "家电", 2, "5L大容量，无油低脂，一机多用。"},
                {"破壁豆浆机", "329.0", 15, 75, "家电", 6, "免滤免洗，细腻无渣，12小时预约。"},
                {"智能空气净化器", "899.0", 6, 31, "家电", 12, "CADR值600，除醛除菌，APP互联。"},
                {"变频节能电冰箱", "2199.0", 12, 45, "家电", 2, "一级能效，风冷无霜，干湿分储。"},
                {"全自动滚筒洗衣机", "1599.0", 18, 62, "家电", 6, "10公斤大容量，除菌洗，高温自清洁。"},
                {"静音塔式电风扇", "179.0", 64, 158, "家电", 12, "无叶设计，12档风速，遥控定时。"},
                {"超声波加湿器", "129.0", 88, 214, "家电", 2, "5L大水箱，上加水，静音恒湿。"},
                {"手持蒸汽挂烫机", "159.0", 70, 183, "家电", 6, "20秒出蒸汽，平挂两烫，便携收纳。"},
                {"智能扫地机器人", "1299.0", 25, 167, "家电", 12, "激光导航，自动集尘，扫拖一体。"},
                {"双仓多功能电烤箱", "299.0", 36, 92, "家电", 2, "35L上下独立控温，热风循环。"},
                {"即热式电热水壶", "99.0", 110, 276, "家电", 6, "3秒即热，6段控温，泡奶泡茶更方便。"},
                {"油汀取暖器", "259.0", 40, 121, "家电", 12, "13片油汀，取暖不干燥，带烘衣架。"},
                {"负离子电吹风", "139.0", 96, 305, "家电", 2, "负离子护发，恒温热风，折叠便携。"},
                {"触屏电磁炉", "169.0", 82, 149, "家电", 6, "2200W爆炒火力，九档控温，送炒锅。"},
                {"迷你台式洗碗机", "899.0", 20, 58, "家电", 12, "免安装，高温除菌洗，2-4人份。"},
                {"除螨仪吸尘器", "349.0", 48, 176, "家电", 2, "紫外线+热风除螨，尘杯可视。"},
                {"恒温电热毯", "119.0", 66, 132, "家电", 6, "九档定时，断电保护，双控双温。"},
                {"壁挂式冷风扇", "229.0", 34, 87, "家电", 12, "水冷降温，遥控摇头，带冰晶盒。"},
                {"智能语音电饭煲", "249.0", 52, 143, "家电", 2, "语音播报，柴火饭口感，24h预约。"},
                {"不锈钢电饼铛", "129.0", 74, 168, "家电", 6, "双面加热，悬浮烤盘，煎烤烙一机搞定。"},
                // ===== 食品（20）=====
                {"原味坚果礼盒", "29.9", 150, 350, "食品", 3, "每日坚果混合装，新鲜锁存小包装。"},
                {"即食燕麦片", "35.0", 180, 260, "食品", 9, "澳洲进口燕麦，高膳食纤维。"},
                {"手工小熊曲奇", "19.9", 300, 620, "食品", 3, "黄油烘焙，独立包装，下午茶首选。"},
                {"低糖全麦面包", "16.9", 260, 480, "食品", 9, "无添加蔗糖，全麦含量50%，代餐合适。"},
                {"高钙学生奶粉", "89.0", 90, 175, "食品", 3, "高钙配方，独立小袋，冲泡易吸收。"},
                {"新疆灰枣", "25.9", 320, 540, "食品", 9, "肉厚核小，自然挂干，甘甜糯香。"},
                {"即食鸡胸肉", "39.9", 220, 730, "食品", 3, "高蛋白低脂肪，开袋即食，健身伴侣。"},
                {"炭烤牛肉干", "49.9", 130, 410, "食品", 9, "内蒙古黄牛肉，炭烤香辣，有嚼劲。"},
                {"冻干草莓脆", "22.9", 280, 366, "食品", 3, "-35℃冻干锁鲜，酥脆酸甜。"},
                {"海盐苏打饼干", "12.9", 400, 820, "食品", 9, "海盐风味，酥脆不腻，办公室零食。"},
                {"芝士夹心威化", "15.9", 350, 590, "食品", 3, "四层威化，芝士夹心，一口爆浆。"},
                {"五谷杂粮礼盒", "68.0", 85, 152, "食品", 9, "八种杂粮科学配比，五谷丰登好彩头。"},
                {"麻辣卤味鸭脖", "32.9", 190, 610, "食品", 3, "秘制卤汁，麻辣过瘾，独立锁鲜装。"},
                {"蜂蜜柚子茶酱", "29.9", 160, 288, "食品", 9, "真实果肉含量≥30%，温水冲饮。"},
                {"柳州螺蛳粉", "13.9", 500, 1560, "食品", 3, "酸笋爽脆，汤浓味足，正宗柳州味。"},
                {"水果燕麦脆", "26.9", 240, 445, "食品", 9, "酸奶块+冻干水果，干吃泡酸奶都好。"},
                {"桂花酒酿小圆子", "18.9", 210, 330, "食品", 3, "手工小圆子，桂花酒酿，甜而不腻。"},
                {"手撕全麦吐司", "21.9", 270, 512, "食品", 9, "低温发酵，手撕口感，麦香浓郁。"},
                {"香辣小鱼干", "19.9", 230, 388, "食品", 3, "深海小鱼，香辣入味，下酒下饭。"},
                {"红枣核桃糕", "28.9", 170, 296, "食品", 9, "红枣打浆，整颗核桃，软糯香甜。"},
                // ===== 数码（20）=====
                {"智能运动手表", "599.0", 8, 42, "数码", 4, "心率/血氧监测，50米防水，长续航。"},
                {"蓝牙降噪耳机", "459.0", 35, 98, "数码", 8, "主动降噪，蓝牙5.3，续航40小时。"},
                {"5G全面屏手机", "1999.0", 15, 210, "数码", 4, "6.7英寸120Hz高刷屏，5000mAh电池。"},
                {"11英寸平板电脑", "1299.0", 22, 133, "数码", 8, "2.5K护眼屏，四扬声器，网课利器。"},
                {"轻薄办公笔记本", "4599.0", 10, 67, "数码", 4, "14英寸2.8K屏，标压处理器，1.4kg。"},
                {"便携蓝牙音箱", "199.0", 60, 285, "数码", 8, "360°环绕音效，IPX7防水，续航24h。"},
                {"20000毫安移动电源", "129.0", 95, 470, "数码", 4, "22.5W快充，数显电量，可上飞机。"},
                {"智能手环血氧版", "169.0", 80, 356, "数码", 8, "血氧/心率/睡眠监测，14天续航。"},
                {"4K行车记录仪", "299.0", 55, 244, "数码", 4, "4K夜视，停车监控，语音声控。"},
                {"机械键盘87键", "249.0", 42, 198, "数码", 8, "热插拔轴体，RGB背光，三模连接。"},
                {"无线充电底座", "79.0", 130, 388, "数码", 4, "15W快充，金属面板，iPhone/安卓通用。"},
                {"1080P智能摄像头", "119.0", 105, 452, "数码", 8, "360°云台，红外夜视，移动侦测报警。"},
                {"电竞游戏鼠标", "159.0", 70, 266, "数码", 4, "26000DPI，59g轻量化，宏编程。"},
                {"27英寸显示器", "899.0", 16, 88, "数码", 8, "2K IPS，100Hz，低蓝光不闪屏。"},
                {"USB-C扩展坞", "149.0", 88, 233, "数码", 4, "八合一扩展，4K投屏，100W PD充电。"},
                {"智能体脂秤", "69.0", 150, 521, "数码", 8, "28项身体数据，APP曲线管理。"},
                {"头戴式游戏耳机", "199.0", 48, 176, "数码", 4, "7.1声道环绕，降噪麦克风，RGB灯效。"},
                {"口袋手机打印机", "399.0", 30, 94, "数码", 8, "热敏无墨打印，错题/手账/照片。"},
                {"智能门锁指纹款", "699.0", 14, 76, "数码", 4, "半导体指纹，临时密码，防猫眼开锁。"},
                {"入门航拍无人机", "999.0", 0, 118, "数码", 8, "4K云台增稳，智能跟随，31分钟续航。"},
                // ===== 美饮（20）=====
                {"现磨挂耳咖啡", "45.0", 200, 500, "美饮", 7, "云南日晒阿拉比卡，10包装挂耳。"},
                {"低糖鲜奶茶", "15.9", 500, 800, "美饮", 11, "现制轻乳茶，0植脂末。"},
                {"生椰拿铁咖啡液", "39.9", 260, 720, "美饮", 7, "冷萃咖啡液，一条兑一杯，生椰香浓。"},
                {"冷萃咖啡浓缩液", "49.0", 180, 466, "美饮", 11, "12小时低温冷萃，冰美式灵魂。"},
                {"桂花乌龙茶包", "29.0", 320, 588, "美饮", 7, "窨制桂花+高山乌龙，冷泡热泡皆宜。"},
                {"茉莉花茶", "25.0", 280, 470, "美饮", 11, "七窨茉莉，茶汤清亮，花香入水。"},
                {"NFC鲜榨橙汁", "19.9", 240, 640, "美饮", 7, "100%鲜榨非浓缩，不加水不加糖。"},
                {"无糖气泡苏打水", "12.9", 420, 950, "美饮", 11, "0糖0卡0脂肪，气泡绵密。"},
                {"芒果汁饮料", "15.0", 300, 540, "美饮", 7, "台农芒果榨取，果肉含量≥40%。"},
                {"经典原味可可", "33.0", 190, 322, "美饮", 11, "加纳可可粉，丝绒口感，热冲冷调均可。"},
                {"燕麦植物奶", "36.0", 210, 388, "美饮", 7, "0乳糖0添加蔗糖，咖啡伴侣好搭子。"},
                {"柠檬蜂蜜茶", "17.9", 260, 455, "美饮", 11, "真柠檬+洋槐蜂蜜，酸甜清爽。"},
                {"浓缩黑咖啡", "42.0", 230, 612, "美饮", 7, "意式拼配，油脂丰厚，提神醒脑。"},
                {"蜜香红茶", "27.0", 200, 344, "美饮", 11, "蜜香明显，喉韵甘甜，奶茶店同款。"},
                {"手作芋泥奶茶", "18.9", 310, 705, "美饮", 7, "真实芋头熬煮，颗颗芋圆，料足满杯。"},
                {"青柑普洱茶", "59.0", 120, 258, "美饮", 11, "新会青柑+云南熟普，一颗一泡。"},
                {"玫瑰花茶", "23.0", 240, 377, "美饮", 7, "平阴重瓣玫瑰，花冠完整，香气馥郁。"},
                {"生榨椰汁", "16.9", 280, 498, "美饮", 11, "海南椰肉生榨，椰香浓郁，早餐搭档。"},
                {"酸梅汤浓缩汁", "21.9", 220, 366, "美饮", 7, "乌梅山楂陈皮熬制，1:8稀释饮用。"},
                {"抹茶拿铁粉", "35.0", 180, 298, "美饮", 11, "宇治抹茶风味，抹茶+乳粉二合一。"}
        };
        int imgIdx = 0;
        for (Object[] row : rows) {
            imgIdx++;
            String shopNum = "服装".equals(row[4]) ? "M1649035678" : "M1649035675";
            saveProduct((String) row[0], (String) row[1], (Integer) row[2], (Integer) row[3],
                    (String) row[4], (String) row[6],
                    String.format("/static/goods/real/real-%03d.jpg", imgIdx), shopNum);
        }
        log.info("Mall-X 商品库初始化完成，共 {} 个商品", productRepository.count());
    }

    private void saveProduct(String name, String price, int stock, int sales, String category, String descr, String image, String shopNum) {
        Product p = new Product();
        p.setName(name);
        p.setPrice(new BigDecimal(price));
        p.setStock(stock);
        p.setSales(sales);
        p.setCategory(category);
        p.setState(stock > 0 ? 1 : 0);
        p.setDescr(descr);
        p.setImage(image);
        p.setShopNum(shopNum);
        p.setCreatedAt(LocalDateTime.now().minusDays(30));
        productRepository.save(p);
    }

    /** 种子方法：初始化服务商（ISV）列表与一条商户入驻审核通过的站内通知 */
    private void seedOther() {
        if (isvRepository.count() == 0) {
            saveIsv("V202600010001", "杭州联汇科技服务商", "0571-88001111", "钱经理", "isv-app-8866001", 1);
            saveIsv("V202600010002", "广东云商支付服务商", "020-83002222", "林经理", "isv-app-8866002", 1);
            saveIsv("V202600010003", "四川天府收单服务商", "028-85003333", "周经理", "isv-app-8866003", 0);
        }
        if (mchNoticeRepository.count() == 0) {
            notice("M1649035675", "支持小小数码专营店", "入驻审核结果", "恭喜，您的入驻申请已审核通过，可以开始上架商品经营。", "审核通知", 1, 60);
        }
    }

    private void saveIsv(String num, String name, String phone, String contact, String appId, int state) {
        Isv i = new Isv();
        i.setIsvNum(num);
        i.setIsvName(name);
        i.setLinkPhone(phone);
        i.setContact(contact);
        i.setAppId(appId);
        i.setState(state);
        i.setCreatedAt(LocalDateTime.now().minusDays(120));
        isvRepository.save(i);
    }

    private void notice(String shopNum, String shopName, String title, String content, String type, int state, int daysAgo) {
        MchNotice n = new MchNotice();
        n.setShopNum(shopNum);
        n.setShopName(shopName);
        n.setTitle(title);
        n.setContent(content);
        n.setNoticeType(type);
        n.setState(state);
        n.setCreatedAt(LocalDateTime.now().minusDays(daysAgo));
        mchNoticeRepository.save(n);
    }

    /** 演示交易数据：近 7 天支付订单 + 退款 + 履约单 + 操作日志，保证统计看板开箱即有数据 */
    private void seedDemoOrders() {
        if (payOrderRepository.count() > 0) return;
        String[][] goods = {
                {"智能运动手表", "599.0", "M1649035675", "支持小小数码专营店"},
                {"蓝牙降噪耳机", "459.0", "M1649035675", "支持小小数码专营店"},
                {"原味坚果礼盒", "29.9", "M1649035675", "支持小小数码专营店"},
                {"现磨挂耳咖啡", "45.0", "M1649035675", "支持小小数码专营店"},
                {"智能空气净化器", "899.0", "M1649035675", "支持小小数码专营店"},
                {"轻量羽绒服", "99.0", "M1649035678", "悦己美妆个护"},
                {"窄管直筒牛仔裤", "149.0", "M1649035678", "悦己美妆个护"}
        };
        long seq = 0;
        java.util.List<BizOrder> seeded = new java.util.ArrayList<>();
        for (int day = 7; day >= 1; day--) {
            for (int i = 0; i < 2 + R.nextInt(3); i++) {
                String[] g = goods[R.nextInt(goods.length)];
                BigDecimal money = new BigDecimal(g[1]).multiply(BigDecimal.valueOf(1 + R.nextInt(3))).setScale(2, RoundingMode.HALF_UP);
                String way = WAYS[R.nextInt(WAYS.length)];
                LocalDateTime payTime = LocalDateTime.now().minusDays(day).withHour(9 + R.nextInt(10)).withMinute(R.nextInt(60));
                PayOrder po = new PayOrder();
                po.setPayOrderId("P1552351087778" + String.format("%06d", ++seq));
                po.setMchOrderNo("M" + payTime.format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd")) + String.format("%04d", seq));
                po.setShopNum(g[2]);
                po.setShopName(g[3]);
                po.setAppId(g[2].equals("M1649035678") ? "8a77b20f8b2101c4018b2c3d4e5f0001" : "62591053e4b05076b70116b4");
                po.setWayCode(way);
                po.setMoney(money);
                po.setRefundMoney(BigDecimal.ZERO);
                BigDecimal fee = money.multiply(new BigDecimal("0.003")).setScale(2, RoundingMode.HALF_UP);
                po.setFee(fee);
                po.setRate("0.3%");
                po.setOrderState(0);
                po.setSubject(g[0]);
                po.setUserNum("Uuser");
                po.setChannelOrderNo("CH" + seq);
                po.setCreatedAt(payTime.minusMinutes(2));
                po.setPayTime(payTime);
                po.setUpdatedAt(payTime);
                payOrderRepository.save(po);

                // 履约单：一部分已完成，一部分待发货/待收货
                BizOrder biz = new BizOrder();
                biz.setOrderNum(po.getMchOrderNo());
                biz.setPayOrderId(po.getPayOrderId());
                biz.setUserNum("Uuser");
                biz.setShopNum(g[2]);
                biz.setGoodsName(g[0]);
                biz.setNum(1 + R.nextInt(2));
                biz.setTotal(money);
                biz.setState(day >= 4 ? 3 : (day >= 2 ? 2 : 1));
                biz.setWayCode(way);
                biz.setBuyer("测试**");
                biz.setAddress("演示地址-" + seq);
                biz.setLogisticsNo(biz.getState() >= 2 ? "SF" + (100000000 + seq) : "");
                biz.setCreatedAt(po.getCreatedAt());
                biz.setUpdatedAt(payTime);
                bizOrderRepository.save(biz);
                seeded.add(biz);

                // 商户入账：仅已完成(state=3)订单在确认收货后结算；待发货/待收货订单货款托管中不记账
                if (biz.getState() == 3) {
                    merchantRepository.findById(g[2]).ifPresent(m -> {
                        m.setTotalIncome(m.getTotalIncome().add(money));
                        m.setBalance(m.getBalance().add(money).subtract(fee));
                        m.setTotalFee(m.getTotalFee().add(fee));
                        merchantRepository.save(m);
                    });
                }
            }
        }
        // 一笔失败单 + 一笔退款单
        PayOrder failed = payOrderRepository.findAll().get(0);
        PayOrder po2 = new PayOrder();
        po2.setPayOrderId(failed.getPayOrderId() + "F");
        po2.setMchOrderNo(failed.getMchOrderNo() + "F");
        po2.setShopNum(failed.getShopNum());
        po2.setShopName(failed.getShopName());
        po2.setAppId(failed.getAppId());
        po2.setWayCode("WECHAT");
        po2.setMoney(new BigDecimal("1299.00"));
        po2.setRefundMoney(BigDecimal.ZERO);
        po2.setFee(BigDecimal.ZERO);
        po2.setRate("0.2%");
        po2.setOrderState(3);
        po2.setSubject("便携显示器");
        po2.setUserNum("Uuser");
        po2.setCreatedAt(LocalDateTime.now().minusDays(1));
        po2.setUpdatedAt(LocalDateTime.now().minusDays(1));
        payOrderRepository.save(po2);

        // 退款单：对应一笔已完成订单——订单关闭，商户经营数据同步扣减（与新结算口径一致）
        BizOrder refundedBiz = seeded.stream().filter(b -> b.getState() == 3).findFirst().orElse(null);
        if (refundedBiz != null) {
            PayOrder refundedPo = payOrderRepository.findById(refundedBiz.getPayOrderId()).orElse(null);
            if (refundedPo != null) {
                refundedBiz.setState(4); // 退款成功，订单关闭
                bizOrderRepository.save(refundedBiz);
                RefundOrder refund = new RefundOrder();
                refund.setRefundOrderId("R1552351087778" + String.format("%06d", 999001));
                refund.setPayOrderId(refundedPo.getPayOrderId());
                refund.setShopNum(refundedPo.getShopNum());
                refund.setRefundMoney(refundedPo.getMoney());
                refund.setRefundState(1);
                refund.setRefundReason("商品与描述不符，协商退货退款");
                refund.setApplyTime(LocalDateTime.now().minusDays(1));
                refund.setRefundTime(LocalDateTime.now().minusDays(1).plusHours(2));
                refundOrderRepository.save(refund);
                merchantRepository.findById(refundedPo.getShopNum()).ifPresent(m -> {
                    m.setTotalIncome(m.getTotalIncome().subtract(refundedPo.getMoney()).max(BigDecimal.ZERO));
                    // 与 RefundService 售后扣除口径一致：余额扣「实付-手续费」（入账时余额加的是 money-fee，退款只冲回这一部分）
                    m.setBalance(m.getBalance().subtract(refundedPo.getMoney()).add(refundedPo.getFee()).max(BigDecimal.ZERO));
                    m.setTotalFee(m.getTotalFee().subtract(refundedPo.getFee()).max(BigDecimal.ZERO));
                    merchantRepository.save(m);
                });
            }
        }

        SysLog log1 = new SysLog();
        log1.setUsername("admin");
        log1.setModule("商户管理");
        log1.setAction("审核通过商户「支持小小数码专营店」");
        log1.setIp("192.168.1.10");
        log1.setResult("成功");
        log1.setCreatedAt(LocalDateTime.now().minusDays(59));
        sysLogRepository.save(log1);
    }
}
