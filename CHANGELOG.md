# Changelog

## v7.0.0 Beta2 (2026-03-15)

### 版本信息
- 版本号从 `7.0.0 Beta1` 升级到 `7.0.0 Beta2`，`versionCode` 升级到 `114`。
- 本次为“个人页壁纸自定义补全 + 图片预览与保存增强 + 首页/搜索头部打磨 + 播放与离线后台控制修复”的综合更新版本。

### 个人页壁纸与背景装扮
- 个人页背景装扮新增手势调节能力，支持双指缩放、单指拖动，并分别保存手机端与平板端的 `scale / offsetX / offsetY`。
- 个人页头图底部补上更自然的渐变过渡，减少背景图与内容区之间的生硬断层。
- 背景装扮新增“恢复默认背景”能力，用户可以从自定义背景快速回退到默认样式。
- 官方壁纸选择链路修复为“列表用缩略图、详情/调整/保存用原图”，解决外面看正常、点进去变成小图的问题。
- 开屏欢迎图改为按图片与屏幕比例动态选择展示策略，比例差过大的图片不再被 `FULL_CROP` 过度放大裁切。

### 图片预览与保存体验
- 修复动态图片全屏预览偶发只显示中间一小块的问题，预览图会按容器尺寸稳定铺开。
- 全屏图片预览新增长按保存，和右上角下载按钮共用同一条保存逻辑，减少保存路径分裂。
- 图片保存结果补充震动反馈：成功给轻震，失败给重震，长按保存和按钮保存保持一致。

### 首页头部、搜索与视觉细节
- 首页顶部标签在左右切换时会更早跟随 `pager` 目标页滚动，高亮跟手性更好，不再明显“慢半拍”。
- 进一步修复首页顶部标签位移动画与首页左右切换不同步的问题，标签胶囊会按 `pager` 实时滑动进度连续跟手。
- 修复顶部标签在左右切换后把左侧 `推荐` 挤出可见区、指示器贴边显示不全的问题；已可见标签不再被强行滚到最左侧。
- 修复顶部标签在切页停稳后偶发闪回 `推荐` 的问题；停稳态现在优先跟随 `pagerCurrentPage`，不再等待分类状态回写后一拍。
- 首页顶部 tab 长条和搜索框的液态玻璃/普通模糊材质都收敛了横向亮带问题，减少中间像多一条分界线的观感。
- 首页左上头像与右上设置按钮统一为同尺寸外框与同一水平线，搜索栏两侧更对称。
- 搜索页顶部主栏改为仅保留 `返回 + 更长的搜索框 + 搜索`，热搜开关下移到“热门搜索”标题右侧。
- 搜索框 placeholder 改为单行省略，热搜词不会再在输入框内折成两行；关闭热搜时标题行和开关仍保留，方便随时重新开启。

### 播放、视频详情与离线后台控制
- 修复视频详情页评论数较长时，左侧 tab 会把右侧弹幕开关/发弹幕/设置整体挤出屏幕的问题。
- 离线视频/音频播放接入现有 `MiniPlayerManager + MediaSession + MediaStyle notification + PlaybackService` 链路，后台听离线内容时可触发系统自带的媒体播放界面。

### 更新检查与版本分发
- 修复 `7.0.0 Beta` 通道自动检查更新可能漏检的问题，版本比较现在正确识别 `Beta1 / Beta2 / 正式版` 的先后关系。
- 自动更新检测新增仓库版本文件回退路径；即使 GitHub Release 尚未创建，只要默认分支上的版本号已更新，也能识别到新版本。

### 工程化与回归测试
- 新增个人页壁纸 transform、官方壁纸原图选择、图片保存反馈、搜索头部布局、首页头部视觉、离线播放媒体会话、更新检查版本解析、首页顶部标签跟手等策略测试。
- 持续把首页头部、搜索、壁纸、图片预览和离线播放规则沉淀为可单测的 policy/helper，降低后续继续调视觉与后台播放链路时的回归风险。

## v7.0.0 Beta1 (2026-03-15)

### 版本信息
- 版本号从 `6.9.9` 升级到 `7.0.0 Beta1`，`versionCode` 升级到 `113`。
- 本次为“播放与登录体验修复 + 首页/动态页空间利用率优化 + 流畅度与网络链路结构性优化启动”的综合更新版本。

### 播放与切换
- 修复听视频随机播放总是反复命中同几首的问题，随机模式改为一轮内尽量不重复。
- 修复全屏 `4:3` 画幅切换无效的问题，并统一 `适应 / 填充 / 16:9 / 4:3 / 拉伸` 的画面比例策略。

### 登录
- 移除手机号登录的 UI 入口，当前仅保留扫码登录。
- 在扫码登录界面补充原因说明，明确为什么现在需要通过扫码完成登录。

### 界面优化
- 缩窄底栏与首页顶栏，减少横向留白和纵向厚重感，提升空间利用率。
- 收紧动态页卡片与横向 UP 列表的间距，让信息密度更自然。
- 修复关闭玻璃/模糊效果后，深色模式底栏错误显示为白色的问题。

### 流畅度与网络优化
- 恢复共享网络栈的 HTTP/2，多请求场景下连接复用更好。
- 提高 API HTTP 缓存预算，减少重复拉取的浪费。
- SponsorBlock 请求改为复用共享网络客户端，不再单独起一套连接池。
- 收紧首页后台预加载预算，减少进入页面时的额外负担。
- 动态页启动改为主 feed 先到，关注列表延后少量补齐，降低首屏请求扇出。
- 动态页评论弹窗和楼中楼回复预览从主 feed 树中拆出，减少评论操作时整页重组。
- 首页顶部 tab、底栏导航、播放器交互设置改为聚合订阅，降低根部状态订阅数量，减轻重组压力。

### 工程化、测试与文档
- 补充了随机播放、画面比例、动态页状态、网络策略、首页设置映射、播放器交互设置映射等定向测试。
- 补充了本轮性能优化设计、实施计划、Beta1 交接文档与发布更新日志文档。

## v6.9.9 (2026-03-14)

### 版本信息
- 版本号从 `6.9.8` 升级到 `6.9.9`，`versionCode` 升级到 `112`。
- 本次为“播放器后台控制与听视频模式修复 + 视频详情交互收敛 + 搜索结果外观同步”的综合更新版本。

### 播放器、后台控制与听视频模式
- 修复系统媒体中心/通知栏里“下一首可以切换，但切回上一首无效”的问题；播放器会同时接管 `seekToPrevious/Next` 与 `seekToPrevious/NextMediaItem` 两条命令链，后台播放切歌更稳定。
- 修复 MediaSession 队列索引与真实 timeline 不一致时可能触发的崩溃，降低后台播放、锁屏控制和外部媒体控件场景下的非法状态风险。
- 重构前后切歌导航策略，统一分 P、合集、播放列表与直接队列回退顺序，减少前后切歌行为不对称的问题。
- 听视频模式新增更明确的渲染/布局策略：系统 PiP 下收敛为更紧凑的封面模式，封面尺寸同时受宽度和可用高度约束，避免顶部按钮、控制层与封面互相挤压。

### 视频详情、收藏与评论交互
- 全屏播放、详情操作区和底部交互栏的收藏入口统一改为打开收藏夹选择面板，不再直接做危险的立即取消收藏。
- 收藏保存后的已收藏状态与收藏数改为按最终保存结果统一回写；清空选择时会正确回到未收藏状态，减少图标状态和计数不同步的问题。
- 视频评论里的“更多回复”现在优先保持在嵌入式评论面板内展开，尽量不再用脱离播放器上下文的独立全屏回复层，浏览回复时视频上下文更连续。
- 动态楼中楼回复预览补齐图片预览承载，动态评论与视频评论的子回复展示路径更一致。

### 搜索、首页与下载体验
- 搜索结果卡片现在会跟随首页相同的玻璃/普通样式开关，视频、UP、番剧和直播结果的卡片材质与徽标显示规则更统一。
- 首页/搜索视频卡片补齐付费与充电专属徽标策略，降低不同列表里同一视频卡片信息层级不一致的问题。
- 已完成下载任务点击后会更明确地进入离线播放目标，离线内容回看路径更直接。
- 继续补强首页预加载、点击手势预算、播放器顶部/侧边交互条和弹幕设置等细节策略，减少多入口界面之间的视觉与交互割裂。

### 工程化与回归测试
- 新增搜索结果外观、视频收藏保存、评论展示策略、后台播放切歌、听视频 PiP 渲染与播放器队列同步等一批 policy/test 覆盖。
- 持续把播放器、搜索、下载和详情页交互规则沉淀为可单测的 helper/policy，降低后续继续调播放器与外观联动时的回归成本。

## v6.9.8 (2026-03-14)

### 版本信息
- 版本号从 `6.9.7` 升级到 `6.9.8`，`versionCode` 升级到 `111`。
- 本次为“听视频模式与播放器交互修复 + 竖屏播放可读性打磨 + 搜索结果外观同步”的综合更新版本。

### 听视频模式与播放器交互修复
- 修复听视频模式下封面图与顶部按钮可能重叠的问题，封面尺寸现在会同时受可用高度约束，避免顶到返回和小窗入口。
- 修复听视频进入系统小窗后仍挤压完整控制层的问题，小窗模式下改为更稳定的紧凑渲染，避免按钮分布失真。
- 视频开始播放后会更及时隐藏初始播放框与封面层，减少已经起播但仍停留在“播放框”状态的割裂感。

### 倍速、竖屏播放与可见性优化
- 新增长按倍速后上滑锁定倍速能力，并补强与画面放大/缩放手势之间的冲突处理，避免多指手势抢占长按倍速状态。
- 提升竖屏视频右侧互动按钮在浅色背景下的可见性，为图标补上更克制的深色底托，弱化“白图标融进画面”的问题。
- 继续补强播放器细节交互，降低长按、缩放、控制层切换等场景下的误触和视觉冲突。

