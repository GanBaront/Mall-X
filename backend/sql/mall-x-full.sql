-- ============================================================
-- Mall-X 多商家电商平台 · 数据库全量脚本（干净状态，2026-10-28 重新导出）
-- 内容：建库(mallDb) + 全部表结构 + 基础数据（账号/商户/商品/渠道，无订单）
-- 来源：mysqldump 从运行库完整导出，MySQL 8.0.20，utf8mb4
--
-- 导入方式一（Navicat）：右键连接 -> 运行 SQL 文件 -> 选本文件 -> 编码 65001 (UTF-8)
-- 导入方式二（命令行）：mysql -uroot -p < mall-x-full.sql
-- 重复导入会先 DROP 再重建（幂等）
--
-- 内置账号（密码）：admin/admin123、user/123456、merchant/123456、yunshang/123456、yueji/123456
-- 若本机 MySQL 密码非 123456，请同步修改 backend 各模块 application.yml 的 spring.datasource.password
-- ============================================================
-- MySQL dump 10.13  Distrib 8.0.20, for Win64 (x86_64)
--
-- Host: localhost    Database: mallDb
-- ------------------------------------------------------
-- Server version	8.0.20

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Current Database: `mallDb`
--

/*!40000 DROP DATABASE IF EXISTS `mallDb`*/;

CREATE DATABASE /*!32312 IF NOT EXISTS*/ `mallDb` /*!40100 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci */ /*!80016 DEFAULT ENCRYPTION='N' */;

USE `mallDb`;

--
-- Table structure for table `biz_order`
--

DROP TABLE IF EXISTS `biz_order`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_order` (
  `order_num` varchar(255) NOT NULL,
  `address` varchar(255) DEFAULT NULL,
  `buyer` varchar(255) DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `goods_name` varchar(255) DEFAULT NULL,
  `image` varchar(255) DEFAULT NULL,
  `logistics_no` varchar(255) DEFAULT NULL,
  `num` int DEFAULT NULL,
  `pay_order_id` varchar(255) DEFAULT NULL,
  `product_id` bigint DEFAULT NULL,
  `shop_num` varchar(255) DEFAULT NULL,
  `state` int DEFAULT NULL,
  `total` decimal(38,2) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `user_num` varchar(255) DEFAULT NULL,
  `way_code` varchar(255) DEFAULT NULL,
  `price` decimal(38,2) DEFAULT NULL,
  `finish_time` datetime(6) DEFAULT NULL,
  `pay_time` datetime(6) DEFAULT NULL,
  `ship_time` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`order_num`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `biz_order`
--

LOCK TABLES `biz_order` WRITE;
/*!40000 ALTER TABLE `biz_order` DISABLE KEYS */;
/*!40000 ALTER TABLE `biz_order` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mch_app`
--

DROP TABLE IF EXISTS `mch_app`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mch_app` (
  `app_id` varchar(255) NOT NULL,
  `app_name` varchar(255) DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `remark` varchar(255) DEFAULT NULL,
  `shop_num` varchar(255) DEFAULT NULL,
  `state` int DEFAULT NULL,
  PRIMARY KEY (`app_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mch_app`
--

LOCK TABLES `mch_app` WRITE;
/*!40000 ALTER TABLE `mch_app` DISABLE KEYS */;
INSERT INTO `mch_app` VALUES ('62591053e4b05076b70116b4','支持小小数码专营店-收银台','2026-08-30 10:13:03.413955',NULL,'M1649035675',1),('8a77b20f8b2101c4018b2c3d4e5f0001','悦己美妆个护-收银台','2026-08-30 10:13:03.478004',NULL,'M1649035678',1),('a1b2c3d4e5f60718293a4b5c','云上服饰旗舰店-小程序','2026-08-30 10:13:03.447414',NULL,'M1649035676',1);
/*!40000 ALTER TABLE `mch_app` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mch_notice`
--

DROP TABLE IF EXISTS `mch_notice`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mch_notice` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `content` varchar(1000) DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `notice_type` varchar(255) DEFAULT NULL,
  `shop_name` varchar(255) DEFAULT NULL,
  `shop_num` varchar(255) DEFAULT NULL,
  `state` int DEFAULT NULL,
  `title` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mch_notice`
--

