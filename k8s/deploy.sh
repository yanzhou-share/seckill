#!/bin/bash

echo "=== 秒杀系统 K8S 部署脚本 ==="

# 检查kubectl
if ! command -v kubectl &> /dev/null; then
    echo "错误: kubectl 未安装"
    exit 1
fi

# 检查集群连接
if ! kubectl cluster-info &> /dev/null; then
    echo "错误: 无法连接到K8S集群"
    exit 1
fi

# 创建命名空间
echo "创建命名空间..."
kubectl create namespace seckill --dry-run=client -o yaml | kubectl apply -f -

# 部署所有资源
echo "部署资源..."
kubectl apply -k . -n seckill

# 等待Pod就绪
echo "等待Pod就绪..."
kubectl wait --for=condition=ready pod -l app=mysql -n seckill --timeout=120s
kubectl wait --for=condition=ready pod -l app=redis -n seckill --timeout=60s
kubectl wait --for=condition=ready pod -l app=seckill -n seckill --timeout=120s

# 显示状态
echo ""
echo "=== 部署状态 ==="
kubectl get pods -n seckill
echo ""
kubectl get services -n seckill
echo ""
kubectl get ingress -n seckill

echo ""
echo "=== 部署完成 ==="
echo "访问地址: http://seckill.example.com"
echo "(需要配置DNS或修改hosts文件)"