### 搜索结果与外观设置同步
- 修复外观设置中关闭玻璃样式后，搜索结果卡片没有同步切换的问题。
- 搜索页视频结果现在会沿用首页卡片的玻璃/普通样式开关与徽标展示规则，UP 主、番剧、直播结果也统一接入同一套卡片材质策略。
- 搜索页的顶部区域与结果区域不再出现“搜索框已切换、结果卡片没切换”的外观割裂。

### 说明
- 推荐使用普通模糊代替液态玻璃，后续完善之后可切换到液态玻璃。

## v6.9.6 (2026-03-11)

### 版本信息
- 版本号从 `6.9.5` 升级到 `6.9.6`，`versionCode` 升级到 `109`。
- 本次为“动态桌面链路重写 + 评论线程与楼中楼补全 + 首页视觉与交互细节修复”的综合更新版本。

### 动态详情、评论与楼中楼
- 按桌面动态接口重写动态详情读取与解析链路，补齐 `opus` 正文合并策略，减少动态正文显示不全、预览摘要覆盖详情正文的问题。
- 动态评论改为按真实 `oid/type` 读取，并结合评论总数接口选择正确评论线程，减少评论总数和评论内容对不上的情况。
- 动态评论弹层补齐楼中楼能力，支持主评论下的回复预览、查看回复入口与二级评论拉取，不再只能看到单层主评论。

### 设置与视觉联动修复
- 修复外观设置里关闭“底栏磨砂”后自动重新打开“液态玻璃”的问题。
- 同步修复关闭“液态玻璃”时反向强开“底栏磨砂”的联动异常，两个效果现在只会在开启互斥项时互相关闭，关闭时保留用户当前选择。
- 修复合集/分集场景下选择其他视频后偶发跳回第一集的问题；合集弹窗、侧边抽屉和相关跳转链路现在会显式保留目标 `cid`，当前集判断也改为按 `bvid + cid` 精确匹配。

### 工程化与回归测试
- 新增动态 `opus` 正文合并、评论线程选择、评论总数回退、楼中楼入口与视觉开关联动等回归测试。
- 继续把动态评论与首页视觉策略沉淀为独立 policy/helper，降低后续调接口和调 UI 时的回归风险。

## v6.9.4 (2026-03-10)

### 版本信息
- 版本号从 `6.9.3` 升级到 `6.9.4`，`versionCode` 升级到 `107`。
- 本次为“首页/搜索与批量缓存体验打磨 + 竖屏播放链路修复 + 评论区站内跳转补强”的综合更新版本。

### 首页、搜索与批量缓存体验打磨
- 首页顶部标签材质与次级模糊策略重新收敛，不再因为交互预算降级而过度关闭 Liquid Glass / Blur 效果，头部观感更稳定。
- 搜索首页新增热搜显隐开关，并为热搜区与历史区切换补上更顺滑的进出场动效，手机和平板布局都统一了展示策略。
- 批量缓存弹窗改为按屏幕高度自适应限制最大高度，候选列表可滚动，短屏设备上也能更容易够到统一画质和确认按钮。

### 竖屏滑动、播放器与评论区修复
- 修复进入竖屏后上下滑动视频时，当前视频状态可能提前串到其他视频的问题；退出竖屏后主播放器同步逻辑也改为更稳妥的 `bvid/cid` 校验。
- 竖屏推荐流在滑到队尾附近时会继续补充相关推荐并去重，避免连续下滑时突然断流。
- 播放器拖动进度条时，控制层显示的当前时间会优先跟随 seek 预览位置，减少“手势预览位置和时间文字不同步”的割裂感。
- 评论区富文本新增对裸 `BV` 号的识别与点击跳转，竖屏评论面板里的站内视频链接也会优先尝试应用内打开。

### 工程化与回归测试
- 新增批量缓存弹窗布局策略、搜索热搜显隐与首页动效、播放器预览进度、竖屏分页补货、竖屏进度同步、主播放器 `bvid/cid` 同步和评论区 `BV` 点击等回归测试。
- 继续把首页头部视觉策略、播放器覆盖层与评论组件逻辑沉淀为可单测的 helper，降低后续交互调优时的回归风险。

## v6.9.3 (2026-03-08)

### 版本信息
- 版本号从 `6.9.2` 升级到 `6.9.3`，`versionCode` 升级到 `106`。
- 本次为“应用内更新补全 + 当前视频批量缓存 + 播放器/搜索/空间页修复 + 缓存清理增强”的综合更新版本。

### 应用内更新与批量缓存
- 应用内更新从“仅检查版本”升级为“检查更新 + 应用内下载 APK + 拉起系统安装器”，更新链路更完整。
- 当前视频详情页新增批量缓存能力，支持对分 P / 合集条目做统一勾选、统一画质选择与批量入队。
- 批量缓存结果会区分已加入、已存在与失败任务，减少重复操作带来的困惑。

### 播放器、字幕与横屏体验
- 修复开启“字幕自动开启”时，横屏手动关闭字幕后回到竖屏又重新显示的问题，字幕开关状态在同一视频内保持一致。
- 优化横屏播放器底栏布局，压缩右侧按钮间距并限制弹幕输入占位文案单行省略，整体观感更平衡。
- 继续补强弹幕与播放器控制条相关细节，减少横竖屏切换和叠层状态下的割裂感。

### 搜索、动态/空间页与缓存清理
- 修复搜索结果中用户认证徽标误把大量账号标成“机构”的问题，认证标签展示更保守准确。
- 补强空间页头图与动态加载策略，改善“头图不显示 / 动态空白或卡住”的问题。
- 默认清除缓存现在会额外清掉字幕缓存、弹幕缓存、应用私有日志和应用内更新残留文件，同时明确保留离线下载、播放记录等用户数据。

### 工程化与回归测试
- 新增应用内更新资产选择、下载状态、安装策略、批量缓存候选与入队、字幕覆盖、搜索认证、空间页加载、横屏底栏、缓存清理等回归测试。
- 扩展字幕缓存、弹幕缓存和日志持久化策略测试，降低后续播放器与缓存治理改动的回归风险。

## v6.9.2 (2026-03-07)

### 版本信息
- 版本号从 `6.9.1` 升级到 `6.9.2`，`versionCode` 升级到 `105`。
- 本次为“视频详情性能优化 + 播放器稳定性增强 + 动态/空间页交互修复”的维护更新版本。

### 视频详情与评论区性能优化
- 评论区分页策略改为接近列表底部时再继续加载，避免进入评论页后连续自动翻页，对边播边刷评论场景更友好。
- 视频播放中会自动切换到轻量评论渲染：减少头像淡入和装饰性内容、弱化楼中楼预览，优先保证评论区滑动流畅度。
- 视频详情页在播放态不再额外预加载相邻分页内容，降低简介页与评论页并行组合带来的额外负担。

### 播放器与播放控制稳定性
- 小窗、后台播放、播放器控制栏与覆盖层相关策略继续收敛，播放服务、迷你播放器位置与顶部控制条行为更稳定。
- 弹幕设置面板与详情页联动细节补强，减少切换状态、恢复画面或叠层展示时的异常与错位。
- AI 总结提示与重试链路继续打磨，失败回退与状态反馈更清晰。

### 动态页、空间页与界面修复
- 修复动态页点进某个已关注 UP 后无法回到“全部关注动态”的问题；再次点击当前 UP 即可取消筛选。
- 修复空间页投稿区“播放全部”按钮左侧图标显示不完整的问题，按钮图标与文字排布更稳定。
- 继续补强系统主题对比度、模糊效果降级与崩溃日志持久化等基础体验，减少极端设备和异常场景下的可用性问题。

### 工程化与回归测试
- 新增视频评论性能策略测试，覆盖评论区分页阈值、播放态轻量评论模式与详情页预加载策略。
- 扩展评论组件、动态页状态、播放器控制条、后台播放、弹幕设置与 AI 总结等回归测试，降低后续性能优化与交互调整的回归风险。

## v6.9.1 (2026-03-07)

### 版本信息
- 版本号从 `6.9.0` 升级到 `6.9.1`，`versionCode` 升级到 `104`。
- 本次为“评论区滑动性能优化 + 桌面端评论标签语义对齐”的维护更新版本。

### 评论区滑动性能优化
- 评论项渲染链路做了热路径收敛：IP 属地文案、点赞显示数、楼中楼用户名预览前缀、特殊评论标签解析等派生值统一改为稳定 helper，减少滚动时的重复计算。
- 评论表情映射合并逻辑改为仅在输入数据变化时构建，避免主评论和楼中楼在滑动过程中反复创建临时 `Map` 对象。
- 主评论列表、评论详情页、楼中楼弹窗和平板评论区统一补齐 `LazyColumn contentType`，提升列表复用效率，降低滚动掉帧概率。

### 桌面端评论标签语义对齐
- 评论响应模型新增对桌面端 `config.show_up_flag` 与 `card_label` 的解析支持，后续评论特殊标签展示不再只依赖客户端猜测。
- 特殊评论下方新增桌面端同语义的 `UP主觉得很赞` 文本标签，样式改为纯文字展示，不再使用胶囊背景。
- 标签展示优先采用服务端 `card_label.text_content`，只有在接口允许展示且 `up_action.like=true` 时才回退到本地 `UP主觉得很赞` 文案，尽量与桌面端规则保持一致。
- 一级评论、评论详情页、楼中楼列表与平板评论布局已统一接入这套标签逻辑，避免不同入口显示不一致。

### 工程化与回归测试
- 新增评论特殊标签解析测试，覆盖 `card_label`、`show_up_flag` 与 `up_action` 的反序列化场景。
- 扩展评论组件策略测试，覆盖特殊标签优先级、回退条件、点赞数推导、IP 文案标准化和列表内容类型分类。

## v6.9.0 (2026-03-07)

### 版本信息
- 版本号从 `6.8.2` 升级到 `6.9.0`，`versionCode` 升级到 `103`。
- 本次为“设置分享能力上线 + 播放器与 AI 总结体验增强 + 链接解析补强 + 个人页/底栏视觉修复”的综合更新版本。

