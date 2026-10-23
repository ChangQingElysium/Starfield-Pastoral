#!/bin/zsh
set -eu
cd "${0:A:h}"
if ! /usr/bin/python3 fabric/client.py launch "$@"; then
    print 'Fabric 启动失败。启动阶段日志：fabric/run-client/logs/launcher.log'
    print '游戏日志（如已生成）：fabric/run-client/logs/latest.log'
    read -r '?按回车关闭此窗口。'
    exit 1
fi
