# K8S 部署指南

## 前置要求
- K8S集群（Minikube / Docker Desktop / 云厂商）
- kubectl 已配置
- Docker 已构建镜像

## 快速部署

### 1. 构建Docker镜像
```bash
# 在项目根目录执行
docker build -t seckill:latest .
```

### 2. 部署到K8S
```bash
# Linux/Mac
cd k8s
chmod +x deploy.sh
./deploy.sh

# Windows
cd k8s
deploy.bat
```

### 3. 手动部署
```bash
kubectl create namespace seckill
kubectl apply -k k8s/ -n seckill
```

## 资源说明

| 文件 | 说明 |
|------|------|
| deployment.yaml | 应用Deployment（3副本） |
| service.yaml | 应用Service（ClusterIP） |
| ingress.yaml | Ingress配置 |
| configmap.yaml | 配置（Redis地址） |
| secret.yaml | 敏感配置（数据库密码、JWT密钥） |
| mysql.yaml | MySQL部署+PVC+InitSQL |
| redis.yaml | Redis部署+Service |
| kustomization.yaml | Kustomize资源编排 |

## 访问方式

### 方式1：修改hosts
```
127.0.0.1 seckill.example.com
```

### 方式2：端口转发
```bash
kubectl port-forward -n seckill service/seckill-service 8080:80
```

## 常用命令

```bash
# 查看状态
kubectl get pods -n seckill
kubectl get services -n seckill

# 查看日志
kubectl logs -f -l app=seckill -n seckill

# 进入容器
kubectl exec -it $(kubectl get pod -l app=seckill -n seckill -o jsonpath='{.items[0].metadata.name}') -n seckill -- bash

# 扩缩容
kubectl scale deployment seckill --replicas=5 -n seckill

# 删除
kubectl delete namespace seckill
```

## 生产环境建议

1. **Secret管理**: 使用外部密钥管理（如Vault）
2. **监控**: 添加Prometheus + Grafana
3. **日志**: 添加ELK日志收集
4. **HPA**: 配置自动扩缩容
5. **PVC**: 使用云存储（如阿里云OSS）