### 设置分享与安全导入
- 新增 `设置分享` 页面，支持把应用中的可交流设置导出为可读 JSON，并支持从文件一键导入。
- 导出文件支持用户直接查看内容，也支持通过系统分享发给其他人，便于交流播放器、外观、弹幕和导航偏好。
- 导入/导出改为白名单模式，只包含外观、播放、手势、弹幕、导航等可分享项；账号、下载路径、WebDAV、隐私与设备相关配置会自动跳过，降低误分享和误覆盖风险。
- 设置搜索已接入“设置分享”入口，能通过“导入 / 导出 / 分享设置 / JSON”等关键词直接打开。

### 播放器与视频详情体验
- 默认播放速度设置升级为更直观的滑杆 + 预设档位控件，调整速度更快、反馈更清晰。
- 修复平板竖屏状态下点击视频中间会被误带入横屏全屏的问题，退出播放页后也不会再残留横屏方向。
- 修复长按点赞触发三连后，投币已满场景下投币按钮没有正确高亮的问题。
- AI 总结链路补强：新增“生成中 / 未登录 / 暂无总结 / 接口失败”等更明确的状态诊断与提示，减少空白和误解。
- 视频信息区展示策略继续收敛，AI 总结入口、标题/作者信息与简介展示更稳定。

### 链接解析与路由兼容
- Bilibili 链接解析器补齐更多深链和分享链接格式，支持更多 `aid` 数字路径、动态/图文链接与包装 URL 的目标提取。
- 动态与视频目标识别更稳，降低外部分享链接打开后误判页面类型或跳错详情页的概率。

### 个人页与导航视觉修复
- 修复“我的页”滚动时状态栏颜色闪烁的问题，移动端沉浸式头图场景下顶部遮罩与系统栏样式改为单一策略驱动。
- 底栏关闭磨砂后，改为稳定的纯白背景层，显著提升浅色场景下的底栏图标和文字可读性。

### 工程化与回归测试
- 新增并补齐设置分享、默认播放速度、AI 总结提示、链接解析、平板播放策略、个人页系统栏策略、底栏表面颜色等多组回归测试。
- 设置分享 ViewModel 新增工厂构造器回归测试，避免后续再次出现入口页创建即崩溃的问题。

## v6.8.1 (2026-03-07)

### 版本信息
- 版本号从 `6.8.0` 升级到 `6.8.1`，`versionCode` 升级到 `101`。
- 本次为“默认画质链路纠偏 + 搜索/评论体验修复 + 播放提示可读性优化”的维护更新版本。

### 默认画质与 1080P 播放策略
- 修复默认画质设为 `1080P60` 时对非大会员首播判断的误伤；非大会员现在会自动按可播放能力回落到 `1080P`，未登录则回落到 `720P`。
- “自动最高画质”改为先按账号能力封顶后再选，避免把 `1080P60` 等大会员专享档位误当成普通用户首播目标。
- 补强已登录非大会员的首包补拉与日志诊断，降低“第一次 720P、重进才恢复 1080P”的概率。
- 新增播放诊断日志，统一记录首播起始画质、取流结果、画质菜单与最终选轨，后续排查会员能力、首包回落和切档异常更直接。

### 搜索与评论体验修复
- 修复未登录视频搜索第一页偶发空白的问题；当主搜索接口空成功返回时会自动走兼容回退链路。
- 搜索页空态文案改为区分“确实无结果”和“结果被过滤”，减少“看起来像坏了”的误解。
- 评论分页新增“零增量即收口”策略，解决滑到底后持续转圈但没有新评论的问题。
- 修复未登录场景下评论数显示为 `0`、或评论总数正常但列表为空的情况，游客最热评论链路改为优先使用更兼容的分页参数，并在空成功响应时自动补拉兼容主链路。
- 评论总数解析改为优先采用更可靠的总数来源，并在详情页已知评论数时作为本地保底，减少“数量对不上内容”或“评论 6118 但列表空白”的情况。

### 播放提醒与设置提示
- 切换画质后的提示不再停在底部角落，而是改为播放器中部的高对比提示层，位置更稳定、可读性更高。
- 设置页在选择 `1080P60` 时会明确提示非大会员/未登录用户的实际起播画质，减少设置预期与实际播放不一致。

### 平板影院布局与详情展示
- 平板影院模式补齐合集入口与多 `P` 入口，侧边信息面板现在可直接展开合集和分P选择，减少大屏场景下还要回到其它入口切集的问题。
- 影院信息区重做标题与简介展示，整合 `VideoTitleWithDesc`、AI 总结和简介空态，详情层级更完整，信息密度更合理。
- 平板影院布局策略与对应测试同步补齐，避免后续继续调整信息面板时把合集/分P模块漏掉。

### 工程化与回归测试
- 新增并补齐默认画质、搜索空态、游客搜索回退、评论读取策略、评论分页、平板影院布局、播放提示位置等多组策略测试。
- 增补播放与评论链路日志，方便后续继续排查首包画质、游客评论、分页异常与游客空成功响应问题。

## v6.8.0 (2026-03-06)

### 版本信息
- 版本号从 `6.7.2` 升级到 `6.8.0`，`versionCode` 升级到 `100`。
- 本次为“收藏夹订阅链路补全 + 动态图片预览交互优化 + 首页/个人页细节打磨 + 播放与更新体验修复”的综合更新版本。

### 收藏夹、订阅与列表能力
- 补齐收藏夹“订阅”数据映射与聚合策略，修复部分订阅项进入后详情为空、无法正确识别合集/系列的问题。
- 收藏列表页与 ViewModel 的跳转/展示链路同步收敛，订阅项与普通收藏夹的打开行为更一致。
- 相关播放入口策略补强，降低列表来源切换时的空态与误跳转概率。

### 动态图片预览与手势交互
- 重做动态图片预览关闭与过渡策略，补齐缩放图、拖拽关闭、回弹与手势边界处理。
- 优化 `ZoomableImage` 与预览弹窗的联动，减少预览态切换时的跳变和误触。
- 补齐图片预览转场策略测试，方便后续继续调动画时回归。

### 首页、侧栏、搜索与个人页细节
- 修复首页下拉刷新区域的顶部留白/间距问题，头部与刷新体验更稳定。
- 首页头部、TopBar、侧边栏与部分入口视觉继续收敛，信息层级和点击区域更统一。
- 搜索页、消息页、关注页与个人页若干布局细节优化；个人页壁纸操作区与资料展示层级进一步整理。
- 底栏设置与若干导航入口细节同步打磨，减少设置项与实际呈现不一致的情况。

### 更新弹窗可读性修复
- 修复深色模式下“发现新版本 / 更新日志”弹窗正文对比度过低的问题。
- 启动时自动更新提示与设置页中的更新日志弹窗统一改为高对比文案样式，避免深色背景与半透明弹窗叠加后看不清文字。

### 视频详情、平板布局与播放体验
- 视频详情页、内容区与信息区的若干布局策略继续优化，竖屏分页和详情呈现更稳。
- 平板影院布局与相关策略补强，减少大屏场景下布局切换时的错位与留白问题。
- Mini Player 管理链路继续收敛，降低回收不及时与状态残留的风险。

### 视频画质体验修复
- 修复已登录非大会员场景下，更新后的首包能力与画质菜单展示不一致的问题。
- 保留服务端已广告的 `1080P` 选项，并统一首播与切档的取流策略，减少“最高只能看到 720P”的误判。

### 工程化与回归测试
- 补齐首页下拉刷新、图片预览转场、收藏播放策略、视频详情布局、竖屏分页、路由与画质等多组策略测试。
- 更新弹窗新增独立的视觉策略测试，确保深色模式下的文案对比度不会再次回退。

### 验证
- `./gradlew testDebugUnitTest --tests 'com.android.purebilibili.feature.settings.AppUpdateDialogVisualPolicyTest' --tests 'com.android.purebilibili.feature.settings.AppUpdateCheckerTest' --tests 'com.android.purebilibili.feature.settings.AppUpdateReleaseNotesPolicyTest' --tests 'com.android.purebilibili.feature.settings.AppUpdateUiPolicyTest'`
- `./gradlew testDebugUnitTest --tests 'com.android.purebilibili.feature.video.usecase.VideoPlaybackUseCaseQualitySwitchTest' --tests 'com.android.purebilibili.data.repository.VideoLoadPolicyTest'`

## v6.7.2 (2026-03-05)

### 版本信息
- 版本号从 `6.7.1` 升级到 `6.7.2`，`versionCode` 升级到 `99`。
- 本次为“导航与返回动效收敛 + 播放器音频稳定性修复 + 主题与设置体验增强 + 启动壁纸能力补强”的综合维护版本。

### 导航与返回动效（重点）
- 导航转场模块拆分并收敛（新增 `AppNavigationTransitions`），统一设置页与详情页的进退场策略，减少规则分散导致的回归。
- 视频详情返回卡片链路继续稳态化：补齐条件判断与路径兜底，降低预测返回与共享元素并发时的错位/闪动。
- 导航动效参数（`AppNavigationMotionSpec`、`AppNavigationTransitionPolicy`）同步更新，兼顾手感与稳定性。

### 播放器与视频详情体验
- 修复“Hi-Res 音源下长按 2x 容易失真”的问题：长按倍速对 Hi-Res 增加兼容限幅并保真恢复。
- 视频详情页覆盖层、进度条布局、相关推荐卡片细节继续优化，减少状态切换时的层级冲突和视觉跳变。
- 播放恢复建议与返回封面策略补强，弱网/切页/前后台切换下的播放器稳态更一致。

### 设置、主题与首页视觉
- 动画设置新增“预测返回”联动策略与可用性提示，开关依赖关系更清晰。
- 外观与主题相关策略更新（主题模式、分段策略、设置项视觉）并同步到设置页展示。
- 首页头部与卡片视觉细节继续收敛，统一动效与信息层级，提升整体一致性。

