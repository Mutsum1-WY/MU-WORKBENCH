# MU WORKBENCH

> 一个**完全离线**的个人工作台安卓 App：今日待办、可视化时钟、番茄钟、项目进度、日历、校内资讯、课程性价比与课外加分。
> 单文件 HTML（HTML + CSS + JS 全内联，零外部依赖），用 Capacitor 套一层安卓外壳打成 APK。

<p align="center">
  <img src="screenshots/today.png" width="240" alt="今日页">
  <img src="screenshots/calendar.png" width="240" alt="日历页">
  <img src="screenshots/settings.png" width="240" alt="设置页">
</p>

## 这是什么

- **只做个人待办与提醒**。查成绩、选课、交作业请用学校更全的云邮 / 教务系统；这里只把「今天要做什么」摊平。
- **数据只在本机**。默认不联网、不上传、没有后端、没有账号体系；所有数据存在 WebView 的 `localStorage`（键名 `mu_workbench_v1`），换设备用「导出备份 / 导入备份」搬家。
- **只有两处会联网，且都要你手动点**：更新节假日数据、同步云邮待办。
- 云邮 / 信息门户是**附加功能**：要输入学工号密码，凭据只存本机、可随时清除，且只用于登录与读取，不发送任何提交或写入请求。

## 功能

| 模块 | 说明 |
|---|---|
| **今日** | 今日待办列表；逾期任务自动顺延并在原位划掉；顶部提醒横幅；右下角 `＋ 新建待办` |
| **待办类型** | 单次 / 每日 / 工作日（跳法定节假日）/ 休息日 / 自定义星期 / 每 N 天；可逐条设优先级、分类、提醒时间、子步骤 |
| **提醒** | App 版把每条待办注册成**系统闹钟**（不是页面里的定时器），可设汇总兜底提醒、提示音三档音量、精确闹钟权限 |
| **时钟** | 表盘 + 数字时钟 + 今日进度；番茄钟（可关联待办，云邮待办也能关联）；秒表 / 倒计时 |
| **项目** | 可自建的进度卡片，每张卡下挂若干技能点，各自带进度条 |
| **日历** | 月历 + 年月快选 + 节假日 / 调休标记 + 长按加待办 + 给一段日期标「放假 / 调休上班」 |
| **资讯** | 校内通知 / 学术讲座（信息门户，只读），支持搜索、按时间与部门筛、打开看正文与附件 |
| **课程** | 培养方案课程性价比（按学时 / 学分 / 考核方式排序），课外加分记账与计分器、114 项竞赛清单 |
| **后台检查** | 约每 15 分钟用**已登录的令牌**复查云邮新作业与校内新通知，发现新的就发系统通知——不用一直开着 App |
| **设置** | 提醒、主题色、番茄钟时长、数据备份、云邮凭据、后台常驻 |

## 通知能有多快

这个 App 没有服务器、也不上传数据，所以「立刻知道」只能靠手机自己去问学校服务器。分三种情况：

| 场景 | 最快多久 | 靠什么 |
|---|---|---|
| 待办**到点**提醒 | **准点（秒级）** | 每条待办注册成系统**精确闹钟**，JS 被系统冻结也照响 |
| 云邮**新作业/新测验** | 约 **15 分钟**（省电模式下可能更久） | WorkManager 定期任务 + `refresh_token` 换新令牌后拉列表 |
| 校内**新通知/讲座** | 约 **15 分钟** | 复用资讯页的会话 cookie 拉列表 |

**为什么不是秒级**：Android 对后台任务的硬限制就是 WorkManager 最小 15 分钟一次；真要秒级只能靠服务器推送，那要求把凭据放到服务器上，与「数据只在本机」冲突，所以没做。

**为什么不会把账号搞没**：后台检查**绝不重新登录**。它只用 App 里登录成功后拿到的 `access_token`，过期了用 `refresh_token` 续（不走密码、不计登录失败次数）；门户那边只复用会话 cookie，过期就提示你打开 App 同步一次。

**怎么开**：设置 →「后台检查新内容」→ 打开总开关（默认关）。里面还能单独关掉云邮 / 资讯，或限制「只在 Wi-Fi 下检查」。首次开启建议先点一次「立即检查一次」，确认通知能正常弹出（Android 13+ 还要给通知权限）。

## 界面

- **六套主题色**：渐变蓝（默认）、青碧、紫罗兰、薄荷、樱粉、落日。整套配色挂在 `<html data-accent>` 上，一个属性同时改掉页面底色、卡片、进度条、按钮、选中态与系统状态栏色。
- **深色模式**：跟随系统（`prefers-color-scheme`），六套主题在深色下都可用。
- **手机优先**：按 390px 设计；宽屏（≥900px）时底部 Tab 自动变成左侧固定导航。

<p align="center">
  <img src="screenshots/clock.png" width="240" alt="时钟页">
  <img src="screenshots/today-dark.png" width="240" alt="深色模式">
</p>

## 安装

从 [**Releases**](../../releases/latest) 下载 `MU-WORKBENCH-v1.1-debug.apk` 直接安装：

- 系统要求 **Android 6.0+**（`minSdk 23`，`targetSdk 35`）
- 未签名的 **debug 包**，安装时需要允许「安装未知来源应用」
- APK 大小约 5.5 MB，sha256 `e28dadd537f9032f577056a6cf3bdac077d950248b62d5f1de9ad79ecaf912bd`
- 仓库里不直接放 APK（二进制会和源码不同步），Release 上的包对应 tag `v1.1`，打包用的 `index.html` 与该 tag 下的源码逐字节一致

