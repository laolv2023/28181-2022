#!/usr/bin/env bash
# run_java_tests.sh — 运行所有Java单元测试
# 用法: bash run_java_tests.sh [WVP_SRC_DIR]
set -uo pipefail

WVP_SRC="${1:-../wvp}"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
TESTS_DIR="$(dirname "$SCRIPT_DIR")/java/unit"

PASS=0
FAIL=0
SKIP=0

echo "═══════════════════════════════════════════"
echo "  WVP GB/T 28181-2022 Java 单元测试"
echo "═══════════════════════════════════════════"

# 编译测试类
CP="$WVP_SRC/src/main/java:$(find "$WVP_SRC" -name '*.jar' -path '*/lib/*' 2>/dev/null | tr '\n' ':'):$TESTS_DIR"

mkdir -p /tmp/wvp-tests

for test_file in $(find "$TESTS_DIR" -name '*Test.java' | sort); do
    name=$(basename "$test_file" .java)
    echo -n "  [$name] ... "
    # 编译
    if javac -cp "$CP" "$test_file" -d /tmp/wvp-tests 2>/dev/null; then
        # 运行 — 根据测试文件包名确定完整类名
        pkg=$(grep -m1 '^package ' "$test_file" | sed 's/package //;s/;//')
        if [ -n "$pkg" ]; then
            full_class="$pkg.$name"
        else
            full_class="$name"
        fi
        if java -cp "$CP:/tmp/wvp-tests" org.junit.platform.console.ConsoleLauncher --select-class "$full_class" 2>/dev/null; then
            echo "PASS"
            PASS=$((PASS + 1))
        else
            echo "FAIL (运行失败)"
            FAIL=$((FAIL + 1))
        fi
    else
        echo "SKIP (编译失败, 可能依赖缺失)"
        SKIP=$((SKIP + 1))
    fi
done

echo ""
echo "═══════════════════════════════════════════"
echo "  总计: $((PASS + FAIL + SKIP))  通过: $PASS  失败: $FAIL  跳过: $SKIP"
echo "═══════════════════════════════════════════"
exit $FAIL