### 护眼插件与启动壁纸能力
- 护眼覆盖层与策略补强，参数决策更稳定，减少不同场景下的显示割裂感。
- 启动壁纸新增历史记录策略、随机池可见集合与预览策略，随机展示与回溯能力更完善。

### 工程化与回归测试
- 新增/补齐多组策略测试，覆盖启动壁纸历史、随机池预览、动画设置、导航转场、视频详情返回策略、进度条布局与相关 UI 规则。
- URL 解析、模糊预算、护眼策略等关键基础模块的回归测试同步加强。

## v6.7.1 (2026-03-05)

### 版本信息
- 版本号从 `6.7.0` 升级到 `6.7.1`，`versionCode` 升级到 `98`。
- 本次为“视频详情返回动画稳定性 + 系统栏恢复链路 + 卡片回位一致性”修复版本。

### 动画与返回稳定性
- 修复首页/历史/收藏/稍后看等列表路由在视频返回场景下的转场干扰，统一卡片回位策略。
- 优化“视频详情 -> 相关推荐视频 -> 返回上一详情”的整条链路，视频到视频路由层改为 No-Op，降低抖动和层间抢动画。
- 相关推荐卡片共享元素曲线收敛到统一弹簧参数，关注徽标加入共享键，减少回程错位与跳变。

### 系统 UI 恢复
- 修复“详情页点推荐视频再返回后状态栏/通知下拉异常”问题，退出详情时恢复完整 system bars 快照（颜色、亮暗、behavior）。

## v6.7.0 (2026-03-05)

### 版本信息
- 版本号从 `6.6.0` 升级到 `6.7.0`，`versionCode` 升级到 `97`。
- 本次为“顺序连播逻辑纠偏 + 详情页防回拉 + 视觉流畅策略收敛 + 工程化补强”的夜间整合发布版本。

### 播放与连播逻辑（重点）
- 修复“顺序播放/自动连播结束后闪到下一条标题但仍回到当前视频循环”的问题。
- `顺序播放` 结束行为调整为：优先播放`下一个分P/合集下一集`，若当前合集无后续再回退到播放列表下一条。
- 详情页主播放器与内部 `bvid` 同步策略收敛，避免普通连播场景被路由初始 `bvid` 回拉覆盖。
- 补齐顺序连播策略测试，覆盖“合集优先 / 播放列表兜底 / 无下一条停止”等分支。

### 交互与界面优化
- 全屏覆盖层、视频区与回复区交互细节优化，减少控件状态切换抖动与遮挡。
- 设置页（播放/动画/搜索入口）与首页卡片展示样式进一步收敛，信息密度与可读性更平衡。
- 视频详情页若干行为策略补强（如播放器折叠状态判定、返回同步路径稳态处理）。

### 流畅度与性能策略
- 动画与转场参数继续收敛：降低高负载设备上的动效堆叠成本，减轻“慢但不顺”的体感。
- 模糊渲染预算与运行时视觉降级策略增强，在持续高 jank 时更早进入保护态并支持自动恢复。
- 首页/导航链路的性能策略与测试同步更新，减少极端场景下的主线程压力。

### 数据模型与工程化
- 响应模型与搜索模型解析能力扩展，提升复杂返回结构下的兼容性与容错。
- Baseline Profile 基准采样与性能脚本补强，方便回归时识别真实样本并稳定复现。
- 策略测试面持续补齐，覆盖播放、转场、模糊、首页性能与设置映射等关键路径。

## v6.6.0 (2026-03-04)

### 版本信息
- 版本号从 `6.5.0` 升级到 `6.6.0`，`versionCode` 升级到 `96`。
- 本次为“字幕链路完善 + 列表播放策略修复 + 私信/搜索/卡片体验优化”的综合更新版本。

### 字幕链路完善（重点）
- 字幕功能默认开启，减少需要手动打开字幕能力的门槛。
- 播放器信息接口切换到 WBI 签名链路（`x/player/wbi/v2`），修复部分场景下看点/播放器信息拉取不稳定问题。
- 横屏控制栏中的字幕面板样式收敛：间距、字号、控件尺寸更紧凑，操作更集中。
- 播放区字幕渲染优化为阴影增强方案，降低底色遮挡感并提升复杂画面可读性。

### 收藏夹“听视频”播放策略修复
- 修复收藏夹中“顺序播放/随机播放”在当前曲目结束后直接暂停、需手动切歌才能继续的问题。
- 播放结束策略调整为来源分流：
  - 收藏夹外部队列：按播放队列模式连续播放（顺序/随机/单曲循环）。
  - 首页/动态等普通来源：继续遵循全局“播放完成策略”（如播完暂停）。

### 私信、搜索与卡片体验优化
- 私信会话排序收敛为“置顶优先 + 最近消息时间优先”，并补充置顶操作的乐观更新与失败回刷兜底。
- 搜索页筛选栏支持横向滚动，减少小屏场景筛选项挤压与截断。
- 首页视频卡片信息布局优化：贴封面模式下播放量/评论/时长对齐更清晰。

### 视觉与流畅策略升级
- 新增统一模糊预算策略（`BlurBudgetPolicy`），按区域与动效档位裁剪模糊强度上限，避免高负载时持续重模糊。
- 导航转场参数收敛：手机与平板端滑动/淡入淡出/背景模糊时长下调，减少“慢但不顺”的体感。
- 入场动画策略收敛：`Normal/Reduced` 档位降低排队延迟与总时长，首屏响应更直接。
- 新增“智能流畅优先”开关：检测到持续高 jank 时临时降级为低动效与低模糊预算，并支持自动恢复。
- 性能采样脚本新增样本有效性标记（`sample_valid`），避免 `frames=0` 被误判为有效基线。

### 稳定性与测试
- 补齐字幕策略测试，确保默认开启行为与测试断言一致。
- 新增播放结束策略测试，覆盖收藏夹来源下的顺序/随机/单曲循环分支，降低回归风险。

## v6.5.0 (2026-03-03)

### 版本信息
- 版本号从 `6.4.2` 升级到 `6.5.0`，`versionCode` 升级到 `95`。
- 本次为“播放器交互修复 + 评论可用性提升 + 关注页性能优化 + 定向流量能力接入”的综合更新版本。

### 播放器与交互体验修复（重点）
- 新增“固定全屏比例”偏好，减少每次播放重复手动调整比例。
- 竖屏发弹幕输入弹窗与键盘避让链路优化，修复输入区域与输入法之间异常缝隙问题。
- 亮度/音量侧滑反馈动画优化：小步进平滑过渡，关键阈值切换更清晰。
- 画质展示文案收敛，移除类似 `480P-32`、`4K-120` 的数字尾缀，改为更直观的档位显示。

### 播放进度与合集连续观看
- 分P/合集恢复播放逻辑增强：支持记住最近观看分P与时间进度，并补齐从详情返回后的恢复提示链路。
- 播放结束与下一集衔接策略优化，减少从已看分P回退到 `P1` 的误触发场景。

### 评论系统与未登录可用性
- 评论反诈检测链路升级：补充二次确认与多通道探测，降低误判“秒删/影藏”的概率。
- 评论读取新增游客优先/双通道降级策略：
  - 已登录优先走鉴权链路，失败自动降级游客链路；
  - 未登录优先走游客链路，必要时自动回退。
- 修复未登录用户“无法查看评论”的问题，同时仅保留发布/点赞/点踩/删除/举报等写操作登录限制。

### 关注页性能与状态保持
- 新增关注列表本地缓存存储，降低反复进入页面时的全量重刷成本。
- 修复“从关注页进入主播详情返回后立即整页重刷”的体验问题，回退后优先复用缓存，手动下拉再强制刷新。

### 定向流量能力接入
- 新增 B 站定向流量开关与播放链路参数覆盖策略（结合移动网络状态启用）。
- 优化定向模式下的取流参数与回退策略，提升在运营商定向套餐场景下的可用性。

### 其他体验与稳定性优化
- 直播小窗/播放器覆盖层、首页卡片与转场动效细节优化，减少突兀跳变。
- 空间页、收藏夹与播放设置若干行为修复与策略单测补充，提升整体稳定性。

## v6.4.1 (2026-03-02)

### 版本信息
- 版本号从 `6.4.0` 升级到 `6.4.1`，`versionCode` 升级到 `93`。
- 本次为“全屏策略收敛 + 字幕链路稳定性 + 播放设置增强”的维护发布版本。

### 全屏策略与设置收敛（重点）
- 默认全屏方向收敛为 4 个主模式：`自动 / 不改 / 竖屏 / 横屏`，降低配置复杂度。
- 历史模式值兼容迁移：旧配置中的 `比例(4)`、`重力(5)` 自动回收为 `自动`，避免老配置进入隐性分支。
- 修复竖屏视频场景下全屏按钮“看起来无响应”的体验问题：当目标方向为竖屏时，进入竖屏全屏覆盖层。
- 同步更新方向策略单测，确保收敛后全屏进入/退出链路行为一致。

### 播放设置增强
- 新增“画中画不加载弹幕”开关（系统 PiP 模式下生效）。
- 新增“中部滑动切换全屏”开关。
- 新增“左右侧滑动调节亮度/音量”及“调节系统亮度”开关。
- 新增“全屏显示互动按钮”开关。
- 新增“观看人数”开关，关闭时停止在线人数轮询并清空展示。
- 新增“底部进度条展示”策略：`始终展示 / 始终隐藏 / 仅全屏展示 / 仅全屏隐藏`。
- 新增“自动启用字幕”偏好：`关闭 / 开启 / 无 AI / 自动`。

### 字幕链路稳定性与可控性
- 增强字幕轨道元数据（轨道标识、AI 状态、类型等），并补充轨道绑定键策略，减少错误匹配。
- 新增可信字幕 URL 校验、AI 字幕识别与轨道排序策略，优先选择更可信、非 AI 轨道。
- 新增字幕展示模式自动决策策略（结合偏好、AI 识别与静音状态）。
- 字幕拉取新增按 `bvid/cid/轨道/URL` 维度的 Cue 缓存键，减少重复下载与解析开销。

