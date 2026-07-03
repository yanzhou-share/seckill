@echo off
echo === 秒杀系统 K8S 部署脚本 ===

REM 检查kubectl
where kubectl >nul 2>nul
if %errorlevel% neq 0 (
    echo 错误: kubectl 未安装
    exit /b 1
)

REM 检查集群连接
kubectl cluster-info >nul 2>nul
if %errorlevel% neq 0 (
    echo 错误: 无法连接到K8S集群
    exit /b 1
)

REM 创建命名空间
echo 创建命名空间...
kubectl create namespace seckill --dry-run=client -o yaml | kubectl apply -f -

REM 部署所有资源
echo 部署资源...
kubectl apply -k . -n seckill

REM 等待Pod就绪
echo 等待Pod就绪...
kubectl wait --for=condition=ready pod -l app=mysql -n seckill --timeout=120s
kubectl wait --for=condition=ready pod -l app=redis -n seckill --timeout=60s
kubectl wait --for=condition=ready pod -l app=seckill -n seckill --timeout=120s

REM 显示状态
echo.
echo === 部署状态 ===
kubectl get pods -n seckill
echo.
kubectl get services -n seckill
echo.
kubectl get ingress -n seckill

echo.
echo === 部署完成 ===
echo 访问地址: http://seckill.example.com
echo (需要配置DNS或修改hosts文件)
