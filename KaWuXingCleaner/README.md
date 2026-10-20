# 悟空清理 · KaWuXing Cleaner

安卓手机垃圾清理 App，功能参照猎豹清理大师：**一键扫描 → 分类勾选 → 批量清理 → 存储分析 → 内存加速**。
Kotlin + Jetpack Compose 原生实现，targetSdk 34（Android 14），minSdk 24，**不联网、不上传任何数据**。

---

## 一、和「猎豹清理大师」的对应关系

| 猎豹的功能 | 本项目 | 说明 |
| --- | --- | --- |
| 一键扫描垃圾 | ✅ 一个按钮扫全盘 | 8 类垃圾并行统计，实时显示正在扫描的路径 |
| 垃圾分类清理 | ✅ 8 类可勾选 | 应用缓存 / 缩略图 / 日志临时文件 / 安装包残留 / 广告缓存 / 卸载残留 / 空文件夹 / 大文件 |
| 清理结果动画 | ✅ 清理完成页 | 显示实际释放量、删除项数、被系统拒绝的项数 |
| 深度清理（微信/QQ 专项） | ⚠️ 部分 | 通过「应用缓存」统计 + 卸载残留识别覆盖，不做聊天记录解析 |
| 存储分析 | ✅ 按类型 + 目录 | 视频/图片/音频/文档/安装包/压缩包/其他，Top 8 目录、Top 10 大文件 |
| 手机加速 | ⚠️ 有限 | 只做「读真实内存 + 清本应用缓存」，Android 8+ 不允许第三方强杀进程，不伪造加速数字 |
| 应用管理/卸载 | ⚠️ 跳系统 | 点应用条目跳到系统「应用详情」，由系统执行清除/卸载 |
| 白名单 | ✅ | 路径关键字匹配，扫描与删除双重跳过 |

**能力边界是本项目的设计前提**：Android 11 之后，第三方应用既不能读别的应用私有目录（`/data/data/<pkg>`），也不能删别的应用缓存。所以这里不吹「一键清 5GB」，只统计能统计的、只删有权删的。

---

## 二、功能明细

### 1. 一键扫描
- BFS 单次遍历 `/storage/emulated/0`，遇到缓存类目录整体记账后不再下钻，避免重复统计；
- 硬上限：25 万文件 / 90 秒，超出即截断并在界面标注「抽样扫描」，不做无上限遍历卡死手机；
- 扫描过程中实时显示当前目录路径。

### 2. 八类垃圾
| 类别 | 判定规则 | 默认勾选 |
| --- | --- | --- |
| 应用缓存 | 目录名命中 `cache / .cache / webviewcache / image_cache / okhttp / glide_cache` 等 | ✅ |
| 缩略图缓存 | 目录名命中 `.thumbnails / thumbnails` | ✅ |
| 日志与临时文件 | 扩展名 `log / tmp / temp / bak / dump / trace`，或文件名以 `~` `tmp_` 开头 | ✅ |
| 安装包残留 | 扩展名 `apk / apks / xapk / aab` | ✅ |
| 广告缓存 | 目录名命中 `adcache / ad_cache / .ads / advertcache` | ✅ |
| 卸载残留 | `/Android/data/<pkg>`、`/Android/obb/<pkg>` 里包名对应的应用已卸载 | ✅ |
| 空文件夹 | 不含任何子项的目录 | ✅ |
| 大文件 | ≥ 50MB 的任意文件 | ❌ **必须手动勾选** |

### 3. 清理执行
- 只删共享存储内的路径，跨出 `/storage/emulated/0` 一律跳过；
- 白名单二次校验（扫描时跳过一次，删除前再校验一次）；
- 按**实际删掉的字节数**记账，不按扫描预估值虚报；
- 清理后补删「因文件被删才变空」的目录。

### 4. 存储分析
按扩展名分七类统计占用，列出占用最大的 8 个一级目录、Top 10 大文件（含修改时间）。

### 5. 内存加速
直接读 `/proc/meminfo`（MemTotal / MemAvailable / Cached / SwapTotal / SwapFree），显示真实占用。
「一键加速」只清理本应用 cacheDir + externalCacheDirs 并刷新读数 —— 这是普通应用唯一能做的事，不做假动画。

### 6. 各应用缓存统计
用 `StorageStatsManager.queryStatsForPackage` 逐包读取缓存体积，按大小倒序，点条目跳系统「应用详情」清除。需要「使用情况访问」权限。

### 7. 白名单 / 清理记录
白名单按路径关键字（不区分大小写）匹配；本地记录累计释放量、上次清理时间、累计扫描次数。

---

## 三、权限说明