### 推荐反馈能力
- 新增“不感兴趣”反馈入口链路，记录 `bvid/UP 主/关键词` 到当日反馈快照，用于后续推荐调节。

### 工程化与回归
- 新增并更新策略单测，覆盖全屏模式收敛、字幕策略、字幕缓存键与播放器覆盖层行为：
  - `FullscreenModeMappingPolicyTest`
  - `VideoDetailLayoutModePolicyTest`
  - `BiliSubtitlePolicyTest`
  - `VideoRepositorySubtitleCachePolicyTest`
  - `PlaybackSettingsSelectionPolicyTest`
  - `TopControlBarPolicyTest`
  - `VideoPlayerOverlayPolicyTest`
  - `VideoGestureFeedbackPolicyTest`
  - `VideoLoadRequestPolicyTest`

## v6.4.0 (2026-03-01)

### 版本信息
- 版本号从 `6.3.3` 升级到 `6.4.0`，`versionCode` 升级到 `92`。
- 本次为“横屏布局与全屏方向策略”优先版本，重点修复横屏切换与退出链路问题。

### 横屏布局与方向策略（重点）
- 新增“横屏适配”总开关，可控制是否启用横屏布局与横屏逻辑（平板默认可开启）。
- 新增“默认全屏方向”模式：`自动 / 不改方向 / 竖屏 / 横屏 / 比例判断 / 重力感应`。
- 修复“点击横屏按钮后仍保持竖屏”的问题：手动全屏进入时按模式和视频方向决策目标方向。
- 修复“退出横屏后又自动旋转回横屏”的问题：补齐自动旋转与手动全屏状态协同策略。

### 播放器交互与控制层增强
- 新增全屏相关设置：
  - 全屏手势反向（上滑/下滑进退全屏可反转）
  - 自动进入全屏（播放就绪后）
  - 自动退出全屏（播放结束后）
  - 全屏锁定按钮显示开关
  - 全屏截图按钮显示开关
  - 全屏顶部电量显示开关
- 横屏手势区优化：中间区域支持滑动触发进退全屏，并增加方向切换提示文案。

### 手势反馈可读性优化
- 优化亮度/音量手势反馈：动态图标、数值显示与层次动画更清晰。
- 调整亮度/音量反馈容器样式，去除黑色边框遮挡感，减少亮色画面下的视觉突兀。

### 工程化与回归
- 新增/更新策略单测，覆盖全屏方向策略与手势反馈策略：
  - `VideoDetailLayoutModePolicyTest`
  - `VideoGestureFeedbackPolicyTest`
  - `CuteLoadingIndicatorPolicyTest`

## v6.3.1 (2026-02-26)

### 版本信息
- 版本号从 `6.3.0` 升级到 `6.3.1`，`versionCode` 升级到 `89`。
- 本次为“听视频链路 + 分P选择体验 + 横竖屏全屏 + 返回动效”集中修复版本。

### 听视频与播放列表修复
- 修复视频播放中切到其他应用再返回后，部分场景出现“有画面无声音”的问题（含“离开播放页后停止”开关联动场景）。
- 修复“听视频”从收藏夹进入时播放源错位问题：不再误跳到收藏夹中第一个视频所在合集，改为按当前选中视频上下文播放。

### 分P选择与选集体验升级
- 重构分P选择组件，新增“预览 + 展开”双模式：小屏竖屏优先展示横向预览，支持一键展开完整分集面板；横屏/大屏优先网格直出。
- 选集面板新增搜索能力（按 P 号/标题），并支持章节分组筛选，分集较多时定位更快。
- 优化分P网格自适应策略：按屏幕宽度与方向自动调整列数、卡片尺寸与底部安全区留白。
- 修复视频跳转时的 CID 解析优先级：显式 CID 缺失时，优先使用相关推荐里的匹配 CID，再回退到合集分集 CID，减少错分P播放。

### 横竖屏与返回动效修复
- 修复未开启共享过渡时，返回首页视频卡片动效方向不合理的问题：左右列回退方向改为符合空间关系。
- 修复自动旋转场景下的全屏退出问题：横屏进入全屏后，旋转回竖屏可自动退出全屏。

## v6.3.0 (2026-02-25)

### 版本信息
- 版本号从 `6.2.1` 升级到 `6.3.0`，`versionCode` 升级到 `88`。
- 本次为“听视频播放列表 + 动态稳定性 + 首播画质鉴权 + 底栏视觉收敛”的集中发布版本。

### 听视频播放列表（收藏夹 / 稍后再看 / 列表页）
- 新增收藏夹场景“听视频”播放入口，并补齐稍后再看等列表页的可用入口。
- 优化“听视频”按钮可点击时机，减少进入页面后按钮长时间灰置的等待感。
- 播放模式补齐并可直接切换：
  - `顺序播放`
  - `随机播放`
  - `单曲循环`
- 优化播放页模式按钮布局与居中逻辑，修复与封面贴边/错位问题。
- 修复从首页视频进入听视频时封面与当前播放内容不一致的问题，统一按当前播放上下文绑定。

### 动态页稳定性与请求策略
- 修复快速切换多个 UP 主动态时偶发“无数据”问题。
- 针对动态请求补充更稳健的重试与状态同步策略，降低瞬时失败导致的空白页概率。
- 优化异常态展示与重试链路，降低 `HTTP 412 Precondition Failed` 对体验的影响。
- 结合现有 API 文档与实现策略，补强特殊动态类型（如充电相关动态）的加载兼容性。

### 播放清晰度与登录鉴权修复（重点）
- 修复“已登录非大会员首次播放仅 720P、重载后才恢复更高分辨率”的问题。
- 登录态判定由“仅 Cookie”升级为“`SESSDATA` 或 `access_token` 任一有效即视为已登录”。
- 在无 Cookie 但有 APP token 的场景下，1080P（`qn=80`）可走 APP API 鉴权路径，减少误降级。
- 播放前增加上下文自举，避免首次加载时因上下文未绑定而回落默认 720P。

### 底栏视觉与玻璃效果收敛
- 去除底栏玻璃反光高光边框，移除顶部“镜面反射”观感。
- 底栏边框改为更轻量低透明描边，保持层次感同时避免反光干扰。

### 工程化与回归
- 新增并更新画质鉴权与上下文策略相关单测，覆盖：
  - 登录态双通道判定（Cookie / access_token）
  - 无 Cookie 场景下 1080P 的 APP API 尝试策略
  - PlayerViewModel 上下文自举策略
- 已执行针对性单测：
  - `VideoLoadPolicyTest`
  - `PlayerViewModelContextPolicyTest`

## v6.2.1 (2026-02-25)

### 版本信息
- 版本号从 `6.2.0` 升级到 `6.2.1`，`versionCode` 升级到 `87`。
- 本次为视频详情返回首页链路的性能与一致性优化版本，包含动效、播放器状态与可观测性增强。

### 横屏控制栏新增能力（关键）
- 横屏播放器右侧新增 `字幕` 按钮：
  - 新增独立字幕面板，支持字幕语言快速切换（如中/英/双语/关闭，按轨道能力动态展示）。
  - 新增“字幕大字号”开关，横屏下可直接调整字幕可读性。
- 横屏播放器右侧新增 `更多` 按钮（聚合操作面板）：
  - 新增“下集”快捷入口。
  - 新增“播放顺序”快捷切换入口（展示当前顺序标签并可一键切换）。
  - 新增“画面比例”快捷入口（保持当前比例状态高亮）。
  - 新增“竖屏”快捷入口（横屏下快速回到竖屏观看）。
- 横屏控制按钮布局重排：
  - 高频项（画质、倍速、字幕、更多）固定在主操作区，次级项收拢到弹出面板，减少遮挡并提升触达效率。

### 返回首页动画与性能优化（重点）
- 优化“未开启共享过渡”时的视频详情返回首页动效：
  - 下调无共享模式导航动效时长（滑动/淡入淡出/背景模糊）。
  - 视频详情 -> 首页返回链路改为更轻量的回退过渡，减少高频返回时掉帧风险。
  - 首页返回抑制窗口在无共享场景下缩短，降低等待感。
  - 底栏恢复延迟按场景动态调整（无共享更快恢复）。
- 返回首页曲线继续对齐 iOS 风格非线性（先快后慢），并保留快速返回场景稳定性策略。
- 返回阶段统一采用封面层，取消“视频画面 -> 封面”中途转换，返回全程保持封面可见。
- 共享过渡开启且极快返回场景下，卡片回收过程中封面保持完整可见，避免中途露底或闪烁。
- 返回首页时顶部标签页全程可见，不再中途隐藏。

### 播放状态稳定性
- 修复返回首页后音频短暂停留问题，离开视频域时更早执行静音/暂停兜底，避免残留播音。

### 横竖屏与全屏行为
- 优化旋转到横屏时的进入策略，直接进入视频全屏播放态，减少中间态闪烁与割裂。

### 设置项排查与可用性
- 针对“液态玻璃选项消失”反馈补充排查：确认该能力未移除，仍由首页视觉/动画效果配置联动控制并在对应设置页生效。
- 补充相关可观测信息，便于后续区分“选项缺失”与“配置联动导致未显示”的用户反馈。

### 弹幕与视觉一致性
- 弹幕开关按钮（详情页与播放控制层）启用态颜色统一改为主题色（`MaterialTheme.colorScheme.primary`），提升主题一致性。

### 动画性能埋点增强
- 新增并完善 `home_return_animation_perf` 事件采集：
  - 实际耗时、计划抑制时长、共享过渡是否开启/就绪。
  - 共享过渡未就绪/未开启场景标记，便于单独分析非共享返回性能。
  - 快速返回标记、平板标记、卡片动画开关。
  - 内置插件与 JSON 插件数量（含 feed/danmaku 分项），用于分析插件压力与动画帧率表现。

## v6.2.0 (2026-02-24)

