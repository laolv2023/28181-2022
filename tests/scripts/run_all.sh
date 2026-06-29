#!/usr/bin/env bash
# run_all.sh — 全量测试入口
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

echo "╔═══════════════════════════════════════════╗"
echo "║   WVP GB/T 28181-2022 合规性测试          ║"
echo "╚═══════════════════════════════════════════╝"
echo ""

echo "=== 第1阶段: Java 单元测试 ==="
bash "$SCRIPT_DIR/run_java_tests.sh"
echo ""

echo "=== 第2阶段: SIPp 场景测试 ==="
bash "$SCRIPT_DIR/run_sipp_tests.sh"
echo ""

echo "=== 测试完成 ==="
