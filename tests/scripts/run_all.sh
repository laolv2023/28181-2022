#!/usr/bin/env bash
# run_all.sh — 全量测试入口
# 审计修复P1-20: 收集退出码, 有失败时返回非零
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
OVERALL_FAIL=0

echo "╔═══════════════════════════════════════════╗"
echo "║   WVP GB/T 28181-2022 合规性测试          ║"
echo "╚═══════════════════════════════════════════╝"
echo ""

echo "=== 第1阶段: Java 单元测试 ==="
bash "$SCRIPT_DIR/run_java_tests.sh" || OVERALL_FAIL=1
echo ""

echo "=== 第2阶段: SIPp 场景测试 ==="
bash "$SCRIPT_DIR/run_sipp_tests.sh" || OVERALL_FAIL=1
echo ""

if [ "$OVERALL_FAIL" -eq 0 ]; then
    echo "=== 全部测试通过 ==="
else
    echo "=== 存在测试失败 ==="
fi
exit $OVERALL_FAIL