### 版本信息
- 版本号从 `6.1.5` 升级到 `6.2.0`，`versionCode` 升级到 `86`。
- 本次为当晚集中修复与体验优化版本，覆盖动态、历史、播放、首页、设置、直播与仓库层逻辑。

### 动态模块修复（重点）
- 修复动态流中“订阅合集/剧集”卡片无法点击进入的问题。
- 修复动态视频链接在仅有 aid 场景下无法打开的问题，统一支持 `BV`、`av`、`aid` 三种目标解析。
- 补齐动态卡片与转发卡片的跳转兜底链路（`archive`、`ugc_season`、`jump_url`、`aid`）。
- 修复动态评论参数解析，优先使用 `basic.comment_id_str` 与 `basic.comment_type`，并增强多类型回退推断。
- 修复动态发评硬编码参数问题，改为按当前动态解析出的 `(oid, type)` 发起请求。
- 修复动态“视频”分栏筛选遗漏，纳入 `DYNAMIC_TYPE_PGC` 与 `DYNAMIC_TYPE_UGC_SEASON`。

### 历史记录能力增强
- 新增历史记录卡片长按删除能力。
- 新增历史页顶部“批量删除”按钮与批量选择流程（全选、删除、完成）。
- 删除链路接入官方历史删除接口：`x/v2/history/delete`。
- 历史删除复用“稍后再看”同款消散动画效果，提升反馈一致性。
- 新增历史删除策略与映射逻辑（渲染 key、`kid` 组装规则），覆盖 archive、pgc、live、article 等类型。

### 播放与视频链路优化
- 优化视频详情加载策略，补强混合 ID 输入下的视频信息查询流程。
- 新增/完善导航目标 CID 解析策略，减少跨页面跳转丢失分 P 的情况。
- 持续优化播放器覆盖层与控制栏交互（底部控制、全屏覆盖层、竖屏覆盖层、直播弹幕层）。
- 优化播放进度管理、播放器状态同步与相关用例处理逻辑。

### 首页、关注、列表与稍后再看
- 优化首页下拉刷新交互策略与提示状态逻辑。
- 优化视频卡片历史进度条显示策略。
- 更新关注列表批量选择等策略细节与对应测试。
- 增补历史播放策略与稍后再看删除策略相关测试。

### 直播、弹幕与仓库层
- 优化直播弹幕协议与客户端处理流程。
- 优化弹幕过滤与合并策略，补齐高级过滤场景测试。
- 调整部分仓库层行为与容错处理（含关注分组等场景策略测试）。

### 设置与应用框架
- 新增应用更新自动检查进程门禁，避免同进程重复触发自动检查。
- 优化设置页与平板设置布局的一致性与交互细节。
- 同步调整 `MainActivity` 与导航接入点，匹配本次行为改动。

### 工程化与测试
- 持续抽离可测试策略模块，覆盖动态、首页、历史、播放、设置等高频逻辑。
- 新增并更新多组单元测试，重点覆盖跳转、删除、过滤、选择与参数解析回归点。

### 验证结果
- 已执行：`./gradlew :app:testDebugUnitTest`
- 结果：`BUILD SUCCESSFUL`

### 当晚版本轨迹
- `v6.1.3`：版本升级并合入动态/详情链路修复。
- `v6.1.4`：发布版本。
- `v6.2.0`：当晚集中稳定性修复与交互增强版本。

## [6.1.4] - 2026-02-24

### ✨ New Features (新增功能)

- **播放与画质策略升级**:
  - 新增解码/画质策略层，补充 AVC/HEVC/AV1 与分辨率选择兜底逻辑。
  - 优化播放页画质入口与切换链路，提升高画质场景下的可控性与稳定性。
- **设置页交互升级（iOS 风格）**:
  - 新增可拖动、可实时打断的滑动分段控件，并接入主题/编码/推荐流类型等关键选项。
  - 模糊强度与推荐流类型入口完成图标语义重整，信息表达更清晰。
- **下载与存储能力补强**:
  - 引入下载存储策略模块，完善下载路径与任务落盘策略。
  - 下载弹窗与任务链路同步适配新的路径选择能力。

### 🛠 Improvements & Fixes (优化与修复)

- **视频卡片视觉修复**:
  - 去除视频时长标签周围黑边，统一封面叠加层观感。
- **转场与动效体验优化**:
  - 调整导航与图片预览过渡参数，改善进出场平滑度与一致性。
- **权限与隐私相关设置完善**:
  - 补充敏感权限申请链路与设置项联动，优化权限状态反馈。

### ✅ Tests (测试)

- 新增/补充策略与视觉相关单测，覆盖：
  - HDR/编码选择策略
  - 下载存储策略
  - 分段控件与设置策略
  - 导航/转场策略
  - 视频卡片时长标签视觉策略

### 📦 Release

- **Version Bump**: Updated app version to `6.1.4` (`versionCode` `84`).

## [6.0.3] - 2026-02-18

### ✨ New Features (新增功能)

- **平板端视频详情重设计（Stage + Side Curtain）**:
  - 点击视频后改为“舞台式播放器 + 侧幕式内容”布局，强化平板交互层次与沉浸感。
  - 侧幕支持 `PEEK / OPEN / HIDDEN` 状态切换，并提供窄态快捷入口（评论/相关推荐）。
  - 引入按屏宽自适应策略，覆盖主流平板尺寸区间，避免固定尺寸硬编码。

### 🛠 Improvements & Fixes (优化与修复)

- **播放器卡死恢复链路增强**:
  - 新增错误恢复策略与生命周期播放策略，按错误类型执行差异化重试。
  - 解码异常场景支持自动回退编码重载，降低“起播失败后卡死”概率。
  - 补充播放错误日志与生命周期判定细节，便于后续排障。
- **平板详情页信息区结构优化**:
  - 播放器下方区域收敛为“互动按钮 + 视频简介”，移除冗余推荐速览。
  - 该区域背景改为纯白，提升信息对比与视觉一致性。
- **互动按钮可用性修复**:
  - 点赞图标改为更常见样式，修复“大拇指过细”观感问题。
  - 点赞交互改为稳定单按钮实现，修复点赞后偶发闪动。
  - 投币按钮文案改为“投币/已投币”，修正文字与语义。
  - 收藏图标切换为常用五角星样式。
- **侧幕交互策略调整**:
  - 取消“展开后自动收起”，改为仅由用户手动控制，避免操作被打断。

### ✅ Tests (测试)

- 新增/更新测试:
  - `TabletCinemaLayoutPolicyTest`
  - `PlayerErrorRecoveryPolicyTest`
  - `PlayerLifecyclePlaybackPolicyTest`

### 📦 Release

- **Version Bump**: Updated app version to `6.0.3` (`versionCode` `74`).

## [5.3.4] - 2026-02-15

### ✨ New Features (新增功能)

- **今日推荐单卡片交互升级**:
  - 新增收起/展开能力，收起时仅保留轻量头部，不再自动跟随首页推荐流同步重算。
  - 新增“刷新”入口，支持只刷新推荐单（不触发首页推荐流下拉刷新）。
  - 手动刷新会优先消耗当前预览队列，快速“换一批”。
- **播放结束行为可选**:
  - 播放设置新增“选择播放顺序”：`播完暂停` / `顺序播放` / `单个循环` / `列表循环` / `自动连播`。
  - 针对“稍后再看”场景优化：从列表进入播放时默认按顺序队列运行，看完一条可无缝接下一条。
  - 横屏/竖屏播放器内新增“播放顺序”快捷入口，可直接弹出同款五选项面板切换。

### 🛠 Improvements & Fixes (优化与修复)

- **竖屏链路稳定性修复**:
  - 修复竖屏滑到新视频后，进入 UP 主页（或搜索）返回时内容回退到首视频的问题。
  - 竖屏“简介”面板内点击 UP 头像现在可直接进入 UP 空间，返回后保持竖屏视频上下文。
  - 竖屏会话内播放结束改为由竖屏分页接管自动续播，自动下滑到下一条后起播，避免主链路抢占导致的“仅音频无画面/无法继续滑动”问题。
  - 竖屏恢复时新增初始页定位策略，优先回到上次浏览的 bvid（命中推荐队列时）。
- **播放顺序面板与动作栏视觉修正**:
  - 下调播放器内“选择播放顺序”面板选项字号，避免弹窗文字过大挤占视线。
  - 统一详情页操作栏五列按钮槽位与基线，修复“点赞（大拇指）一列”与其余列视觉不齐的问题。
- **播放器顶栏与弹幕可读性修复**:
  - 非全屏播放器左上角快捷入口由“画质”改为“弹幕开/关”，避免与底部画质入口重复。
  - 指令型弹幕新增可读性过滤：仅展示可读文本提示，自动忽略 `upower_state` 等结构化 payload，修复黄色乱码弹幕问题。
- **图片预览开关动画重构（iOS 风格）**:
  - 重做图片预览打开/关闭动画阻尼与速度曲线，改为更贴近 iOS 的顺滑回收手感。
  - 过渡期间圆角改为恒定策略，移除“中途圆角渐变”过程，避免视觉抖动与形变感。
  - 关闭阶段移除过冲回弹，改为单段收拢到源位置，降低违和感。
  - 图片预览改为非 Dialog 全局 Overlay Host，避免深层列表项内弹窗导致的返回手势失效。
  - 新增预测性返回手势联动：支持边滑边预览退出进度，手势取消时平滑回弹，退出过程可中断。
  - 根据反馈取消图片预览“可打断跟手”返回动画，统一为固定退场动画，避免中途停留和手势状态不一致。
  - 修复预测返回取消时偶发停留在中间态（卡住不回位）的问题，新增常规 Back 兜底与回弹收敛保障，避免手势穿透回到首页后预览层残留。

### 📦 Release

- **Version Bump**: Updated app version to `5.3.4` (`versionCode` `70`).

## [5.3.3] - 2026-02-14

### 🛠 Improvements & Fixes (优化与修复)

