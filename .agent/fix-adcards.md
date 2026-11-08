# 广告卡反弹修复（1.3.0 之后）

用户：2026-11-08「主页和我页面的广告反弹了」，并明确：
「对于你认为不会变的文字，可以根据文字匹配，但是如果有专门的名称，还是匹配名称」。

## 为什么反弹
1.3.0 的 d7de503 把字面量匹配全部删掉了（当时要求「禁止按文字匹配控件」），
其中「绑定作业帮APP账号」推广卡规则一起没了；
另外 1.3.0 把首页清理从 5 趟(600→12000ms)砍到 2 趟(600/2500ms)，
而首页信息流/我的页广告卡是**异步加载**的，2.5s 之后才 inflate 的卡没人管。

## 新的判据（优先类名，稳定文案兜底）
dex 类表（analysis/classes.txt）里能直接看出广告专用类：
- `com.baidu.homework.activity.newhomepage.a_home.holder.AD*Holder`
  （ADNewBaseHolder / ADInnerNativeBaseHolder / ADNativeHolder12680 / ADNewGroupBaseHolder …）
  = 首页信息流广告 Holder；名字带版本号后缀，**只能前缀匹配**；
- `com.baidu.homework.activity.user.user_tab.holder.VipAbCardViewHolder`
  = 「我的」页顶部会员/广告横滑卡；
- `com.baidu.homework.adx.widget.XMarqueeView`（adx＝广告交易）= 广告卡里的跑马灯一行 → 藏它所在的整张卡；
- `com.baidu.homework.activity.user.user_tab.jgw.UserJGWBannerView` = 「我的」页推广位 → 整块藏；
- 稳定文案兜底：`绑定作业帮APP账号`（账号推广卡，文案多年不变）。

## 实现
新增 `AdCardKiller`：
- hook `androidx.recyclerview.widget.RecyclerView$ViewHolder` 构造器 **一次**，
  构造完成后只做一次类名前缀比较；命中就 GONE itemView。
  好处：不受异步加载影响（卡一创建就被压），也不做任何全树遍历。
- adx 跑马灯 / JGW 推广位：hook 各自**构造器**，挂 attach 监听，attach 时才藏父容器（构造期还没 parent）。
- 防「反弹」：命中的 itemView 再挂 attach + layout 监听，谁把它设回 VISIBLE 就再压回 GONE。
- 成本：每个 ViewHolder 一次字符串比较；没有定时器、没有全树扫描、不读文字（除稳定文案那一条）。

HomeChromeKiller 恢复「绑定作业帮APP账号」卡片规则（复用原有那两趟遍历，不新增遍历）。