| 权限 | 用途 | 不授予的后果 |
| --- | --- | --- |
| `MANAGE_EXTERNAL_STORAGE`（所有文件访问） | 全盘扫描与删除共享存储里的垃圾 | 只能看到本应用自己的目录，扫描结果为空 |
| `PACKAGE_USAGE_STATS`（使用情况访问） | 统计各应用缓存体积 | 「各应用缓存」列表为空，其余功能不受影响 |
| `QUERY_ALL_PACKAGES` | 读取应用列表，判断卸载残留 | 无法识别卸载残留目录 |
| `READ/WRITE_EXTERNAL_STORAGE`（≤ API 32/29） | 老系统上的存储读写 | 老系统无法扫描 |

两项核心权限都在 App 内提供跳转入口（首页红卡、「我的」页），不需要去设置里翻。

---

## 四、构建与上传

### 方式 A：GitHub Actions 自动构建（推荐，本机不需要 Android SDK）

仓库已带 `.github/workflows/android.yml`，推上去自动出 APK：

1. 浏览器打开 <https://github.com/new>，仓库名填 `KaWuXing`（若已存在则跳过）；
2. 双击 `tools\push_to_github.bat`，一路回车 —— 会自动探测本机代理、用 Git Credential Manager 弹浏览器登录；
3. 5–15 分钟后到 Actions 页，底部 **Artifacts → KaWuXingCleaner-debug-apk** 下载，解压即得 `app-debug.apk`。

**固定下载地址**（每次推送自动覆盖，不用再翻 Artifacts）：

```
https://github.com/chengxiaofeng-prog/KaWuXing/releases/download/latest/app-debug.apk
```

### 方式 B：命令行 + PAT

```bash
export GITHUB_TOKEN=ghp_xxxxxxxxxxxxxxxx     # 勾选 repo 权限
bash push-to-github.sh KaWuXing             # 仓库已存在则自动进子目录，避免覆盖
```

脚本会：探测代理 → 校验 token → 仓库不存在则创建 → 判断仓库是否为空 → 推送 `main` → 打印 Actions 地址。
推送完成后会把 token 从 `.git/config` 里抹掉，不留明文。

### 方式 C：本地 Android Studio
用 Android Studio（Ladybug 及以上）直接 Open 本目录，等 Gradle 同步完成后 `Run 'app'`。
本工程未携带 `gradle-wrapper.jar`，首次同步请让 Studio 用自带的 Gradle 8.7，或在 Actions 里下载 `gradle-wrapper` artifact 解压到根目录。

---

## 五、目录结构

```
app/src/main/java/com/kawuxing/cleaner/
├── CleanerApp.kt               Application
├── MainActivity.kt             4 个底部 Tab（清理/存储/加速/我的）
├── core/
│   ├── Fmt.kt                  体积、时间、百分比格式化
│   └── Perm.kt                 所有文件访问 / 使用情况访问的检查与跳转
├── data/Prefs.kt               白名单、累计释放量、清理记录
├── model/Junk.kt               类别、条目、扫描结果、内存状态等数据模型
├── scan/
│   ├── JunkScanner.kt          BFS 全盘扫描（核心）
│   ├── AppCacheScanner.kt      StorageStatsManager 逐包缓存统计
│   └── MemoryReader.kt         /proc/meminfo 解析
├── clean/JunkCleaner.kt        删除执行 + 白名单校验 + 实际释放量记账
├── vm/CleanerViewModel.kt      扫描/清理/权限/缓存状态
└── ui/
    ├── Comp.kt                 圆环仪表盘、分类行、类型条等组件
    ├── Bars.kt                 进度条（Canvas 自绘，不依赖版本 API）
    ├── HomeScreen.kt           首页：仪表盘 + 一键扫描 + 分类列表
    ├── StorageScreen.kt        存储分析
    ├── BoostScreen.kt          内存加速 + 应用缓存
    └── MineScreen.kt           权限、白名单、记录、关于
```

---

## 六、安全边界（务必读）

- **不会**删除照片、文档、音视频等个人文件：它们只会出现在「大文件」里，且默认不勾选；
- **不会**清理其他应用的私有数据（`/data/data/<pkg>`），那需要 root，本应用不 root；
- **不会**上传任何文件列表、文件内容或设备信息，APK 无网络权限；
- 「应用缓存」删的是可再生的东西，代价是首次打开某些 App 会重新加载图片；
- 如果你把重要文件放进某个 App 的 cache 目录，它同样会被当作缓存清掉 —— 所以白名单要么不用，要么用对。

---

## 七、版本

| 版本 | 内容 |
| --- | --- |
| 1.0.0 | 首版：8 类垃圾扫描清理、存储分析、内存加速、应用缓存统计、白名单 |

Apache-2.0
