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
        err "请先克隆: git clone https://github.com/648540858/wvp-GB28181-pro.git $WVP_DIR"
        exit 1
    fi
    [ -d "$SRC_DIR" ] || { err "升级源码目录不存在: $SRC_DIR"; exit 1; }
    [ -d "$PATCH_DIR" ] || { err "补丁目录不存在: $PATCH_DIR"; exit 1; }
    log "前置检查通过"
}

copy_new_files() {
    log "步骤1: 复制新增/替换文件..."
    local count=0 failed=0
    while IFS= read -r -d '' file; do
        local rel_path="${file#$SRC_DIR/}"
        local target="$WVP_DIR/$rel_path"
        mkdir -p "$(dirname "$target")"
        if cp "$file" "$target"; then
            count=$((count + 1)); log "  ✅ $(basename "$file")"
        else
            failed=$((failed + 1)); warn "  ❌ $(basename "$file")"
        fi
    done < <(find "$SRC_DIR" -name '*.java' -print0)
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
    local new_files=(
        "src/main/java/com/genersoft/iot/vmp/gb28181/utils/GBProtocolVersionHelper.java"
        "src/main/java/com/genersoft/iot/vmp/gb28181/auth/SM3DigestHelper.java"
        "src/main/java/com/genersoft/iot/vmp/gb28181/auth/RegisterRedirectHelper.java"
        "src/main/java/com/genersoft/iot/vmp/gb28181/utils/SdpFieldHelper.java"
        "src/main/java/com/genersoft/iot/vmp/gb28181/utils/MansrtspHelper.java"
        "src/main/java/com/genersoft/iot/vmp/gb28181/utils/TcpReconnectHelper.java"
        "src/main/java/com/genersoft/iot/vmp/gb28181/utils/SipCharsetHelper.java"
        "src/main/java/com/genersoft/iot/vmp/gb28181/utils/GbCode2022.java"
        "src/main/java/com/genersoft/iot/vmp/gb28181/utils/SipMessageFilter.java"
        "src/main/java/com/genersoft/iot/vmp/gb28181/auth/CertAuthHelper.java"
        "src/main/java/com/genersoft/iot/vmp/gb28181/auth/DataIntegrityHelper.java"
        "src/main/java/com/genersoft/iot/vmp/gb28181/auth/GB35114Helper.java"
        "src/main/java/com/genersoft/iot/vmp/gb28181/utils/ExtensionApplicationHandler.java"
        "src/main/java/com/genersoft/iot/vmp/conf/SipTlsProperties.java"
        "src/main/java/com/genersoft/iot/vmp/gb28181/transmit/event/request/impl/message/query/cmd/HomePositionQueryMessageHandler.java"
        "src/main/java/com/genersoft/iot/vmp/gb28181/transmit/event/request/impl/message/query/cmd/CruiseTrackQueryMessageHandler.java"
        "src/main/java/com/genersoft/iot/vmp/gb28181/transmit/event/request/impl/message/query/cmd/PtzPreciseStatusQueryMessageHandler.java"
        "src/main/java/com/genersoft/iot/vmp/gb28181/transmit/event/request/impl/message/query/cmd/StorageCardStatusQueryMessageHandler.java"
        "src/main/java/com/genersoft/iot/vmp/gb28181/transmit/event/request/impl/message/notify/cmd/DeviceUpgradeResultNotifyMessageHandler.java"
        "src/main/java/com/genersoft/iot/vmp/gb28181/transmit/event/request/impl/message/notify/cmd/SnapshotFinishedNotifyMessageHandler.java"
        "src/main/java/com/genersoft/iot/vmp/gb28181/transmit/event/request/impl/message/control/cmd/SnapshotConfigMessageHandler.java"
    )
    for f in "${new_files[@]}"; do
        [ -f "$WVP_DIR/$f" ] && log "  ✅ $(basename "$f")" || { err "  ❌ $f (缺失)"; errors=$((errors + 1)); }
    done
    local patched_files=(
        "src/main/java/com/genersoft/iot/vmp/gb28181/transmit/event/request/impl/RegisterRequestProcessor.java"
        "src/main/java/com/genersoft/iot/vmp/gb28181/auth/DigestServerAuthenticationHelper.java"
        "src/main/java/com/genersoft/iot/vmp/gb28181/transmit/cmd/impl/SIPCommander.java"
        "src/main/java/com/genersoft/iot/vmp/gb28181/bean/Device.java"
        "src/main/java/com/genersoft/iot/vmp/gb28181/transmit/event/request/impl/message/control/cmd/DeviceControlQueryMessageHandler.java"
        "src/main/java/com/genersoft/iot/vmp/conf/UserSetting.java"
    )
    for f in "${patched_files[@]}"; do
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
    for patch_file in "$PATCH_DIR"/*.patch; do
        log "  回退: $(basename "$patch_file")"
        patch -p3 -d "$WVP_DIR" --reverse --fuzz=3 < "$patch_file" 2>/dev/null || true
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
