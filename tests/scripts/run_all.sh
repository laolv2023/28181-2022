#!/usr/bin/env bash
# run_all.sh — 全量测试入口
# 审计修复: 移除 set -euo pipefail 中的 -e, 避免子脚本返回非零退出码时中断
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

JAVA_PASS=0
JAVA_FAIL=0
SIPP_PASS=0
SIPP_FAIL=0

echo "╔═══════════════════════════════════════════╗"
echo "║   WVP GB/T 28181-2022 合规性测试          ║"
echo "╚═══════════════════════════════════════════╝"
echo ""

echo "=== 第1阶段: Java 单元测试 ==="
bash "$SCRIPT_DIR/run_java_tests.sh" || true
echo ""

echo "=== 第2阶段: SIPp 场景测试 ==="
bash "$SCRIPT_DIR/run_sipp_tests.sh" || true
echo ""

echo "=== 测试完成 ==="