不想装 App 也可以直接用浏览器打开 `MU-WORKBENCH/index.html`（除了「系统级闹钟」和「云邮」外功能都在）。

想自己出包见下一节。

## 自己构建

需要 **Node.js 20+**、**JDK 17** 与 Android SDK（命令行或 Android Studio 均可）。

```bash
cd MU-WORKBENCH-ANDROID

npm install
npm run android:apk     # = sync-web → cap sync android → gradlew assembleDebug
# 产物：android/app/build/outputs/apk/debug/app-debug.apk
```

也可以分步执行：

```bash
npm run sync-web        # 网页版 index.html → www/index.html（带 sha256 校验）
npx cap sync android    # 再把 www 同步进 android/ 原生工程
cd android && gradlew assembleDebug      # macOS/Linux 用 ./gradlew
```

Windows 上一条命令出包：运行 [`build-apk.bat`](MU-WORKBENCH-ANDROID/build-apk.bat)。
流程细节（图标怎么生成、包名与版本号在哪改、常见编译错误）见 [打包手册.md](MU-WORKBENCH-ANDROID/打包手册.md)。

### 关于「网页源码 → App」的同步

整个 App 只有一个源文件 `MU-WORKBENCH/index.html`，没有任何 npm 运行时依赖，也没有前端构建步骤。
`sync-www.cjs` 负责把它复制进 `www/` 并打印两边的 sha256，确保**装进 APK 的网页和源文件逐字节一致**——这是这个项目唯一容易出错的地方（改完忘了 sync 就会发现"改了没生效"）。

## 仓库结构

```
MU-WORKBENCH/index.html        ← 唯一源文件：HTML + CSS + JS 全部内联
MU-WORKBENCH-ANDROID/
  ├─ android/                  ← 安卓原生工程（Capacitor 外壳 + 前台服务 + 闹钟 + 后台检查）
  │    └─ app/src/main/java/com/mu/workbench/
  │         ├─ MainActivity / MuNative / MuKeepAlive    ← WebView 与 JS 桥
  │         ├─ KeepAliveService                          ← 前台常驻服务
  │         └─ MuBgCheck / BgCheck / BgCheckWorker       ← 后台检查（WorkManager + 原生 HTTP）
  ├─ www/                      ← 由 sync-www.cjs 生成，不入库
  ├─ sync-www.cjs              ← 网页 → www 同步（带哈希校验）
  ├─ build-apk.bat             ← 一键出包
  └─ 打包手册.md               ← 打包流程与踩坑记录
screenshots/                   ← README 用截图
```

> iOS 工程（`MU-WORKBENCH-ANDROID/ios`）与对应的 CI 工作流不在本仓库里；这套代码本身是跨平台的，需要 iOS 版可以基于同一份 `index.html` 补一个 Capacitor iOS 外壳。

## 一些实现上的选择

- **计时不靠定时器数秒**：番茄钟 / 倒计时记 `endsAt`，剩余时间由 `(endsAt - now)` 现算，所以切屏、锁屏、JS 被系统节流都不会走慢；再叠加前台服务与原生结束通知，才能做到「准时响」。
- **提醒走系统闹钟**：WebView 的 JS 定时器在后台会被安卓限制，所以每条待办都注册成系统闹钟，改时间/关开关时会重新排。
- **后台检查不重新登录**：后台任务只复用 App 登录成功后的 `access_token / refresh_token`（云邮）和会话 cookie（门户）。每 15 分钟重新走一次 CAS 登录几乎必然触发风控、验证码甚至锁号，这条路从一开始就没走。
- **云邮登录走 CAS**：学校账号在统一身份认证里，所以是「CAS 换票 → `grant_type=third`」，而不是平台自己的账号密码模式；`service` 必须是 `https://ucloud.bupt.edu.cn/uclass`，写成 `http` 会在换票时报错。
- **信息门户只支持 HTTP**：网络配置里只给 `my.bupt.edu.cn` 开明文例外，没有全局放开。
- **体积**：没有框架、没有图片资源，`index.html` 约 480 KB（含 152 门课程数据与 114 项竞赛清单）。
- **动效**：全部包在 `prefers-reduced-motion: reduce` 的兜底里；系统开了「减少动态效果」就静止。

## 已知限制

- 云邮 / 信息门户依赖学校接口，学校改版就可能失效；这两块在浏览器里不可用（需要原生网络能力）。
- **后台检查最快 15 分钟**（Android 对后台任务的硬限制），省电模式 / Doze 下可能更久；国产 ROM 若禁止自启或强杀进程，会直接失效——这类机型建议同时在系统设置里给 App 开「自启动」与「不限制耗电」。
- 后台检查依赖 App 里的登录态：云邮 `refresh_token` 失效、或门户 cookie 过期时，会提示你打开 App 同步一次。
- 多设备不同步：没有服务端，换设备请用备份文件搬运。
- 未签名、未上架，装到新手机需要重新授权未知来源。

## 数据来源与免责

课程数据来自学校公开的 2026 级培养方案与校发〔2021〕51 号文件；推免与加分细则以**学院当年发布的细则**为准，App 里的数值只作参考。

问题与建议：**482843907@qq.com**
