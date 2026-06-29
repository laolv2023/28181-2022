#!/usr/bin/env bash
# merge.sh — WVP GB/T 28181-2022 合规性升级合并脚本
# news/ 目录为扁平结构，文件名→WVP目标路径映射通过 FILE_MAP 定义
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

# ═══════════════════════════════════════════════════════════
# 文件名 → WVP 目标路径映射表
# news/ 下的文件是扁平结构（无子目录），合并时需要复制到 WVP 的正确目录
# ═══════════════════════════════════════════════════════════
declare -A FILE_MAP=(
    ["DeviceControlType.java"]="src/main/java/com/genersoft/iot/vmp/common/enums/DeviceControlType.java"
    ["SipTlsProperties.java"]="src/main/java/com/genersoft/iot/vmp/conf/SipTlsProperties.java"
    ["CertAuthHelper.java"]="src/main/java/com/genersoft/iot/vmp/gb28181/auth/CertAuthHelper.java"
    ["DataIntegrityHelper.java"]="src/main/java/com/genersoft/iot/vmp/gb28181/auth/DataIntegrityHelper.java"
    ["GB35114Helper.java"]="src/main/java/com/genersoft/iot/vmp/gb28181/auth/GB35114Helper.java"
    ["RegisterRedirectHelper.java"]="src/main/java/com/genersoft/iot/vmp/gb28181/auth/RegisterRedirectHelper.java"
    ["SM3DigestHelper.java"]="src/main/java/com/genersoft/iot/vmp/gb28181/auth/SM3DigestHelper.java"
    ["SnapshotConfigMessageHandler.java"]="src/main/java/com/genersoft/iot/vmp/gb28181/transmit/event/request/impl/message/control/cmd/SnapshotConfigMessageHandler.java"
    ["DeviceUpgradeResultNotifyMessageHandler.java"]="src/main/java/com/genersoft/iot/vmp/gb28181/transmit/event/request/impl/message/notify/cmd/DeviceUpgradeResultNotifyMessageHandler.java"
    ["SnapshotFinishedNotifyMessageHandler.java"]="src/main/java/com/genersoft/iot/vmp/gb28181/transmit/event/request/impl/message/notify/cmd/SnapshotFinishedNotifyMessageHandler.java"
    ["CruiseTrackQueryMessageHandler.java"]="src/main/java/com/genersoft/iot/vmp/gb28181/transmit/event/request/impl/message/query/cmd/CruiseTrackQueryMessageHandler.java"
    ["HomePositionQueryMessageHandler.java"]="src/main/java/com/genersoft/iot/vmp/gb28181/transmit/event/request/impl/message/query/cmd/HomePositionQueryMessageHandler.java"
    ["PtzPreciseStatusQueryMessageHandler.java"]="src/main/java/com/genersoft/iot/vmp/gb28181/transmit/event/request/impl/message/query/cmd/PtzPreciseStatusQueryMessageHandler.java"
    ["StorageCardStatusQueryMessageHandler.java"]="src/main/java/com/genersoft/iot/vmp/gb28181/transmit/event/request/impl/message/query/cmd/StorageCardStatusQueryMessageHandler.java"
    ["ExtensionApplicationHandler.java"]="src/main/java/com/genersoft/iot/vmp/gb28181/utils/ExtensionApplicationHandler.java"
    ["GBProtocolVersionHelper.java"]="src/main/java/com/genersoft/iot/vmp/gb28181/utils/GBProtocolVersionHelper.java"
    ["GbCode2022.java"]="src/main/java/com/genersoft/iot/vmp/gb28181/utils/GbCode2022.java"
    ["MansrtspHelper.java"]="src/main/java/com/genersoft/iot/vmp/gb28181/utils/MansrtspHelper.java"
    ["SdpFieldHelper.java"]="src/main/java/com/genersoft/iot/vmp/gb28181/utils/SdpFieldHelper.java"
    ["SipCharsetHelper.java"]="src/main/java/com/genersoft/iot/vmp/gb28181/utils/SipCharsetHelper.java"
    ["SipMessageFilter.java"]="src/main/java/com/genersoft/iot/vmp/gb28181/utils/SipMessageFilter.java"
    ["TcpReconnectHelper.java"]="src/main/java/com/genersoft/iot/vmp/gb28181/utils/TcpReconnectHelper.java"
)