- **Bangumi 时间线稳定性修复**:
  - 在 `BangumiTimelineScreen` 中将列表 key 调整为 `seasonId + episodeId` 复合 key，避免 `episodeId` 重复时导致的列表崩溃问题。
- **文案一致性修正（Watch Later）**:
  - 全局修正“稀后再看”为“稍后再看”，统一了侧边栏、底部导航与相关注释文案。
- **竖屏播放器交互完善**:
  - 顶栏去除重复的三点菜单入口，避免与底部“简介”功能重叠。
  - 横竖屏切换入口下移至右下角操作区，单手触达更顺手。
  - 竖屏支持长按倍速播放（松手恢复原速），并增加实时倍速反馈。
  - 竖屏分享文案补齐视频标题（格式：`【标题】+ 链接`）。
- **首页顶栏视觉比例微调**:
  - 顶栏选中背景（胶囊）整体缩小，缓解“色块偏大”观感。
  - 顶栏文字字号小幅上调（仍低于旧版体量），提升可读性与对比平衡。
  - 关键尺寸改为集中 token 策略，后续可快速回滚/AB 调整。
- **首页顶栏自定义（显示/隐藏/排序）**:
  - 新增顶部标签顺序与可见项持久化配置（DataStore）。
  - 设置页支持顶部标签显隐与排序（`推荐`固定显示），并支持一键重置。
  - 首页顶部标签映射改为按“当前配置列表”驱动，移除对固定索引的依赖。
  - 直播入口路由改为按标签语义判断，避免自定义排序后点击错位。
  - 修复顶栏配置热更新时 `HorizontalPager` 可能发生的越界崩溃（索引保护 + 页码钳制）。
- **今日推荐单可用性优化**:
  - 点击推荐单视频后会立即从当前队列移除，降低“看完即废”的体感。
  - 已点击条目在后续重建推荐时会被过滤，避免短时间重复回流。
  - 卡片内补充了简短使用说明（点击即移除、下拉可换一批）。
- **竖屏交互继续对齐（P2）**:
  - 修复切换竖屏后“优先显示封面、未直接起播”的问题，改为播放器就绪后直出播放画面。
  - 进一步修正为“首帧渲染后才隐藏封面”，避免竖屏切换瞬间出现黑屏+播放键。
  - 竖屏第一页进度条支持使用进入时进度做初始种子，并在暂停/等待首帧阶段保持进度显示。
  - 竖屏入口改为“进入瞬间进度快照”驱动，避免重组时读取实时位置导致进度条回退。
  - 暂停图标显示条件改为“真实暂停态”判定，缓冲/待播期间不再误显示大播放键。
  - 修复共享播放器模式下的进度回写冲突：竖屏播放期间不再对同一 ExoPlayer 执行周期性回写 seek，避免“首帧重复/画面抽动”。
  - 修复横屏点击“竖屏”路由错误：不再回落到普通详情页，改为直接进入竖屏沉浸播放流（必要时先退出横屏全屏）。
  - 竖屏右下角旋转入口图标由“窗口样式”替换为“屏幕旋转”图标，语义更贴近“切换横屏”。
  - 启动“单 ExoPlayer 复用”重构第一阶段：竖屏页复用主播放器实例，仅切换承载容器，减少进竖屏重拉流与状态丢失。
  - 竖屏底部右下角去重，保留单一横竖屏切换入口，避免双按钮语义重叠。
  - 标题支持直接点击打开“简介 + 推荐”面板，推荐项可直接跳转到竖屏流内对应视频。
  - 竖屏 UP 信息区支持点击进入 UP 主页。
- **首页/稍后再看删除动效加速（参考 Telegram 风格）**:
  - 不感兴趣与稍后再看删除统一切换为“快版”消散预设，减少拖沓感。
  - 缩短粒子动画总时长与波前扩散时间，删除反馈更干脆。
  - 稍后再看页面新增“批量删除”模式（选择/全选/确认删除）。
- **播放加载链路性能优化（针对“加载慢、卡顿”反馈）**:
  - 自动最高画质策略分级：非大会员优先稳定起播档，降低高画质协商失败概率。
  - 新增 APP API 风控冷却（命中 `-351` 后短期跳过 APP API），避免无效重试。
  - DASH 高画质失败后快速回落到 80，减少重试等待。
  - 首帧优先策略：非关键请求延后触发，并限制推荐预加载仅在 Wi-Fi 下执行。
- **发布渠道安全提示增强**:
  - 设置页“关于与支持”新增常驻发布渠道声明卡片（固定展示官方渠道）。
  - 设置页新增“发布渠道声明”入口弹窗，统一展示免责声明文案。
  - 新用户首次打开应用时弹出发布渠道声明（仅首次确认）。
- **UP 主页连续播放链路补强**:
  - 空间页“播放全部”和视频列表点击改为构建外部播放列表（`setExternalPlaylist`），从所点视频开始顺序串联播放。
- **测试补充**:
  - 扩展 `HomeTopCategoryPolicyTest`，覆盖自定义顺序/可见性与兜底逻辑。
  - 扩展 `TopTabLayoutPolicyTest`，覆盖直播路由语义判断逻辑。
  - 新增 `TodayWatchQueuePolicyTest`，覆盖点击消费后的移除与补货判定逻辑。
  - 扩展 `TodayWatchPolicyTest`，覆盖已消费条目过滤逻辑。
  - 新增 `PortraitSharePolicyTest`，覆盖竖屏分享文案拼装规则。
  - 扩展 `PortraitFullscreenOverlayPolicyTest`，覆盖顶栏重复菜单隐藏策略。
  - 扩展 `PortraitPagerSwitchPolicyTest`，覆盖竖屏封面显示策略。
  - 新增 `SpacePlaybackPolicyTest`，覆盖 UP 空间外部播放列表构建与起播索引逻辑。

### 📦 Release

- **Version Bump**: Updated app version to `5.3.3` (`versionCode` `69`).

## [5.3.2] - 2026-02-13

### 🛠 Improvements & Fixes (优化与修复)

- **Top/Bottom Label Alignment Rework**:
  - reduced top-tab selected-state scaling in `图标+文字` mode to remove visual drop/misalignment
  - normalized top-tab icon/text metrics (icon size, line-height, spacing) for consistent optical center
  - adjusted bottom-bar icon+text metrics and baseline to improve icon/title alignment consistency
- **Version Bump**: Updated app version to `5.3.2` (`versionCode` `68`).

## [5.3.1] - 2026-02-13

### ✨ New Features (新增功能)

- **Bangumi Policy Layer**: Added dedicated policy modules to split view logic from screens/viewmodels:
  - `BangumiFollowStatusPolicy`
  - `BangumiModePolicy`
  - `BangumiPlaybackUrlPolicy`
  - `BangumiSeasonActionPolicy`
  - `BangumiUiPolicy`
  - `MyFollowPolicy`
  - `MyFollowStats`
  - `MyFollowStatsDetailPolicy`
  - `MyFollowWatchInsightPolicy`
- **Home Top Category Policy**: Added `HomeTopCategoryPolicy` to centralize top-tab category order/mapping.
- **Mine Drawer Visual Policy**: Added `MineSideDrawerVisualPolicy` for blur/opacity/scrim tuning in one place.
- **Danmaku Settings Policy**: Added `DanmakuSettingsPolicy` for opacity normalization and boundary handling.
- **Watch Later External Playlist Policy**: Added `WatchLaterPlaybackPolicy` to build external queue and start index from clicked item.
- **Top Tab Label Mode Setting**: Added configurable top-tab label mode (`图标+文字 / 仅图标 / 仅文字`) in settings and wired to home header.
- **Playlist Persistence**: Added `PlaylistManager` state persistence/restore (playlist, index, play mode, external-queue flag) and app-start initialization.

### 🛠 Improvements & Fixes (优化与修复)

- **Top Tab Refraction Behavior**: Top indicator now disables refraction when fully stationary; refraction only applies during drag/settle motion.
- **Top Tab Text Clarity**: Removed double-text crossfade rendering that caused ghosting/blur on selected tabs; switched to single-layer color interpolation.
- **Top Tab Icon+Text Layout Polish**: Improved visual placement by changing icon+text style to a cleaner horizontal arrangement.
- **Watch Later Playback Order**:
  - card click now also sets external playlist (not only top-right "play all")
  - queue starts from the clicked item index
  - playback flow no longer falls back to recommended queue unexpectedly in this scenario
- **Search Repository Reliability**: Improved video search fallback path (`all/v2`) and page-info handling/logging.
- **Danmaku Repository Robustness**:
  - refined segment-count fallback policy
  - strengthened thumb-up state resolution and error message mapping
  - improved segment cache safety behavior
- **Eye Protection Logic Cleanup**: Consolidated eye-care decision/tuning/reminder logic into policy helpers for predictable behavior.
- **Bangumi Experience Refinement**:
  - clearer follow-state preload flow
  - safer season-id resolution for action routes
  - cleaner playback-url collection and UI sizing rules
  - improved MyFollow type/stat/watch-insight derivation

### ✅ Tests (测试)

- Added and/or updated unit tests:
  - `BangumiFilterAndSearchTypePolicyTest`
  - `BangumiFollowStatusPolicyTest`
  - `BangumiModePolicyTest`
  - `BangumiPlaybackUrlPolicyTest`
  - `BangumiSeasonActionPolicyTest`
  - `BangumiUiPolicyTest`
  - `MyFollowPolicyTest`
  - `MyFollowStatsPolicyTest`
  - `MyFollowStatsDetailPolicyTest`
  - `MyFollowWatchInsightPolicyTest`
  - `HomeTopCategoryPolicyTest`
  - `MineSideDrawerVisualPolicyTest`
  - `TopTabLayoutPolicyTest`
  - `TopTabLabelModePolicyTest`
  - `TopTabRefractionPolicyTest`
  - `DanmakuRepositoryPolicyTest`
  - `DanmakuSettingsPolicyTest`
  - `EyeProtectionPolicyTest`
  - `LiquidLensProfileTest`
  - `VideoPlaybackUseCaseQualitySwitchTest`
  - `WatchLaterPlaybackPolicyTest`

