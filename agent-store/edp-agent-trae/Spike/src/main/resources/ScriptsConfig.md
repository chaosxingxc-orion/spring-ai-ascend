---
# 话术配置文件
# 该文件包含 EDPAgent 的所有话术配置

# 通用话术（用于工具调用、todolist 等）
scripts:
  tool_start: "正在调用：{tool_name}"
  tool_end: "{tool_name} 执行完成"
  todo_start: "开始执行：{title}"
  todo_end: "{title} 已完成"
  todolist_start: "规划任务清单"
  todolist_end: "todolist规划完成"
  interrupt_start: "需要您确认以下信息"
  product_recommend_success: "我找到以上您可能感兴趣的产品，可以告诉我购买哪支产品及购买金额，如果不满意请告诉我重新推荐，比如换一批产品，或者持有周期在12个月以上的产品。"
  product_recommend_empty: '我理解你对稳健收益的追求，但"稳赚不赔"的产品在金融领域并不存在。我可以从其他角度出发，为你筛选一些历史表现稳健产品作为参考。'
  product_recommend_no_card: "当前理财账户没有绑定借记卡，请到理财菜单下的交易账户管理进行绑定"
  mcp_result_empty: "根据您的条件没有找到合适产品，您可以从以下产品中选择一个或者重新筛选。"
  request_start: "您的请求已收到。"
  planning_start: "我们正在为您进行规划。"
  product_select_confirm: "请确认是否购买{amount}元{productName}理财产品"
  product_select_missing_product: "您可以告诉我想要购买第几支产品"
  product_select_missing_amount: "请问您购买的金额是多少"
  product_select_invalid: "抱歉没有理解您的意思，请重新输入"
  task_cancelled: "好的，已为您取消当前操作。如需其他帮助，请随时告诉我。"
  cancel_confirm: "确认要取消当前操作吗？"
  out_of_scope: "正在学习中，暂不支持该业务。"
  fund_planning_success: "已为您完成理财产品购买"
  fund_planning_buy_failed: "购买失败，请重新尝试"
  fund_planning_transfer_limit: "您已超过转账次数限制，购买失败"
  fund_planning_balance_insufficient: "您的活期账户余额不足，结束理财产品购买"
  fund_planning_card_mismatch: "您只有一张卡，不满足当前理财购买流程，已退出购买"
  fund_planning_purchase_aborted: "购买异常，已退出购买流程"
  fund_planning_session_timeout: "对话超时，已退出购买流程"
  fund_planning_wealth_insufficient: "理财账户资金不足，查询活期账户余额"
  fund_planning_both_insufficient: "您的活期账户余额不足，结束理财产品购买"

# think_chunk 推送模式配置
# real_stream: 真实 LLM 流式 shard 直接推送前端
# fixed_script: 用预定义固定话术帧替代 LLM shard 推送前端
think_chunk_mode: fixed_script

# 固定话术帧配置（仅 think_chunk_mode=fixed_script 时生效）
think_chunk_fixed_scripts:
  enabled: true
  chars_per_frame: 4          # 每帧2字符（逐字渲染模式，0=不切分整句推送）
  tokens_between_frames: 2   # 每累积2个LLM token推送一个子帧
  min_interval_ms: 50        # 子帧间最少间隔100ms（0=不限速）

  # ── planning 阶段话术（第1轮思考）──────────────────────────────
  # 默认话术（query_patterns 全未命中时降级使用）
  default_scripts:
    - "正在分析您的需求..."
  # 按 query 关键词匹配的差异化话术组（按声明顺序遍历，首个命中即生效）
  query_patterns:
    - keywords: ["推荐", "理财", "产品", "筛选"]
      scripts:
        - "正在搜索理财产品..."
    - keywords: ["购买", "买", "下单", "确认"]
      scripts:
        - "正在确认购买信息..."
    - keywords: ["余额", "查询", "账户", "转账"]
      scripts:
        - "正在查询账户信息..."

  # ── executing 阶段话术（第2轮及后续思考轮次）────────────────────
  # 当 Agent 在执行工具后进入反思/决策思考时使用
  execution_scripts:
    - "正在分析执行结果..."

  # ── resuming 阶段话术（Cascade 续轮，query="continue"）─────────
  # 当用户在中断后回复触发续轮时使用
  # enable_resume_scripts: 是否启用 resuming 阶段固定话术（默认 true）
  # 设为 false 时，resuming 阶段不输出固定话术
  enable_resume_scripts: false
  resume_scripts:
    - "当前业务步骤已为您处理完毕"

  # 保留 scripts 字段（向后兼容：以上所有字段均为空时降级使用）
  scripts:
    - "正在分析您的需求..."
---