# 补丁修改的文件列表（用于验证）
declare -a PATCHED_FILES=(
    "src/main/java/com/genersoft/iot/vmp/gb28181/transmit/event/request/impl/RegisterRequestProcessor.java"
    "src/main/java/com/genersoft/iot/vmp/gb28181/auth/DigestServerAuthenticationHelper.java"
    "src/main/java/com/genersoft/iot/vmp/gb28181/transmit/cmd/impl/SIPCommander.java"
    "src/main/java/com/genersoft/iot/vmp/gb28181/bean/Device.java"
    "src/main/java/com/genersoft/iot/vmp/gb28181/transmit/event/request/impl/message/control/cmd/DeviceControlQueryMessageHandler.java"
    "src/main/java/com/genersoft/iot/vmp/conf/UserSetting.java"
)

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
    log "步骤1: 复制新增/替换文件..."
    local count=0 failed=0
    for src_file in "$SRC_DIR"/*.java; do
        local filename=$(basename "$src_file")
        local target_rel="${FILE_MAP[$filename]:-}"
        if [ -z "$target_rel" ]; then
            warn "  ⚠️ $filename (无映射, 跳过)"
            failed=$((failed + 1))
            continue
        fi
        local target="$WVP_DIR/$target_rel"
        mkdir -p "$(dirname "$target")"
        if cp "$src_file" "$target"; then
            count=$((count + 1)); log "  ✅ $filename → $target_rel"
        else
            failed=$((failed + 1)); warn "  ❌ $filename"
        fi
    done
    log "步骤1完成: 复制 $count 个文件, 失败 $failed 个"
}

apply_patches() {
    log "步骤2: 应用补丁..."
    local count=0 failed=0
    for patch_file in "$PATCH_DIR"/*.patch; do
        local patch_name=$(basename "$patch_file")
        log "  应用: $patch_name"
        if patch -p3 -d "$WVP_DIR" --fuzz=3 < "$patch_file" 2>&1; then
            count=$((count + 1)); log "    ✅ 成功"
        else
            failed=$((failed + 1))
            warn "    ❌ 失败 (可能已应用或存在冲突)"
            patch -p3 -d "$WVP_DIR" --dry-run --reverse < "$patch_file" 2>/dev/null && warn "    (补丁可能已应用)" || true
        fi
    done
    log "步骤2完成: 应用 $count 个补丁, 失败 $failed 个"
}

verify_merge() {
    log "步骤3: 验证合并结果..."
    local errors=0
    # 验证新增文件
    for filename in "${!FILE_MAP[@]}"; do
        local target="$WVP_DIR/${FILE_MAP[$filename]}"
        if [ -f "$target" ]; then
            log "  ✅ $filename"
        else
            err "  ❌ $filename (缺失)"; errors=$((errors + 1))
        fi
    done
    # 验证补丁
    for f in "${PATCHED_FILES[@]}"; do
        if grep -q "改造项" "$WVP_DIR/$f" 2>/dev/null; then
            log "  ✅ $(basename "$f") (补丁已应用)"
        else
            err "  ❌ $(basename "$f") (补丁未应用)"; errors=$((errors + 1))
        fi
    done
    grep -q "PTZ_PRECISE\|FORMAT_SDCARD\|TARGET_TRACK\|DEVICE_UPGRADE" \
        "$WVP_DIR/src/main/java/com/genersoft/iot/vmp/common/enums/DeviceControlType.java" 2>/dev/null && \
        log "  ✅ DeviceControlType (2022枚举值已添加)" || { err "  ❌ DeviceControlType (2022枚举值缺失)"; errors=$((errors + 1)); }
    log "═══════════════════════════════════════════"
    [ $errors -eq 0 ] && log "  合并验证通过！所有 38 个改造项已就位。" || err "  合并验证失败: $errors 个错误"
    log "═══════════════════════════════════════════"
}

revert_merge() {
    log "回退合并操作..."
    # 反向应用补丁
    for patch_file in "$PATCH_DIR"/*.patch; do
        log "  回退补丁: $(basename "$patch_file")"
        patch -p3 -d "$WVP_DIR" --reverse --fuzz=3 < "$patch_file" 2>/dev/null || true
    done
    # 删除新增文件
    for filename in "${!FILE_MAP[@]}"; do
        local target="$WVP_DIR/${FILE_MAP[$filename]}"
        [ -f "$target" ] && rm "$target" && log "  删除: $filename"
    done
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
