package com.example.regionsync.model;

/**
 * 数据类别枚举 — 每种数据有且只有一个 Master Region。
 *
 * <pre>
 * ┌──────────────────┬────────────────┬──────────────────────────┐
 * │ 数据类型          │ Master 地区     │ 其他地区角色              │
 * ├──────────────────┼────────────────┼──────────────────────────┤
 * │ SYSTEM_CONFIG    │ NA (总部)       │ Slave (只读)             │
 * │ ROLE_PERMISSION  │ NA (总部)       │ Slave (只读)             │
 * │ JOB              │ 各自 Region     │ Slave (只读/按需同步)     │
 * │ TALENT           │ 各自 Region     │ Slave (只读/按需同步)     │
 * │ FINANCE          │ 各自 Region     │ 不同步 (合规要求)         │
 * └──────────────────┴────────────────┴──────────────────────────┘
 * </pre>
 */
public enum DataCategory {

    /** 系统配置 — 全局只读，Master 固定为 NA */
    SYSTEM_CONFIG,

    /** 权限/角色 — 全局只读，Master 固定为 NA */
    ROLE_PERMISSION,

    /** 职位数据 — 按 Region 归属，各 Region 是自己 Job 的 Master */
    JOB,

    /** 人才数据 — 按 Region 归属，各 Region 是自己 Talent 的 Master */
    TALENT,

    /** 财务数据 — 各 Region 本地读写，不跨区同步（合规要求） */
    FINANCE
}