### 📦 Release

- **Version Bump**: Updated app version to `5.3.1` (`versionCode` `67`).

## [5.3.0] - 2026-02-12

### ✨ New Features (新增功能)

- **Today Watch Plugin (今日推荐单插件)**: Added a new built-in plugin that locally analyzes watch history and builds a daily recommendation queue with two modes:
  - `今晚轻松看`
  - `深度学习看`
- **Today Watch Card UI**: Added a dedicated recommendation card in Home/Recommend with:
  - mode switch chips
  - UP 主榜
  - recommended video queue (with UP avatar/name)
  - per-item explanation tags
- **Local Personalization Stores**:
  - added creator-profile persistence store (`TodayWatchProfileStore`)
  - added negative-feedback persistence store (`TodayWatchFeedbackStore`)
- **Eye Protection 2.0**: Rebuilt eye-care plugin with:
  - three presets (`轻柔 / 平衡 / 专注`) plus DIY tuning
  - real-time settings preview
  - reminder cadence + snooze options
  - richer humanized reminder copy

### 🛠 Improvements & Fixes (优化与修复)

- **Cold Start Discoverability**: Fixed issue where Today Watch card was loaded but often out of viewport on cold start; now applies one-shot startup reveal strategy during startup window.
- **Refresh Toast Lifecycle Fix**: Fixed issue where “新增 X 条内容” hint could remain on screen and not auto-dismiss reliably.
- **Recommendation Signal Upgrade**:
  - fused history completion + recency + creator affinity
  - linked eye-care night signal (shorter, lower-stimulation preference at night)
  - integrated dislike penalties (video / creator / keyword)
  - diversified queue ordering to avoid consecutive same-creator streaks
- **Playback Quality Switching Reliability**:
  - quality options now prioritize actual DASH switchable tracks
  - cache switching now requires exact quality match; falls back to API fetch when missing
  - improved quality-switch toast wording for clearer fallback explanation
- **History Model Enrichment**: Added `author_mid` mapping in history response conversion so creator affinity can be computed accurately.
- **Plugin Registry Update**: Built-in plugin count updated from 4 to 5 by registering Today Watch plugin in app startup.
- **App Icon Switching Fix**: Resolved icon switching errors caused by mismatched Telegram activity-alias names during app startup icon-state sync (`icon_telegram_pink`, `icon_telegram_purple`, `icon_telegram_dark`).

### ✅ Tests (测试)

- Added and verified unit tests:
  - `TodayWatchPolicyTest`
  - `TodayWatchMotionPolicyTest`
  - `TodayWatchStartupRevealPolicyTest`
  - `EyeProtectionPolicyTest`
  - `VideoPlaybackUseCaseQualitySwitchTest`

### 📦 Release

- **Version Bump**: Updated app version to `5.3.0`.

## [5.2.2] - 2026-02-11

### ✨ New Features (新增功能)

- **Danmaku Interaction Callback**: Wired danmaku click callback end-to-end for context menu and interaction extension scenarios.

### 🛠 Improvements & Fixes (优化与修复)

- **Portrait Video Mode Upgrade**: Improved portrait-mode player flow, including playback continuity when swiping between videos, progress synchronization across portrait/landscape transitions, and overlay control consistency.
- **Dynamic Feed UX**: Added dynamic-tab bottom reselect double-tap to top behavior and improved smoothness of Home/Dynamic return-to-top animations.
- **Forwarded Dynamic Images**: Fixed an issue where images inside forwarded dynamics could not be opened for preview.
- **Image Preview Animation Polish**: Unified open/close motion for image preview dialog across entry points, with smoother rounded-corner transitions and spring-like close rebound.
- **Inbox User Info Stability**: Improved reliability of avatar/username resolution in private-message list after repeated entry.
- **Version Bump**: Updated app version to `5.2.2`.

## [5.2.1] - 2026-02-11

### 🛠 Improvements & Fixes (优化与修复)

- **Space Dynamic Navigation**: Fixed an issue where image/text dynamics in personal space could not be opened; now dynamic cards route correctly:
  - video dynamics -> native video detail
  - non-video dynamics -> dynamic detail page (`t.bilibili.com/{id_str}`)
- **Home Double-Tap Stability**: Fixed blank area appearing when double-tapping Home from non-top position with "header auto-collapse" enabled; Home double-tap now restores top header/tabs before scroll/refresh.
- **Liquid Glass Indicator Tuning**: Improved bottom bar indicator geometry in icon+text mode so labels participate in refraction more reliably.
- **Version Bump**: Updated app version to `5.2.1`.

## [5.2.0] - 2026-02-10

### ✨ New Features (新增功能)

- **Top Tabs Style Sync**: Top category tab bar now follows bottom bar style linkage, supporting floating/non-floating, blur, and liquid glass modes with unified visual language.
- **Refraction Upgrade**: Added stronger liquid lens profile for tab/bottom indicators during horizontal slide, with a clearer spherical feel and edge-space warp.
- **Incremental Timeline Refresh**: Added optional incremental refresh for Recommend/Following/Dynamic feeds, preserving old content and prepending only new items.
- **Refresh Delta Feedback**: Added "new items count" prompt after manual refresh and an old-content divider cue in Recommend.

### 🛠 Improvements & Fixes (优化与修复)

- **Top Indicator Geometry**: Refined top indicator size/shape/centering and boundary clamping to prevent clipping and offset drift when sliding.
- **Bottom Indicator Refraction Source**: Fixed cases where icon/text were not clearly refracted by switching to icon-layer backdrop capture.
- **Default Visual Bootstrapping**: Added one-time startup migration to ensure default Home visual settings are enabled on first launch after update:
  - floating bottom bar
  - liquid glass enabled
  - top blur enabled
- **Version Bump**: Updated app version to `5.2.0`.

## [5.1.4] - 2026-02-08

### 🛠 Improvements & Fixes (优化与修复)

- **Playback Fix**: Resolved playback issues in certain scenarios.

## [5.1.3] - 2026-02-08

### ✨ New Features (新增功能)

- **Search Upgrade**: Extended search types and interaction flow (视频/UP/番剧/直播), improved suggestion/discover results, and optimized pagination/loading behavior.
- **Comment Preference**: Added configurable default comment sort preference and synchronized it across comment entry points.
- **Danmaku Plugin 2.0**: Added user ID/hash blocking for danmaku plugins, plus in-play hot refresh when danmaku plugin configs/rules change.
- **Fullscreen Clock**: Added a top time display in landscape/fullscreen overlays.
- **Settings Tips Expansion**: Added more hidden usage tips in the tips page.
- **Version Easter Egg**: Enhanced version-click visual/easter-egg effects and toggles.

### 🛠 Improvements & Fixes (优化与修复)

- **Bottom Bar UX**: Reworked bottom bar visibility rules for top-level destinations and fixed alignment/position issues when tab count changes.
- **Playback Completion UX**: When "Auto-play next" is disabled, playback completion no longer forces intrusive action popups.
- **Background Playback Fix**: Fixed an issue where switching recommended videos inside detail page could dirty lifecycle flags and cause unexpected pause on Home/background.
- **Gesture Anti-MisTouch**: Brightness/volume vertical gestures are now limited to left/right one-third zones; center zone no longer triggers accidental adjustments.
- **Like Icon Unification**: Replaced heart-like visuals with thumb-up style across key interaction surfaces.
- **Comment Logic Reliability**: Fixed missing UP/置顶 comments under some sort modes and improved mixed-source comment loading behavior.
- **Firebase Telemetry Hardening**: Strengthened Firebase Analytics + Crashlytics integration (user/session context, custom keys, screen/event tracing, and error domain reporting).
- **General Stability**: Multiple UI/state synchronization fixes across home, video detail, plugin center, and settings.

## [5.1.1] - 2026-02-07

### ✨ New Features (新增功能)

- **Experimental Features**: Added some experimental features for better user experience.

### 🛠 Improvements & Fixes (优化与修复)

- **System Stability**: Fixed some known issues and optimized layout performance.

## [5.1.0] - 2026-02-06

### 🛠 Improvements (优化)

- **Scrolling Performance**: Optimized list scrolling performance and reduced recomposition overhead.
- **UI Interaction**: Enhanced card press feedback and physics.

## [5.0.5] - 2026-02-05

### ✨ New Features (新增功能)

- **Video Player Optimization**: Narrowed brightness/volume trigger zones in portrait mode to prevent accidental triggers when swiping for fullscreen.
- **AI Summary**: Added support for AI-generated video summaries.
- **Music Identification**: Added support for identifying and searching for BGM in videos.
- **Version Bump**: Updated app version to 5.0.5.

### 🛠 Improvements (优化)

- **Engineering**: Removed mandatory dependency on `google-services.json` for cleaner builds.
- **Tablet Support**: Improved drawer and bottom bar interaction on tablets.
- **Messaging**: Enhanced private message loading and added video link previews.

## [5.0.1] - 2026-02-01

### ✨ New Features (新增功能)

- **Deep Link Support**: Added comprehensive support for Bilibili links (Video, Live, Space, Dynamic). Supports `bilibili.com`, `m.bilibili.com`, `live.bilibili.com`, `space.bilibili.com`, `t.bilibili.com`.
- **Playback Controls**:
  - Added "Loop Single" (单曲循环) mode.
  - Added "Shuffle" (随机播放) mode.
  - Added "Sequential" (顺序播放) mode.
  - Added "Pause on Completion" (播完暂停) logic when auto-play is disabled.
- **Settings**:
  - Fixed "Auto-Play Next" setting synchronization.

### 🐛 Bug Fixes (修复)

- **UI**: Fixed "Share" button in video detail screen not responding.
- **UI**: Renamed "IP属地" to "IP归属地" for consistency.
- **Compilation**: Resolved build errors related to `PlaylistManager` and `PlayMode`.
