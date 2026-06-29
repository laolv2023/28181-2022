#!/usr/bin/env bash
# run_sipp_tests.sh — 运行所有SIPp测试场景
# 用法: bash run_sipp_tests.sh [WVP_IP:PORT]
set -euo pipefail

WVP_ADDR="${1:-127.0.0.1:5060}"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
TESTS_DIR="$(dirname "$SCRIPT_DIR")/sipp"

PASS=0
FAIL=0
FAILED_TESTS=""

echo "═══════════════════════════════════════════"
echo "  WVP GB/T 28181-2022 SIPp 测试"
echo "  目标: $WVP_ADDR"
echo "═══════════════════════════════════════════"

for scenario in $(find "$TESTS_DIR" -name '*.xml' | sort); do
    name=$(basename "$scenario" .xml)
    echo -n "  [$name] ... "
    if sipp "$WVP_ADDR" -sf "$scenario" -m 1 -timeout 10s -timeout_error 2>/dev/null; then
        echo "PASS"
        PASS=$((PASS + 1))
    else
        echo "FAIL"
        FAIL=$((FAIL + 1))
        FAILED_TESTS="$FAILED_TESTS\n  - $name"
    fi
done

echo ""
echo "═══════════════════════════════════════════"
echo "  总计: $((PASS + FAIL))  通过: $PASS  失败: $FAIL"
if [ "$FAIL" -gt 0 ]; then
    echo -e "  失败用例:$FAILED_TESTS"
fi
echo "═══════════════════════════════════════════"
exit $FAIL