LOCK TABLES `mch_notice` WRITE;
/*!40000 ALTER TABLE `mch_notice` DISABLE KEYS */;
INSERT INTO `mch_notice` VALUES (1,'恭喜，您的入驻申请已审核通过，可以开始上架商品经营。','2026-08-29 10:13:04.849778','审核通知','支持小小数码专营店','M1649035675',1,'入驻审核结果');
/*!40000 ALTER TABLE `mch_notice` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mch_shop`
--

DROP TABLE IF EXISTS `mch_shop`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mch_shop` (
  `shop_num` varchar(255) NOT NULL,
  `apply_time` datetime(6) DEFAULT NULL,
  `audit_remark` varchar(255) DEFAULT NULL,
  `balance` decimal(38,2) DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `mch_rate` varchar(255) DEFAULT NULL,
  `password` varchar(255) DEFAULT NULL,
  `shop_address` varchar(255) DEFAULT NULL,
  `shop_desc` varchar(255) DEFAULT NULL,
  `shop_name` varchar(255) DEFAULT NULL,
  `shop_phone` varchar(255) DEFAULT NULL,
  `shop_state` int DEFAULT NULL,
  `total_fee` decimal(38,2) DEFAULT NULL,
  `total_income` decimal(38,2) DEFAULT NULL,
  `username` varchar(255) DEFAULT NULL,
  `withdrawn` decimal(38,2) DEFAULT NULL,
  PRIMARY KEY (`shop_num`),
  UNIQUE KEY `UKriuujjwq8r296s9om3oe7ygr1` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mch_shop`
--

LOCK TABLES `mch_shop` WRITE;
/*!40000 ALTER TABLE `mch_shop` DISABLE KEYS */;
INSERT INTO `mch_shop` VALUES ('M1649035675','2026-08-29 10:13:03.371698',NULL,0.00,'2026-08-29 10:13:03.371698','0.6','123456','浙江省杭州市西湖区文一西路 100 号','支持小小数码专营店，正品保障。','支持小小数码专营店','13800001111',1,0.00,0.00,'merchant',0.00),('M1649035676','2026-08-29 10:13:03.431350',NULL,0.00,'2026-08-29 10:13:03.431350','0.6','123456','广东省广州市天河区体育西路 88 号','云上服饰旗舰店，正品保障。','云上服饰旗舰店','13800002222',0,0.00,0.00,'yunshang',0.00),('M1649035678','2026-08-29 10:13:03.462665',NULL,0.00,'2026-08-29 10:13:03.462665','0.45','123456','江苏省南京市玄武区中山东路 300 号','悦己美妆个护，正品保障。','悦己美妆个护','13800004444',1,0.00,0.00,'yueji',0.00);
/*!40000 ALTER TABLE `mch_shop` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `mkt_coupon`
--

DROP TABLE IF EXISTS `mkt_coupon`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mkt_coupon` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `amount` decimal(38,2) DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `discount_rate` decimal(38,2) DEFAULT NULL,
  `expire_at` datetime(6) DEFAULT NULL,
  `name` varchar(255) DEFAULT NULL,
  `state` int DEFAULT NULL,
  `type` int DEFAULT NULL,
  `used_at` datetime(6) DEFAULT NULL,
  `used_order_num` varchar(255) DEFAULT NULL,
  `user_num` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `mkt_coupon`
--

LOCK TABLES `mkt_coupon` WRITE;
/*!40000 ALTER TABLE `mkt_coupon` DISABLE KEYS */;
/*!40000 ALTER TABLE `mkt_coupon` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `pay_channel`
--

DROP TABLE IF EXISTS `pay_channel`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pay_channel` (
  `way_code` varchar(255) NOT NULL,
  `apiv3key` varchar(255) DEFAULT NULL,
  `app_id` varchar(255) DEFAULT NULL,
  `app_secret` varchar(255) DEFAULT NULL,
  `mch_id` varchar(255) DEFAULT NULL,
  `name` varchar(255) DEFAULT NULL,
  `rate` varchar(255) DEFAULT NULL,
  `remark` varchar(255) DEFAULT NULL,
  `state` int DEFAULT NULL,
  PRIMARY KEY (`way_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `pay_channel`
--

LOCK TABLES `pay_channel` WRITE;
/*!40000 ALTER TABLE `pay_channel` DISABLE KEYS */;
INSERT INTO `pay_channel` VALUES ('ALIPAY',NULL,'2021003188612345','channel-secret-alipay','2088123456789012','支付宝','0.3','支付宝当面付+手机网站支付',1),('UNIONPAY',NULL,'unionpay-mer-0088','channel-secret-unionpay','89834018399UPPE','银联支付','0.15','银联全渠道支付',0),('WECHAT',NULL,'wx8886666aabbccdd','channel-secret-wechat','1900006666','微信支付','0.2','微信支付渠道',1);
/*!40000 ALTER TABLE `pay_channel` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `pay_order`
--

DROP TABLE IF EXISTS `pay_order`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pay_order` (
  `pay_order_id` varchar(255) NOT NULL,
  `app_id` varchar(255) DEFAULT NULL,
  `body` varchar(255) DEFAULT NULL,
  `channel_order_no` varchar(255) DEFAULT NULL,
  `coupon_discount` decimal(38,2) DEFAULT NULL,
  `coupon_id` bigint DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `fee` decimal(38,2) DEFAULT NULL,
  `mch_order_no` varchar(255) DEFAULT NULL,
  `money` decimal(38,2) DEFAULT NULL,
  `order_state` int DEFAULT NULL,
  `pay_time` datetime(6) DEFAULT NULL,
  `rate` varchar(255) DEFAULT NULL,
  `refund_money` decimal(38,2) DEFAULT NULL,
  `shop_name` varchar(255) DEFAULT NULL,
  `shop_num` varchar(255) DEFAULT NULL,
  `subject` varchar(255) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `user_num` varchar(255) DEFAULT NULL,
  `way_code` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`pay_order_id`),
  UNIQUE KEY `uk_mch_order_no` (`mch_order_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `pay_order`
--

LOCK TABLES `pay_order` WRITE;
/*!40000 ALTER TABLE `pay_order` DISABLE KEYS */;
/*!40000 ALTER TABLE `pay_order` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `pay_transfer`
--

DROP TABLE IF EXISTS `pay_transfer`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pay_transfer` (
  `transfer_id` varchar(255) NOT NULL,
  `amount` decimal(38,2) DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `entry_account` varchar(255) DEFAULT NULL,
  `entry_name` varchar(255) DEFAULT NULL,
  `finish_time` datetime(6) DEFAULT NULL,
  `shop_name` varchar(255) DEFAULT NULL,
  `shop_num` varchar(255) DEFAULT NULL,
  `state` int DEFAULT NULL,
  PRIMARY KEY (`transfer_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `pay_transfer`
--

LOCK TABLES `pay_transfer` WRITE;
/*!40000 ALTER TABLE `pay_transfer` DISABLE KEYS */;
/*!40000 ALTER TABLE `pay_transfer` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `pay_way`
--

DROP TABLE IF EXISTS `pay_way`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pay_way` (
  `way_code` varchar(255) NOT NULL,
  `name` varchar(255) DEFAULT NULL,
  `rate` varchar(255) DEFAULT NULL,
  `remark` varchar(255) DEFAULT NULL,
  `state` int DEFAULT NULL,
  `way_type` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`way_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `pay_way`
--

LOCK TABLES `pay_way` WRITE;
/*!40000 ALTER TABLE `pay_way` DISABLE KEYS */;
INSERT INTO `pay_way` VALUES ('ALIPAY','支付宝支付','0.3',NULL,1,'跳转支付'),('UNIONPAY','银联支付','0.15',NULL,0,'跳转支付'),('WECHAT','微信支付','0.2',NULL,1,'跳转支付');
/*!40000 ALTER TABLE `pay_way` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `pms_category`
--

DROP TABLE IF EXISTS `pms_category`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pms_category` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(255) DEFAULT NULL,
  `parent_id` bigint DEFAULT NULL,
  `sort` int DEFAULT NULL,
  `state` int DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=15 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `pms_category`
--

LOCK TABLES `pms_category` WRITE;
/*!40000 ALTER TABLE `pms_category` DISABLE KEYS */;
INSERT INTO `pms_category` VALUES (1,'数码电器',0,1,1),(2,'手机通讯',1,1,1),(3,'电脑办公',1,2,1),(4,'影音设备',1,3,1),(5,'服饰鞋包',0,2,1),(6,'男装',5,1,1),(7,'女装',5,2,1),(8,'箱包配饰',5,3,1),(9,'美妆个护',0,3,1),(10,'护肤',9,1,1),(11,'彩妆',9,2,1),(12,'食品生鲜',0,4,1),(13,'水果',12,1,1),(14,'休闲零食',12,2,1);
/*!40000 ALTER TABLE `pms_category` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `pms_product`
--

DROP TABLE IF EXISTS `pms_product`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pms_product` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `category` varchar(255) DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `descr` varchar(255) DEFAULT NULL,
  `image` varchar(255) DEFAULT NULL,
  `name` varchar(255) DEFAULT NULL,
  `price` decimal(38,2) DEFAULT NULL,
  `sales` int DEFAULT NULL,
  `shop_num` varchar(255) DEFAULT NULL,
  `state` int DEFAULT NULL,
  `stock` int DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=101 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `pms_product`
--

LOCK TABLES `pms_product` WRITE;
/*!40000 ALTER TABLE `pms_product` DISABLE KEYS */;
INSERT INTO `pms_product` VALUES (1,'服装','2026-09-28 10:13:03.855792','90%白鸭绒填充，轻暖蓬松，三色可选。','/static/goods/real/real-001.jpg','轻量羽绒服',99.00,120,'M1649035678',1,58),(2,'服装','2026-09-28 10:13:03.866815','柔软亲肤，加厚保暖，多色可选。','/static/goods/real/real-002.jpg','针织羊毛围巾',59.00,210,'M1649035678',0,0),(3,'服装','2026-09-28 10:13:03.877332','弹力面料，显瘦版型。','/static/goods/real/real-003.jpg','窄管直筒牛仔裤',149.00,64,'M1649035678',1,44),(4,'服装','2026-09-28 10:13:03.888385','雪纺面料，收腰显瘦，度假必备。','/static/goods/real/real-004.jpg','法式碎花连衣裙',189.00,331,'M1649035678',1,66),(5,'服装','2026-09-28 10:13:03.898435','重磅纯棉，国风印花，男女同款。','/static/goods/real/real-005.jpg','国潮印花卫衣',129.00,275,'M1649035678',1,88),(6,'服装','2026-09-28 10:13:03.909580','UPF50+ 防晒，凉感透气，通勤百搭。','/static/goods/real/real-006.jpg','冰丝防晒衬衫',99.00,189,'M1649035678',1,120),(7,'服装','2026-09-28 10:13:03.921069','内里加绒，束脚版型，秋冬保暖。','/static/goods/real/real-007.jpg','加绒保暖卫裤',119.00,156,'M1649035678',1,75),(8,'服装','2026-09-28 10:13:03.934597','水洗做旧工艺，版型挺括，耐穿耐洗。','/static/goods/real/real-008.jpg','复古水洗夹克',219.00,98,'M1649035678',1,32),(9,'服装','2026-09-28 10:13:03.944659','速干透气面料，商务运动两相宜。','/static/goods/real/real-009.jpg','运动速干Polo衫',89.00,233,'M1649035678',1,140),(10,'服装','2026-09-28 10:13:03.955178','70%羊毛含量，廓形剪裁，气质通勤。','/static/goods/real/real-010.jpg','羊毛混纺大衣',399.00,87,'M1649035678',1,21),(11,'服装','2026-09-28 10:13:03.964991','轻盈飘逸，收腰显高，多色可选。','/static/goods/real/real-011.jpg','雪纺收腰长裙',159.00,176,'M1649035678',1,54),(12,'服装','2026-09-28 10:13:03.975088','耐磨帆布，立体口袋，街头风。','/static/goods/real/real-012.jpg','工装多口袋短裤',69.00,322,'M1649035678',1,160),(13,'服装','2026-09-28 10:13:03.985604','100%桑蚕丝，亲肤滑爽，精致镶边。','/static/goods/real/real-013.jpg','桑蚕丝睡衣套装',299.00,65,'M1649035678',1,18),(14,'服装','2026-09-28 10:13:03.997127','垂感面料，高腰提臀，遮肉显瘦。','/static/goods/real/real-014.jpg','高腰阔腿西裤',169.00,141,'M1649035678',1,62),(15,'服装','2026-09-28 10:13:04.006403','拼接撞色，罗纹收口，学院风。','/static/goods/real/real-015.jpg','撞色拼接棒球服',179.00,118,'M1649035678',1,47),(16,'服装','2026-09-28 10:13:04.016932','磨毛法兰绒，柔软厚实，情侣款。','/static/goods/real/real-016.jpg','法兰绒格子衬衫',109.00,264,'M1649035678',1,95),(17,'服装','2026-09-28 10:13:04.027476','裸感高弹，深蹲不透，蜜桃提臀。','/static/goods/real/real-017.jpg','弹力瑜伽健身裤',89.00,489,'M1649035678',1,175),(18,'服装','2026-09-28 10:13:04.038529','A类纯棉，卡通印花，保暖舒适。','/static/goods/real/real-018.jpg','儿童卡通连帽卫衣',79.00,208,'M1649035678',1,130),(19,'服装','2026-09-28 10:13:04.050061','防泼水面料，腰带收腰，气场全开。','/static/goods/real/real-019.jpg','中长款商务风衣',289.00,73,'M1649035678',1,26),(20,'服装','2026-09-28 10:13:04.061051','精梳棉，罗纹弹力，内搭神器。','/static/goods/real/real-020.jpg','精梳罗纹打底衫',59.00,397,'M1649035678',1,210),(21,'家电','2026-09-28 10:13:04.070086','5L大容量，无油低脂，一机多用。','/static/goods/real/real-021.jpg','家用空气炸锅',199.00,86,'M1649035675',1,23),(22,'家电','2026-09-28 10:13:04.080600','免滤免洗，细腻无渣，12小时预约。','/static/goods/real/real-022.jpg','破壁豆浆机',329.00,75,'M1649035675',1,15),(23,'家电','2026-09-28 10:13:04.091628','CADR值600，除醛除菌，APP互联。','/static/goods/real/real-023.jpg','智能空气净化器',899.00,31,'M1649035675',1,6),(24,'家电','2026-09-28 10:13:04.100518','一级能效，风冷无霜，干湿分储。','/static/goods/real/real-024.jpg','变频节能电冰箱',2199.00,45,'M1649035675',1,12),(25,'家电','2026-09-28 10:13:04.110025','10公斤大容量，除菌洗，高温自清洁。','/static/goods/real/real-025.jpg','全自动滚筒洗衣机',1599.00,62,'M1649035675',1,18),(26,'家电','2026-09-28 10:13:04.122575','无叶设计，12档风速，遥控定时。','/static/goods/real/real-026.jpg','静音塔式电风扇',179.00,158,'M1649035675',1,64),(27,'家电','2026-09-28 10:13:04.133126','5L大水箱，上加水，静音恒湿。','/static/goods/real/real-027.jpg','超声波加湿器',129.00,214,'M1649035675',1,88),(28,'家电','2026-09-28 10:13:04.142650','20秒出蒸汽，平挂两烫，便携收纳。','/static/goods/real/real-028.jpg','手持蒸汽挂烫机',159.00,183,'M1649035675',1,70),(29,'家电','2026-09-28 10:13:04.154540','激光导航，自动集尘，扫拖一体。','/static/goods/real/real-029.jpg','智能扫地机器人',1299.00,167,'M1649035675',1,25),(30,'家电','2026-09-28 10:13:04.164605','35L上下独立控温，热风循环。','/static/goods/real/real-030.jpg','双仓多功能电烤箱',299.00,92,'M1649035675',1,36),(31,'家电','2026-09-28 10:13:04.173395','3秒即热，6段控温，泡奶泡茶更方便。','/static/goods/real/real-031.jpg','即热式电热水壶',99.00,276,'M1649035675',1,110),(32,'家电','2026-09-28 10:13:04.181925','13片油汀，取暖不干燥，带烘衣架。','/static/goods/real/real-032.jpg','油汀取暖器',259.00,121,'M1649035675',1,40),(33,'家电','2026-09-28 10:13:04.190462','负离子护发，恒温热风，折叠便携。','/static/goods/real/real-033.jpg','负离子电吹风',139.00,305,'M1649035675',1,96),(34,'家电','2026-09-28 10:13:04.199706','2200W爆炒火力，九档控温，送炒锅。','/static/goods/real/real-034.jpg','触屏电磁炉',169.00,149,'M1649035675',1,82),(35,'家电','2026-09-28 10:13:04.209583','免安装，高温除菌洗，2-4人份。','/static/goods/real/real-035.jpg','迷你台式洗碗机',899.00,58,'M1649035675',1,20),(36,'家电','2026-09-28 10:13:04.217100','紫外线+热风除螨，尘杯可视。','/static/goods/real/real-036.jpg','除螨仪吸尘器',349.00,176,'M1649035675',1,48),(37,'家电','2026-09-28 10:13:04.225659','九档定时，断电保护，双控双温。','/static/goods/real/real-037.jpg','恒温电热毯',119.00,132,'M1649035675',1,66),(38,'家电','2026-09-28 10:13:04.233196','水冷降温，遥控摇头，带冰晶盒。','/static/goods/real/real-038.jpg','壁挂式冷风扇',229.00,87,'M1649035675',1,34),(39,'家电','2026-09-28 10:13:04.243274','语音播报，柴火饭口感，24h预约。','/static/goods/real/real-039.jpg','智能语音电饭煲',249.00,143,'M1649035675',1,52),(40,'家电','2026-09-28 10:13:04.251802','双面加热，悬浮烤盘，煎烤烙一机搞定。','/static/goods/real/real-040.jpg','不锈钢电饼铛',129.00,168,'M1649035675',1,74),(41,'食品','2026-09-28 10:13:04.261983','每日坚果混合装，新鲜锁存小包装。','/static/goods/real/real-041.jpg','原味坚果礼盒',29.90,350,'M1649035675',1,150),(42,'食品','2026-09-28 10:13:04.269809','澳洲进口燕麦，高膳食纤维。','/static/goods/real/real-042.jpg','即食燕麦片',35.00,260,'M1649035675',1,180),(43,'食品','2026-09-28 10:13:04.278043','黄油烘焙，独立包装，下午茶首选。','/static/goods/real/real-043.jpg','手工小熊曲奇',19.90,620,'M1649035675',1,300),(44,'食品','2026-09-28 10:13:04.287917','无添加蔗糖，全麦含量50%，代餐合适。','/static/goods/real/real-044.jpg','低糖全麦面包',16.90,480,'M1649035675',1,260),(45,'食品','2026-09-28 10:13:04.295959','高钙配方，独立小袋，冲泡易吸收。','/static/goods/real/real-045.jpg','高钙学生奶粉',89.00,175,'M1649035675',1,90),(46,'食品','2026-09-28 10:13:04.306493','肉厚核小，自然挂干，甘甜糯香。','/static/goods/real/real-046.jpg','新疆灰枣',25.90,540,'M1649035675',1,320),(47,'食品','2026-09-28 10:13:04.315600','高蛋白低脂肪，开袋即食，健身伴侣。','/static/goods/real/real-047.jpg','即食鸡胸肉',39.90,730,'M1649035675',1,220),(48,'食品','2026-09-28 10:13:04.327108','内蒙古黄牛肉，炭烤香辣，有嚼劲。','/static/goods/real/real-048.jpg','炭烤牛肉干',49.90,410,'M1649035675',1,130),(49,'食品','2026-09-28 10:13:04.338628','-35℃冻干锁鲜，酥脆酸甜。','/static/goods/real/real-049.jpg','冻干草莓脆',22.90,366,'M1649035675',1,280),(50,'食品','2026-09-28 10:13:04.348153','海盐风味，酥脆不腻，办公室零食。','/static/goods/real/real-050.jpg','海盐苏打饼干',12.90,820,'M1649035675',1,400),(51,'食品','2026-09-28 10:13:04.359668','四层威化，芝士夹心，一口爆浆。','/static/goods/real/real-051.jpg','芝士夹心威化',15.90,590,'M1649035675',1,350),(52,'食品','2026-09-28 10:13:04.367513','八种杂粮科学配比，五谷丰登好彩头。','/static/goods/real/real-052.jpg','五谷杂粮礼盒',68.00,152,'M1649035675',1,85),(53,'食品','2026-09-28 10:13:04.376562','秘制卤汁，麻辣过瘾，独立锁鲜装。','/static/goods/real/real-053.jpg','麻辣卤味鸭脖',32.90,610,'M1649035675',1,190),(54,'食品','2026-09-28 10:13:04.385653','真实果肉含量≥30%，温水冲饮。','/static/goods/real/real-054.jpg','蜂蜜柚子茶酱',29.90,288,'M1649035675',1,160),(55,'食品','2026-09-28 10:13:04.393598','酸笋爽脆，汤浓味足，正宗柳州味。','/static/goods/real/real-055.jpg','柳州螺蛳粉',13.90,1560,'M1649035675',1,500),(56,'食品','2026-09-28 10:13:04.401640','酸奶块+冻干水果，干吃泡酸奶都好。','/static/goods/real/real-056.jpg','水果燕麦脆',26.90,445,'M1649035675',1,240),(57,'食品','2026-09-28 10:13:04.410143','手工小圆子，桂花酒酿，甜而不腻。','/static/goods/real/real-057.jpg','桂花酒酿小圆子',18.90,330,'M1649035675',1,210),(58,'食品','2026-09-28 10:13:04.418160','低温发酵，手撕口感，麦香浓郁。','/static/goods/real/real-058.jpg','手撕全麦吐司',21.90,512,'M1649035675',1,270),(59,'食品','2026-09-28 10:13:04.428193','深海小鱼，香辣入味，下酒下饭。','/static/goods/real/real-059.jpg','香辣小鱼干',19.90,388,'M1649035675',1,230),(60,'食品','2026-09-28 10:13:04.438724','红枣打浆，整颗核桃，软糯香甜。','/static/goods/real/real-060.jpg','红枣核桃糕',28.90,296,'M1649035675',1,170),(61,'数码','2026-09-28 10:13:04.444772','心率/血氧监测，50米防水，长续航。','/static/goods/real/real-061.jpg','智能运动手表',599.00,42,'M1649035675',1,8),(62,'数码','2026-09-28 10:13:04.453274','主动降噪，蓝牙5.3，续航40小时。','/static/goods/real/real-062.jpg','蓝牙降噪耳机',459.00,98,'M1649035675',1,35),(63,'数码','2026-09-28 10:13:04.461859','6.7英寸120Hz高刷屏，5000mAh电池。','/static/goods/real/real-063.jpg','5G全面屏手机',1999.00,210,'M1649035675',1,15),(64,'数码','2026-09-28 10:13:04.468368','2.5K护眼屏，四扬声器，网课利器。','/static/goods/real/real-064.jpg','11英寸平板电脑',1299.00,133,'M1649035675',1,22),(65,'数码','2026-09-28 10:13:04.479567','14英寸2.8K屏，标压处理器，1.4kg。','/static/goods/real/real-065.jpg','轻薄办公笔记本',4599.00,67,'M1649035675',1,10),(66,'数码','2026-09-28 10:13:04.487853','360°环绕音效，IPX7防水，续航24h。','/static/goods/real/real-066.jpg','便携蓝牙音箱',199.00,285,'M1649035675',1,60),(67,'数码','2026-09-28 10:13:04.495876','22.5W快充，数显电量，可上飞机。','/static/goods/real/real-067.jpg','20000毫安移动电源',129.00,470,'M1649035675',1,95),(68,'数码','2026-09-28 10:13:04.503389','血氧/心率/睡眠监测，14天续航。','/static/goods/real/real-068.jpg','智能手环血氧版',169.00,356,'M1649035675',1,80),(69,'数码','2026-09-28 10:13:04.511912','4K夜视，停车监控，语音声控。','/static/goods/real/real-069.jpg','4K行车记录仪',299.00,244,'M1649035675',1,55),(70,'数码','2026-09-28 10:13:04.520439','热插拔轴体，RGB背光，三模连接。','/static/goods/real/real-070.jpg','机械键盘87键',249.00,198,'M1649035675',1,42),(71,'数码','2026-09-28 10:13:04.527440','15W快充，金属面板，iPhone/安卓通用。','/static/goods/real/real-071.jpg','无线充电底座',79.00,388,'M1649035675',1,130),(72,'数码','2026-09-28 10:13:04.536448','360°云台，红外夜视，移动侦测报警。','/static/goods/real/real-072.jpg','1080P智能摄像头',119.00,452,'M1649035675',1,105),(73,'数码','2026-09-28 10:13:04.543523','26000DPI，59g轻量化，宏编程。','/static/goods/real/real-073.jpg','电竞游戏鼠标',159.00,266,'M1649035675',1,70),(74,'数码','2026-09-28 10:13:04.549537','2K IPS，100Hz，低蓝光不闪屏。','/static/goods/real/real-074.jpg','27英寸显示器',899.00,88,'M1649035675',1,16),(75,'数码','2026-09-28 10:13:04.557664','八合一扩展，4K投屏，100W PD充电。','/static/goods/real/real-075.jpg','USB-C扩展坞',149.00,233,'M1649035675',1,88),(76,'数码','2026-09-28 10:13:04.564709','28项身体数据，APP曲线管理。','/static/goods/real/real-076.jpg','智能体脂秤',69.00,521,'M1649035675',1,150),(77,'数码','2026-09-28 10:13:04.571800','7.1声道环绕，降噪麦克风，RGB灯效。','/static/goods/real/real-077.jpg','头戴式游戏耳机',199.00,176,'M1649035675',1,48),(78,'数码','2026-09-28 10:13:04.580318','热敏无墨打印，错题/手账/照片。','/static/goods/real/real-078.jpg','口袋手机打印机',399.00,94,'M1649035675',1,30),(79,'数码','2026-09-28 10:13:04.588324','半导体指纹，临时密码，防猫眼开锁。','/static/goods/real/real-079.jpg','智能门锁指纹款',699.00,76,'M1649035675',1,14),(80,'数码','2026-09-28 10:13:04.595612','4K云台增稳，智能跟随，31分钟续航。','/static/goods/real/real-080.jpg','入门航拍无人机',999.00,118,'M1649035675',0,0),(81,'美饮','2026-09-28 10:13:04.606759','云南日晒阿拉比卡，10包装挂耳。','/static/goods/real/real-081.jpg','现磨挂耳咖啡',45.00,500,'M1649035675',1,200),(82,'美饮','2026-09-28 10:13:04.614905','现制轻乳茶，0植脂末。','/static/goods/real/real-082.jpg','低糖鲜奶茶',15.90,800,'M1649035675',1,500),(83,'美饮','2026-09-28 10:13:04.623206','冷萃咖啡液，一条兑一杯，生椰香浓。','/static/goods/real/real-083.jpg','生椰拿铁咖啡液',39.90,720,'M1649035675',1,260),(84,'美饮','2026-09-28 10:13:04.630225','12小时低温冷萃，冰美式灵魂。','/static/goods/real/real-084.jpg','冷萃咖啡浓缩液',49.00,466,'M1649035675',1,180),(85,'美饮','2026-09-28 10:13:04.640949','窨制桂花+高山乌龙，冷泡热泡皆宜。','/static/goods/real/real-085.jpg','桂花乌龙茶包',29.00,588,'M1649035675',1,320),(86,'美饮','2026-09-28 10:13:04.649978','七窨茉莉，茶汤清亮，花香入水。','/static/goods/real/real-086.jpg','茉莉花茶',25.00,470,'M1649035675',1,280),(87,'美饮','2026-09-28 10:13:04.660368','100%鲜榨非浓缩，不加水不加糖。','/static/goods/real/real-087.jpg','NFC鲜榨橙汁',19.90,640,'M1649035675',1,240),(88,'美饮','2026-09-28 10:13:04.669419','0糖0卡0脂肪，气泡绵密。','/static/goods/real/real-088.jpg','无糖气泡苏打水',12.90,950,'M1649035675',1,420),(89,'美饮','2026-09-28 10:13:04.679442','台农芒果榨取，果肉含量≥40%。','/static/goods/real/real-089.jpg','芒果汁饮料',15.00,540,'M1649035675',1,300),(90,'美饮','2026-09-28 10:13:04.688386','加纳可可粉，丝绒口感，热冲冷调均可。','/static/goods/real/real-090.jpg','经典原味可可',33.00,322,'M1649035675',1,190),(91,'美饮','2026-09-28 10:13:04.698123','0乳糖0添加蔗糖，咖啡伴侣好搭子。','/static/goods/real/real-091.jpg','燕麦植物奶',36.00,388,'M1649035675',1,210),(92,'美饮','2026-09-28 10:13:04.706880','真柠檬+洋槐蜂蜜，酸甜清爽。','/static/goods/real/real-092.jpg','柠檬蜂蜜茶',17.90,455,'M1649035675',1,260),(93,'美饮','2026-09-28 10:13:04.718344','意式拼配，油脂丰厚，提神醒脑。','/static/goods/real/real-093.jpg','浓缩黑咖啡',42.00,612,'M1649035675',1,230),(94,'美饮','2026-09-28 10:13:04.726337','蜜香明显，喉韵甘甜，奶茶店同款。','/static/goods/real/real-094.jpg','蜜香红茶',27.00,344,'M1649035675',1,200),(95,'美饮','2026-09-28 10:13:04.735019','真实芋头熬煮，颗颗芋圆，料足满杯。','/static/goods/real/real-095.jpg','手作芋泥奶茶',18.90,705,'M1649035675',1,310),(96,'美饮','2026-09-28 10:13:04.743542','新会青柑+云南熟普，一颗一泡。','/static/goods/real/real-096.jpg','青柑普洱茶',59.00,258,'M1649035675',1,120),(97,'美饮','2026-09-28 10:13:04.752099','平阴重瓣玫瑰，花冠完整，香气馥郁。','/static/goods/real/real-097.jpg','玫瑰花茶',23.00,377,'M1649035675',1,240),(98,'美饮','2026-09-28 10:13:04.763194','海南椰肉生榨，椰香浓郁，早餐搭档。','/static/goods/real/real-098.jpg','生榨椰汁',16.90,498,'M1649035675',1,280),(99,'美饮','2026-09-28 10:13:04.773138','乌梅山楂陈皮熬制，1:8稀释饮用。','/static/goods/real/real-099.jpg','酸梅汤浓缩汁',21.90,366,'M1649035675',1,220),(100,'美饮','2026-09-28 10:13:04.781660','宇治抹茶风味，抹茶+乳粉二合一。','/static/goods/real/real-100.jpg','抹茶拿铁粉',35.00,298,'M1649035675',1,180);
/*!40000 ALTER TABLE `pms_product` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `refund_order`
--

DROP TABLE IF EXISTS `refund_order`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `refund_order` (
  `refund_order_id` varchar(255) NOT NULL,
  `apply_time` datetime(6) DEFAULT NULL,
  `biz_pre_state` int DEFAULT NULL,
  `pay_order_id` varchar(255) DEFAULT NULL,
  `refund_money` decimal(38,2) DEFAULT NULL,
  `refund_reason` varchar(255) DEFAULT NULL,
  `refund_state` int DEFAULT NULL,
  `refund_time` datetime(6) DEFAULT NULL,
  `shop_num` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`refund_order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `refund_order`
--

LOCK TABLES `refund_order` WRITE;
/*!40000 ALTER TABLE `refund_order` DISABLE KEYS */;
/*!40000 ALTER TABLE `refund_order` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_isv`
--

DROP TABLE IF EXISTS `sys_isv`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_isv` (
  `isv_num` varchar(255) NOT NULL,
  `app_id` varchar(255) DEFAULT NULL,
  `contact` varchar(255) DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `isv_name` varchar(255) DEFAULT NULL,
  `link_phone` varchar(255) DEFAULT NULL,
  `state` int DEFAULT NULL,
  PRIMARY KEY (`isv_num`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_isv`
--

LOCK TABLES `sys_isv` WRITE;
/*!40000 ALTER TABLE `sys_isv` DISABLE KEYS */;
INSERT INTO `sys_isv` VALUES ('V202600010001','isv-app-8866001','钱经理','2026-06-30 10:13:04.810090','杭州联汇科技服务商','0571-88001111',1),('V202600010002','isv-app-8866002','林经理','2026-06-30 10:13:04.822147','广东云商支付服务商','020-83002222',1),('V202600010003','isv-app-8866003','周经理','2026-06-30 10:13:04.835202','四川天府收单服务商','028-85003333',0);
/*!40000 ALTER TABLE `sys_isv` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_log`
--

DROP TABLE IF EXISTS `sys_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `action` varchar(255) DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `ip` varchar(255) DEFAULT NULL,
  `module` varchar(255) DEFAULT NULL,
  `result` varchar(255) DEFAULT NULL,
  `username` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_log`
--

LOCK TABLES `sys_log` WRITE;
/*!40000 ALTER TABLE `sys_log` DISABLE KEYS */;
/*!40000 ALTER TABLE `sys_log` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_user`
--

DROP TABLE IF EXISTS `sys_user`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_user` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `avatar` varchar(255) DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `nickname` varchar(255) DEFAULT NULL,
  `password` varchar(255) DEFAULT NULL,
  `real_name` varchar(255) DEFAULT NULL,
  `role` varchar(255) DEFAULT NULL,
  `state` int DEFAULT NULL,
  `username` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK51bvuyvihefoh4kp5syh2jpi4` (`username`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_user`
--

LOCK TABLES `sys_user` WRITE;
/*!40000 ALTER TABLE `sys_user` DISABLE KEYS */;
INSERT INTO `sys_user` VALUES (1,NULL,'2026-10-28 10:13:03.207546',NULL,'admin123','平台超级管理员','ROLE_SUPER_ADMIN',1,'admin'),(2,NULL,'2026-10-28 10:13:03.326624',NULL,'123456','运营专员-小李','ROLE_OPS',1,'ops01'),(3,NULL,'2026-10-28 10:13:03.338158',NULL,'123456','审核专员-小王','ROLE_AUDIT',1,'audit01'),(4,NULL,'2026-10-28 10:13:03.350198',NULL,'123456','普通用户','ROLE_USER',1,'user');
/*!40000 ALTER TABLE `sys_user` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-10-28 10:14:57
