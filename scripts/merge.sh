#!/usr/bin/env bash
# merge.sh — WVP GB/T 28181-2022 合规性升级合并脚本
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_DIR="$(dirname "$SCRIPT_DIR")"
WVP_DIR="${1:-$REPO_DIR/wvp}"
SRC_DIR="$REPO_DIR/news"
PATCH_DIR="$REPO_DIR/patches"

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; NC='\033[0m'
log()  { echo -e "${GREEN}[$(date +%H:%M:%S)]${NC} $*"; }
warn() { echo -e "${YELLOW}[$(date +%H:%M:%S)] WARN:${NC} $*"; }
err()  { echo -e "${RED}[$(date +%H:%M:%S)] ERROR:${NC} $*" >&2; }

check_prerequisites() {
    log "═══════════════════════════════════════════"
    log "  WVP GB/T 28181-2022 合规性升级合并"
    log "  WVP 目录: $WVP_DIR"
    log "═══════════════════════════════════════════"
    if [ ! -d "$WVP_DIR/src/main/java" ]; then
        err "WVP 源码目录不存在: $WVP_DIR/src/main/java"
        err "WVP 源码应已包含在仓库 wvp/ 目录中。如缺失请重新克隆仓库。"
        exit 1
    fi
    [ -d "$SRC_DIR" ] || { err "升级源码目录不存在: $SRC_DIR"; exit 1; }
    [ -d "$PATCH_DIR" ] || { err "补丁目录不存在: $PATCH_DIR"; exit 1; }
    log "前置检查通过"
}

copy_new_files() {
    log "── 步骤1: 复制新增/替换文件 ──"
    local count=0
    while IFS= read -r -d '' file; do
        local rel="${file#$SRC_DIR/}"
        local target="$WVP_DIR/$rel"
        mkdir -p "$(dirname "$target")"
        cp "$file" "$target"
        count=$((count + 1))
        log "  复制: $rel"
    done < <(find "$SRC_DIR" -name '*.java' -print0)
    log "共复制 $count 个文件"
}

apply_patches() {
    log "── 步骤2: 应用补丁 ──"
    local count=0
    for patch_file in "$PATCH_DIR"/*.patch; do
        [ -f "$patch_file" ] || continue
        local name=$(basename "$patch_file")
        log "  应用: $name"
        if patch -p1 -d "$WVP_DIR" --fuzz=1 < "$patch_file"; then
            count=$((count + 1))
        else
            warn "  $name 应用失败（可能已应用或上下文不匹配）"
        fi
    done
    log "共应用 $count 个补丁"
}

verify_merge() {
    log "── 步骤3: 验证合并结果 ──"
    local ok=true

    for patch_file in "$PATCH_DIR"/*.patch; do
        local name=$(basename "$patch_file")
        if patch -p1 -d "$WVP_DIR" --dry-run --reverse < "$patch_file" >/dev/null 2>&1; then
            log "  ✅ $name: 已应用"
        else
            err "  ❌ $name: 未应用"
            ok=false
        fi
    done

    local file_count=$(find "$SRC_DIR" -name '*.java' | wc -l)
    local target_count=0
    while IFS= read -r -d '' file; do
        local rel="${file#$SRC_DIR/}"
        [ -f "$WVP_DIR/$rel" ] && target_count=$((target_count + 1))
    done < <(find "$SRC_DIR" -name '*.java' -print0)

    if [ "$target_count" -eq "$file_count" ]; then
        log "  ✅ 新增文件: $target_count/$file_count"
    else
        err "  ❌ 新增文件: $target_count/$file_count"
        ok=false
    fi

    if $ok; then
        log "验证通过，建议执行: cd $WVP_DIR && mvn compile -DskipTests"
    else
        warn "验证未完全通过，请检查上述问题"
    fi
}

revert_merge() {
    log "── 回退合并 ──"
    for patch_file in "$PATCH_DIR"/*.patch; do
        log "  回退补丁: $(basename "$patch_file")"
        patch -p1 -d "$WVP_DIR" --reverse --fuzz=1 < "$patch_file" 2>/dev/null || true
    done
    while IFS= read -r -d '' file; do
        local target="$WVP_DIR/${file#$SRC_DIR/}"
        [ -f "$target" ] && rm "$target" && log "  删除: $(basename "$target")"
    done < <(find "$SRC_DIR" -name '*.java' -print0)
    log "回退完成"
}

main() {
    case "${1:-}" in
        --verify) check_prerequisites; verify_merge ;;
        --revert) check_prerequisites; revert_merge ;;
        --help|-h)
            echo "用法: bash merge.sh [WVP目录|--verify|--revert|--help]"
            echo "  bash merge.sh                  合并到 wvp/ 目录"
            echo "  bash merge.sh /path/to/wvp     合并到指定目录"
            echo "  bash merge.sh --verify         仅验证"
            echo "  bash merge.sh --revert         回退合并"
            ;;
        *) check_prerequisites; copy_new_files; apply_patches; verify_merge
           log "合并完成！请执行: cd $WVP_DIR && mvn compile" ;;
    esac
}
main "$@"
